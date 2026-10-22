package org.mydreamduels.dreamcoinshop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders MiniMessage as legacy "§" colour codes, for plain Spigot (no Adventure), Spigot chat and
 * PlaceholderAPI. Supports the tags this plugin's files use: &lt;#hex&gt;, named colours, &lt;b&gt; &lt;i&gt;
 * &lt;u&gt; &lt;st&gt; &lt;obf&gt;, &lt;gradient:..&gt;, &lt;rainbow&gt;, &lt;reset&gt;, &lt;newline&gt; and placeholders.
 * &lt;shadow:..&gt;, &lt;hover&gt; and &lt;click&gt; are accepted and ignored (legacy text can't show them).
 */
final class MiniLegacy {

    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("black", "#000000"), Map.entry("dark_blue", "#0000AA"), Map.entry("dark_green", "#00AA00"),
            Map.entry("dark_aqua", "#00AAAA"), Map.entry("dark_red", "#AA0000"), Map.entry("dark_purple", "#AA00AA"),
            Map.entry("gold", "#FFAA00"), Map.entry("gray", "#AAAAAA"), Map.entry("grey", "#AAAAAA"),
            Map.entry("dark_gray", "#555555"), Map.entry("dark_grey", "#555555"), Map.entry("blue", "#5555FF"),
            Map.entry("green", "#55FF55"), Map.entry("aqua", "#55FFFF"), Map.entry("red", "#FF5555"),
            Map.entry("light_purple", "#FF55FF"), Map.entry("yellow", "#FFFF55"), Map.entry("white", "#FFFFFF"));

    private static final class Style {
        String color;
        boolean bold, italic, underlined, strike, obfuscated;
        Gradient gradient;
        String tag;

        Style copy(String tag) {
            Style s = new Style();
            s.color = color; s.bold = bold; s.italic = italic; s.underlined = underlined;
            s.strike = strike; s.obfuscated = obfuscated; s.gradient = gradient; s.tag = tag;
            return s;
        }
    }

    private static final class Gradient {
        final List<int[]> stops = new ArrayList<>();
        int length;
    }

    private record Piece(String text, Style style, int gradientIndex) {
    }

    private static final String[] RAINBOW = {"#ff0000", "#ffff00", "#00ff00", "#00ffff", "#0000ff", "#ff00ff"};

    static final MiniLegacy INSTANCE = new MiniLegacy();

    private MiniLegacy() {
    }

    /** "#a1b2c3" only - anything else (a typo in a config) is ignored instead of breaking the text. */
    private static boolean isHex(String s) {
        if (s == null || s.length() != 7 || s.charAt(0) != '#') return false;
        for (int i = 1; i < 7; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }

    String render(String mini, Ph ph) {
        List<Piece> pieces = new ArrayList<>();
        Deque<Style> stack = new ArrayDeque<>();
        stack.push(new Style());
        parse(expand(mini, ph, 0), ph, pieces, stack);
        return emit(pieces);
    }

    /** Inserts snippet placeholders before parsing, so their tags are parsed too. */
    private String expand(String mini, Ph ph, int depth) {
        if (depth > 3) return mini;
        String out = mini;
        if (ph != null) {
            for (Map.Entry<String, String> e : ph.snippets.entrySet()) out = out.replace("<" + e.getKey() + ">", e.getValue());
        }
        return out.equals(mini) ? out : expand(out, ph, depth + 1);
    }

    private void parse(String s, Ph ph, List<Piece> pieces, Deque<Style> stack) {
        StringBuilder text = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && (s.charAt(i + 1) == '<' || s.charAt(i + 1) == '\\')) {
                text.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            int close = c == '<' ? s.indexOf('>', i) : -1;
            if (close < 0) {
                text.append(c);
                i++;
                continue;
            }
            String tag = s.substring(i + 1, close);
            if (!handleTag(tag, ph, pieces, stack, text)) {
                text.append('<').append(tag).append('>'); // unknown: keep as text, like MiniMessage
            }
            i = close + 1;
        }
        flush(text, pieces, stack);
    }

    private boolean handleTag(String tag, Ph ph, List<Piece> pieces, Deque<Style> stack, StringBuilder text) {
        if (tag.isEmpty()) return false;
        String lower = tag.toLowerCase(Locale.ROOT);

        if (lower.startsWith("/")) {
            flush(text, pieces, stack);
            String name = lower.substring(1);
            if (name.contains(":")) name = name.substring(0, name.indexOf(':'));
            for (Style s : stack) {
                if (name.isEmpty() || name.equals(s.tag)) {
                    while (stack.size() > 1) {
                        Style popped = stack.pop();
                        if (popped == s) break;
                    }
                    break;
                }
            }
            return true;
        }

        String name = lower.contains(":") ? lower.substring(0, lower.indexOf(':')) : lower;
        String arg = lower.contains(":") ? tag.substring(tag.indexOf(':') + 1) : "";

        if (ph != null && ph.values.containsKey(tag)) {
            text.append(ph.values.get(tag));
            return true;
        }
        if (name.equals("newline") || name.equals("br")) {
            text.append('\n');
            return true;
        }
        if (name.equals("reset") || name.equals("r")) {
            flush(text, pieces, stack);
            while (stack.size() > 1) stack.pop();
            return true;
        }

        Style top = stack.peek();
        Style next = null;
        if (isHex(lower)) {
            next = top.copy(name);
            next.color = lower;
            next.gradient = null;
        } else if (NAMED.containsKey(name)) {
            next = top.copy(name);
            next.color = NAMED.get(name);
            next.gradient = null;
        } else if (name.equals("color") || name.equals("colour") || name.equals("c")) {
            String col = arg.toLowerCase(Locale.ROOT);
            next = top.copy(name);
            next.color = isHex(col) ? col : NAMED.getOrDefault(col, top.color);
            next.gradient = null;
        } else if (name.equals("b") || name.equals("bold")) {
            next = top.copy(name);
            next.bold = true;
        } else if (name.equals("i") || name.equals("em") || name.equals("italic")) {
            next = top.copy(name);
            next.italic = true;
        } else if (name.equals("u") || name.equals("underlined")) {
            next = top.copy(name);
            next.underlined = true;
        } else if (name.equals("st") || name.equals("strikethrough")) {
            next = top.copy(name);
            next.strike = true;
        } else if (name.equals("obf") || name.equals("obfuscated")) {
            next = top.copy(name);
            next.obfuscated = true;
        } else if (name.equals("gradient")) {
            Gradient g = new Gradient();
            for (String part : arg.split(":")) {
                String col = part.toLowerCase(Locale.ROOT);
                String hex = isHex(col) ? col : NAMED.get(col);
                if (hex != null) g.stops.add(rgb(hex));
            }
            if (g.stops.size() == 1) g.stops.add(g.stops.get(0));
            if (g.stops.isEmpty()) g.stops.add(new int[] {255, 255, 255});
            next = top.copy(name);
            next.gradient = g;
        } else if (name.equals("rainbow")) {
            Gradient g = new Gradient();
            for (String hex : RAINBOW) g.stops.add(rgb(hex));
            next = top.copy(name);
            next.gradient = g;
        } else if (name.equals("shadow") || name.equals("font") || name.equals("hover") || name.equals("click")
                || name.equals("insert") || name.equals("transition")) {
            next = top.copy(name); // no legacy equivalent: accepted so the closing tag pairs up
        }
        if (next == null) return false;
        flush(text, pieces, stack);
        stack.push(next);
        return true;
    }

    private static void flush(StringBuilder text, List<Piece> pieces, Deque<Style> stack) {
        if (text.length() == 0) return;
        Style s = stack.peek();
        int gradientIndex = -1;
        if (s.gradient != null) {
            gradientIndex = s.gradient.length;
            s.gradient.length += text.codePointCount(0, text.length());
        }
        pieces.add(new Piece(text.toString(), s, gradientIndex));
        text.setLength(0);
    }

    private static String emit(List<Piece> pieces) {
        StringBuilder out = new StringBuilder();
        String lastCodes = null;
        for (Piece piece : pieces) {
            Style s = piece.style();
            int[] cps = piece.text().codePoints().toArray();
            for (int i = 0; i < cps.length; i++) {
                int cp = cps[i];
                if (cp == '\n') {
                    out.append('\n');
                    lastCodes = null; // re-apply the style on the next line
                    continue;
                }
                String color = s.color;
                if (s.gradient != null && piece.gradientIndex() >= 0) color = gradientColor(s.gradient, piece.gradientIndex() + i);
                String codes = codes(color, s);
                if (!codes.equals(lastCodes)) {
                    out.append(codes);
                    lastCodes = codes;
                }
                out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }

    private static String codes(String color, Style s) {
        StringBuilder c = new StringBuilder(hex(color == null ? "#FFFFFF" : color));
        if (s.bold) c.append("§l");
        if (s.italic) c.append("§o");
        if (s.underlined) c.append("§n");
        if (s.strike) c.append("§m");
        if (s.obfuscated) c.append("§k");
        return c.toString();
    }

    /** "#a1b2c3" -> "§x§a§1§b§2§c§3" (Spigot 1.16+ hex colour). */
    private static String hex(String color) {
        StringBuilder sb = new StringBuilder("§x");
        for (char ch : color.substring(1).toLowerCase(Locale.ROOT).toCharArray()) sb.append('§').append(ch);
        return sb.toString();
    }

    private static String gradientColor(Gradient g, int index) {
        if (g.length <= 1) return toHex(g.stops.get(0));
        double t = (double) index / (g.length - 1);
        double scaled = t * (g.stops.size() - 1);
        int a = Math.min((int) Math.floor(scaled), g.stops.size() - 2);
        double local = scaled - a;
        int[] from = g.stops.get(a), to = g.stops.get(a + 1);
        int[] mix = new int[3];
        for (int k = 0; k < 3; k++) mix[k] = (int) Math.round(from[k] + (to[k] - from[k]) * local);
        return toHex(mix);
    }

    private static int[] rgb(String hex) {
        int v = Integer.parseInt(hex.substring(1), 16);
        return new int[] {(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
    }

    private static String toHex(int[] c) {
        return String.format("#%02x%02x%02x", c[0], c[1], c[2]);
    }
}
