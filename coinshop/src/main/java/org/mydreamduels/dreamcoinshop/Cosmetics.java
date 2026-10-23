package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What players see of their purchases: the name gradient (chat, tab list, display name), glow,
 * perk permissions, and the tag / flag / chat colour pieces that chat and PlaceholderAPI use.
 */
final class Cosmetics {

    private static final List<ChatColor> RAINBOW = List.of(ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW,
            ChatColor.GREEN, ChatColor.AQUA, ChatColor.BLUE, ChatColor.LIGHT_PURPLE);
    private static final Map<String, ChatColor> GLOW_KEYS = Map.of("red", ChatColor.RED, "gold", ChatColor.GOLD,
            "green", ChatColor.GREEN, "aqua", ChatColor.AQUA, "blue", ChatColor.BLUE, "purple", ChatColor.DARK_PURPLE,
            "white", ChatColor.WHITE);
    private static final String TEAM_PREFIX = "dcs_glow_";

    private final Dreamcoinshop plugin;
    private final Platform platform;
    private final Map<UUID, PermissionAttachment> perks = new ConcurrentHashMap<>();
    /** Players whose names we changed, so we only reset names we set (never another plugin's nickname). */
    private final java.util.Set<UUID> named = ConcurrentHashMap.newKeySet();
    private volatile int rainbowIndex;
    private Platform.Task rainbowTask;
    private Platform.Task glowTask;
    private boolean warnedFolia;

    Cosmetics(Dreamcoinshop plugin, Platform platform) {
        this.plugin = plugin;
        this.platform = platform;
    }

