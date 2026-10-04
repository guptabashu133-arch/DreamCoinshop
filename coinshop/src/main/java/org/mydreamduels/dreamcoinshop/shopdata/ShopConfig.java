package org.mydreamduels.dreamcoinshop.shopdata;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopConfig {
    private final Map<String, ShopOptions.ChatColorOption> chatColors = new LinkedHashMap<>();
    private final Map<String, ShopOptions.GlowOption> glows = new LinkedHashMap<>();
    private final Map<String, ShopOptions.PerkOption> perks = new LinkedHashMap<>();
    private final Map<String, ShopOptions.TagOption> tags = new LinkedHashMap<>();
    private final Map<String, ShopOptions.GradientOption> nameGradients = new LinkedHashMap<>();
    private final Map<String, ShopOptions.SellwandTier> sellwands = new LinkedHashMap<>();
    private final Map<String, ShopOptions.SpawnerWandTier> spawnerWands = new LinkedHashMap<>();
    private final Map<String, ShopOptions.BuyCoinsTier> buyCoins = new LinkedHashMap<>();
    private final Map<String, ShopOptions.OrbShopItem> orbShopItems = new LinkedHashMap<>();
    private final Map<String, Double> sellPrices = new LinkedHashMap<>();
    private final Map<String, String> menuTitles = new LinkedHashMap<>();
    private final Map<String, String> menuDescriptions = new LinkedHashMap<>();
    private String guiTitle = "<gold><bold>Coinshop";
    private boolean soundsEnabled = true;
    private String chatNameFormat = "<name> <tag>";
    private String storeUrl = "";
    private int orbIntervalMinutes = 5;
    private double orbAmountPerInterval = 1.0;
    /** how often coins.yml / orbs.yml are written out; see CurrencyManager */
    private long balanceSaveIntervalSeconds = 15L;
    private String orbShopTitle = "<light_purple><bold>Orbshop";
    private String spawnerWandGiveCommand = "ds givewand %player% %multiplier%";

    public void load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                plugin.getConfig().setDefaults((Configuration) defaults);
                plugin.getConfig().options().copyDefaults(true);
                plugin.saveConfig();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not merge new config.yml defaults - if a menu looks empty, delete config.yml and let it regenerate.", e);
        }
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        this.guiTitle = cfg.getString("settings.gui-title", this.guiTitle);
        this.soundsEnabled = cfg.getBoolean("settings.sounds-enabled", true);
        this.chatNameFormat = cfg.getString("settings.chat-name-format", this.chatNameFormat);
        this.storeUrl = cfg.getString("settings.store-url", "");
        this.orbIntervalMinutes = cfg.getInt("orbs.interval-minutes", 5);
        this.orbAmountPerInterval = cfg.getDouble("orbs.amount-per-interval", 1.0);
        this.balanceSaveIntervalSeconds = cfg.getLong("storage.balance-save-interval-seconds", 15L);
        this.orbShopTitle = cfg.getString("orbs.gui-title", this.orbShopTitle);
        this.spawnerWandGiveCommand = cfg.getString("settings.spawnerwand-give-command", this.spawnerWandGiveCommand);

        this.menuTitles.clear();
        ConfigurationSection mt = cfg.getConfigurationSection("settings.menu-titles");
        if (mt != null) {
            for (String key : mt.getKeys(false)) {
                this.menuTitles.put(key, mt.getString(key));
            }
        }
        this.menuDescriptions.clear();
        ConfigurationSection md = cfg.getConfigurationSection("settings.menu-descriptions");
        if (md != null) {
            for (String key : md.getKeys(false)) {
                this.menuDescriptions.put(key, md.getString(key));
            }
        }
        this.chatColors.clear();
        ConfigurationSection cc = cfg.getConfigurationSection("chatcolors");
        if (cc != null) {
            for (String key : cc.getKeys(false)) {
                ConfigurationSection s = cc.getConfigurationSection(key);
                if (s == null) continue;
                this.chatColors.put(key, new ShopOptions.ChatColorOption(key, s.getString("display", key), s.getString("color", null), s.contains("gradient") ? s.getStringList("gradient") : null, s.getDouble("price", 1000.0)));
            }
        }
        this.glows.clear();
        ConfigurationSection gg = cfg.getConfigurationSection("glows");
        if (gg != null) {
            for (String key : gg.getKeys(false)) {
                ConfigurationSection s = gg.getConfigurationSection(key);
                if (s == null) continue;
                this.glows.put(key, new ShopOptions.GlowOption(key, s.getString("display", key), s.getString("color", null), s.getBoolean("rainbow", false), s.getDouble("price", 1000.0)));
            }
        }
        this.perks.clear();
        ConfigurationSection pp = cfg.getConfigurationSection("perks");
        if (pp != null) {
            for (String key : pp.getKeys(false)) {
                ConfigurationSection s = pp.getConfigurationSection(key);
                if (s == null) continue;
                this.perks.put(key, new ShopOptions.PerkOption(key, s.getString("display", key), s.getString("permission", "dreamcoinshop.perk." + key), s.getDouble("price", 100.0)));
            }
        }
        this.tags.clear();
        ConfigurationSection tt = cfg.getConfigurationSection("tags");
        if (tt != null) {
            for (String key : tt.getKeys(false)) {
                ConfigurationSection s = tt.getConfigurationSection(key);
                if (s == null) continue;
                this.tags.put(key, new ShopOptions.TagOption(key, s.getString("display", key), s.getString("text", key), s.getDouble("price", 1000.0)));
            }
        }
        this.nameGradients.clear();
        ConfigurationSection ng = cfg.getConfigurationSection("namegradients");
        if (ng != null) {
            for (String key : ng.getKeys(false)) {
                ConfigurationSection s = ng.getConfigurationSection(key);
                if (s == null) continue;
                this.nameGradients.put(key, new ShopOptions.GradientOption(key, s.getString("display", key), s.getString("from", "#FFFFFF"), s.getString("to", "#FFFFFF"), s.getDouble("price", 1000.0)));
            }
        }
        this.sellwands.clear();
        ConfigurationSection sw = cfg.getConfigurationSection("sellwands");
        if (sw != null) {
            for (String key : sw.getKeys(false)) {
                ConfigurationSection s = sw.getConfigurationSection(key);
                if (s == null) continue;
                this.sellwands.put(key, new ShopOptions.SellwandTier(key, s.getString("display", key), s.getDouble("multiplier", 1.5), s.getInt("uses", 500), s.getDouble("price", 800.0)));
            }
        }
        this.spawnerWands.clear();
        ConfigurationSection spw = cfg.getConfigurationSection("spawnerwands");
        if (spw != null) {
            for (String key : spw.getKeys(false)) {
                ConfigurationSection s = spw.getConfigurationSection(key);
                if (s == null) continue;
                this.spawnerWands.put(key, new ShopOptions.SpawnerWandTier(key, s.getString("display", key), s.getDouble("multiplier", 1.5), s.getInt("uses", 500), s.getDouble("price", 1200.0)));
            }
        }
        this.buyCoins.clear();
        ConfigurationSection bc = cfg.getConfigurationSection("buycoins");
        if (bc != null) {
            for (String key : bc.getKeys(false)) {
                ConfigurationSection s = bc.getConfigurationSection(key);
                if (s == null) continue;
                this.buyCoins.put(key, new ShopOptions.BuyCoinsTier(key, s.getString("price-label", key), s.getInt("coins", 0), s.getString("store-url", "")));
            }
        }
        this.sellPrices.clear();
        ConfigurationSection spSec = cfg.getConfigurationSection("sell-prices");
        if (spSec != null) {
            for (String mat : spSec.getKeys(false)) {
                this.sellPrices.put(mat.toUpperCase(), spSec.getDouble(mat));
            }
        }
        this.orbShopItems.clear();
        ConfigurationSection os = cfg.getConfigurationSection("orbshop");
        if (os != null) {
            for (String key : os.getKeys(false)) {
                ConfigurationSection s = os.getConfigurationSection(key);
                if (s == null) continue;
                this.orbShopItems.put(key, new ShopOptions.OrbShopItem(key, s.getString("display", key), s.getDouble("price", 10.0), s.getStringList("commands")));
            }
        }
    }

    public String getGuiTitle() {
        return this.guiTitle;
    }

    public boolean isSoundsEnabled() {
        return this.soundsEnabled;
    }

    public String getChatNameFormat() {
        return this.chatNameFormat;
    }

    public String getStoreUrl() {
        return this.storeUrl;
    }

    public Map<String, ShopOptions.ChatColorOption> getChatColors() {
        return this.chatColors;
    }

    public Map<String, ShopOptions.GlowOption> getGlows() {
        return this.glows;
    }

    public Map<String, ShopOptions.PerkOption> getPerks() {
        return this.perks;
    }

    public Map<String, ShopOptions.TagOption> getTags() {
        return this.tags;
    }

    public Map<String, ShopOptions.GradientOption> getNameGradients() {
        return this.nameGradients;
    }

    public Map<String, ShopOptions.SellwandTier> getSellwands() {
        return this.sellwands;
    }

    public Map<String, ShopOptions.SpawnerWandTier> getSpawnerWands() {
        return this.spawnerWands;
    }

    public Map<String, ShopOptions.BuyCoinsTier> getBuyCoins() {
        return this.buyCoins;
    }

    public Map<String, ShopOptions.OrbShopItem> getOrbShopItems() {
        return this.orbShopItems;
    }

    public Map<String, Double> getSellPrices() {
        return this.sellPrices;
    }

    public Map<String, String> getMenuTitles() {
        return this.menuTitles;
    }

    public Map<String, String> getMenuDescriptions() {
        return this.menuDescriptions;
    }

    public int getOrbIntervalMinutes() {
        return this.orbIntervalMinutes;
    }

    public double getOrbAmountPerInterval() {
        return this.orbAmountPerInterval;
    }

    public long getBalanceSaveIntervalSeconds() {
        return this.balanceSaveIntervalSeconds;
    }

    public String getOrbShopTitle() {
        return this.orbShopTitle;
    }

    public String getSpawnerWandGiveCommand() {
        return this.spawnerWandGiveCommand;
    }

    public String menuTitle(String key, String fallback) {
        return this.menuTitles.getOrDefault(key, fallback);
    }

    public String menuDescription(String key, String fallback) {
        return this.menuDescriptions.getOrDefault(key, fallback);
    }

    public double getSellPrice(String material) {
        return this.sellPrices.getOrDefault(material.toUpperCase(), 0.0);
    }
}
