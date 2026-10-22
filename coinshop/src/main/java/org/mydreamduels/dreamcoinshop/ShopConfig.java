package org.mydreamduels.dreamcoinshop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/** config.yml, read once per (re)load. Everything else reads these cached values. */
final class ShopConfig {

    static final int CONFIG_VERSION = 2;

    /** Anything a player can buy in a cosmetic category (colours, glows, perks, tags, gradients). */
    record Item(Category category, String key, String display, double price, String icon,
                String color, List<String> gradient, boolean rainbow, String text, String permission) {

        boolean isGradient() {
            return gradient != null && gradient.size() >= 2;
        }
    }

    record Wand(String key, String display, double multiplier, int uses, double price) {
    }

    record BuyTier(String key, String priceLabel, long coins) {
    }

    record OrbItem(String key, String display, double price, String icon, List<String> commands) {
    }

    enum MenuStyle { AUTO, DIALOG, GUI }

    enum BedrockFlags { TEXT, GLYPH, HIDE }

    record Storage(String type, String host, int port, String database, String username, String password,
                   boolean ssl, String prefix, String redisUsername, int redisDatabase, String serverName, int pollMs) {
    }

    /** The sections of the shop, in main menu order. */
    static final List<String> SECTIONS = List.of("chatcolors", "glows", "perks", "tags", "gradients", "flags",
            "sellwands", "spawnerwands", "buycoins", "topcoins", "orbshop");

    // menus
    MenuStyle menuStyle = MenuStyle.AUTO;
    MenuStyle bedrockStyle = MenuStyle.GUI;
    int clickCooldownMs = 400;
    boolean showBalance = true;
    final Map<String, String> titles = new LinkedHashMap<>();
    final Map<String, String> descriptions = new LinkedHashMap<>();
    final Map<String, Boolean> sections = new LinkedHashMap<>();

    // buying
    boolean equipOnPurchase = true;
    boolean logPurchases = true;

    // chat and names
    boolean chatEnabled = true;
    String chatFormat = "<name><tag><flag><gray>: </gray><message>";
    boolean chatTags = true;
    boolean chatFlags = true;
    boolean chatColors = true;
    EventPriority chatPriority = EventPriority.MONITOR;
    boolean namesTabList = true;
    boolean namesDisplay = true;

    // flags
    double flagPrice = 1000;
    List<String> flagsSold = List.of();
    boolean flagsSellAll = true;
    List<String> flagsFeatured = List.of();
    boolean flagsRotate = true;
    int flagsRotateSeconds = 2;
    int flagsRotateMaxSeconds = 60;
    String flagTextFormat = "<gray>[<white><code></white>]</gray>";
    BedrockFlags flagsBedrock = BedrockFlags.TEXT;
    int flagsPerPage = 24;

    String storeUrl = "";
    String spawnerWandCommand = "ds givewand %player% %multiplier%";

    // orbs
    boolean orbPayout = true;
    int orbIntervalMinutes = 5;
    double orbAmount = 1;
    boolean orbMessage = true;

    int topSize = 10;
    int topRefreshSeconds = 30;

    Storage storage;

    // items
    final Map<Category, Map<String, Item>> items = new LinkedHashMap<>();
    final Map<String, Wand> sellwands = new LinkedHashMap<>();
    final Map<String, Wand> spawnerWands = new LinkedHashMap<>();
    final Map<String, BuyTier> buyTiers = new LinkedHashMap<>();
    final Map<String, OrbItem> orbItems = new LinkedHashMap<>();
    final Map<String, Double> sellPrices = new LinkedHashMap<>();

    /** config.yml can't be read (a YAML mistake). Nothing is changed or saved then. */
    static final class BrokenConfig extends Exception {
        BrokenConfig(String message) {
            super(message);
        }
    }

