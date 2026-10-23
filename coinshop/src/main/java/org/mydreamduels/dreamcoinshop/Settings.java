package org.mydreamduels.dreamcoinshop;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings an admin can change in-game (/coinshop settings): switches, values, choices and
 * prices. A change is written to config.yml (sounds.yml for the sound switch) and applied right
 * away with a settings reload.
 */
final class Settings {

    enum Kind { TOGGLE, NUMBER, CHOICE, TEXT }

    record Def(String id, Kind kind, String path, String icon, double min, double max, double step, List<String> options) {

        /** Dialog input keys may only contain letters, digits and _. */
        String inputKey() {
            return id.replace('-', '_');
        }

        boolean integer() {
            return step == Math.floor(step);
        }
    }

    static final String SOUNDS = "sounds.yml:enabled";

    static final List<Def> TOGGLES = List.of(
            toggle("cat-chatcolors", "categories.chatcolors", "NAME_TAG"),
            toggle("cat-glows", "categories.glows", "GLOWSTONE_DUST"),
            toggle("cat-perks", "categories.perks", "NETHER_STAR"),
            toggle("cat-tags", "categories.tags", "PAPER"),
            toggle("cat-gradients", "categories.gradients", "FIREWORK_STAR"),
            toggle("cat-flags", "categories.flags", "WHITE_BANNER"),
            toggle("cat-particles", "categories.particles", "BLAZE_POWDER"),
            toggle("cat-sellwands", "categories.sellwands", "BLAZE_ROD"),
            toggle("cat-spawnerwands", "categories.spawnerwands", "SPAWNER"),
            toggle("cat-buycoins", "categories.buycoins", "GOLD_INGOT"),
            toggle("cat-topcoins", "categories.topcoins", "DIAMOND"),
            toggle("cat-orbshop", "categories.orbshop", "ENDER_PEARL"),
            toggle("equip-on-purchase", "shop.equip-on-purchase", "ARMOR_STAND"),
            toggle("log-purchases", "shop.log-purchases", "WRITABLE_BOOK"),
            toggle("show-balance", "menu.show-balance", "GOLD_NUGGET"),
            toggle("sounds", SOUNDS, "NOTE_BLOCK"),
            toggle("chat", "chat.enabled", "OAK_SIGN"),
            toggle("chat-tags", "chat.show-tags", "NAME_TAG"),
            toggle("chat-flags", "chat.show-flags", "WHITE_BANNER"),
            toggle("chat-colors", "chat.chat-colors", "MAGENTA_DYE"),
            toggle("names-tab", "names.tab-list", "PLAYER_HEAD"),
            toggle("names-display", "names.display-name", "NAME_TAG"),
            toggle("flags-rotate", "flags.rotate.enabled", "COMPASS"),
            toggle("particles-others", "particle-settings.visible-to-others", "ENDER_EYE"),
            toggle("particles-invisible", "particle-settings.when-invisible", "GLASS"),
            toggle("sale-broadcast", "sale.broadcast", "BELL"),
            toggle("sale-old-price", "sale.show-old-price", "PAPER"),
            toggle("pay-coins", "pay.coins.enabled", "GOLD_NUGGET"),
            toggle("pay-orbs", "pay.orbs.enabled", "AMETHYST_SHARD"),
            toggle("orbs-payout", "orbs.payout.enabled", "ENDER_PEARL"),
            toggle("orbs-message", "orbs.payout.message", "BELL"));

    static final List<Def> VALUES = List.of(
            number("flag-price", "flags.price", "WHITE_BANNER", 0, 10_000_000, 100),
            number("orb-interval", "orbs.payout.interval-minutes", "CLOCK", 1, 1440, 1),
            number("orb-amount", "orbs.payout.amount", "ENDER_PEARL", 0, 100_000, 0.5),
            number("rotate-seconds", "flags.rotate.seconds", "COMPASS", 1, 60, 1),
            number("top-size", "top.size", "DIAMOND", 1, 45, 1),
            number("top-full-size", "top.full-size", "BOOK", 10, 100, 5),
            number("click-cooldown", "menu.click-cooldown-ms", "LEVER", 0, 2000, 50),
            number("particle-interval", "particle-settings.interval-ticks", "CLOCK", 1, 40, 1),
            number("pay-tax", "pay.coins.tax-percent", "GOLD_INGOT", 0, 50, 1),
            number("pay-min", "pay.coins.min", "GOLD_NUGGET", 0, 1_000_000, 1),
            text("coins-name", "currencies.coins.name", "GOLD_INGOT"),
            text("orbs-name", "currencies.orbs.name", "AMETHYST_SHARD"),
            text("orbshop-name", "currencies.orbs.shop-name", "ENDER_PEARL"),
            choice("menu-style", "menu.style", "CHEST", List.of("AUTO", "DIALOG", "GUI")),
            choice("bedrock-style", "menu.bedrock-style", "CHEST", List.of("GUI", "DIALOG")),
            choice("flags-bedrock", "flags.bedrock", "WHITE_BANNER", List.of("TEXT", "GLYPH", "HIDE")),
            new Def("store-url", Kind.TEXT, "store-url", "GOLD_INGOT", 0, 0, 0, List.of()));

