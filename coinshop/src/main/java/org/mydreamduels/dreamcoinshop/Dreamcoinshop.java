package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * Dreamcoinshop: a cosmetics shop (chat colours, glows, tags, name gradients, country flags,
 * perks, sellwands) paid with Coins, plus an Orbs shop. Runs on Spigot, Paper, Purpur and Folia,
 * 1.20 to 26.x: dialog menus on Paper 1.21.6+, chest menus everywhere else.
 */
public class Dreamcoinshop extends JavaPlugin implements Listener {

    private Platform platform;
    private BedrockDetector bedrock;
    private volatile ShopConfig config;
    private Text text;
    private Sounds sounds;
    private Flags flags;
    private DataManager data;
    private Cosmetics cosmetics;
    private Shop shop;
    private Settings settings;
    private Sales sales;
    private ParticleEffects particles;
    private ChatPrompts prompts;
    private Menus menus;
    private PaperChat paperChat;
    private LegacyChat legacyChat;
    private org.bukkit.event.EventPriority chatPriority;
    private Platform.Task orbTask;
    private int orbInterval = -1;
    private DreamLicense license;

    @Override
    public void onEnable() {
        platform = Platform.create(this);
        bedrock = new BedrockDetector(getLogger());
        ShopConfig cfg = new ShopConfig();
        try {
            cfg.load(this);
        } catch (ShopConfig.BrokenConfig e) {
            // Without a readable config we don't know which database to use: better stop than split the data
            getLogger().severe(e.getMessage());
            getLogger().severe("Fix config.yml and restart. Dreamcoinshop is disabled; nothing was changed.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        config = cfg;
        text = Capabilities.ADVENTURE ? new AdventureText(this, platform) : new LegacyText(this, platform);
        sounds = new Sounds(this, platform);
        sounds.reload();
        flags = Flags.load();
        cosmetics = new Cosmetics(this, platform);
        particles = new ParticleEffects(this, platform);
        shop = new Shop(this, platform);
        settings = new Settings(this, platform);
        sales = new Sales(this);

        data = new DataManager(this, platform, cfg.storage.serverName());
        data.topRefreshSeconds(cfg.topRefreshSeconds);
        data.onReloaded(this::applyLoaded);
        getServer().getPluginManager().registerEvents(data, this);
        getServer().getPluginManager().registerEvents(bedrock, this);
        data.start(createStorage(cfg.storage), !cfg.storage.type().equals("SQLITE"), cfg.storage.pollMs());

        sales.start();
        menus = new Menus(this, platform);
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(new SellwandListener(this), this);
        prompts = new ChatPrompts(this);
        getServer().getPluginManager().registerEvents(prompts, this);
        getServer().getPluginManager().registerEvents(this, this);
        registerChat();
        cosmetics.start();

        ShopCommand shopCommand = new ShopCommand(this);
        command("coinshop", shopCommand, "Opens the Coinshop", List.of("cs"));
        command("orbshop", shopCommand, "Opens the Shard Shop", List.of());
        command("coin", new CurrencyCommand(this, Currency.COINS), "Coins balance and pay", List.of());
        command("orb", new CurrencyCommand(this, Currency.ORBS), "Shards balance and pay", List.of());
        CommandAliases.register(this, commands.get("coin"), cfg.coinCommands);
        CommandAliases.register(this, commands.get("orb"), cfg.orbCommands);
        CommandAliases.register(this, commands.get("orbshop"), cfg.orbshopCommands);
        PerkCommands perkCommands = new PerkCommands(this);
        getServer().getPluginManager().registerEvents(perkCommands, this);
        perkCommands.register();

        startOrbTimer();
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new Placeholders(this).register();
            } catch (Throwable t) {
                getLogger().warning("Could not hook into PlaceholderAPI: " + t);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile prof = data.profile(p);
            if (prof.isLoaded()) applyLoaded(prof);
        }

        String menusInfo = Capabilities.DIALOGS ? "dialogs + chest menu" : "chest menu";
        String storageInfo = data.isReady() ? "§a" + data.storageName() + (data.isShared() ? " §7(shared, cross-server sync)" : "")
                : "§c" + data.storageName() + " (can't connect - see above)";
        Banner.print(this, menusInfo, storageInfo);
        license = DreamLicense.start(this);
    }

    @Override
    public void onDisable() {
        if (config == null) return; // never started (broken config.yml)
        if (license != null) license.stop();
        if (orbTask != null) orbTask.cancel();
        if (sales != null) sales.stop();
        if (particles != null) particles.stopAll();
        if (menus != null) menus.closeAll();
        if (cosmetics != null) {
            cosmetics.stop();
            cosmetics.clearAll();
        }
        if (data != null) data.stop();
    }

    /** The plugin's own commands, by name (a plugin.yml command, or one registered from code). */
    private final java.util.Map<String, org.bukkit.command.Command> commands = new java.util.HashMap<>();

    /**
     * Spigot reads the commands from plugin.yml. Paper, Purpur, Leaf and Folia load the plugin
     * from paper-plugin.yml (no bytecode rewriting at load time), which can't declare commands:
     * there they are registered here.
     */
    private void command(String name, TabExecutor executor, String description, List<String> aliases) {
        PluginCommand cmd = null;
        try {
            cmd = getCommand(name);
        } catch (UnsupportedOperationException paperPlugin) {
            // loaded from paper-plugin.yml
        }
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
            commands.put(name, cmd);
            return;
        }
        CodeCommand code = new CodeCommand(this, name, description, aliases, executor);
        org.bukkit.command.CommandMap map = CommandAliases.commandMap(this);
        if (map == null) {
            getLogger().severe("Could not register /" + name + " (no command map).");
            return;
        }
        map.register(getName().toLowerCase(java.util.Locale.ROOT), code);
        commands.put(name, code);
    }

