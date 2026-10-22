package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

/**
 * What this server can do, detected once at startup. Everything platform- or version-specific is
 * gated on these flags, so the same jar runs on Spigot, Paper, Purpur and Folia from 1.20 to 26.x.
 * Classes are looked up in the server's class loader, so nothing this plugin ships can fake them.
 */
final class Capabilities {

    static final boolean FOLIA = hasClass("io.papermc.paper.threadedregions.RegionizedServer");
    /** Paper's built-in Adventure text. Plain Spigot has none and gets legacy colour codes instead. */
    static final boolean ADVENTURE = nativeAdventure();
    /** Paper's chat event with per-viewer rendering. */
    static final boolean PAPER_CHAT = ADVENTURE && hasClass("io.papermc.paper.event.player.AsyncChatEvent");
    /** Dialog menus: Paper 1.21.6+. */
    static final boolean DIALOGS = ADVENTURE && hasClass("io.papermc.paper.dialog.Dialog")
            && hasClass("io.papermc.paper.event.player.PlayerCustomClickEvent");
    /** Sprite and player-head icons inside text: Paper 1.21.9+. */
    static final boolean OBJECTS = ADVENTURE && hasClass("net.kyori.adventure.text.object.PlayerHeadObjectContents");
    /** Coloured text shadows: Paper 1.21.4+. */
    static final boolean SHADOWS = ADVENTURE && hasClass("net.kyori.adventure.text.format.ShadowColor");
    /** Folia has no scoreboard API, so coloured glow (which needs teams) is off there. */
    static final boolean SCOREBOARDS = !FOLIA;
    /** Enchantment shine without an enchantment: 1.20.5+. */
    static final boolean GLINT = hasMethod(ItemMeta.class, "setEnchantmentGlintOverride", Boolean.class);
    /** Custom head textures through the Bukkit profile API: 1.18.1+. */
    static final boolean PROFILES = hasMethod(Bukkit.class, "createPlayerProfile", UUID.class, String.class);

    private Capabilities() {
    }

    /** "1.21.11" from "1.21.11-R0.1-SNAPSHOT" (works on Spigot too). */
    static String minecraftVersion() {
        return Bukkit.getBukkitVersion().split("-")[0];
    }

    /** minecraftVersion() >= the given version; missing parts count as 0. Handles "26.3" too. */
    static boolean isAtLeast(int... wanted) {
        String[] parts = minecraftVersion().split("\\.");
        for (int i = 0; i < wanted.length; i++) {
            int have = 0;
            if (i < parts.length) {
                String digits = parts[i].replaceAll("\\D.*", "");
                have = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            }
            if (have != wanted[i]) return have > wanted[i];
        }
        return true;
    }

    static String describe() {
        String platform = FOLIA ? "Folia" : ADVENTURE ? "Paper" : "Spigot";
        return platform + " " + minecraftVersion();
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name, false, Bukkit.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            owner.getMethod(name, params);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean nativeAdventure() {
        try {
            Class<?> audience = Class.forName("net.kyori.adventure.audience.Audience", false, Bukkit.class.getClassLoader());
            return audience.isAssignableFrom(Player.class);
        } catch (Throwable t) {
            return false;
        }
    }
}