    /** Price editor sections: id, config path, currency label. */
    record PriceSection(String id, String path, boolean orbs, String icon) {
    }

    static final List<PriceSection> PRICE_SECTIONS = List.of(
            new PriceSection("chatcolors", "chatcolors", false, "NAME_TAG"),
            new PriceSection("glows", "glows", false, "GLOWSTONE_DUST"),
            new PriceSection("perks", "perks", false, "NETHER_STAR"),
            new PriceSection("tags", "tags", false, "PAPER"),
            new PriceSection("gradients", "namegradients", false, "FIREWORK_STAR"),
            new PriceSection("particles", "particles", false, "BLAZE_POWDER"),
            new PriceSection("sellwands", "sellwands", false, "BLAZE_ROD"),
            new PriceSection("spawnerwands", "spawnerwands", false, "SPAWNER"),
            new PriceSection("orbshop", "orbshop", true, "ENDER_PEARL"));

    private static Def toggle(String id, String path, String icon) {
        return new Def(id, Kind.TOGGLE, path, icon, 0, 1, 1, List.of());
    }

    private static Def number(String id, String path, String icon, double min, double max, double step) {
        return new Def(id, Kind.NUMBER, path, icon, min, max, step, List.of());
    }

    private static Def text(String id, String path, String icon) {
        return new Def(id, Kind.TEXT, path, icon, 0, 0, 0, List.of());
    }

    private static Def choice(String id, String path, String icon, List<String> options) {
        return new Def(id, Kind.CHOICE, path, icon, 0, 0, 0, options);
    }

    static Def find(String id) {
        for (Def d : TOGGLES) if (d.id().equals(id)) return d;
        for (Def d : VALUES) if (d.id().equals(id)) return d;
        return null;
    }

    static PriceSection priceSection(String id) {
        for (PriceSection s : PRICE_SECTIONS) if (s.id().equals(id)) return s;
        return null;
    }

    private final Dreamcoinshop plugin;
    private final Platform platform;

    Settings(Dreamcoinshop plugin, Platform platform) {
        this.plugin = plugin;
        this.platform = platform;
    }

    /** Current value, from the loaded settings (safe on any thread). */
    Object get(Def d) {
        ShopConfig c = plugin.config();
        return switch (d.id()) {
            case "equip-on-purchase" -> c.equipOnPurchase;
            case "log-purchases" -> c.logPurchases;
            case "show-balance" -> c.showBalance;
            case "sounds" -> plugin.sounds().isEnabled();
            case "chat" -> c.chatEnabled;
            case "chat-tags" -> c.chatTags;
            case "chat-flags" -> c.chatFlags;
            case "chat-colors" -> c.chatColors;
            case "names-tab" -> c.namesTabList;
            case "names-display" -> c.namesDisplay;
            case "flags-rotate" -> c.flagsRotate;
            case "particles-others" -> c.particlesVisibleToOthers;
            case "particles-invisible" -> c.particlesWhenInvisible;
            case "sale-broadcast" -> c.saleBroadcast;
            case "sale-old-price" -> c.saleShowOldPrice;
            case "pay-coins" -> c.payCoins.enabled();
            case "pay-orbs" -> c.payOrbs.enabled();
            case "particle-interval" -> (double) c.particlesIntervalTicks;
            case "pay-tax" -> c.payCoins.taxPercent();
            case "pay-min" -> c.payCoins.min();
            case "coins-name" -> c.coinsName;
            case "orbs-name" -> c.orbsName;
            case "orbshop-name" -> c.orbshopName;
            case "orbs-payout" -> c.orbPayout;
            case "orbs-message" -> c.orbMessage;
            case "flag-price" -> c.flagPrice;
            case "orb-interval" -> (double) c.orbIntervalMinutes;
            case "orb-amount" -> c.orbAmount;
            case "rotate-seconds" -> (double) c.flagsRotateSeconds;
            case "top-size" -> (double) c.topSize;
            case "top-full-size" -> (double) c.topFullSize;
            case "click-cooldown" -> (double) c.clickCooldownMs;
            case "menu-style" -> c.menuStyle.name();
            case "bedrock-style" -> c.bedrockStyle.name();
            case "flags-bedrock" -> c.flagsBedrock.name();
            case "store-url" -> c.storeUrl;
            default -> d.id().startsWith("cat-") ? c.section(d.id().substring(4)) : null;
        };
    }

