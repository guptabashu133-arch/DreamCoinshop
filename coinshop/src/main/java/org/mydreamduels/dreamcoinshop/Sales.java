package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A sale: every price (or the prices of some sections) is percent cheaper until a time. Kept in
 * the database (meta "sale"), so every server of a network has the same sale; the others see a
 * new one within 10 seconds and announce it too.
 */
final class Sales {

    /** Shop sections that can be on sale. */
    static final List<String> SECTIONS = List.of("chatcolors", "glows", "perks", "tags", "gradients", "flags",
            "particles", "sellwands", "spawnerwands", "orbshop");

    /** sections empty = everything. */
    record Sale(int percent, long startedAt, long endsAt, Set<String> sections) {
        static final Sale NONE = new Sale(0, 0, 0, Set.of());

        boolean active(long now) {
            return percent > 0 && now < endsAt;
        }

        boolean covers(String section) {
            return sections.isEmpty() || sections.contains(section);
        }

        String encode() {
            return percent + "|" + startedAt + "|" + endsAt + "|" + String.join(",", sections);
        }

        static Sale decode(String raw) {
            if (raw == null || raw.isBlank()) return NONE;
            String[] p = raw.split("\\|", -1);
            try {
                Set<String> sections = new LinkedHashSet<>();
                if (p.length > 3) for (String s : p[3].split(",")) if (!s.isBlank()) sections.add(s.trim());
                return new Sale(Integer.parseInt(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2]), Set.copyOf(sections));
            } catch (RuntimeException e) {
                return NONE;
            }
        }
    }

    private static final String META_KEY = "sale";

    private final Dreamcoinshop plugin;
    private volatile Sale current = Sale.NONE;
    private Platform.Task task;

    Sales(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    /** Loads the sale now and checks for changes from the other servers every 10 s. */
    void start() {
        stop();
        refresh(false);
        task = plugin.platform().asyncTimer(() -> refresh(true), 10_000, 10_000);
    }

    void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void refresh(boolean announce) {
        plugin.data().execute(s -> {
            Sale loaded = Sale.decode(s.getMeta(META_KEY));
            Sale before = current;
            current = loaded;
            long now = System.currentTimeMillis();
            // Started on another server: tell the players here too
            if (announce && loaded.active(now) && loaded.startedAt() != before.startedAt()) plugin.platform().global(() -> broadcast(loaded));
        });
    }

    Sale current() {
        return current;
    }

    /** The sale percent for a section right now (0 = none). */
    int percent(String section) {
        Sale s = current;
        return s.active(System.currentTimeMillis()) && s.covers(section) ? s.percent() : 0;
    }

    /** The price to pay right now. Whole prices stay whole. */
    double price(String section, double base) {
        int pct = percent(section);
        if (pct <= 0 || base <= 0) return base;
        double v = base * (100 - pct) / 100.0;
        return base == Math.floor(base) ? Math.round(v) : Math.round(v * 100) / 100.0;
    }

    /** Starts a sale (replacing any running one). Main/global thread or any. */
    void start(int percent, long durationMs, Set<String> sections, Runnable done) {
        long now = System.currentTimeMillis();
        Sale sale = new Sale(Math.max(1, Math.min(95, percent)), now, now + durationMs, Set.copyOf(sections));
        current = sale;
        plugin.data().execute(s -> {
            s.setMeta(META_KEY, sale.encode());
            if (done != null) plugin.platform().global(done);
        });
        broadcast(sale);
        refreshMenus();
    }

    void stop(Runnable done) {
        current = Sale.NONE;
        plugin.data().execute(s -> {
            s.setMeta(META_KEY, "");
            if (done != null) plugin.platform().global(done);
        });
        refreshMenus();
    }

    private void refreshMenus() {
        plugin.platform().global(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) plugin.menus().refresh(p);
        });
    }

    private void broadcast(Sale sale) {
        if (!plugin.config().saleBroadcast) return;
        Ph ph = Ph.of("percent", sale.percent(), "time", time(sale.endsAt() - System.currentTimeMillis()),
                "sections", sectionNames(sale));
        String path = sale.sections().isEmpty() ? "sale.started" : "sale.started-some";
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.text().send(p, path, ph);
            plugin.sounds().play(p, "sale-started");
        }
        plugin.text().send(Bukkit.getConsoleSender(), path, ph);
    }

    String sectionNames(Sale sale) {
        if (sale.sections().isEmpty()) return Text.plain(plugin.text().raw("sale.everything"));
        StringBuilder sb = new StringBuilder();
        for (String s : sale.sections()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Text.plain(plugin.text().raw("menu.button." + s)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ time

    /** "1d 2h", "2h 30m", "45m", "30s". */
    static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m";
        return s + "s";
    }

    /** "30m", "2h", "1d12h", "90" (minutes). Null if it can't be read or isn't above 0. */
    static Long parseTime(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.matches("\\d+")) s = s + "m";
        if (!s.matches("(\\d+[dhms])+")) return null;
        long total = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([dhms])").matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
            if (total > 366L * 86_400_000L) return null;
        }
        return total > 0 ? total : null;
    }
}
