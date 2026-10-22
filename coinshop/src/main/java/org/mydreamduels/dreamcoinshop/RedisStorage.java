package org.mydreamduels.dreamcoinshop;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Player data in Redis, for networks that already run one. Keys (all start with the prefix):
 *   player:&lt;uuid&gt;   hash: name, coins, orbs
 *   unlocks:&lt;uuid&gt;  set of "category:item"
 *   active:&lt;uuid&gt;   hash: category -> item
 *   names           hash: lowercase name -> uuid
 *   top:coins, top:orbs  sorted sets for the leaderboards
 *   meta            hash
 *   changes         pub/sub channel: "uuid|origin", so other servers update right away
 * Balance changes are one Lua script each, so two servers can never lose each other's change.
 */
final class RedisStorage implements Storage {

    private static final int TIMEOUT_MS = 5000;

    /** KEYS: player hash, top set, op key. ARGV: column, delta, uuid, op id ('' = none). */
    private static final String ADD =
            "if ARGV[4] ~= '' and not redis.call('SET', KEYS[3], '1', 'NX', 'EX', '86400') then return 'dup' end "
            + "local v = tonumber(redis.call('HINCRBYFLOAT', KEYS[1], ARGV[1], ARGV[2])) "
            + "if v < 0 then v = 0 redis.call('HSET', KEYS[1], ARGV[1], '0') end "
            + "redis.call('ZADD', KEYS[2], v, ARGV[3]) return tostring(v)";

    private final String host, username, password;
    private final int port, database;
    private final boolean ssl;
    private final String prefix, channel;
    private final Logger log;
    private RedisClient client;
    private volatile boolean closed;
    private Thread listener;
    private volatile RedisClient listenerClient;

    RedisStorage(String host, int port, boolean ssl, String username, String password, int database, String prefix, Logger log) {
        if (!prefix.matches("[A-Za-z0-9_:]{0,24}")) {
            throw new IllegalArgumentException("table-prefix may only contain letters, digits, _ and :");
        }
        this.host = host;
        this.port = port;
        this.ssl = ssl;
        this.username = username;
        this.password = password;
        this.database = database;
        this.prefix = prefix;
        this.channel = prefix + "changes";
        this.log = log;
    }

    @Override
    public String describe() {
        return "Redis";
    }

    private RedisClient connect() throws IOException {
        return new RedisClient(host, port, ssl, username, password, database, TIMEOUT_MS);
    }

    /** One command; on a broken connection it reconnects and tries once more. */
    private synchronized Object call(String... args) throws IOException {
        if (closed) throw new IOException("closed");
        boolean reused = client != null && client.isOpen();
        if (!reused) client = connect();
        try {
            return client.call(args);
        } catch (RedisClient.RedisException e) {
            throw e; // a Redis error reply; the connection itself is fine
        } catch (IOException | RuntimeException e) {
            client.close();
            client = null;
            if (!reused || closed) throw e;
            client = connect();
            try {
                return client.call(args);
            } catch (IOException | RuntimeException again) {
                if (!(again instanceof RedisClient.RedisException)) {
                    client.close();
                    client = null;
                }
                throw again;
            }
        }
    }

    private String playerKey(UUID uuid) {
        return prefix + "player:" + uuid;
    }

    private String unlocksKey(UUID uuid) {
        return prefix + "unlocks:" + uuid;
    }

    private String activeKey(UUID uuid) {
        return prefix + "active:" + uuid;
    }

    private String topKey(Currency c) {
        return prefix + "top:" + c.column;
    }

    @Override
    public void init() throws IOException {
        Object pong = call("PING");
        if (!"PONG".equals(pong)) throw new IOException("Redis answered " + pong + " to PING");
    }

    @Override
    public Data load(UUID uuid, String name) throws IOException {
        if (name != null) {
            call("HSET", playerKey(uuid), "name", name);
            call("HSET", prefix + "names", name.toLowerCase(Locale.ROOT), uuid.toString());
        }
        Data data = read(uuid);
        return data != null ? data : Data.empty(uuid, name);
    }

    @Override
    public Data read(UUID uuid) throws IOException {
        Object raw = call("HGETALL", playerKey(uuid));
        Map<String, String> fields = pairs(raw);
        Object members = call("SMEMBERS", unlocksKey(uuid));
        Object act = call("HGETALL", activeKey(uuid));
        if (fields.isEmpty() && !(members instanceof List<?> l && !l.isEmpty())) return null;
        Data empty = Data.empty(uuid, fields.get("name"));
        Data data = new Data(uuid, empty.name(), num(fields.get("coins")), num(fields.get("orbs")), empty.unlocked(), empty.active());
        if (members instanceof List<?> list) {
            for (Object o : list) {
                String s = String.valueOf(o);
                int split = s.indexOf(':');
                if (split <= 0) continue;
                Category c = Category.byId(s.substring(0, split));
                if (c != null) data.unlocked().get(c).add(s.substring(split + 1));
            }
        }
        pairs(act).forEach((k, v) -> {
            Category c = Category.byId(k);
            if (c != null && v != null) data.active().put(c, v);
        });
        return data;
    }

    private static Map<String, String> pairs(Object reply) {
        Map<String, String> out = new java.util.HashMap<>();
        if (reply instanceof List<?> list) {
            for (int i = 0; i + 1 < list.size(); i += 2) out.put(String.valueOf(list.get(i)), (String) list.get(i + 1));
        }
        return out;
    }