    /** The items of a price section with their current prices, in config order. */
    Map<String, Double> prices(PriceSection s) {
        ShopConfig c = plugin.config();
        Map<String, Double> out = new LinkedHashMap<>();
        switch (s.id()) {
            case "chatcolors" -> c.items(Category.CHATCOLOR).values().forEach(i -> out.put(i.key(), i.price()));
            case "glows" -> c.items(Category.GLOW).values().forEach(i -> out.put(i.key(), i.price()));
            case "perks" -> c.items(Category.PERK).values().forEach(i -> out.put(i.key(), i.price()));
            case "tags" -> c.items(Category.TAG).values().forEach(i -> out.put(i.key(), i.price()));
            case "gradients" -> c.items(Category.GRADIENT).values().forEach(i -> out.put(i.key(), i.price()));
            case "particles" -> c.items(Category.PARTICLE).values().forEach(i -> out.put(i.key(), i.price()));
            case "sellwands" -> c.sellwands.values().forEach(w -> out.put(w.key(), w.price()));
            case "spawnerwands" -> c.spawnerWands.values().forEach(w -> out.put(w.key(), w.price()));
            case "orbshop" -> c.orbItems.values().forEach(o -> out.put(o.key(), o.price()));
            default -> {
            }
        }
        return out;
    }

    /** Display name of a priced item. */
    String priceName(PriceSection s, String key) {
        ShopConfig c = plugin.config();
        return switch (s.id()) {
            case "chatcolors" -> name(c.item(Category.CHATCOLOR, key), key);
            case "glows" -> name(c.item(Category.GLOW, key), key);
            case "perks" -> name(c.item(Category.PERK, key), key);
            case "tags" -> name(c.item(Category.TAG, key), key);
            case "gradients" -> name(c.item(Category.GRADIENT, key), key);
            case "particles" -> name(c.item(Category.PARTICLE, key), key);
            case "sellwands" -> c.sellwands.containsKey(key) ? c.sellwands.get(key).display() : key;
            case "spawnerwands" -> c.spawnerWands.containsKey(key) ? c.spawnerWands.get(key).display() : key;
            case "orbshop" -> c.orbItems.containsKey(key) ? c.orbItems.get(key).display() : key;
            default -> key;
        };
    }

    private static String name(ShopConfig.Item item, String key) {
        return item == null ? key : item.display();
    }

    /**
     * Saves changes (config path -> value) and reloads the settings, then runs then (on the
     * main/global thread, where config.yml is safe to write).
     */
    void apply(Map<String, Object> changes, org.bukkit.entity.Player who, Runnable then) {
        // Checked on every save, not only when the menu opened: the permission may be gone by now
        if (who != null && !who.hasPermission("dreamcoinshop.settings")) {
            plugin.text().send(who, "general.no-permission", null);
            return;
        }
        if (changes.isEmpty()) {
            if (then != null) platform.global(then);
            return;
        }
        platform.global(() -> {
            // Start from the file as it is now, so edits made to it by hand (or the license key the
            // plugin wrote) are kept; and never save over a file that has a mistake in it
            try {
                ShopConfig.parse(new java.io.File(plugin.getDataFolder(), "config.yml"));
            } catch (ShopConfig.BrokenConfig e) {
                plugin.getLogger().severe(e.getMessage());
                if (who != null) plugin.text().send(who, "settings.config-broken", null);
                return;
            }
            plugin.reloadConfig();
            FileConfiguration cfg = plugin.getConfig();
            boolean configChanged = false;
            for (Map.Entry<String, Object> e : changes.entrySet()) {
                if (e.getKey().equals(SOUNDS)) {
                    plugin.sounds().setEnabled(Boolean.TRUE.equals(e.getValue()));
                } else {
                    cfg.set(e.getKey(), e.getValue());
                    configChanged = true;
                }
            }
            if (configChanged) plugin.saveConfig();
            plugin.reloadSettings();
            if (then != null) then.run();
        });
    }

    /** A number for config.yml: whole numbers stay whole. */
    static Object store(Def d, double value) {
        double v = Math.max(d.min(), Math.min(d.max(), value));
        return d.integer() ? (Object) (long) Math.round(v) : (Object) v;
    }

    static Object storePrice(double value) {
        double v = Math.max(0, Math.min(1_000_000_000, value));
        return v == Math.floor(v) ? (Object) (long) v : (Object) v;
    }

    static List<String> pricePaths(PriceSection s, Map<String, Double> prices) {
        List<String> out = new ArrayList<>();
        for (String key : prices.keySet()) out.add(s.path() + "." + key + ".price");
        return out;
    }
}
