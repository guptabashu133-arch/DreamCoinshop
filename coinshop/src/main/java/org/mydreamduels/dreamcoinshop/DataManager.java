package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Player data: an in-memory copy for every online player, and one database thread that writes
 * each change straight away (in order, retried if the database is briefly gone). The server
 * thread never waits for the database, except once at login, which already runs async.
 *
 * Several servers can share one MySQL / MariaDB / PostgreSQL / Redis database. Every write then
 * also leaves a "this player changed" notice; the other servers pick it up (Redis: instantly,
 * SQL: every poll-ms) and reload that player if they have them online. Balance changes are
 * written as "+ amount", never as a full value, so two servers can't overwrite each other.
 */
final class DataManager implements Listener {

    @FunctionalInterface
    interface Op {
        void run(Storage storage) throws Exception;
    }

    private record Top(List<Storage.TopEntry> entries, long at) {
    }

    private static final long[] RETRY_MS = {500, 1000, 2000, 5000, 10000};

    private final Dreamcoinshop plugin;
    private final Platform platform;
    private final Logger log;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<Currency, Top> tops = new EnumMap<>(Currency.class);
    private final Map<Currency, AtomicBoolean> topLoading = new EnumMap<>(Currency.class);
    private final AtomicBoolean polling = new AtomicBoolean();
    private final String origin;

    private Storage storage;
    private ExecutorService db;
    private boolean shared;
    private volatile boolean ready;
    private volatile boolean stopping;
    private volatile long lastChangeId;
    private volatile long topRefreshMs = 30_000;
    private Platform.Task pollTask;
    private Platform.Task maintenanceTask;
    /** Called (on any thread) after a player's data was reloaded because another server changed it. */
    private volatile Consumer<Profile> onReloaded = p -> { };

