package org.mydreamduels.dreamcoinshop;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * An online player's data, kept in memory so the shop never waits for the database. Every change
 * is also written to the database straight away (see {@link DataManager}). Safe to use from any
 * thread: chat runs async, menus on the player's thread, the database on its own thread.
 */
final class Profile {

    final UUID uuid;
    private String name;
    private double coins;
    private double orbs;
    private final EnumMap<Category, Set<String>> unlocked = new EnumMap<>(Category.class);
    private final EnumMap<Category, String> active = new EnumMap<>(Category.class);
    private boolean loaded;
    /** Database writes for this player that haven't finished yet. */
    private int pendingWrites;

    Profile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        for (Category c : Category.values()) unlocked.put(c, new LinkedHashSet<>());
    }

    synchronized String name() {
        return name;
    }

    synchronized void name(String name) {
        this.name = name;
    }

    /** False until the data came from the database; the shop refuses to sell until then. */
    synchronized boolean isLoaded() {
        return loaded;
    }

    synchronized double balance(Currency c) {
        return c == Currency.COINS ? coins : orbs;
    }

    private void put(Currency c, double v) {
        if (c == Currency.COINS) coins = v;
        else orbs = v;
    }

    /** Takes the amount if the player has it. */
    synchronized boolean tryCharge(Currency c, double amount) {
        if (amount < 0 || balance(c) < amount) return false;
        put(c, balance(c) - amount);
        return true;
    }

    synchronized void add(Currency c, double delta) {
        put(c, Math.max(0, balance(c) + delta));
    }

    synchronized void set(Currency c, double value) {
        put(c, Math.max(0, value));
    }

    synchronized boolean owns(Category c, String item) {
        return unlocked.get(c).contains(item);
    }

    synchronized Set<String> owned(Category c) {
        return new LinkedHashSet<>(unlocked.get(c));
    }

    /** True if it wasn't owned yet. */
    synchronized boolean unlock(Category c, String item) {
        return unlocked.get(c).add(item);
    }

    /** True if it was owned. Also unequips it. */
    synchronized boolean revoke(Category c, String item) {
        boolean had = unlocked.get(c).remove(item);
        if (item.equals(active.get(c))) active.remove(c);
        return had;
    }

    synchronized String active(Category c) {
        return active.get(c);
    }

    synchronized void setActive(Category c, String item) {
        if (item == null) active.remove(c);
        else active.put(c, item);
    }

    synchronized void beginWrite() {
        pendingWrites++;
    }

    synchronized void endWrite() {
        pendingWrites--;
    }

    /**
     * Replaces everything with what the database has. Refused (false) while one of this player's
     * own writes is still on its way: the data read then doesn't have it yet.
     */
    synchronized boolean apply(Storage.Data data) {
        if (pendingWrites > 0) return false;
        coins = Math.max(0, data.coins());
        orbs = Math.max(0, data.orbs());
        for (Category c : Category.values()) {
            Set<String> set = unlocked.get(c);
            set.clear();
            Set<String> from = data.unlocked().get(c);
            if (from != null) set.addAll(from);
        }
        active.clear();
        for (Map.Entry<Category, String> e : data.active().entrySet()) {
            if (e.getValue() != null) active.put(e.getKey(), e.getValue());
        }
        loaded = true;
        return true;
    }
}
