package org.mydreamduels.dreamcoinshop;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player data in SQL: SQLite, MySQL / MariaDB or PostgreSQL. Plain JDBC, no Bukkit, so it can be
 * tested on its own. One connection, used only by the plugin's database thread.
 *
 * Tables (all names start with the table prefix):
 *   players  uuid, name, coins, orbs
 *   unlocks  uuid, category, item          - everything a player bought
 *   active   uuid, category, item          - what they have equipped (one per category)
 *   changes  id, uuid, origin              - "this player changed" notices for the other servers
 *   meta     k, v                          - e.g. whether the old yml files were imported
 *   ops      id                            - ids of applied balance changes (safe retries), kept a day
 */
final class SqlStorage implements Storage {

    enum Dialect { SQLITE, MYSQL, POSTGRESQL }

    interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final Dialect dialect;
    private final ConnectionFactory factory;
    private final String players, unlocks, active, changes, meta, ops;
    private Connection connection;

    SqlStorage(Dialect dialect, ConnectionFactory factory, String prefix) {
        if (!prefix.matches("[A-Za-z0-9_]{0,24}")) {
            throw new IllegalArgumentException("table-prefix may only contain letters, digits and _");
        }
        this.dialect = dialect;
        this.factory = factory;
        this.players = prefix + "players";
        this.unlocks = prefix + "unlocks";
        this.active = prefix + "active";
        this.changes = prefix + "changes";
        this.meta = prefix + "meta";
        this.ops = prefix + "ops";
    }

    @Override
    public String describe() {
        return switch (dialect) {
            case SQLITE -> "SQLite";
            case MYSQL -> "MySQL/MariaDB";
            case POSTGRESQL -> "PostgreSQL";
        };
    }

