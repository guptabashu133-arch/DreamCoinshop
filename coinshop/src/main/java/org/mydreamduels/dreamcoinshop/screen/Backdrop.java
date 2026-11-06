package org.mydreamduels.dreamcoinshop.screen;

import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * The picture behind a queue screen, visible in the empty space around the rows (title,
 * gaps, footer). Built-in designs are drawn here in code (original art); "image" uses any PNG
 * from plugins/Dreamcoinshop/backgrounds/, darkened so the text stays readable. The result is
 * already in map colours (ordered dithering gives smooth gradients), made once per screen.
 */
public final class Backdrop {

    /** Built-in designs; "none" = plain background colour. */
    /** Built-in designs ("none" = plain background colour), then the extra ones from {@link Designs}. */
    private static final String[] BASE = {"none", "gradient", "stars", "grid", "trophies", "waves"};
    public static final String[] STYLES = java.util.stream.Stream.concat(java.util.Arrays.stream(BASE),
            java.util.Arrays.stream(Designs.NAMES)).toArray(String[]::new);

    private static final int[] BAYER = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};

    private Backdrop() {
    }

    public static boolean isStyle(String s) {
        for (String st : STYLES) if (st.equalsIgnoreCase(s)) return true;
        return false;
    }

    /** Map colours for the whole screen (W*H), or null for "none". image may be null. */
    public static byte[] build(String style, int width, int height, int bg, BufferedImage image) {
        String st = style == null ? "none" : style.toLowerCase(Locale.ROOT);
        if (st.equals("none") || st.isBlank()) return null;
        int[] rgb = new int[width * height];
        gradient(rgb, width, height, bg);
        if (Designs.has(st)) Designs.draw(st, rgb, width, height, bg);
        else switch (st) {
            case "stars" -> stars(rgb, width, height, bg);
            case "grid" -> grid(rgb, width, height, bg);
            case "trophies" -> trophies(rgb, width, height, bg);
            case "waves" -> waves(rgb, width, height, bg);
            case "image" -> {
                if (image != null) image(rgb, width, height, bg, image);
            }
            default -> {
            }
        }
        byte[] out = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) out[y * width + x] = dither(rgb[y * width + x], x, y);
        }
        return out;
    }

    // ------------------------------------------------------------ designs

    /** Lighter at the top, darker at the bottom, with soft dark corners. */
    private static void gradient(int[] px, int w, int h, int bg) {
        int top = mix(bg, 0xFFFFFF, 0.08), bottom = mix(bg, 0x000000, 0.45);
        for (int y = 0; y < h; y++) {
            int row = mix(top, bottom, (double) y / (h - 1));
            for (int x = 0; x < w; x++) {
                double dx = (x - w / 2.0) / (w / 2.0), dy = (y - h / 2.0) / (h / 2.0);
                double vignette = Math.max(0, Math.min(1, (dx * dx + dy * dy - 0.55) * 0.6));
                px[y * w + x] = mix(row, 0x000000, vignette);
            }
        }
    }

    private static void stars(int[] px, int w, int h, int bg) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double n = noise(x, y, 7);
                if (n > 0.9935) px[y * w + x] = mix(px[y * w + x], 0xFFFFFF, 0.35 + (n - 0.9935) * 90);
            }
        }
        // a few bigger twinkles: a small plus shape
        for (int i = 0; i < (w * h) / 3500; i++) {
            int x = 3 + (int) (noise(i, 1, 11) * (w - 6)), y = 3 + (int) (noise(i, 2, 13) * (h - 6));
            int c = mix(0xBFE6FF, 0xFFFFFF, noise(i, 3, 17));
            px[y * w + x] = c;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int k = (y + d[1]) * w + x + d[0];
                px[k] = mix(px[k], c, 0.55);
            }
        }
    }

    private static void grid(int[] px, int w, int h, int bg) {
        int line = mix(bg, 0x5CC8FF, 0.16), dot = mix(bg, 0x5CC8FF, 0.45);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean gx = x % 16 == 0, gy = y % 16 == 0;
                if (gx && gy) px[y * w + x] = dot;
                else if (gx || gy) px[y * w + x] = mix(px[y * w + x], line, 0.7);
            }
        }
    }

    /** Faint repeating trophies, every other row shifted. */
    private static void trophies(int[] px, int w, int h, int bg) {
        int[] s = BoardArt.trophy();
        int tile = 32;
        for (int ty = 0; ty < h; ty += tile) {
            int shift = (ty / tile) % 2 == 0 ? 0 : tile / 2;
            for (int tx = -tile; tx < w; tx += tile) {
                int ox = tx + shift + 8, oy = ty + 8;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        int c = s[y * 16 + x];
                        int X = ox + x, Y = oy + y;
                        if (c < 0 || X < 0 || Y < 0 || X >= w || Y >= h) continue;
                        px[Y * w + X] = mix(px[Y * w + X], c, 0.16);
                    }
                }
            }
        }
    }

    /** Soft diagonal light bands. */
    private static void waves(int[] px, int w, int h, int bg) {
        int light = mix(bg, 0x5CC8FF, 0.5);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double v = Math.sin((x + y * 1.6) / 22.0) * 0.5 + Math.sin((x * 0.6 - y) / 37.0) * 0.5;
                if (v > 0.55) px[y * w + x] = mix(px[y * w + x], light, (v - 0.55) * 0.35);
            }
        }
    }

    /** Your own picture: scaled to cover the screen, darkened and tinted towards the background colour. */
    private static void image(int[] px, int w, int h, int bg, BufferedImage img) {
        double scale = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
        int sw = (int) Math.ceil(w / scale), sh = (int) Math.ceil(h / scale);
        int sx0 = (img.getWidth() - sw) / 2, sy0 = (img.getHeight() - sh) / 2;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // box-average the source pixels under this screen pixel (no jaggies when shrinking)
                int x0 = sx0 + (int) (x / scale), x1 = Math.max(x0 + 1, sx0 + (int) ((x + 1) / scale));
                int y0 = sy0 + (int) (y / scale), y1 = Math.max(y0 + 1, sy0 + (int) ((y + 1) / scale));
                long r = 0, g = 0, b = 0, n = 0;
                for (int yy = y0; yy < y1; yy += Math.max(1, (y1 - y0) / 3)) {
                    for (int xx = x0; xx < x1; xx += Math.max(1, (x1 - x0) / 3)) {
                        int c = img.getRGB(Math.min(img.getWidth() - 1, Math.max(0, xx)), Math.min(img.getHeight() - 1, Math.max(0, yy)));
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                        n++;
                    }
                }
                int c = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
                px[y * w + x] = mix(mix(c, 0x000000, 0.28), bg, 0.12);
            }
        }
    }

    // ------------------------------------------------------------ helpers

    private static byte dither(int rgb, int x, int y) {
        int t = BAYER[(y & 3) * 4 + (x & 3)] - 8; // -8..7
        int d = t * 2;
        int r = clamp(((rgb >> 16) & 255) + d), g = clamp(((rgb >> 8) & 255) + d), b = clamp((rgb & 255) + d);
        return Palette.of((r << 16) | (g << 8) | b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static double noise(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0;
    }
}