    /** A command registered from code, for Paper plugins. */
    private static final class CodeCommand extends org.bukkit.command.Command implements org.bukkit.command.PluginIdentifiableCommand {
        private final Dreamcoinshop plugin;
        private final TabExecutor executor;

        CodeCommand(Dreamcoinshop plugin, String name, String description, List<String> aliases, TabExecutor executor) {
            super(name, description, "/" + name, aliases);
            this.plugin = plugin;
            this.executor = executor;
        }

        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
            if (!plugin.isEnabled()) return false;
            return executor.onCommand(sender, this, label, args);
        }

        @Override
        public List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
            List<String> out = executor.onTabComplete(sender, this, alias, args);
            return out == null ? List.of() : out;
        }

        @Override
        public org.bukkit.plugin.Plugin getPlugin() {
            return plugin;
        }
    }

    // ------------------------------------------------------------------ storage

    private Storage createStorage(ShopConfig.Storage s) {
        String type = s.type();
        String prefix = s.prefix();
        try {
            switch (type) {
                case "MYSQL", "MARIADB" -> {
                    return new SqlStorage(SqlStorage.Dialect.MYSQL, () -> openMariaDb(s), prefix);
                }
                case "POSTGRESQL", "POSTGRES", "PGSQL" -> {
                    return new SqlStorage(SqlStorage.Dialect.POSTGRESQL, () -> openPostgres(s), prefix);
                }
                case "REDIS" -> {
                    return new RedisStorage(s.host(), s.port(), s.ssl(), s.redisUsername(), s.password(), s.redisDatabase(), prefix, getLogger());
                }
                case "SQLITE" -> {
                    return sqlite(prefix);
                }
                default -> getLogger().warning("storage.type '" + type + "' is unknown - using SQLITE.");
            }
        } catch (IllegalArgumentException e) {
            getLogger().severe("storage: " + e.getMessage() + " - using SQLite with the prefix dcs_ instead.");
            return sqlite("dcs_");
        }
        return sqlite(prefix.matches("[A-Za-z0-9_]{0,24}") ? prefix : "dcs_");
    }

    private Storage sqlite(String prefix) {
        File file = new File(getDataFolder(), "data.db");
        return new SqlStorage(SqlStorage.Dialect.SQLITE, () -> {
            getDataFolder().mkdirs();
            try {
                // Every Spigot / Paper / Folia server ships the SQLite driver
                Driver driver = (Driver) Class.forName("org.sqlite.JDBC").getDeclaredConstructor().newInstance();
                java.sql.Connection c = driver.connect("jdbc:sqlite:" + file.getAbsolutePath(), new Properties());
                if (c == null) throw new SQLException("SQLite refused " + file);
                return c;
            } catch (ReflectiveOperationException e) {
                throw new SQLException("This server has no SQLite driver - use MYSQL, POSTGRESQL or REDIS in storage.type", e);
            }
        }, prefix);
    }

    /** The bundled drivers are used directly (not DriverManager), so other plugins' drivers can't interfere. */
    private static java.sql.Connection openMariaDb(ShopConfig.Storage s) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", s.username());
        props.setProperty("password", s.password());
        props.setProperty("sslMode", s.ssl() ? "trust" : "disable");
        props.setProperty("allowPublicKeyRetrieval", "true");
        props.setProperty("connectTimeout", "5000");
        props.setProperty("socketTimeout", "10000");
        // The MariaDB driver talks to MySQL and MariaDB alike
        java.sql.Connection c = new org.mariadb.jdbc.Driver().connect("jdbc:mariadb://" + s.host() + ":" + s.port() + "/" + s.database(), props);
        if (c == null) throw new SQLException("The MariaDB driver refused the connection URL");
        return c;
    }

    private static java.sql.Connection openPostgres(ShopConfig.Storage s) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", s.username());
        props.setProperty("password", s.password());
        props.setProperty("sslmode", s.ssl() ? "require" : "disable");
        props.setProperty("connectTimeout", "5");
        props.setProperty("socketTimeout", "10");
        java.sql.Connection c = new org.postgresql.Driver().connect("jdbc:postgresql://" + s.host() + ":" + s.port() + "/" + s.database(), props);
        if (c == null) throw new SQLException("The PostgreSQL driver refused the connection URL");
        return c;
    }

    // ------------------------------------------------------------------ players

    /** After a player's data loaded (or changed on another server): show their cosmetics. Any thread. */
    private void applyLoaded(Profile profile) {
        Player player = Bukkit.getPlayer(profile.uuid);
        if (player == null) return;
        cosmetics.refresh(player);
        cosmetics.applyPerks(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Profile profile = data.profile(player);
        if (profile.isLoaded()) applyLoaded(profile);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        cosmetics.clear(event.getPlayer());
        particles.stop(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------ timers

    private void startOrbTimer() {
        ShopConfig cfg = config;
        if (orbTask != null && orbInterval == cfg.orbIntervalMinutes) return;
        if (orbTask != null) orbTask.cancel();
        orbInterval = cfg.orbIntervalMinutes;
        long ticks = Math.max(1, cfg.orbIntervalMinutes) * 60L * 20L;
        orbTask = platform.timer(this::payOrbs, ticks, ticks);
    }

    private void payOrbs() {
        ShopConfig cfg = config;
        if (!cfg.orbPayout || cfg.orbAmount <= 0) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile profile = data.online(p.getUniqueId());
            if (profile == null || !profile.isLoaded()) continue;
            data.add(profile, Currency.ORBS, cfg.orbAmount);
            if (cfg.orbMessage) {
                text.send(p, "orbs.received", Ph.of("amount", Shop.format(cfg.orbAmount)));
                sounds.play(p, "orb-received");
            }
        }
    }

    private void registerChat() {
        ShopConfig cfg = config;
        if (chatPriority == cfg.chatPriority && (paperChat != null || legacyChat != null)) return;
        if (paperChat != null) paperChat.unregister();
        if (legacyChat != null) legacyChat.unregister();
        paperChat = null;
        legacyChat = null;
        chatPriority = cfg.chatPriority;
        if (Capabilities.PAPER_CHAT) paperChat = PaperChat.register(this, chatPriority);
        else legacyChat = LegacyChat.register(this, chatPriority);
    }

    // ------------------------------------------------------------------ reload

    /**
     * Re-reads config.yml, messages.yml and sounds.yml and applies them. Main/global thread.
     * False if config.yml has a mistake: then the settings in use stay as they are.
     */
    boolean reloadSettings() {
        ShopConfig old = config;
        ShopConfig cfg = new ShopConfig();
        try {
            cfg.load(this);
        } catch (ShopConfig.BrokenConfig e) {
            getLogger().severe(e.getMessage() + " - keeping the settings in use until it's fixed.");
            return false;
        }
        config = cfg;
        text.reload();
        sounds.reload();
        data.topRefreshSeconds(cfg.topRefreshSeconds);
        if (old.topSize != cfg.topSize || old.topFullSize != cfg.topFullSize) data.invalidateTops();
        registerChat();
        startOrbTimer();
        if (old.particlesIntervalTicks != cfg.particlesIntervalTicks) particles.stopAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Profile prof = data.online(p.getUniqueId());
            if (prof != null && prof.isLoaded()) applyLoaded(prof);
        }
        return true;
    }

    /** /coinshop reload: 0 = done, 1 = done but the storage settings changed (need a restart), -1 = config.yml broken. */
    int reloadAll() {
        ShopConfig.Storage before = config.storage;
        menus.closeAll();
        if (!reloadSettings()) return -1;
        return before.equals(config.storage) ? 0 : 1;
    }

    // ------------------------------------------------------------------ access

    ShopConfig config() {
        return config;
    }

    Text text() {
        return text;
    }

    Sounds sounds() {
        return sounds;
    }

    Flags flags() {
        return flags;
    }

    DataManager data() {
        return data;
    }

    Cosmetics cosmetics() {
        return cosmetics;
    }

    Shop shop() {
        return shop;
    }

    Settings settings() {
        return settings;
    }

    Sales sales() {
        return sales;
    }

    ParticleEffects particles() {
        return particles;
    }

    ChatPrompts prompts() {
        return prompts;
    }

    Menus menus() {
        return menus;
    }

    BedrockDetector bedrock() {
        return bedrock;
    }

    Platform platform() {
        return platform;
    }
}
