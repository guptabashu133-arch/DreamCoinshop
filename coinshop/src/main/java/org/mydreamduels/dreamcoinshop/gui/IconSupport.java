package org.mydreamduels.dreamcoinshop.gui;

import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * Puts a real item/block texture on a Dialog button (Minecraft's "object component" sprite
 * feature - client-side since 1.21.9, Adventure API since 4.25.0). No resource pack needed.
 * <p>
 * Unlike a general-purpose item picker (which has to handle every Material in the game), every
 * icon used by this plugin is a hand-picked, ordinary item/block - a crafting table, a dye, a
 * gold ingot, etc. That means a plain lowercase-name lookup is correct for almost everything;
 * ICON_ALIASES below only covers the handful of blocks whose real texture file isn't just
 * "<name>.png" (e.g. a crafting table's top face is "crafting_table_top", not "crafting_table").
 */
public final class IconSupport {

    private IconSupport() {}

    /**
     * Bedrock/PE (Geyser) fix: Bedrock clients can't render sprite "object components" at all.
     * Geyser logs "Expected tag to be a literal string..." for every button that has one and the
     * button then arrives blank - or the whole menu never opens. So before a dialog is built we
     * remember who it is being built for, and hand out NO icons at all to Bedrock players; they
     * get the plain text label (which Geyser turns into a normal Bedrock form button) instead.
     * Java players are unaffected and still get the icons.
     */
    private static final ThreadLocal<Boolean> VIEWER_IS_BEDROCK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Call this at the top of every method that builds a dialog, before any icon() call. */
    public static void setViewer(Player player) {
        VIEWER_IS_BEDROCK.set(isBedrock(player));
    }

    /**
     * Floodgate gives Bedrock players a UUID whose first 8 bytes are zero (00000000-0000-0000-xxxx-xxxxxxxxxxxx),
     * so this needs no Floodgate/Geyser dependency at all.
     */
    public static boolean isBedrock(Player player) {
        return player != null && player.getUniqueId().getMostSignificantBits() == 0L;
    }

    private static final Key BLOCKS_ATLAS = Key.key("minecraft", "blocks");
    private static final Key ITEMS_ATLAS = Key.key("minecraft", "items");
    private static Boolean capable;
    private static Boolean itemsHaveOwnAtlas;

    private static final Map<Material, String> ICON_ALIASES = Map.of(
            Material.CRAFTING_TABLE, "crafting_table_top",
            Material.SMITHING_TABLE, "smithing_table_front",
            Material.GRINDSTONE, "grindstone_side",
            Material.ANVIL, "anvil_top",
            Material.SPAWNER, "spawner"
    );

    public static boolean isEnabled() {
        if (capable == null) {
            try {
                Class.forName("net.kyori.adventure.text.object.ObjectContents");
                Class.forName("net.kyori.adventure.text.ObjectComponent");
                capable = versionAtLeast(1, 21, 9);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                capable = false;
            }
        }
        return capable;
    }

    private static boolean itemsHaveOwnAtlas() {
        if (itemsHaveOwnAtlas == null) itemsHaveOwnAtlas = versionAtLeast(1, 21, 11);
        return itemsHaveOwnAtlas;
    }

    private static boolean versionAtLeast(int major, int minor, int patch) {
        try {
            String id = ServerBuildInfo.buildInfo().minecraftVersionId();
            String[] parts = id.split("[.\\-]");
            int a = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int b = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int c = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            if (a != major) return a > major;
            if (b != minor) return b > minor;
            return c >= patch;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Sprite Component for this material, or null if unsupported - callers must fall back to
     *  plain text (no icon) when this returns null. */
    public static Component icon(Material material) {
        if (!isEnabled() || material == null) return null;
        if (VIEWER_IS_BEDROCK.get()) return bedrockGlyph(material); // TaraashMC-Pack item glyph (null = text only)
        try {
            String textureName = ICON_ALIASES.getOrDefault(material, material.name().toLowerCase(Locale.ROOT));
            boolean isBlock = material.isBlock();
            Key atlas = (isBlock || !itemsHaveOwnAtlas()) ? BLOCKS_ATLAS : ITEMS_ATLAS;
            String folder = isBlock ? "block" : "item";
            Key sprite = Key.key("minecraft", folder + "/" + textureName);
            return Component.object(b -> b.contents(ObjectContents.sprite(atlas, sprite)));
        } catch (Throwable t) {
            return null;
        }
    }

    private static Map<String, String> itemGlyphs;

    /** Full-size item glyph from the TaraashMC-Pack item pages (EC-F2); null if the pack has none. */
    public static Component bedrockGlyph(Material material) {
        if (material == null) return null;
        if (itemGlyphs == null) {
            Map<String, String> map = new java.util.HashMap<>();
            try (java.io.InputStream in = IconSupport.class.getClassLoader().getResourceAsStream("item-glyphs.properties")) {
                java.util.Properties p = new java.util.Properties();
                if (in != null) p.load(in);
                for (String k : p.stringPropertyNames()) {
                    map.put(k, new String(Character.toChars(Integer.parseInt(p.getProperty(k).trim(), 16))));
                }
            } catch (Exception ignored) {
            }
            itemGlyphs = map;
        }
        String g = itemGlyphs.get(material.name());
        return g == null ? null : Component.text(g, net.kyori.adventure.text.format.NamedTextColor.WHITE);
    }

    /**
     * Small (action-bar size) Bedrock icon from the TaraashMC-Pack shared page EB (U+EB80..).
     * Bedrock can't draw sprites, but it draws these font glyphs; white so the text colour
     * doesn't tint them. null when the pack has no small icon for that item.
     */
    public static Component bedrockSmallGlyph(Material material) {
        Integer cp = material == null ? null : BEDROCK_SMALL.get(material);
        return cp == null ? null : Component.text(new String(Character.toChars(cp)), net.kyori.adventure.text.format.NamedTextColor.WHITE);
    }

    private static final Map<Material, Integer> BEDROCK_SMALL = Map.ofEntries(
            Map.entry(Material.GOLD_NUGGET, 0xEB80), Map.entry(Material.GOLD_INGOT, 0xEB81),
            Map.entry(Material.EXPERIENCE_BOTTLE, 0xEB82), Map.entry(Material.ENDER_PEARL, 0xEB83),
            Map.entry(Material.AMETHYST_SHARD, 0xEB84), Map.entry(Material.NETHER_STAR, 0xEB85),
            Map.entry(Material.GOLDEN_HELMET, 0xEB86), Map.entry(Material.CLOCK, 0xEB8C),
            Map.entry(Material.DIAMOND, 0xEBA4), Map.entry(Material.EMERALD, 0xEBA5),
            Map.entry(Material.ENDER_EYE, 0xEBB5), Map.entry(Material.TOTEM_OF_UNDYING, 0xEB9B));

    /** Builds "[icon] label" - prepends the icon (with a trailing space) before the given label
     *  Component, or just returns the label unchanged if icons aren't supported right now. */
    public static Component withIcon(Material material, Component label) {
        Component icon = icon(material);
        if (icon == null) return label;
        return icon.append(Component.text(" ")).append(label);
    }
}