    private Connection conn() throws SQLException {
        if (connection == null || connection.isClosed() || (dialect != Dialect.SQLITE && !connection.isValid(3))) {
            closeQuietly();
            connection = factory.open();
            if (dialect == Dialect.SQLITE) {
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA busy_timeout = 5000");
                    st.execute("PRAGMA journal_mode = WAL");
                    st.execute("PRAGMA synchronous = NORMAL");
                }
            }
        }
        return connection;
    }

    private String doubleType() {
        return switch (dialect) {
            case SQLITE -> "REAL";
            case MYSQL -> "DOUBLE";
            case POSTGRESQL -> "DOUBLE PRECISION";
        };
    }

    private String max() {
        return dialect == Dialect.SQLITE ? "MAX" : "GREATEST";
    }

    /** "INSERT ... that does nothing if the row exists" in this dialect. */
    private String insertIgnore(String table, String columns, String values, String conflict) {
        if (dialect == Dialect.MYSQL) return "INSERT IGNORE INTO " + table + " (" + columns + ") VALUES (" + values + ")";
        return "INSERT INTO " + table + " (" + columns + ") VALUES (" + values + ") ON CONFLICT (" + conflict + ") DO NOTHING";
    }

    @Override
    public synchronized void init() throws SQLException {
        String id = switch (dialect) {
            case SQLITE -> "id INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL -> "id BIGINT AUTO_INCREMENT PRIMARY KEY";
            case POSTGRESQL -> "id BIGSERIAL PRIMARY KEY";
        };
        String text = dialect == Dialect.MYSQL ? "TEXT" : "TEXT";
        try (Statement st = conn().createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + players + " (uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "name VARCHAR(16), name_lower VARCHAR(16), coins " + doubleType() + " NOT NULL DEFAULT 0, "
                    + "orbs " + doubleType() + " NOT NULL DEFAULT 0, updated_at BIGINT NOT NULL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + unlocks + " (uuid VARCHAR(36) NOT NULL, category VARCHAR(16) NOT NULL, "
                    + "item VARCHAR(64) NOT NULL, PRIMARY KEY (uuid, category, item))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + active + " (uuid VARCHAR(36) NOT NULL, category VARCHAR(16) NOT NULL, "
                    + "item VARCHAR(64) NOT NULL, PRIMARY KEY (uuid, category))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + changes + " (" + id + ", uuid VARCHAR(36) NOT NULL, "
                    + "origin VARCHAR(64) NOT NULL, created_at BIGINT NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + meta + " (k VARCHAR(64) NOT NULL PRIMARY KEY, v " + text + ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + ops + " (id VARCHAR(36) NOT NULL PRIMARY KEY, created_at BIGINT NOT NULL)");
        }
        createIndex(players + "_name", players, "name_lower");
        createIndex(players + "_coins", players, "coins");
        createIndex(players + "_orbs", players, "orbs");
    }

    private void createIndex(String name, String table, String column) throws SQLException {
        try (Statement st = conn().createStatement()) {
            if (dialect == Dialect.MYSQL) {
                // MySQL has no CREATE INDEX IF NOT EXISTS (MariaDB does)
                try (ResultSet rs = st.executeQuery("SHOW INDEX FROM " + table + " WHERE Key_name = '" + name + "'")) {
                    if (rs.next()) return;
                }
                st.executeUpdate("CREATE INDEX " + name + " ON " + table + " (" + column + ")");
            } else {
                st.executeUpdate("CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + column + ")");
            }
        }
    }

    private void ensurePlayer(UUID uuid) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(insertIgnore(players, "uuid, coins, orbs, updated_at", "?, 0, 0, ?", "uuid"))) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized Data load(UUID uuid, String name) throws SQLException {
        ensurePlayer(uuid);
        if (name != null) {
            try (PreparedStatement ps = conn().prepareStatement("UPDATE " + players
                    + " SET name = ?, name_lower = ?, updated_at = ? WHERE uuid = ?")) {
                ps.setString(1, name);
                ps.setString(2, name.toLowerCase(Locale.ROOT));
                ps.setLong(3, System.currentTimeMillis());
                ps.setString(4, uuid.toString());
                ps.executeUpdate();
            }
        }
        Data data = read(uuid);
        return data != null ? data : Data.empty(uuid, name);
    }

    @Override
    public synchronized Data read(UUID uuid) throws SQLException {
        Data data;
        try (PreparedStatement ps = conn().prepareStatement("SELECT name, coins, orbs FROM " + players + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Data empty = Data.empty(uuid, rs.getString(1));
                data = new Data(uuid, empty.name(), rs.getDouble(2), rs.getDouble(3), empty.unlocked(), empty.active());
            }
        }
        try (PreparedStatement ps = conn().prepareStatement("SELECT category, item FROM " + unlocks + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Category c = Category.byId(rs.getString(1));
                    if (c != null) data.unlocked().get(c).add(rs.getString(2));
                }
            }
        }
        try (PreparedStatement ps = conn().prepareStatement("SELECT category, item FROM " + active + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Category c = Category.byId(rs.getString(1));
                    if (c != null) data.active().put(c, rs.getString(2));
                }
            }
        }
        return data;
    }

    @Override
    public synchronized void addBalance(UUID uuid, Currency currency, double delta, String opId) throws SQLException {
        // Two servers changing the same player can deadlock on MySQL/MariaDB; the database then
        // cancels one of them, which is safe to run again (the op id makes it count only once)
        for (int attempt = 1; ; attempt++) {
            try {
                addBalanceOnce(uuid, currency, delta, opId);
                return;
            } catch (SQLException e) {
                String state = e.getSQLState();
                boolean conflict = state != null && (state.equals("40001") || state.equals("40P01"));
                if (!conflict || attempt >= 5) throw e;
                try {
                    Thread.sleep(10L * attempt + java.util.concurrent.ThreadLocalRandom.current().nextInt(20));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private void addBalanceOnce(UUID uuid, Currency currency, double delta, String opId) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            if (opId != null) {
                try (PreparedStatement ps = c.prepareStatement(insertIgnore(ops, "id, created_at", "?, ?", "id"))) {
                    ps.setString(1, opId);
                    ps.setLong(2, System.currentTimeMillis());
                    if (ps.executeUpdate() == 0) { // already applied (a retry after a timeout)
                        c.rollback();
                        return;
                    }
                }
            }
            // Update first: creating the row up front (INSERT IGNORE) takes a shared lock on
            // MySQL/MariaDB that makes two servers adding at once deadlock each other
            if (updateBalance(c, uuid, currency, delta) == 0) {
                ensurePlayer(uuid);
                updateBalance(c, uuid, currency, delta);
            }
            c.commit();
        } catch (SQLException e) {
            try {
                c.rollback();
            } catch (SQLException ignored) {
            }
            throw e;
        } finally {
            try {
                c.setAutoCommit(auto);
            } catch (SQLException ignored) {
            }
        }
    }

    private int updateBalance(Connection c, UUID uuid, Currency currency, double delta) throws SQLException {
        String col = currency.column;
        try (PreparedStatement ps = c.prepareStatement("UPDATE " + players + " SET " + col + " = "
                + max() + "(" + col + " + ?, 0), updated_at = ? WHERE uuid = ?")) {
            ps.setDouble(1, delta);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            return ps.executeUpdate();
        }
    }

    @Override
    public synchronized void setBalance(UUID uuid, Currency currency, double value) throws SQLException {
        ensurePlayer(uuid);
        try (PreparedStatement ps = conn().prepareStatement("UPDATE " + players + " SET " + currency.column
                + " = ?, updated_at = ? WHERE uuid = ?")) {
            ps.setDouble(1, Math.max(0, value));
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized void unlock(UUID uuid, Category category, String item) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(insertIgnore(unlocks, "uuid, category, item", "?, ?, ?", "uuid, category, item"))) {
            ps.setString(1, uuid.toString());
            ps.setString(2, category.id);
            ps.setString(3, item);
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized void revoke(UUID uuid, Category category, String item) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + unlocks + " WHERE uuid = ? AND category = ? AND item = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, category.id);
            ps.setString(3, item);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + active + " WHERE uuid = ? AND category = ? AND item = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, category.id);
            ps.setString(3, item);
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized void setActive(UUID uuid, Category category, String item) throws SQLException {
        if (item == null) {
            try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + active + " WHERE uuid = ? AND category = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, category.id);
                ps.executeUpdate();
            }
            return;
        }
        String sql = dialect == Dialect.MYSQL
                ? "INSERT INTO " + active + " (uuid, category, item) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE item = ?"
                : "INSERT INTO " + active + " (uuid, category, item) VALUES (?, ?, ?) ON CONFLICT (uuid, category) DO UPDATE SET item = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, category.id);
            ps.setString(3, item);
            ps.setString(4, item);
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized List<TopEntry> top(Currency currency, int limit) throws SQLException {
        List<TopEntry> out = new ArrayList<>();
        String col = currency.column;
        try (PreparedStatement ps = conn().prepareStatement("SELECT p.uuid, p.name, p." + col + ", a.item FROM " + players + " p"
                + " LEFT JOIN " + active + " a ON a.uuid = p.uuid AND a.category = ?"
                + " WHERE p." + col + " > 0 ORDER BY p." + col + " DESC LIMIT ?")) {
            ps.setString(1, Category.FLAG.id);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.add(new TopEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getDouble(3), rs.getString(4)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        return out;
    }

    @Override
    public synchronized UUID findByName(String name) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT uuid FROM " + players
                + " WHERE name_lower = ? ORDER BY updated_at DESC LIMIT 1")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString(1)) : null;
            }
        }
    }

    @Override
    public synchronized void importData(Data data) throws SQLException {
        Connection c = conn();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            ensurePlayer(data.uuid());
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + players + " SET coins = ?, orbs = ?, name = COALESCE(?, name), "
                    + "name_lower = COALESCE(?, name_lower) WHERE uuid = ?")) {
                ps.setDouble(1, Math.max(0, data.coins()));
                ps.setDouble(2, Math.max(0, data.orbs()));
                ps.setString(3, data.name());
                ps.setString(4, data.name() == null ? null : data.name().toLowerCase(Locale.ROOT));
                ps.setString(5, data.uuid().toString());
                ps.executeUpdate();
            }
            for (Map.Entry<Category, Set<String>> e : data.unlocked().entrySet()) {
                for (String item : e.getValue()) unlock(data.uuid(), e.getKey(), item);
            }
            for (Map.Entry<Category, String> e : data.active().entrySet()) setActive(data.uuid(), e.getKey(), e.getValue());
            c.commit();
        } catch (SQLException ex) {
            c.rollback();
            throw ex;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    @Override
    public synchronized String getMeta(String key) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT v FROM " + meta + " WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Override
    public synchronized void setMeta(String key, String value) throws SQLException {
        String sql = dialect == Dialect.MYSQL
                ? "INSERT INTO " + meta + " (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = ?"
                : "INSERT INTO " + meta + " (k, v) VALUES (?, ?) ON CONFLICT (k) DO UPDATE SET v = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized void publish(UUID uuid, String origin) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("INSERT INTO " + changes + " (uuid, origin, created_at) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, origin.length() > 64 ? origin.substring(0, 64) : origin);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized List<Change> changesAfter(long id) throws SQLException {
        List<Change> out = new ArrayList<>();
        try (PreparedStatement ps = conn().prepareStatement("SELECT id, uuid, origin FROM " + changes
                + " WHERE id > ? ORDER BY id LIMIT 500")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.add(new Change(rs.getLong(1), UUID.fromString(rs.getString(2)), rs.getString(3)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        return out;
    }

    @Override
    public synchronized long lastChangeId() throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("SELECT COALESCE(MAX(id), 0) FROM " + changes);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    @Override
    public synchronized void prune(long olderThanMillis) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + changes + " WHERE created_at < ?")) {
            ps.setLong(1, olderThanMillis);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM " + ops + " WHERE created_at < ?")) {
            ps.setLong(1, System.currentTimeMillis() - 24L * 3600 * 1000);
            ps.executeUpdate();
        }
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }
}