    private static double num(String s) {
        if (s == null) return 0;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String fmt(double d) {
        return Double.toString(d);
    }

    @Override
    public void addBalance(UUID uuid, Currency currency, double delta, String opId) throws IOException {
        call("EVAL", ADD, "3", playerKey(uuid), topKey(currency), prefix + "op:" + (opId == null ? "-" : opId),
                currency.column, fmt(delta), uuid.toString(), opId == null ? "" : opId);
    }

    @Override
    public void setBalance(UUID uuid, Currency currency, double value) throws IOException {
        double v = Math.max(0, value);
        call("HSET", playerKey(uuid), currency.column, fmt(v));
        call("ZADD", topKey(currency), fmt(v), uuid.toString());
    }

    @Override
    public void unlock(UUID uuid, Category category, String item) throws IOException {
        call("SADD", unlocksKey(uuid), category.id + ":" + item);
    }

    @Override
    public void revoke(UUID uuid, Category category, String item) throws IOException {
        call("SREM", unlocksKey(uuid), category.id + ":" + item);
        Object current = call("HGET", activeKey(uuid), category.id);
        if (item.equals(current)) call("HDEL", activeKey(uuid), category.id);
    }

    @Override
    public void setActive(UUID uuid, Category category, String item) throws IOException {
        if (item == null) call("HDEL", activeKey(uuid), category.id);
        else call("HSET", activeKey(uuid), category.id, item);
    }

    @Override
    public List<TopEntry> top(Currency currency, int limit) throws IOException {
        Object raw = call("ZREVRANGE", topKey(currency), "0", String.valueOf(Math.max(1, limit) - 1), "WITHSCORES");
        List<TopEntry> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (int i = 0; i + 1 < list.size(); i += 2) {
                double score = num(String.valueOf(list.get(i + 1)));
                if (score <= 0) continue;
                try {
                    UUID uuid = UUID.fromString(String.valueOf(list.get(i)));
                    Object name = call("HGET", playerKey(uuid), "name");
                    Object flag = call("HGET", activeKey(uuid), Category.FLAG.id);
                    out.add(new TopEntry(uuid, name == null ? null : String.valueOf(name), score, flag == null ? null : String.valueOf(flag)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }

    @Override
    public UUID findByName(String name) throws IOException {
        Object raw = call("HGET", prefix + "names", name.toLowerCase(Locale.ROOT));
        if (raw == null) return null;
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public void importData(Data data) throws IOException {
        UUID uuid = data.uuid();
        if (data.name() != null) {
            call("HSET", playerKey(uuid), "name", data.name());
            call("HSET", prefix + "names", data.name().toLowerCase(Locale.ROOT), uuid.toString());
        }
        setBalance(uuid, Currency.COINS, data.coins());
        setBalance(uuid, Currency.ORBS, data.orbs());
        for (Map.Entry<Category, Set<String>> e : data.unlocked().entrySet()) {
            for (String item : e.getValue()) unlock(uuid, e.getKey(), item);
        }
        for (Map.Entry<Category, String> e : data.active().entrySet()) setActive(uuid, e.getKey(), e.getValue());
    }

    @Override
    public String getMeta(String key) throws IOException {
        Object raw = call("HGET", prefix + "meta", key);
        return raw == null ? null : String.valueOf(raw);
    }

    @Override
    public void setMeta(String key, String value) throws IOException {
        call("HSET", prefix + "meta", key, value);
    }

    // ------------------------------------------------------------------ sync

    @Override
    public void publish(UUID uuid, String origin) throws IOException {
        call("PUBLISH", channel, uuid + "|" + origin);
    }

    @Override
    public List<Change> changesAfter(long id) {
        return List.of();
    }

    @Override
    public long lastChangeId() {
        return 0;
    }

    @Override
    public void prune(long olderThanMillis) {
    }

    @Override
    public boolean pushesChanges() {
        return true;
    }

    /** Subscribes on its own thread and reconnects by itself. onReconnect runs after a lost connection is back. */
    @Override
    public void listen(Consumer<Change> onChange, Runnable onReconnect) {
        listener = new Thread(() -> {
            long backoff = 1000;
            boolean lost = false;
            while (!closed) {
                try (RedisClient sub = connect()) {
                    listenerClient = sub;
                    sub.send("SUBSCRIBE", channel);
                    sub.read(); // subscribe confirmation
                    sub.setTimeout(30_000);
                    backoff = 1000;
                    if (lost) {
                        lost = false;
                        onReconnect.run(); // messages sent while we were away are gone: reload instead
                    }
                    boolean pinged = false;
                    while (!closed) {
                        Object msg;
                        try {
                            msg = sub.read();
                        } catch (SocketTimeoutException quiet) {
                            // A PING that got no answer in 30 s: the connection is dead even if the socket isn't
                            if (pinged) throw new IOException("Redis stopped answering");
                            sub.send("PING"); // keeps the connection checked; the reply arrives as a message
                            pinged = true;
                            continue;
                        }
                        pinged = false;
                        if (msg instanceof List<?> l && l.size() >= 3 && "message".equals(l.get(0))) {
                            String body = String.valueOf(l.get(2));
                            int split = body.indexOf('|');
                            if (split <= 0) continue;
                            try {
                                onChange.accept(new Change(0, UUID.fromString(body.substring(0, split)), body.substring(split + 1)));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    if (closed) return;
                    lost = true;
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        return;
                    }
                    backoff = Math.min(30_000, backoff * 2);
                } finally {
                    listenerClient = null;
                }
            }
        }, "Dreamcoinshop sync listener");
        listener.setDaemon(true);
        listener.start();
        log.fine("Redis change channel: " + channel);
    }

    @Override
    public void close() {
        closed = true;
        RedisClient sub = listenerClient;
        if (sub != null) sub.close(); // unblocks the listener thread
        if (listener != null) listener.interrupt();
        RedisClient c;
        synchronized (this) {
            c = client;
            client = null;
        }
        if (c != null) c.close();
    }
}
