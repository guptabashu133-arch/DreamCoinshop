package org.mydreamduels.dreamcoinshop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Country flags. The pictures are Minecraft head textures (country-flags.properties, the same set
 * Dreamtab uses), shown as an inline head in chat and dialogs (1.21.9+) and as head items in the
 * chest menu. Bedrock can't show heads in text; it gets a resource-pack glyph or the country code.
 */
final class Flags {

    record Country(String code, String name, String textureHash, int bedrockGlyph) {

        /** The base64 "textures" profile value for this flag's head. */
        String texture() {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + textureHash + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }

        String textureUrl() {
            return "http://textures.minecraft.net/texture/" + textureHash;
        }
    }

    private final Map<String, Country> byCode;
    /** A-Z by name. */
    private final List<Country> all;

    private Flags(Map<String, Country> byCode) {
        this.byCode = Collections.unmodifiableMap(byCode);
        List<Country> list = new ArrayList<>(byCode.values());
        list.sort(Comparator.comparing(Country::name, String.CASE_INSENSITIVE_ORDER));
        this.all = List.copyOf(list);
    }

    static Flags load() {
        Properties textures = properties("/country-flags.properties");
        Properties glyphs = properties("/country-flags-bedrock.properties");
        Map<String, Country> map = new HashMap<>();
        for (String raw : textures.stringPropertyNames()) {
            String code = raw.trim().toUpperCase(Locale.ROOT);
            String hash = textures.getProperty(raw).trim();
            if (!code.matches("[A-Z]{2}") || !hash.matches("[0-9a-f]{32,64}")) continue;
            int glyph = -1;
            String g = glyphs.getProperty(code);
            if (g != null) {
                try {
                    glyph = Integer.parseInt(g.trim(), 16);
                } catch (NumberFormatException ignored) {
                }
            }
            map.put(code, new Country(code, displayName(code), hash, glyph));
        }
        return new Flags(map);
    }

    private static Properties properties(String resource) {
        Properties p = new Properties();
        try (InputStream in = Flags.class.getResourceAsStream(resource)) {
            if (in != null) p.load(in);
        } catch (IOException ignored) {
        }
        return p;
    }

    /** English country name from the JDK ("IN" -> "India"), with a few nicer short names. */
    private static String displayName(String code) {
        return switch (code) {
            case "US" -> "United States";
            case "GB" -> "United Kingdom";
            case "KR" -> "South Korea";
            case "KP" -> "North Korea";
            case "XK" -> "Kosovo";
            default -> {
                String name = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
                yield name == null || name.isBlank() || name.equals(code) ? code : name;
            }
        };
    }

    Country get(String code) {
        return code == null ? null : byCode.get(code.toUpperCase(Locale.ROOT));
    }

    int size() {
        return byCode.size();
    }

    private ShopConfig cachedFor;
    private List<Country> cachedSale = List.of();

    /** The flags for sale: featured ones first, then A-Z. Cached per config load. */
    synchronized List<Country> forSale(ShopConfig cfg) {
        if (cfg != cachedFor) {
            cachedSale = buildForSale(cfg);
            cachedFor = cfg;
        }
        return cachedSale;
    }

    private List<Country> buildForSale(ShopConfig cfg) {
        Set<Country> out = new LinkedHashSet<>();
        for (String code : cfg.flagsFeatured) {
            Country c = get(code);
            if (c != null && sold(cfg, c.code())) out.add(c);
        }
        for (Country c : all) if (sold(cfg, c.code())) out.add(c);
        return List.copyOf(out);
    }

    static boolean sold(ShopConfig cfg, String code) {
        return cfg.flagsSellAll || cfg.flagsSold.contains(code);
    }

    /** Flags whose name or code matches the search, in the same order as {@link #forSale}. */
    List<Country> search(ShopConfig cfg, String query) {
        List<Country> list = forSale(cfg);
        if (query == null || query.isBlank()) return list;
        String q = normalize(query);
        List<Country> out = new ArrayList<>();
        for (Country c : list) {
            if (c.code().equalsIgnoreCase(query.trim()) || normalize(c.name()).contains(q)) out.add(c);
        }
        return out;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** The next count flags the Flags button will show after the current one. */
    List<Country> upcoming(ShopConfig cfg, long nowMillis, int count) {
        List<Country> list = forSale(cfg);
        List<Country> out = new ArrayList<>();
        long step = Math.max(1, cfg.flagsRotateSeconds) * 1000L;
        for (int i = 1; i <= count && i < list.size(); i++) out.add(rotating(cfg, nowMillis + i * step));
        return out;
    }

    /** The flag shown right now on the main menu's Flags button: changes every rotate.seconds. */
    Country rotating(ShopConfig cfg, long nowMillis) {
        List<Country> list = forSale(cfg);
        if (list.isEmpty()) return null;
        long step = Math.max(1, cfg.flagsRotateSeconds) * 1000L;
        int index = (int) Math.floorMod(nowMillis / step, (long) list.size());
        return list.get(index);
    }
}