    /** Reads a yml file strictly: a mistake throws instead of quietly giving an empty file. */
    static YamlConfiguration parse(File file) throws BrokenConfig {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            String why = e.getMessage() == null ? "" : e.getMessage().replaceAll("\\s+", " ");
            throw new BrokenConfig(file.getName() + " has a mistake and can't be read: " + why);
        } catch (IOException e) {
            throw new BrokenConfig("Could not read " + file.getName() + ": " + e.getMessage());
        }
        return yaml;
    }

    /** Writes the bundled config.yml if needed, upgrades a version 1 file, and reads everything. */
    void load(JavaPlugin plugin) throws BrokenConfig {
        plugin.saveDefaultConfig();
        YamlConfiguration onDisk = parse(new File(plugin.getDataFolder(), "config.yml"));
        upgrade(plugin, onDisk);
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException ignored) {
        }
        read(cfg, plugin);
    }

    private void read(FileConfiguration cfg, JavaPlugin plugin) {
        menuStyle = enumOr(MenuStyle.class, cfg.getString("menu.style"), MenuStyle.AUTO);
        bedrockStyle = enumOr(MenuStyle.class, cfg.getString("menu.bedrock-style"), MenuStyle.GUI);
        if (bedrockStyle == MenuStyle.AUTO) bedrockStyle = MenuStyle.GUI;
        clickCooldownMs = clamp(cfg.getInt("menu.click-cooldown-ms", 400), 0, 5000);
        showBalance = cfg.getBoolean("menu.show-balance", true);
        readStrings(cfg.getConfigurationSection("menu.titles"), titles);
        readStrings(cfg.getConfigurationSection("menu.descriptions"), descriptions);
        sections.clear();
        for (String s : SECTIONS) sections.put(s, cfg.getBoolean("categories." + s, true));

        equipOnPurchase = cfg.getBoolean("shop.equip-on-purchase", true);
        logPurchases = cfg.getBoolean("shop.log-purchases", true);

        chatEnabled = cfg.getBoolean("chat.enabled", true);
        chatFormat = cfg.getString("chat.format", chatFormat);
        if (!chatFormat.contains("<message>")) chatFormat = chatFormat + "<gray>: </gray><message>";
        chatTags = cfg.getBoolean("chat.show-tags", true);
        chatFlags = cfg.getBoolean("chat.show-flags", true);
        chatColors = cfg.getBoolean("chat.chat-colors", true);
        chatPriority = enumOr(EventPriority.class, cfg.getString("chat.priority"), EventPriority.MONITOR);
        namesTabList = cfg.getBoolean("names.tab-list", true);
        namesDisplay = cfg.getBoolean("names.display-name", true);

        flagPrice = Math.max(0, cfg.getDouble("flags.price", 1000));
        Object sold = cfg.get("flags.sold");
        if (sold instanceof List<?> list) {
            List<String> codes = new ArrayList<>();
            for (Object o : list) codes.add(String.valueOf(o).trim().toUpperCase(Locale.ROOT));
            flagsSold = List.copyOf(codes);
            flagsSellAll = false;
        } else {
            flagsSold = List.of();
            flagsSellAll = true;
        }
        List<String> featured = new ArrayList<>();
        for (String s : cfg.getStringList("flags.featured")) featured.add(s.trim().toUpperCase(Locale.ROOT));
        flagsFeatured = List.copyOf(featured);
        flagsRotate = cfg.getBoolean("flags.rotate.enabled", true);
        flagsRotateSeconds = clamp(cfg.getInt("flags.rotate.seconds", 2), 1, 60);
        flagsRotateMaxSeconds = clamp(cfg.getInt("flags.rotate.max-seconds", 60), 0, 3600);
        flagTextFormat = cfg.getString("flags.text-format", flagTextFormat);
        flagsBedrock = enumOr(BedrockFlags.class, cfg.getString("flags.bedrock"), BedrockFlags.TEXT);
        flagsPerPage = clamp(cfg.getInt("flags.per-page", 24), 6, 40);

        storeUrl = cfg.getString("store-url", "");
        spawnerWandCommand = cfg.getString("spawnerwand-give-command", spawnerWandCommand);

        orbPayout = cfg.getBoolean("orbs.payout.enabled", true);
        orbIntervalMinutes = clamp(cfg.getInt("orbs.payout.interval-minutes", 5), 1, 1440);
        orbAmount = Math.max(0, cfg.getDouble("orbs.payout.amount", 1));
        orbMessage = cfg.getBoolean("orbs.payout.message", true);

        topSize = clamp(cfg.getInt("top.size", 10), 1, 45);
        topRefreshSeconds = clamp(cfg.getInt("top.refresh-seconds", 30), 5, 3600);

        String type = cfg.getString("storage.type", "SQLITE").trim().toUpperCase(Locale.ROOT);
        int defaultPort = type.startsWith("POSTGRES") ? 5432 : type.equals("REDIS") ? 6379 : 3306;
        storage = new Storage(type,
                cfg.getString("storage.host", "localhost"),
                cfg.getInt("storage.port", defaultPort),
                cfg.getString("storage.database", "minecraft"),
                cfg.getString("storage.username", "root"),
                cfg.getString("storage.password", ""),
                cfg.getBoolean("storage.ssl", false),
                cfg.getString("storage.table-prefix", "dcs_"),
                cfg.getString("storage.redis-username", ""),
                clamp(cfg.getInt("storage.redis-database", 0), 0, 15),
                cfg.getString("storage.server-name", "survival"),
                clamp(cfg.getInt("storage.sync-poll-ms", 1000), 250, 60000));

        items.clear();
        readItems(cfg, "chatcolors", Category.CHATCOLOR, 1000);
        readItems(cfg, "glows", Category.GLOW, 1000);
        readItems(cfg, "perks", Category.PERK, 100);
        readItems(cfg, "tags", Category.TAG, 1000);
        readItems(cfg, "namegradients", Category.GRADIENT, 1000);

        readWands(cfg.getConfigurationSection("sellwands"), sellwands, 800);
        readWands(cfg.getConfigurationSection("spawnerwands"), spawnerWands, 1200);
        buyTiers.clear();
        ConfigurationSection bc = cfg.getConfigurationSection("buycoins");
        if (bc != null) {
            for (String key : bc.getKeys(false)) {
                ConfigurationSection s = bc.getConfigurationSection(key);
                if (s != null) buyTiers.put(key, new BuyTier(key, s.getString("price-label", key), s.getLong("coins", 0)));
            }
        }
        orbItems.clear();
        ConfigurationSection os = cfg.getConfigurationSection("orbshop");
        if (os != null) {
            for (String key : os.getKeys(false)) {
                ConfigurationSection s = os.getConfigurationSection(key);
                if (s != null) {
                    orbItems.put(key, new OrbItem(key, s.getString("display", key), Math.max(0, s.getDouble("price", 10)),
                            s.getString("icon", "SPAWNER"), List.copyOf(s.getStringList("commands"))));
                }
            }
        }
        sellPrices.clear();
        ConfigurationSection sp = cfg.getConfigurationSection("sell-prices");
        if (sp != null) {
            for (String mat : sp.getKeys(false)) sellPrices.put(mat.toUpperCase(Locale.ROOT), sp.getDouble(mat));
        }
    }

    private void readItems(FileConfiguration cfg, String path, Category category, double defaultPrice) {
        Map<String, Item> map = new LinkedHashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection(path);
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null || s.getBoolean("event-only", false)) continue;
                List<String> gradient = null;
                if (category == Category.GRADIENT) gradient = List.of(s.getString("from", "#FFFFFF"), s.getString("to", "#FFFFFF"));
                else if (s.isList("gradient")) gradient = List.copyOf(s.getStringList("gradient"));
                map.put(key, new Item(category, key, s.getString("display", key), Math.max(0, s.getDouble("price", defaultPrice)),
                        s.getString("icon", null), s.getString("color", null), gradient, s.getBoolean("rainbow", false),
                        s.getString("text", key), s.getString("permission", "dreamcoinshop.perk." + key)));
            }
        }
        items.put(category, Collections.unmodifiableMap(map));
    }

    private static void readWands(ConfigurationSection sec, Map<String, Wand> into, double defaultPrice) {
        into.clear();
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s != null) {
                into.put(key, new Wand(key, s.getString("display", key), s.getDouble("multiplier", 1.5),
                        Math.max(1, s.getInt("uses", 500)), Math.max(0, s.getDouble("price", defaultPrice))));
            }
        }
    }

    private static void readStrings(ConfigurationSection sec, Map<String, String> into) {
        into.clear();
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            String v = sec.getString(key);
            if (v != null) into.put(key, v);
        }
    }

    Map<String, Item> items(Category c) {
        return items.getOrDefault(c, Map.of());
    }

    Item item(Category c, String key) {
        return key == null ? null : items(c).get(key);
    }

    boolean section(String id) {
        return sections.getOrDefault(id, true);
    }

    String title(String key, String fallback) {
        return titles.getOrDefault(key, fallback);
    }

    String description(String key, String fallback) {
        return descriptions.getOrDefault(key, fallback);
    }

    double sellPrice(String material) {
        return sellPrices.getOrDefault(material.toUpperCase(Locale.ROOT), 0.0);
    }

    // ------------------------------------------------------------------ upgrade from version 1

    /**
     * Version 1 had no config-version and a different layout (settings: ..., orbs.interval-minutes,
     * head-hunts). Its file is saved as config-old.yml, a fresh config.yml is written, and every value
     * the owner had (prices, items, titles, store link...) is carried over.
     */
    private static void upgrade(JavaPlugin plugin, YamlConfiguration old) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (old.getInt("config-version", 1) >= CONFIG_VERSION) return;
        // Only a real version 1 file (it had "settings:") is rebuilt; anything else just gets the number
        if (!old.isConfigurationSection("settings")) {
            try {
                old.set("config-version", CONFIG_VERSION);
                old.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save config.yml: " + e.getMessage());
            }
            return;
        }
        try {
            File backup = new File(plugin.getDataFolder(), "config-old.yml");
            if (backup.exists()) {
                backup = new File(plugin.getDataFolder(), "config-old-" + System.currentTimeMillis() + ".yml");
            }
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.saveResource("config.yml", true);
            YamlConfiguration fresh = YamlConfiguration.loadConfiguration(file);

            copy(old, fresh, "license-key", "license-key");
            copy(old, fresh, "settings.gui-title", "menu.titles.main");
            copySection(old, fresh, "settings.menu-titles", "menu.titles");
            copySection(old, fresh, "settings.menu-descriptions", "menu.descriptions");
            copy(old, fresh, "settings.store-url", "store-url");
            copy(old, fresh, "settings.spawnerwand-give-command", "spawnerwand-give-command");
            copy(old, fresh, "orbs.interval-minutes", "orbs.payout.interval-minutes");
            copy(old, fresh, "orbs.amount-per-interval", "orbs.payout.amount");
            if (old.isString("orbs.gui-title") && !old.isString("settings.menu-titles.orbshop")) {
                fresh.set("menu.titles.orbshop", old.getString("orbs.gui-title"));
            }
            String nameFormat = old.getString("settings.chat-name-format");
            if (nameFormat != null && !nameFormat.equals("<name> <tag>")) {
                String converted = nameFormat.replace(" <tag>", "<tag>").replace("<tag>", "<tag><flag>");
                fresh.set("chat.format", converted + "<gray>: </gray><message>");
            }
            for (String section : List.of("chatcolors", "glows", "perks", "tags", "namegradients", "sellwands",
                    "sell-prices", "spawnerwands", "buycoins", "orbshop")) {
                if (!old.isConfigurationSection(section)) continue;
                // Empty the section but keep its key, so the comment above it stays
                ConfigurationSection target = fresh.getConfigurationSection(section);
                // Version 1 items have no icon: keep the 2.0 icon of an item with the same key
                Map<String, Object> icons = new HashMap<>();
                if (target != null) {
                    for (String key : target.getKeys(false)) {
                        if (target.contains(key + ".icon")) icons.put(key, target.get(key + ".icon"));
                        target.set(key, null);
                    }
                }
                copySection(old, fresh, section, section);
                for (Map.Entry<String, Object> icon : icons.entrySet()) {
                    String path = section + "." + icon.getKey();
                    if (fresh.isConfigurationSection(path) && !fresh.contains(path + ".icon")) fresh.set(path + ".icon", icon.getValue());
                }
            }
            // The head hunt event is gone; so is its event-only tag
            if (fresh.getBoolean("tags.india.event-only", false)) fresh.set("tags.india", null);
            if (old.isBoolean("settings.sounds-enabled") && !old.getBoolean("settings.sounds-enabled")) {
                YamlConfiguration sounds = MessageFile.load(plugin, "sounds.yml");
                sounds.set("enabled", false);
                sounds.save(new File(plugin.getDataFolder(), "sounds.yml"));
            }
            fresh.set("config-version", CONFIG_VERSION);
            fresh.save(file);
            plugin.getLogger().info("config.yml updated to the new layout (old file saved as " + backup.getName() + "). "
                    + "Your prices, items and texts were kept.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not upgrade config.yml - your old file is config-old.yml", e);
        }
    }

    private static void copy(YamlConfiguration from, YamlConfiguration to, String fromPath, String toPath) {
        if (from.contains(fromPath) && !from.isConfigurationSection(fromPath)) to.set(toPath, from.get(fromPath));
    }

    /** Copies every value under a section, keeping nested sections as sections. */
    private static void copySection(YamlConfiguration from, YamlConfiguration to, String fromPath, String toPath) {
        ConfigurationSection sec = from.getConfigurationSection(fromPath);
        if (sec == null) return;
        for (String key : sec.getKeys(true)) {
            if (sec.isConfigurationSection(key)) continue;
            to.set(toPath + "." + key, sec.get(key));
        }
    }

    // ------------------------------------------------------------------ helpers

    static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    static <E extends Enum<E>> E enumOr(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