    void start() {
        stop();
        if (Capabilities.SCOREBOARDS) {
            rainbowTask = platform.timer(this::tickRainbow, 20, 20);
            // Another plugin (a tab/sidebar plugin) may give players a new scoreboard: put the glow back
            glowTask = platform.timer(() -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Profile prof = plugin.data().online(p.getUniqueId());
                    if (prof != null && prof.active(Category.GLOW) != null) applyGlow(p, prof);
                }
            }, 100, 100);
        }
    }

    void stop() {
        if (rainbowTask != null) rainbowTask.cancel();
        if (glowTask != null) glowTask.cancel();
        rainbowTask = null;
        glowTask = null;
    }

    // ------------------------------------------------------------------ names

    /** The player's real name in their gradient, as MiniMessage. Never their nickname. */
    String nameMini(Player player, Profile profile) {
        String name = Text.escape(player.getName());
        ShopConfig.Item g = active(profile, Category.GRADIENT, "gradients");
        if (g == null || !g.isGradient()) return name;
        return "<gradient:" + g.gradient().get(0) + ":" + g.gradient().get(1) + ">" + name + "</gradient>";
    }

    /** Equipped item of a category, or null if none / the section is off / it no longer exists. */
    ShopConfig.Item active(Profile profile, Category category, String section) {
        if (profile == null) return null;
        ShopConfig cfg = plugin.config();
        if (!cfg.section(section)) return null;
        return cfg.item(category, profile.active(category));
    }

    /** The equipped tag as MiniMessage, or null. */
    String tagMini(Profile profile) {
        ShopConfig.Item tag = active(profile, Category.TAG, "tags");
        return tag == null ? null : tag.text();
    }

    /** The equipped flag, or null. */
    Flags.Country flag(Profile profile) {
        if (profile == null || !plugin.config().section("flags")) return null;
        String code = profile.active(Category.FLAG);
        Flags.Country c = plugin.flags().get(code);
        return c != null && Flags.sold(plugin.config(), c.code()) ? c : null;
    }

    /** "[IN]" style text of a flag. */
    String flagTextMini(Flags.Country c) {
        return plugin.config().flagTextFormat.replace("<code>", c.code()).replace("<country>", Text.escape(c.name()));
    }

    /** The message in the player's chat colour, as MiniMessage (message already escaped). */
    String colorMessage(Profile profile, String escapedMessage) {
        ShopConfig.Item c = active(profile, Category.CHATCOLOR, "chatcolors");
        if (c == null) return escapedMessage;
        if (c.isGradient()) return "<gradient:" + String.join(":", c.gradient()) + ">" + escapedMessage + "</gradient>";
        if (c.color() != null) return "<color:" + c.color() + ">" + escapedMessage + "</color>";
        return escapedMessage;
    }

    /** Applies the name gradient and glow. Any thread. */
    void refresh(Player player) {
        Profile profile = plugin.data().online(player.getUniqueId());
        if (profile == null) return;
        ShopConfig cfg = plugin.config();
        boolean hasGradient = active(profile, Category.GRADIENT, "gradients") != null;
        if (hasGradient && (cfg.namesDisplay || cfg.namesTabList)) {
            named.add(player.getUniqueId());
            plugin.text().setNames(player, nameMini(player, profile), cfg.namesDisplay, cfg.namesTabList);
            // A place that's switched off goes back to normal
            if (!cfg.namesDisplay || !cfg.namesTabList) plugin.text().setNames(player, null, !cfg.namesDisplay, !cfg.namesTabList);
        } else if (named.remove(player.getUniqueId())) {
            plugin.text().setNames(player, null, true, true);
        }
        platform.forPlayer(player, () -> {
            if (player.isOnline()) applyGlow(player, profile);
        });
        plugin.particles().refresh(player);
    }

    // ------------------------------------------------------------------ glow

    /** Player's thread (main thread off Folia). */
    private void applyGlow(Player player, Profile profile) {
        ShopConfig.Item glow = active(profile, Category.GLOW, "glows");
        if (Capabilities.SCOREBOARDS) {
            Scoreboard board = player.getScoreboard();
            String wanted = glow == null ? null : TEAM_PREFIX + (glow.rainbow() ? "rainbow" : glow.key());
            for (Team team : board.getTeams()) {
                if (team.getName().startsWith(TEAM_PREFIX) && !team.getName().equals(wanted) && team.hasEntry(player.getName())) {
                    team.removeEntry(player.getName());
                }
            }
            if (glow != null) {
                Team team = board.getTeam(wanted);
                if (team == null) team = board.registerNewTeam(wanted);
                ChatColor color = glow.rainbow() ? RAINBOW.get(rainbowIndex) : glowColor(glow);
                if (team.getColor() != color) team.setColor(color);
                if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
            }
        } else if (glow != null && !warnedFolia) {
            warnedFolia = true;
            plugin.getLogger().info("Folia has no scoreboard teams, so glows are white here.");
        }
        if (glow == null) {
            PotionEffect current = player.getPotionEffect(PotionEffectType.GLOWING);
            // Only remove our own endless glow (no particles, no icon), not a spectral arrow's
            if (current != null && !current.hasParticles() && !current.hasIcon()
                    && (current.getDuration() > 20 * 60 * 60 || current.getDuration() < 0)) {
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
        } else if (!player.hasPotionEffect(PotionEffectType.GLOWING)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false, false));
        }
    }

    private static ChatColor glowColor(ShopConfig.Item glow) {
        ChatColor byKey = GLOW_KEYS.get(glow.key().toLowerCase(Locale.ROOT));
        if (byKey != null) return byKey;
        return nearest(glow.color());
    }

    /** The legacy colour closest to a hex colour (team colours can only be one of the 16). */
    static ChatColor nearest(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) return ChatColor.WHITE;
        int rgb = Integer.parseInt(hex.substring(1), 16);
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        int[][] palette = {{0, 0, 0}, {0, 0, 170}, {0, 170, 0}, {0, 170, 170}, {170, 0, 0}, {170, 0, 170}, {255, 170, 0},
                {170, 170, 170}, {85, 85, 85}, {85, 85, 255}, {85, 255, 85}, {85, 255, 255}, {255, 85, 85},
                {255, 85, 255}, {255, 255, 85}, {255, 255, 255}};
        ChatColor[] colors = {ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
                ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY, ChatColor.DARK_GRAY,
                ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA, ChatColor.RED, ChatColor.LIGHT_PURPLE,
                ChatColor.YELLOW, ChatColor.WHITE};
        int best = 15;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < palette.length; i++) {
            long d = (long) (r - palette[i][0]) * (r - palette[i][0]) + (long) (g - palette[i][1]) * (g - palette[i][1])
                    + (long) (b - palette[i][2]) * (b - palette[i][2]);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return colors[best];
    }

    private void tickRainbow() {
        rainbowIndex = (rainbowIndex + 1) % RAINBOW.size();
        ChatColor color = RAINBOW.get(rainbowIndex);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Profile profile = plugin.data().online(player.getUniqueId());
            ShopConfig.Item glow = active(profile, Category.GLOW, "glows");
            if (glow == null || !glow.rainbow()) continue;
            Team team = player.getScoreboard().getTeam(TEAM_PREFIX + "rainbow");
            if (team == null) {
                applyGlow(player, profile);
            } else {
                team.setColor(color);
                if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
            }
        }
    }

    // ------------------------------------------------------------------ perks

    /** Gives exactly the perk permissions the player owns. Any thread. */
    void applyPerks(Player player) {
        Profile profile = plugin.data().online(player.getUniqueId());
        if (profile == null) return;
        platform.forPlayer(player, () -> {
            if (!player.isOnline()) return;
            PermissionAttachment attachment = perks.computeIfAbsent(player.getUniqueId(), u -> player.addAttachment(plugin));
            boolean on = plugin.config().section("perks");
            for (String perm : attachment.getPermissions().keySet().toArray(new String[0])) attachment.unsetPermission(perm);
            for (ShopConfig.Item perk : plugin.config().items(Category.PERK).values()) {
                if (on && profile.owns(Category.PERK, perk.key())) attachment.setPermission(perk.permission(), true);
            }
        });
    }

    void clear(Player player) {
        named.remove(player.getUniqueId());
        PermissionAttachment attachment = perks.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (Capabilities.SCOREBOARDS) {
            for (Team team : player.getScoreboard().getTeams()) {
                if (team.getName().startsWith(TEAM_PREFIX) && team.hasEntry(player.getName())) team.removeEntry(player.getName());
            }
        }
    }

    /** On disable: take back every perk permission. */
    void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                clear(p);
            } catch (Throwable ignored) {
            }
        }
        perks.clear();
    }
}
