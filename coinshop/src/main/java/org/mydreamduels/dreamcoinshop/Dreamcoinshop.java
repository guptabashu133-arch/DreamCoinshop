package org.mydreamduels.dreamcoinshop;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.mydreamduels.dreamcoinshop.commands.CoinCommand;
import org.mydreamduels.dreamcoinshop.commands.CoinShopCommand;
import org.mydreamduels.dreamcoinshop.commands.OrbCommand;
import org.mydreamduels.dreamcoinshop.commands.OrbShopCommand;
import org.mydreamduels.dreamcoinshop.config.MessagesConfig;
import org.mydreamduels.dreamcoinshop.config.SoundConfig;
import org.mydreamduels.dreamcoinshop.data.PlayerDataManager;
import org.mydreamduels.dreamcoinshop.data.PlayerProfile;
import org.mydreamduels.dreamcoinshop.economy.CoinsManager;
import org.mydreamduels.dreamcoinshop.economy.OrbsManager;
import org.mydreamduels.dreamcoinshop.gui.CoinShopGUI;
import org.mydreamduels.dreamcoinshop.listeners.ChatFormatListener;
import org.mydreamduels.dreamcoinshop.listeners.PlayerJoinListener;
import org.mydreamduels.dreamcoinshop.listeners.SellwandListener;
import org.mydreamduels.dreamcoinshop.placeholder.DreamcoinshopExpansion;
import org.mydreamduels.dreamcoinshop.shopdata.ShopConfig;
import org.mydreamduels.dreamcoinshop.shopdata.ShopOptions;

