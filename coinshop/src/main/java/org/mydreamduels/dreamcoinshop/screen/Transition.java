package org.mydreamduels.dreamcoinshop.screen;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Page-change effects of a cycling screen: the old page and the new page mixed into one
 * picture, for progress 0..1. Pure (map colour bytes in, bytes out), so any thread may call it.
 */
public final class Transition {

    /** Every effect, in the order the commands list them. "random" picks one of these each time. */
    public static final List<String> EFFECTS = List.of("page", "slide", "wipe", "fade", "flip", "blinds", "curtain", "circle");

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private Transition() {
    }

    /** "PAGE" / "random" / "off" -> a known effect id, "random", "off", or null (unknown). */
    public static String parse(String s) {
        if (s == null) return null;
        String e = s.trim().toLowerCase(Locale.ROOT);
        if (e.equals("none") || e.equals("false")) e = "off";
        if (e.equals("off") || e.equals("random") || EFFECTS.contains(e)) return e;
        return null;
    }

    /** "random" -> one real effect; anything else unchanged. */
    public static String pick(String effect) {
        if (!"random".equals(effect)) return effect;
        return EFFECTS.get(ThreadLocalRandom.current().nextInt(EFFECTS.size()));
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * The picture between from (old page) and to (new page). accent = the new page's colour
     * (map byte) for edges and rings, paper / shadow for the page-turn fold.
     */
    public static byte[] frame(String effect, byte[] from, byte[] to, int W, int H, double progress, byte accent) {
        double p = ease(progress);
        byte[] out = new byte[to.length];
        byte paper = Palette.of(0xE8E2D0), shadow = Palette.of(0x101114), dark = Palette.of(0x0B0D12);
        switch (effect) {
            case "slide" -> {
                // the new page pushes the old one out to the left
                int off = (int) Math.round(p * W);
                for (int y = 0; y < H; y++) {
                    int r = y * W;
                    for (int x = 0; x < W; x++) {
                        int sx = x + off;
                        out[r + x] = sx < W ? from[r + sx] : to[r + sx - W];
                    }
                }
            }
            case "wipe" -> {
                // the new page is revealed left to right behind a glowing bar
                int edge = (int) Math.round(p * (W + 4)) - 2;
                for (int y = 0; y < H; y++) {
                    int r = y * W;
                    for (int x = 0; x < W; x++) {
                        int i = r + x;
                        out[i] = x < edge - 1 ? to[i] : x <= edge + 1 ? keep(to[i], accent) : from[i];
                    }
                }
            }
            case "fade" -> {
                // ordered dissolve: maps can't blend colours, so pixels switch over in a fine pattern
                int level = (int) Math.round(p * 16);
                for (int y = 0; y < H; y++) {
                    int r = y * W;
                    for (int x = 0; x < W; x++) out[r + x] = BAYER[(y & 3) * 4 + (x & 3)] < level ? to[r + x] : from[r + x];
                }
            }
            case "flip" -> {
                // a card turning over: the old page narrows to a line, the new one opens up
                boolean second = p >= 0.5;
                byte[] src = second ? to : from;
                double scale = second ? (p - 0.5) * 2 : 1 - p * 2;
                int half = (int) Math.round(W * scale / 2), cx = W / 2;
                for (int y = 0; y < H; y++) {
                    int r = y * W;
                    for (int x = 0; x < W; x++) {
                        int i = r + x;
                        int d = x - cx;
                        if (half <= 0 || d < -half || d >= half) {
                            out[i] = src[i] == Palette.TRANSPARENT ? Palette.TRANSPARENT : dark;
                        } else {
                            int sx = cx + (int) ((long) d * (W / 2) / Math.max(1, half));
                            sx = Math.max(0, Math.min(W - 1, sx));
                            byte b = src[r + sx];
                            out[i] = src[i] == Palette.TRANSPARENT ? Palette.TRANSPARENT : b == Palette.TRANSPARENT ? dark : b;
                        }
                    }
                }
            }
            case "blinds" -> {
                // horizontal slats turning one after another, top to bottom
                int slat = 16;
                for (int y = 0; y < H; y++) {
                    int r = y * W, band = y / slat, inBand = y % slat;
                    double local = Math.max(0, Math.min(1, p * 1.6 - band * 0.6 / Math.max(1, H / slat)));
                    boolean shown = inBand < local * slat;
                    for (int x = 0; x < W; x++) out[r + x] = shown ? to[r + x] : from[r + x];
                }
            }
            case "curtain" -> {
                // opens from the middle outwards, like a stage curtain
                double open = p * (W / 2.0 + 2);
                int cx = W / 2;
                for (int y = 0; y < H; y++) {
                    int r = y * W;
                    for (int x = 0; x < W; x++) {
                        int i = r + x;
                        double d = Math.abs(x + 0.5 - cx);
                        out[i] = d < open - 1 ? to[i] : d < open + 1 ? keep(to[i], accent) : from[i];
                    }
                }
            }
            case "circle" -> {
                // a growing circle from the centre with a coloured ring
                double max = Math.sqrt((double) W * W + (double) H * H) / 2 + 3;
                double rad = p * max;
                double cx = W / 2.0, cy = H / 2.0;
                for (int y = 0; y < H; y++) {
                    int r = y * W;
                    for (int x = 0; x < W; x++) {
                        int i = r + x;
                        double dx = x + 0.5 - cx, dy = y + 0.5 - cy, d = Math.sqrt(dx * dx + dy * dy);
                        out[i] = d < rad - 1.5 ? to[i] : d < rad + 1.5 ? keep(to[i], accent) : from[i];
                    }
                }
            }
            default -> {
                // "page": the old page is turned over from the top-right corner - its back (paper)
                // folds across, with a soft shadow on the new page underneath
                double slope = 0.6, span = W + H * slope + 24;
                double cut = (1 - p) * span - 12;
                for (int y = 0; y < H; y++) {
                    int r = y * W;
                    for (int x = 0; x < W; x++) {
                        int i = r + x;
                        double d = x + (H - y) * slope - cut;
                        byte b;
                        if (d > 10) b = d < 14 ? keep(to[i], shadow) : to[i];
                        else if (d > 0) b = keep(to[i], d > 8.5 ? shadow : paper);
                        else b = from[i];
                        out[i] = b;
                    }
                }
            }
        }
        return out;
    }

    /** Colour c, but keep the rounded transparent corners transparent. */
    private static byte keep(byte under, byte c) {
        return under == Palette.TRANSPARENT ? Palette.TRANSPARENT : c;
    }
}