    DataManager(Dreamcoinshop plugin, Platform platform, String serverName) {
        this.plugin = plugin;
        this.platform = platform;
        this.log = plugin.getLogger();
        String name = serverName == null || serverName.isBlank() ? "server" : serverName.trim();
        this.origin = name + "#" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x100000, 0xFFFFFF));
        for (Currency c : Currency.values()) topLoading.put(c, new AtomicBoolean());
    }

    void onReloaded(Consumer<Profile> callback) {
        this.onReloaded = callback;
    }

    void topRefreshSeconds(int seconds) {
        topRefreshMs = Math.max(5, seconds) * 1000L;
    }

    /**
     * Connects (waiting up to 20 s, so a wrong password shows up in the startup log), imports the
     * old yml files once, loads everyone already online and starts the sync.
     */
    void start(Storage storage, boolean shared, int pollMs) {
        this.storage = storage;
        this.shared = shared;
        this.db = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Dreamcoinshop database");
            t.setDaemon(true);
            return t;
        });
        Future<?> init = db.submit(() -> {
            storage.init();
            YamlImport.run(plugin, storage);
            lastChangeId = storage.lastChangeId();
            return null;
        });
        try {
            init.get(20, TimeUnit.SECONDS);
            ready = true;
            log.info("Player data: " + storage.describe() + (shared ? " (shared with the other servers)" : ""));
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.severe("Could not connect to the " + storage.describe() + " database: " + cause.getMessage());
            log.severe("Check storage: in config.yml. Retrying every 30 seconds; the shop stays closed until it works.");
            if (!(e instanceof java.util.concurrent.TimeoutException)) init.cancel(true);
        }
        if (shared) {
            if (storage.pushesChanges()) {
                storage.listen(this::remoteChange, this::reloadOnline);
            } else {
                long ms = Math.max(250, pollMs);
                pollTask = platform.asyncTimer(this::poll, ms, ms);
            }
        }
        maintenanceTask = platform.asyncTimer(this::maintenance, 10_000, 10_000);
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile profile = profiles.computeIfAbsent(p.getUniqueId(), u -> new Profile(u, p.getName()));
            if (ready) loadAsync(profile);
        }
    }

    boolean isReady() {
        return ready;
    }

    String storageName() {
        return storage == null ? "none" : storage.describe();
    }

    boolean isShared() {
        return shared;
    }

    /** Finishes every queued write (up to 15 s), then disconnects. */
    void stop() {
        stopping = true;
        if (pollTask != null) pollTask.cancel();
        if (maintenanceTask != null) maintenanceTask.cancel();
        if (db != null) {
            db.shutdown();
            try {
                if (!db.awaitTermination(15, TimeUnit.SECONDS)) {
                    log.warning("The database took too long; some last changes may not be saved.");
                    db.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (storage != null) storage.close();
    }

    // ------------------------------------------------------------------ profiles

    /** The online player's data (never null for an online player; may not be loaded yet). */
    Profile profile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), u -> new Profile(u, player.getName()));
    }

    /** Data of a player online on this server, or null. */
    Profile online(UUID uuid) {
        return profiles.get(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || !ready) return;
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        Profile profile = profiles.computeIfAbsent(uuid, u -> new Profile(u, name));
        profile.name(name);
        try {
            Storage.Data data = db.submit(() -> storage.load(uuid, name)).get(10, TimeUnit.SECONDS);
            applyLoaded(profile, data);
        } catch (Exception e) {
            // Not fatal: the player can join, the shop waits and the maintenance task retries
            log.warning("Could not load " + name + " from the database yet: " + rootMessage(e));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Profile profile = profile(p);
        profile.name(p.getName());
        if (!profile.isLoaded() && ready) loadAsync(profile);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        profiles.remove(event.getPlayer().getUniqueId());
    }

    private void loadAsync(Profile profile) {
        execute(s -> applyLoaded(profile, s.load(profile.uuid, profile.name())));
    }

    private void applyLoaded(Profile profile, Storage.Data data) {
        if (profile.apply(data)) {
            onReloaded.accept(profile);
        } else {
            // One of this player's own writes is still queued: read again after it
            execute(s -> {
                Storage.Data fresh = s.read(profile.uuid);
                applyLoaded(profile, fresh != null ? fresh : data);
            });
        }
    }

    /** Every 10 s: retry players whose data didn't load, reconnect a database that wasn't there at startup. */
    private void maintenance() {
        if (!ready && !stopping) {
            try {
                db.submit(() -> {
                    storage.init();
                    YamlImport.run(plugin, storage);
                    lastChangeId = storage.lastChangeId();
                    return null;
                }).get(20, TimeUnit.SECONDS);
                ready = true;
                log.info("Connected to the " + storage.describe() + " database. The shop is open.");
            } catch (Exception e) {
                return;
            }
        }
        platform.global(() -> {
            for (Profile profile : profiles.values()) {
                Player p = Bukkit.getPlayer(profile.uuid);
                if (p == null) {
                    profiles.remove(profile.uuid, profile); // logged in, then refused by another plugin
                } else if (!profile.isLoaded()) {
                    loadAsync(profile);
                }
            }
        });
        if (shared && !storage.pushesChanges() && ThreadLocalRandom.current().nextInt(30) == 0) {
            execute(s -> s.prune(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)));
        }
    }

    // ------------------------------------------------------------------ changes

    /** Takes the amount if the player has it. False if not (or their data isn't loaded yet). */
    boolean charge(Profile p, Currency c, double amount) {
        if (!p.isLoaded() || !p.tryCharge(c, amount)) return false;
        write(p, s -> s.addBalance(p.uuid, c, -amount));
        return true;
    }

    void add(Profile p, Currency c, double delta) {
        p.add(c, delta);
        write(p, s -> s.addBalance(p.uuid, c, delta));
    }

    void set(Profile p, Currency c, double value) {
        p.set(c, value);
        write(p, s -> s.setBalance(p.uuid, c, value));
    }

    void unlock(Profile p, Category c, String item) {
        if (p.unlock(c, item)) write(p, s -> s.unlock(p.uuid, c, item));
    }

    void revoke(Profile p, Category c, String item) {
        if (p.revoke(c, item)) write(p, s -> s.revoke(p.uuid, c, item));
    }

    void setActive(Profile p, Category c, String item) {
        p.setActive(c, item);
        write(p, s -> s.setActive(p.uuid, c, item));
    }

    /** A change for a player who isn't online here (another server, or offline). */
    void writeOffline(UUID uuid, Op op) {
        Profile here = profiles.get(uuid);
        if (here != null) {
            write(here, op);
            execute(s -> reloadNow(uuid));
            return;
        }
        execute(s -> {
            withRetry(op, "change for " + uuid);
            if (shared) s.publish(uuid, origin);
            if (profiles.containsKey(uuid)) reloadNow(uuid); // they logged in here meanwhile
        });
    }

    private void write(Profile p, Op op) {
        p.beginWrite();
        boolean queued = execute(s -> {
            try {
                withRetry(op, "change for " + p.name());
                if (shared) s.publish(p.uuid, origin);
            } finally {
                p.endWrite();
            }
        });
        if (!queued) p.endWrite();
    }

    /** Runs a write, retrying for about 18 s if the database is briefly unreachable. */
    private void withRetry(Op op, String what) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try {
                op.run(storage);
                return;
            } catch (Exception e) {
                if (attempt >= RETRY_MS.length || (stopping && attempt >= 1)) {
                    log.log(Level.SEVERE, "Could not save a " + what + " to the database: " + rootMessage(e));
                    throw e;
                }
                Thread.sleep(RETRY_MS[attempt]);
            }
        }
    }

    /** Queues database work. False if the plugin is shutting down. */
    boolean execute(Op task) {
        if (db == null) return false;
        try {
            db.execute(() -> {
                try {
                    task.run(storage);
                } catch (Exception e) {
                    if (!(e instanceof InterruptedException)) log.fine("Database task failed: " + rootMessage(e));
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ lookups

    /** The UUID of a player online here, or else the newest one with this name in the database. */
    CompletableFuture<UUID> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return CompletableFuture.completedFuture(online.getUniqueId());
        CompletableFuture<UUID> f = new CompletableFuture<>();
        if (!execute(s -> f.complete(s.findByName(name)))) f.complete(null);
        else completeOnTimeout(f);
        return f;
    }

    /** Current balance of any player (online here, or from the database). */
    CompletableFuture<Double> balance(UUID uuid, Currency c) {
        Profile p = profiles.get(uuid);
        if (p != null && p.isLoaded()) return CompletableFuture.completedFuture(p.balance(c));
        CompletableFuture<Double> f = new CompletableFuture<>();
        if (!execute(s -> {
            Storage.Data d = s.read(uuid);
            f.complete(d == null ? 0.0 : (c == Currency.COINS ? d.coins() : d.orbs()));
        })) f.complete(0.0);
        else completeOnTimeout(f);
        return f;
    }

    private static <T> void completeOnTimeout(CompletableFuture<T> f) {
        f.completeOnTimeout(null, 15, TimeUnit.SECONDS);
    }

    /** Leaderboard from the last refresh (may be empty the first time). */
    List<Storage.TopEntry> top(Currency c) {
        synchronized (tops) {
            Top t = tops.get(c);
            return t == null ? List.of() : t.entries();
        }
    }

    boolean topLoaded(Currency c) {
        synchronized (tops) {
            return tops.containsKey(c);
        }
    }

    /** Reloads the leaderboard if it's older than top.refresh-seconds; then runs after (main thread). */
    void refreshTop(Currency c, int limit, Runnable after) {
        synchronized (tops) {
            Top t = tops.get(c);
            if (t != null && System.currentTimeMillis() - t.at() < topRefreshMs) return;
        }
        if (!topLoading.get(c).compareAndSet(false, true)) return;
        boolean queued = execute(s -> {
            try {
                List<Storage.TopEntry> list = s.top(c, limit);
                synchronized (tops) {
                    tops.put(c, new Top(List.copyOf(list), System.currentTimeMillis()));
                }
                if (after != null) platform.global(after);
            } finally {
                topLoading.get(c).set(false);
            }
        });
        if (!queued) topLoading.get(c).set(false);
    }

    void invalidateTops() {
        synchronized (tops) {
            tops.clear();
        }
    }

    // ------------------------------------------------------------------ sync

    private void poll() {
        if (!ready || !polling.compareAndSet(false, true)) return;
        boolean queued = execute(s -> {
            try {
                for (Storage.Change change : s.changesAfter(lastChangeId)) {
                    lastChangeId = Math.max(lastChangeId, change.id());
                    if (!origin.equals(change.origin()) && profiles.containsKey(change.uuid())) reloadNow(change.uuid());
                }
            } finally {
                polling.set(false);
            }
        });
        if (!queued) polling.set(false);
    }

    private void remoteChange(Storage.Change change) {
        if (!origin.equals(change.origin()) && profiles.containsKey(change.uuid())) execute(s -> reloadNow(change.uuid()));
    }

    /** After the Redis connection was lost for a while: changes may have been missed, so reload everyone. */
    private void reloadOnline() {
        for (UUID uuid : profiles.keySet()) execute(s -> reloadNow(uuid));
    }

    /** Database thread only. */
    private void reloadNow(UUID uuid) throws Exception {
        Profile p = profiles.get(uuid);
        if (p == null) return;
        Storage.Data data = storage.read(uuid);
        if (data == null) return;
        if (p.apply(data)) onReloaded.accept(p);
        else execute(s -> reloadNow(uuid)); // one of our own writes is still queued: read again after it
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
