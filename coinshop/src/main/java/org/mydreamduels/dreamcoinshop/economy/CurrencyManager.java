package org.mydreamduels.dreamcoinshop.economy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Balances live in memory; the file is written on a timer, off the main thread.
 *
 * WHAT THIS FIXES. setBalance() used to call save() every single time a balance changed, and
 * save() rewrote EVERY player's balance into the YAML and then wrote the whole file to disk -
 * synchronously, on the main thread.
 *
 * That made one orb payout cost the server dearly. giveOrbsToOnlinePlayers() calls add() once
 * per online player, so with 42 players online and, say, 800 players in orbs.yml, a single
 * payout did 42 full file writes and about 33,000 yaml.set() calls, all while the server was
 * trying to tick. A spark profile taken while TPS was dropping showed 320 of the plugin's
 * 340ms sitting in FileConfiguration.save - 94% of everything this plugin did was writing the
 * same file over and over.
 *
 * It also got worse over time in a way that is easy to miss: the cost scales with how many
 * players have EVER been recorded in the file, not how many are online, so a server that runs
 * fine at launch degrades steadily for months.
 *
 * Now a balance change only touches the in-memory map and sets a dirty flag. A repeating
 * asynchronous task writes the file when, and only when, something actually changed. The main
 * thread never touches the disk.
 *
 * DURABILITY. Writes are no longer instant, so an unclean shutdown (an OOM kill, a panel
 * "force stop") can lose up to flush-interval-seconds of balance changes. shutdown() flushes
 * synchronously so a normal stop or restart never loses anything, and the default interval is
 * deliberately short. Lower it in config.yml if you would rather trade a little more I/O for
 * a smaller window.
 */
public class CurrencyManager {
    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, Double> balances = new ConcurrentHashMap<>();

    /** set by any balance change, cleared by the flush that writes those changes out */
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    /** guards against two flushes writing the same file at once */
    private final Object writeLock = new Object();
    private BukkitTask flushTask;

    public CurrencyManager(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), fileName);
        if (!this.file.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                this.file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not create " + fileName, e);
            }
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(this.file);
        for (String key : loaded.getKeys(false)) {
            try {
                this.balances.put(UUID.fromString(key), loaded.getDouble(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    /**
     * Starts the background writer. Call once, after the plugin's config is loaded.
     *
     * @param intervalSeconds how often to write, if anything changed. Floored at 5 - anything
     *                        shorter is just disk churn, since the whole point is batching.
     */
    public void startAutoSave(long intervalSeconds) {
        stopAutoSave();
        long ticks = Math.max(5L, intervalSeconds) * 20L;
        this.flushTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::flushIfDirty, ticks, ticks);
    }

    public void stopAutoSave() {
        if (this.flushTask != null) {
            this.flushTask.cancel();
            this.flushTask = null;
        }
    }

    /** Cancels the timer and writes any pending changes on the calling thread. Use on disable. */
    public void shutdown() {
        stopAutoSave();
        writeToDisk();
    }

    public double getBalance(UUID uuid) {
        return this.balances.getOrDefault(uuid, 0.0);
    }

    public boolean has(UUID uuid, double amount) {
        return this.getBalance(uuid) >= amount;
    }

    public void setBalance(UUID uuid, double amount) {
        this.balances.put(uuid, Math.max(0.0, amount));
        this.dirty.set(true);
    }

    public void add(UUID uuid, double amount) {
        this.setBalance(uuid, this.getBalance(uuid) + amount);
    }

    public boolean remove(UUID uuid, double amount) {
        if (!this.has(uuid, amount)) {
            return false;
        }
        this.setBalance(uuid, this.getBalance(uuid) - amount);
        return true;
    }

    public List<Map.Entry<UUID, Double>> getTopBalances(int limit) {
        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(this.balances.entrySet());
        sorted.sort(Comparator.comparingDouble(Map.Entry<UUID, Double>::getValue).reversed());
        if (sorted.size() > limit) {
            sorted = sorted.subList(0, limit);
        }
        return sorted;
    }

    private void flushIfDirty() {
        // Clear the flag BEFORE snapshotting. A change that lands during the write then leaves
        // the flag set and gets picked up by the next flush - the other order could clear a
        // flag whose change was never written.
        if (!this.dirty.compareAndSet(true, false)) {
            return;
        }
        writeToDisk();
    }

    private void writeToDisk() {
        synchronized (this.writeLock) {
            // A fresh YamlConfiguration each time, rather than mutating one shared instance:
            // the shared one was being written from the main thread while nothing stopped a
            // flush reading it, and it also kept keys for entries no longer in the map.
            Map<UUID, Double> snapshot = new HashMap<>(this.balances);
            YamlConfiguration out = new YamlConfiguration();
            for (Map.Entry<UUID, Double> entry : snapshot.entrySet()) {
                out.set(entry.getKey().toString(), entry.getValue());
            }
            try {
                out.save(this.file);
            } catch (IOException e) {
                // Put the flag back: these changes are still only in memory, so the next flush
                // has to try again rather than treat them as written.
                this.dirty.set(true);
                this.plugin.getLogger().log(Level.WARNING, "Could not save " + this.file.getName(), e);
            }
        }
    }
}
