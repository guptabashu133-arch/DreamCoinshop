package org.mydreamduels.dreamcoinshop;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Where player data lives: SQLite (this server only), MySQL / MariaDB / PostgreSQL or Redis
 * (shared by every server of a network). Every method blocks, and is only ever called from the
 * plugin's own database thread, never from the server thread.
 */
interface Storage {

    /** One player's saved data. */
    record Data(UUID uuid, String name, double coins, double orbs,
                Map<Category, Set<String>> unlocked, Map<Category, String> active) {

        static Data empty(UUID uuid, String name) {
            Map<Category, Set<String>> unlocked = new EnumMap<>(Category.class);
            for (Category c : Category.values()) unlocked.put(c, new LinkedHashSet<>());
            return new Data(uuid, name, 0, 0, unlocked, new EnumMap<>(Category.class));
        }
    }

    record TopEntry(UUID uuid, String name, double balance) {
    }

    /** "Something about this player changed", sent by the server that changed it. */
    record Change(long id, UUID uuid, String origin) {
    }

    /** Short name for logs, e.g. "MySQL". */
    String describe();

    /** Connects and creates the tables / keys if needed. */
    void init() throws Exception;

    /** Loads a player, creating them if new, and records their current name. */
    Data load(UUID uuid, String name) throws Exception;

    /** A player's data without creating or renaming them; null if they were never seen. */
    Data read(UUID uuid) throws Exception;

    /** balance += delta, never below 0. Creates the player if needed. */
    void addBalance(UUID uuid, Currency currency, double delta) throws Exception;

    void setBalance(UUID uuid, Currency currency, double value) throws Exception;

    void unlock(UUID uuid, Category category, String item) throws Exception;

    void revoke(UUID uuid, Category category, String item) throws Exception;

    /** item null = nothing equipped in that category. */
    void setActive(UUID uuid, Category category, String item) throws Exception;

    List<TopEntry> top(Currency currency, int limit) throws Exception;

    /** The newest player with this name (case doesn't matter), or null. */
    UUID findByName(String name) throws Exception;

    /** Writes everything of one player at once, replacing what was stored. Used by the yml import. */
    void importData(Data data) throws Exception;

    String getMeta(String key) throws Exception;

    void setMeta(String key, String value) throws Exception;

    // ---- cross-server sync ----

    /** Tells the other servers this player changed. */
    void publish(UUID uuid, String origin) throws Exception;

    /** SQL: changes after this id. Redis pushes them instead (see {@link #listen}). */
    List<Change> changesAfter(long id) throws Exception;

    long lastChangeId() throws Exception;

    /** Forgets change notices older than this time. */
    void prune(long olderThanMillis) throws Exception;

    /** True when changes arrive through {@link #listen} instead of polling. */
    default boolean pushesChanges() {
        return false;
    }

    /** Redis: calls back with each change from another server (on its own thread). */
    default void listen(Consumer<Change> onChange, Runnable onReconnect) {
    }

    void close();
}