public final class Dreamcoinshop extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private ShopConfig shopConfig;
    private CoinsManager coinsManager;
    private OrbsManager orbsManager;
    private PlayerDataManager playerDataManager;
    private CoinShopGUI coinShopGUI;
    private MessagesConfig messages;
    private SoundConfig sounds;
    private final Map<UUID, PermissionAttachment> perkAttachments = new ConcurrentHashMap<>();
    private static final List<ChatColor> RAINBOW_CYCLE = List.of(ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW, ChatColor.GREEN, ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE);
    private final AtomicInteger rainbowIndex = new AtomicInteger(0);
    private static final Map<String, ChatColor> GLOW_COLOR_MAP = Map.of("red", ChatColor.RED, "gold", ChatColor.GOLD, "green", ChatColor.GREEN, "aqua", ChatColor.AQUA, "blue", ChatColor.BLUE, "purple", ChatColor.DARK_PURPLE, "white", ChatColor.WHITE);
    private BukkitTask orbTask;
    private BukkitTask glowSafetyTask;

    public void onEnable() {
        this.loadEverything();
        this.getServer().getPluginManager().registerEvents((Listener) new PlayerJoinListener(this), (Plugin) this);
        this.getServer().getPluginManager().registerEvents((Listener) new ChatFormatListener(this), (Plugin) this);
        this.getServer().getPluginManager().registerEvents((Listener) new SellwandListener(this), (Plugin) this);
        CoinCommand coinCommand = new CoinCommand(this);
        this.getCommand("coin").setExecutor((CommandExecutor) coinCommand);
        this.getCommand("coin").setTabCompleter((TabCompleter) coinCommand);
        this.getCommand("coinshop").setExecutor((CommandExecutor) new CoinShopCommand(this));
        OrbCommand orbCommand = new OrbCommand(this);
        this.getCommand("orb").setExecutor((CommandExecutor) orbCommand);
        this.getCommand("orb").setTabCompleter((TabCompleter) orbCommand);
        this.getCommand("orbshop").setExecutor((CommandExecutor) new OrbShopCommand(this));
        this.getServer().getScheduler().runTaskTimer((Plugin) this, this::tickRainbowGlow, 20L, 20L);
        this.glowSafetyTask = this.getServer().getScheduler().runTaskTimer((Plugin) this, this::reapplyGlowForOnlinePlayers, 100L, 100L);
        this.startOrbTimer();
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DreamcoinshopExpansion(this).register();
            this.getLogger().info("Hooked into PlaceholderAPI - %dreamcoinshop_coins% / %dreamcoinshop_orbs% / %dreamcoinshop_gradient_name% are available.");
        }
        this.getLogger().info("Dreamcoinshop enabled - coinshop dialog GUI ready.");
    }

    public void onDisable() {
        if (this.playerDataManager != null) {
            this.playerDataManager.saveAll();
        }
        // MUST happen: balance changes now sit in memory between flushes, so without this a
        // normal restart would drop everything since the last timer tick. shutdown() cancels
        // the async task and then writes synchronously, so the file is complete before the
        // server finishes stopping.
        if (this.coinsManager != null) {
            this.coinsManager.shutdown();
        }
        if (this.orbsManager != null) {
            this.orbsManager.shutdown();
        }
    }

    private void loadEverything() {
        this.shopConfig = new ShopConfig();
        this.shopConfig.load(this);
        this.messages = new MessagesConfig(this);
        this.messages.load();
        this.sounds = new SoundConfig(this);
        this.sounds.load(this.shopConfig.isSoundsEnabled());
        if (this.coinsManager == null) {
            this.coinsManager = new CoinsManager(this);
        }
        if (this.orbsManager == null) {
            this.orbsManager = new OrbsManager(this);
        }
        if (this.playerDataManager == null) {
            this.playerDataManager = new PlayerDataManager(this);
        }
        // Balances are written on a timer off the main thread now, not on every change.
        // Restarted here (not only on first load) so /dcs reload picks up a changed interval.
        long saveInterval = this.shopConfig.getBalanceSaveIntervalSeconds();
        this.coinsManager.startAutoSave(saveInterval);
        this.orbsManager.startAutoSave(saveInterval);
        this.coinShopGUI = new CoinShopGUI(this);
    }

    public void reloadEverything() {
        this.loadEverything();
        this.startOrbTimer();
        for (Player p : this.getServer().getOnlinePlayers()) {
            this.applyPerkPermissions(p);
            this.refreshCosmetics(p);
        }
    }

    private void startOrbTimer() {
        if (this.orbTask != null) {
            this.orbTask.cancel();
        }
        long intervalTicks = (long) Math.max(1, this.shopConfig.getOrbIntervalMinutes()) * 60L * 20L;
        this.orbTask = this.getServer().getScheduler().runTaskTimer((Plugin) this, this::giveOrbsToOnlinePlayers, intervalTicks, intervalTicks);
    }

    private void giveOrbsToOnlinePlayers() {
        double amount = this.shopConfig.getOrbAmountPerInterval();
        for (Player p : this.getServer().getOnlinePlayers()) {
            this.orbsManager.add(p.getUniqueId(), amount);
            p.sendMessage(this.messages.get("orbs.received", MessagesConfig.ph("amount", (long) amount)));
            this.sounds.play(p, "orb-received");
        }
    }

    private void tickRainbowGlow() {
        int idx = this.rainbowIndex.updateAndGet(i -> (i + 1) % RAINBOW_CYCLE.size());
        ChatColor color = RAINBOW_CYCLE.get(idx);
        for (Player player : this.getServer().getOnlinePlayers()) {
            PlayerProfile profile = this.playerDataManager.get(player.getUniqueId());
            ShopOptions.GlowOption opt = this.shopConfig.getGlows().get(profile.getActiveGlow());
            if (opt == null || !opt.rainbow()) continue;
            Scoreboard board = player.getScoreboard();
            Team team = board.getTeam("dcs_glow_rainbow");
            if (team == null) {
                team = board.registerNewTeam("dcs_glow_rainbow");
            }
            team.setColor(color);
            if (team.hasEntry(player.getName())) continue;
            team.addEntry(player.getName());
        }
    }

    private void reapplyGlowForOnlinePlayers() {
        for (Player player : this.getServer().getOnlinePlayers()) {
            PlayerProfile profile = this.playerDataManager.get(player.getUniqueId());
            if (profile.getActiveGlow() == null) continue;
            // Only repair what is actually missing: re-adding the effect and re-joining the team
            // every 5 s broadcast effect, metadata and team packets for every glowing player.
            if (this.glowIntact(player, profile)) continue;
            this.refreshGlow(player, profile);
        }
    }

    public ChatColor nextRainbowColor() {
        return RAINBOW_CYCLE.get(this.rainbowIndex.get());
    }

    public void applyPerkPermissions(Player player) {
        PlayerProfile profile = this.playerDataManager.get(player.getUniqueId());
        PermissionAttachment attachment = this.perkAttachments.computeIfAbsent(player.getUniqueId(), uuid -> player.addAttachment((Plugin) this));
        this.shopConfig.getPerks().values().forEach(perk -> {
            boolean owned = profile.getUnlockedPerks().contains(perk.key());
            attachment.setPermission(perk.permission(), owned);
        });
    }

    public void clearPerkAttachment(Player player) {
        PermissionAttachment attachment = this.perkAttachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void refreshCosmetics(Player player) {
        PlayerProfile profile = this.playerDataManager.get(player.getUniqueId());
        if (profile.getActiveGradient() != null) {
            Component name = this.getGradientNameComponent(player);
            player.displayName(name);
            player.playerListName(name);
        } else {
            player.displayName(Component.text(player.getName()));
            player.playerListName(Component.text(player.getName()));
        }
        this.refreshGlow(player, profile);
    }

    /**
     * NEW: builds the player's cosmetic name straight from player.getName()
     * (the real Mojang username) plus their currently active gradient -
     * never from player.displayName(). displayName() is a shared, mutable
     * field that any other plugin can silently overwrite (EssentialsX does
     * this for /nick nicknames, and paints it red for ops via
     * ops-name-color). This is now the single source of truth for chat
     * (ChatFormatListener) and, via the dreamcoinshop_gradient_name
     * PlaceholderAPI placeholder, for Dreamtab's tab list too - both bypass
     * displayName() entirely.
     */
    public Component getGradientNameComponent(Player player) {
        PlayerProfile profile = this.playerDataManager.get(player.getUniqueId());
        if (profile.getActiveGradient() != null) {
            ShopOptions.GradientOption opt = this.shopConfig.getNameGradients().get(profile.getActiveGradient());
            if (opt != null) {
                String tag = "<gradient:" + opt.from() + ":" + opt.to() + ">" + player.getName() + "</gradient>";
                return MM.deserialize(tag);
            }
        }
        return Component.text(player.getName());
    }

    /** Same as above but as a raw MiniMessage tag string, for PlaceholderAPI. */
    public String getGradientNameTag(Player player) {
        PlayerProfile profile = this.playerDataManager.get(player.getUniqueId());
        if (profile.getActiveGradient() != null) {
            ShopOptions.GradientOption opt = this.shopConfig.getNameGradients().get(profile.getActiveGradient());
            if (opt != null) {
                return "<gradient:" + opt.from() + ":" + opt.to() + ">" + player.getName() + "</gradient>";
            }
        }
        return player.getName();
    }

    private boolean glowIntact(Player player, PlayerProfile profile) {
        ShopOptions.GlowOption opt = this.shopConfig.getGlows().get(profile.getActiveGlow());
        if (opt == null || !player.hasPotionEffect(PotionEffectType.GLOWING)) return false;
        Team team = player.getScoreboard().getTeam("dcs_glow_" + (opt.rainbow() ? "rainbow" : opt.key()));
        return team != null && team.hasEntry(player.getName());
    }

    private void refreshGlow(Player player, PlayerProfile profile) {
        Scoreboard board = player.getScoreboard();
        for (Team team : board.getTeams()) {
            if (!team.getName().startsWith("dcs_glow_")) continue;
            team.removeEntry(player.getName());
        }
        if (profile.getActiveGlow() == null) {
            player.removePotionEffect(PotionEffectType.GLOWING);
            return;
        }
        ShopOptions.GlowOption opt = this.shopConfig.getGlows().get(profile.getActiveGlow());
        if (opt == null) {
            player.removePotionEffect(PotionEffectType.GLOWING);
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false, false));
        ChatColor color = opt.rainbow() ? this.nextRainbowColor() : GLOW_COLOR_MAP.getOrDefault(opt.key(), ChatColor.WHITE);
        String teamName = "dcs_glow_" + (opt.rainbow() ? "rainbow" : opt.key());
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
        }
        team.setColor(color);
        team.addEntry(player.getName());
    }

    public ShopConfig getShopConfig() {
        return this.shopConfig;
    }

    public CoinsManager getCoinsManager() {
        return this.coinsManager;
    }

    public OrbsManager getOrbsManager() {
        return this.orbsManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }

    public CoinShopGUI getCoinShopGUI() {
        return this.coinShopGUI;
    }

    public MessagesConfig getMessages() {
        return this.messages;
    }

    public SoundConfig getSounds() {
        return this.sounds;
    }
}
