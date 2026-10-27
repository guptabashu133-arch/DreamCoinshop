package org.mydreamduels.dreamcoinshop;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Which texture shows an item in a dialog. Dialog icons are sprites (texture files), and many
 * items have no texture of their own name (crafting_table -> block/crafting_table_front,
 * nether_wart -> item/nether_wart, clock -> item/clock_00). A wrong name draws the purple and
 * black "missing" square, so the name is picked from the list of real textures (textures.txt,
 * from Minecraft 1.21.11) instead of guessed.
 */
final class Sprites {

    private static final String[] BLOCK_SUFFIXES = {"_front", "_top", "_side", "_on", "_stage2", "_bottom"};
    private static final Set<String> TEXTURES = load();

    private Sprites() {
    }

    private static Set<String> load() {
        Set<String> out = new HashSet<>();
        try (InputStream in = Sprites.class.getResourceAsStream("/textures.txt")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) if (!line.isBlank()) out.add(line.trim());
        } catch (Exception ignored) {
        }
        return out;
    }

    /** "item/nether_wart", "block/crafting_table_front" ... or null if the item has no flat picture. */
    static String path(String material) {
        String n = material.toLowerCase(java.util.Locale.ROOT);
        if (TEXTURES.isEmpty()) return null;
        if (n.equals("enchanted_golden_apple")) n = "golden_apple"; // the same picture with a shine
        if (TEXTURES.contains("item/" + n)) return "item/" + n;
        if (TEXTURES.contains("block/" + n)) return "block/" + n;
        for (String s : BLOCK_SUFFIXES) if (TEXTURES.contains("block/" + n + s)) return "block/" + n + s;
        if (TEXTURES.contains("item/" + n + "_00")) return "item/" + n + "_00"; // clock, compass
        if (n.endsWith("_pane") && TEXTURES.contains("block/" + n.substring(0, n.length() - 5))) return "block/" + n.substring(0, n.length() - 5);
        if (n.startsWith("waxed_") && TEXTURES.contains("block/" + n.substring(6))) return "block/" + n.substring(6);
        return null;
    }

    static int count() {
        return TEXTURES.size();
    }
}
