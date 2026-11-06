package org.mydreamduels.dreamcoinshop.screen;

import java.util.Locale;

/**
 * Extra backdrop designs, all drawn here in code (original art, no images). Each fills a
 * width x height RGB picture; Backdrop turns it into map colours. Every design is kept dark
 * enough for white text on top to stay readable.
 */
public final class Designs {

    public static final String[] NAMES = {
            "aurora", "galaxy", "sunset", "ocean", "lava", "synthwave", "matrix", "circuit", "hexagons",
            "diamonds", "confetti", "sunburst", "snow", "sakura", "bubbles", "fireflies", "checker", "rain",
            "ores", "cave", "portal", "nether", "end", "blockwall", "wool", "obsidian"};

    private Designs() {
    }

    public static boolean has(String name) {
        for (String n : NAMES) if (n.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** Draws the design into px (w*h RGB). False if the name isn't one of {@link #NAMES}. */
    public static boolean draw(String name, int[] px, int w, int h, int bg) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "aurora" -> aurora(px, w, h);
            case "galaxy" -> galaxy(px, w, h);
            case "sunset" -> sunset(px, w, h);
            case "ocean" -> ocean(px, w, h);
            case "lava" -> lava(px, w, h);
            case "synthwave" -> synthwave(px, w, h);
            case "matrix" -> matrix(px, w, h);
            case "circuit" -> circuit(px, w, h);
            case "hexagons" -> hexagons(px, w, h, bg);
            case "diamonds" -> diamonds(px, w, h, bg);
            case "confetti" -> confetti(px, w, h, bg);
            case "sunburst" -> sunburst(px, w, h, bg);
            case "snow" -> snow(px, w, h);
            case "sakura" -> sakura(px, w, h);
            case "bubbles" -> bubbles(px, w, h, bg);
            case "fireflies" -> fireflies(px, w, h);
            case "checker" -> checker(px, w, h, bg);
            case "rain" -> rain(px, w, h, bg);
            case "ores" -> ores(px, w, h);
            case "cave" -> cave(px, w, h);
            case "portal" -> portal(px, w, h);
            case "nether" -> nether(px, w, h);
            case "end" -> end(px, w, h);
            case "blockwall" -> blocks(px, w, h);
            case "wool" -> wool(px, w, h);
            case "obsidian" -> obsidian(px, w, h);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ skies & scenes

    /** Night sky with green / purple northern lights and stars. */
    private static void aurora(int[] px, int w, int h) {
        vertical(px, w, h, 0x040A18, 0x0B1A2E);
        stars(px, w, h, 0.994, 11);
        for (int x = 0; x < w; x++) {
            // two curtains: one across the title, one low enough to show below the rows
            double band = h * 0.10 + Math.sin(x / 47.0) * h * 0.06 + Math.sin(x / 19.0 + 1.7) * h * 0.03;
            for (int y = 0; y < h; y++) {
                double d = (y - band) / (h * 0.16);
                if (d < -1 || d > 2.2) continue;
                double glow = d < 0 ? (1 + d) : Math.max(0, 1 - d / 2.2);           // sharp top edge, long fade down
                glow *= 0.55 + 0.45 * fbm(x / 23.0, y / 61.0, 3);                      // curtain streaks
                int col = mix(0x2BFF9A, 0x9B5CFF, Math.max(0, Math.min(1, x / (double) w + Math.sin(x / 90.0) * 0.2)));
                px[y * w + x] = mix(px[y * w + x], col, glow * 0.85);
            }
            double low = h * 0.72 + Math.sin(x / 37.0 + 2) * h * 0.05;
            for (int y = 0; y < h; y++) {
                double d = (y - low) / (h * 0.12);
                if (d < -1 || d > 1.6) continue;
                double glow = (d < 0 ? (1 + d) : Math.max(0, 1 - d / 1.6)) * (0.5 + 0.5 * fbm(x / 21.0 + 5, y / 55.0, 4));
                px[y * w + x] = mix(px[y * w + x], mix(0x9B5CFF, 0x2BFF9A, x / (double) w), glow * 0.7);
            }
        }
        silhouette(px, w, h, 0.86, 0x02050C, 9);
    }

    /** Deep space: soft purple / blue nebula clouds and stars. */
    private static void galaxy(int[] px, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double n = fbm(x / 70.0, y / 70.0, 5), m = fbm(x / 33.0 + 9, y / 33.0, 7);
                int c = mix(0x05040F, 0x3A1660, Math.pow(n, 1.6) * 1.2);
                c = mix(c, 0x1F4FA8, Math.max(0, m - 0.5) * 1.1);
                px[y * w + x] = c;
            }
        }
        stars(px, w, h, 0.990, 21);
        dim(px, 0.15);
    }

    /** Orange-to-purple sky, a striped low sun and dark hills. */
    private static void sunset(int[] px, int w, int h) {
        for (int y = 0; y < h; y++) {
            double t = (double) y / h;
            int c = t < 0.5 ? mix(0x2A1446, 0xC24A5A, t * 2) : mix(0xC24A5A, 0xF29A4A, (t - 0.5) * 2);
            for (int x = 0; x < w; x++) px[y * w + x] = c;
        }
        int cx = w / 2, cy = (int) (h * 0.70), r = (int) (h * 0.22);
        for (int y = cy - r; y <= cy + r; y++) {
            if (y < 0 || y >= h) continue;
            boolean gap = y > cy - r / 3 && ((y - cy) / Math.max(2, r / 8)) % 2 == 0;    // the stripes in the lower half
            for (int x = cx - r; x <= cx + r; x++) {
                if (x < 0 || x >= w || Math.hypot(x - cx, y - cy) > r || gap) continue;
                px[y * w + x] = mix(0xFFE08A, 0xFF7A4A, (double) (y - (cy - r)) / (2 * r));
            }
        }
        silhouette(px, w, h, 0.80, 0x1A0D22, 5);
        silhouette(px, w, h, 0.90, 0x0D0612, 13);
        dim(px, 0.30);
    }

    /** Underwater: deep blue, light rays from the top and rising bubbles. */
    private static void ocean(int[] px, int w, int h) {
        vertical(px, w, h, 0x0F5E8C, 0x041628);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double ray = Math.max(0, Math.sin((x + y * 0.35) / 18.0) * Math.sin((x - y * 0.2) / 41.0));
                px[y * w + x] = mix(px[y * w + x], 0x7FD8FF, ray * 0.25 * (1 - (double) y / h));
            }
        }
        for (int i = 0; i < (w * h) / 2400; i++) {
            int x = (int) (noise(i, 1, 5) * w), y = (int) (noise(i, 2, 5) * h), r = 1 + (int) (noise(i, 3, 5) * 3);
            ring(px, w, h, x, y, r, 0xBFEFFF, 0.6);
        }
        dim(px, 0.25);
    }

    /** Glowing orange cracks between dark rock plates. */
    private static void lava(int[] px, int w, int h) {
        int cell = 34;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] d = worley(x, y, cell, 31);
                double edge = d[1] - d[0];                                   // small near the border between plates
                int rock = mix(0x1A0B08, 0x2E1410, noise(x / 3, y / 3, 4));
                double glow = Math.max(0, 1 - edge / 6.0);
                px[y * w + x] = mix(rock, glow > 0.7 ? 0xFFD24A : 0xFF5A1F, glow);
            }
        }
        dim(px, 0.42);
    }

    /** Retro neon: purple sky, striped sun, glowing perspective grid floor. */
    private static void synthwave(int[] px, int w, int h) {
        int horizon = (int) (h * 0.58);
        for (int y = 0; y < h; y++) {
            int c = y < horizon ? mix(0x12052A, 0x5A1468, (double) y / horizon) : mix(0x0A0418, 0x05020C, (double) (y - horizon) / (h - horizon));
            for (int x = 0; x < w; x++) px[y * w + x] = c;
        }
        stars(px, w, h * 1 / 2, 0.995, 41);
        int cx = w / 2, cy = horizon - 2, r = (int) (h * 0.20);
        for (int y = cy - r; y <= cy; y++) {
            if (y < 0) continue;
            boolean gap = y > cy - r / 2 && ((cy - y) / Math.max(2, r / 9)) % 2 == 1;
            for (int x = cx - r; x <= cx + r; x++) {
                if (x < 0 || x >= w || Math.hypot(x - cx, y - cy) > r || gap) continue;
                px[y * w + x] = mix(0xFFD04A, 0xFF3D8B, (double) (y - (cy - r)) / r);
            }
        }
        int neon = 0xFF4FD8;
        for (int y = horizon; y < h; y++) {
            double depth = (double) (y - horizon + 1) / (h - horizon);
            int step = Math.max(1, (int) (6 / depth));
            boolean hline = ((int) (Math.log(depth * 40 + 1) * 6)) % 3 == 0 && ((y - horizon) % Math.max(1, (int) (depth * 10)) == 0);
            for (int x = 0; x < w; x++) {
                double vx = (x - cx) / (depth * 0.9 + 0.1);
                boolean vline = Math.abs(((vx % 28) + 28) % 28) < 1.2;
                if (vline || hline) px[y * w + x] = mix(px[y * w + x], neon, 0.55 * Math.min(1, depth * 2));
            }
        }
    }

    /** Falling green code columns. */
    private static void matrix(int[] px, int w, int h) {
        fill(px, 0x020A04);
        int col = 6;
        for (int cx = 0; cx < w; cx += col) {
            int seed = cx / col;
            double head = noise(seed, 1, 51) * h * 1.3, len = 30 + noise(seed, 2, 51) * 90;
            for (int y = 0; y < h; y++) {
                double d = head - y;
                if (d < 0 || d > len) continue;
                double fade = 1 - d / len;
                for (int x = cx; x < Math.min(w, cx + col - 2); x++) {
                    if (noise(x, y / 2 + seed * 7, 53) < 0.45) continue;           // broken "glyph" pixels
                    px[y * w + x] = d < 2 ? 0xC8FFD0 : mix(0x020A04, 0x2BD94A, fade * 0.9);
                }
            }
        }
    }

    /** Circuit board: dark green with traces and solder pads. */
    private static void circuit(int[] px, int w, int h) {
        fill(px, 0x06160F);
        int trace = 0x1F7A4A, pad = 0x6BE0A0;
        for (int i = 0; i < (w * h) / 900; i++) {
            int x = (int) (noise(i, 1, 61) * w) / 4 * 4, y = (int) (noise(i, 2, 61) * h) / 4 * 4;
            int len = 8 + (int) (noise(i, 3, 61) * 50);
            boolean horizontal = noise(i, 4, 61) > 0.5;
            for (int k = 0; k < len; k++) {
                int X = horizontal ? x + k : x, Y = horizontal ? y : y + k;
                if (k == len / 2 && noise(i, 5, 61) > 0.5) horizontal = !horizontal; // one 90° bend
                if (X >= 0 && Y >= 0 && X < w && Y < h) px[Y * w + X] = trace;
            }
            disc(px, w, h, x, y, 2, pad);
        }
        dim(px, 0.15);
    }

    /** Winter night: falling snowflakes and a snowy ground. */
    private static void snow(int[] px, int w, int h) {
        vertical(px, w, h, 0x0D1B33, 0x2A3F66);
        for (int i = 0; i < (w * h) / 500; i++) {
            int x = (int) (noise(i, 1, 71) * w), y = (int) (noise(i, 2, 71) * h);
            double s = noise(i, 3, 71);
            if (s > 0.85) {
                for (int[] d : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) set(px, w, h, x + d[0], y + d[1], 0xEAF4FF);
            } else {
                set(px, w, h, x, y, mix(0x2A3F66, 0xFFFFFF, 0.5 + s * 0.5));
            }
        }
        silhouette(px, w, h, 0.88, 0xDDE8F5, 7);
        dim(px, 0.35);
    }

    /** Soft dusk gradient with drifting cherry blossom petals. */
    private static void sakura(int[] px, int w, int h) {
        vertical(px, w, h, 0x3A1E3A, 0x7A3E5E);
        for (int i = 0; i < (w * h) / 700; i++) {
            int x = (int) (noise(i, 1, 81) * w), y = (int) (noise(i, 2, 81) * h);
            int c = mix(0xFFB7D0, 0xFFE3EE, noise(i, 3, 81));
            set(px, w, h, x, y, c);
            set(px, w, h, x + 1, y, c);
            set(px, w, h, x, y + 1, mix(c, 0xC0607E, 0.4));
        }
        dim(px, 0.30);
    }

    /** Dark forest edge at night with glowing fireflies. */
    private static void fireflies(int[] px, int w, int h) {
        vertical(px, w, h, 0x061018, 0x0D2218);
        for (int i = 0; i < (w * h) / 1000; i++) {
            int x = (int) (noise(i, 1, 91) * w), y = (int) (noise(i, 2, 91) * h * 0.9);
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    double d = Math.hypot(dx, dy);
                    if (d > 4) continue;
                    int X = x + dx, Y = y + dy;
                    if (X < 0 || Y < 0 || X >= w || Y >= h) continue;
                    px[Y * w + X] = mix(px[Y * w + X], 0xF2FF8A, d < 1.2 ? 1 : 0.5 * (1 - d / 4));
                }
            }
        }
        silhouette(px, w, h, 0.84, 0x030806, 17);
    }

    // ------------------------------------------------------------ patterns (tinted by the background colour)

    private static void hexagons(int[] px, int w, int h, int bg) {
        base(px, w, h, bg);
        double size = 14;
        int line = mix(bg, 0x7FC8FF, 0.35);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double q = (x * Math.sqrt(3) / 3 - y / 3.0) / size, r = (y * 2 / 3.0) / size;
                double[] c = hexRound(q, r);
                double dq = q - c[0], dr = r - c[1], ds = -dq - dr;
                double edge = Math.max(Math.abs(dq), Math.max(Math.abs(dr), Math.abs(ds)));
                if (edge > 0.46) px[y * w + x] = mix(px[y * w + x], line, (edge - 0.46) * 12);
            }
        }
    }

    private static void diamonds(int[] px, int w, int h, int bg) {
        base(px, w, h, bg);
        int a = mix(bg, 0xFFFFFF, 0.06), b = mix(bg, 0x000000, 0.18), line = mix(bg, 0xFFC23D, 0.30);
        int s = 22;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int u = Math.floorMod(x + y, s * 2), v = Math.floorMod(x - y, s * 2);
                px[y * w + x] = mix(px[y * w + x], (u < s) == (v < s) ? a : b, 0.8);
                if (u == 0 || v == 0) px[y * w + x] = line;                      // thin argyle lines
            }
        }
    }

    private static void confetti(int[] px, int w, int h, int bg) {
        base(px, w, h, bg);
        int[] colours = {0xFF5C5C, 0xFFC23D, 0x5CFF8D, 0x4DE1FF, 0xC77DFF, 0xFF8AD8};
        for (int i = 0; i < (w * h) / 260; i++) {
            int x = (int) (noise(i, 1, 101) * w), y = (int) (noise(i, 2, 101) * h);
            int c = mix(colours[(int) (noise(i, 3, 101) * colours.length) % colours.length], bg, 0.45);
            boolean tall = noise(i, 4, 101) > 0.5;
            for (int k = 0; k < 3; k++) set(px, w, h, tall ? x : x + k, tall ? y + k : y, c);
        }
    }

    private static void sunburst(int[] px, int w, int h, int bg) {
        base(px, w, h, bg);
        int light = mix(bg, 0xFFD27A, 0.18);
        double cx = w / 2.0, cy = -h * 0.15;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double ang = Math.atan2(y - cy, x - cx);
                if (Math.floorMod((int) Math.floor(ang * 24 / Math.PI), 2) == 0) {
                    double fade = Math.max(0, 1 - Math.hypot(x - cx, y - cy) / (h * 1.3));
                    px[y * w + x] = mix(px[y * w + x], light, 0.4 + 0.6 * fade);
                }
            }
        }
    }

    private static void bubbles(int[] px, int w, int h, int bg) {
        base(px, w, h, bg);
        for (int i = 0; i < (w * h) / 1400; i++) {
            int x = (int) (noise(i, 1, 111) * w), y = (int) (noise(i, 2, 111) * h), r = 3 + (int) (noise(i, 3, 111) * 14);
            ring(px, w, h, x, y, r, mix(bg, 0x9FE3FF, 0.35), 0.8);
            set(px, w, h, x - r / 3, y - r / 3, mix(bg, 0xFFFFFF, 0.5)); // shine
        }
    }

    private static void checker(int[] px, int w, int h, int bg) {
        int a = mix(bg, 0xFFFFFF, 0.05), b = mix(bg, 0x000000, 0.15);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) px[y * w + x] = ((x / 16) + (y / 16)) % 2 == 0 ? a : b;
        }
        vignette(px, w, h);
    }

    private static void rain(int[] px, int w, int h, int bg) {
        base(px, w, h, mix(bg, 0x101A2A, 0.5));
        int drop = mix(bg, 0x9FC8FF, 0.35);
        for (int i = 0; i < (w * h) / 450; i++) {
            int x = (int) (noise(i, 1, 121) * w), y = (int) (noise(i, 2, 121) * h), len = 4 + (int) (noise(i, 3, 121) * 7);
            for (int k = 0; k < len; k++) set(px, w, h, x - k / 3, y + k, drop);
        }
    }


    // ------------------------------------------------------------ Minecraft-style (block walls, drawn in code)

    /** One 16x16 block at (bx, by): base colour with per-pixel shading, like a block texture. */
    private static void block(int[] px, int w, int h, int bx, int by, int colour, double rough, int seed) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int X = bx + x, Y = by + y;
                if (X < 0 || Y < 0 || X >= w || Y >= h) continue;
                double n = noise(X, Y, seed) * 0.6 + noise(X >> 1, Y >> 1, seed + 1) * 0.4;
                int c = mix(colour, n > 0.5 ? 0xFFFFFF : 0x000000, Math.abs(n - 0.5) * rough);
                if (x == 15 || y == 15) c = mix(c, 0x000000, 0.18);       // a hint of the block edges
                px[Y * w + X] = c;
            }
        }
    }

    /** Small ore cluster inside a block. */
    private static void ore(int[] px, int w, int h, int bx, int by, int colour, int seed) {
        for (int k = 0; k < 5; k++) {
            int x = bx + 2 + (int) (noise(bx + k, by, seed) * 11), y = by + 2 + (int) (noise(bx, by + k, seed + 3) * 11);
            set(px, w, h, x, y, colour);
            set(px, w, h, x + 1, y, mix(colour, 0xFFFFFF, 0.35));
            set(px, w, h, x, y + 1, mix(colour, 0x000000, 0.3));
        }
    }

    /** Stone wall with diamond, gold, emerald, redstone and lapis ores. */
    private static void ores(int[] px, int w, int h) {
        int[] oreColours = {0x5DECF5, 0xFCD34D, 0x34D27A, 0xE5352B, 0x2E5BD8, 0xD8C8B8};
        for (int by = 0; by < h; by += 16) {
            for (int bx = 0; bx < w; bx += 16) {
                block(px, w, h, bx, by, 0x7A7A7A, 0.35, 3);
                double r = noise(bx / 16, by / 16, 7);
                if (r > 0.80) ore(px, w, h, bx, by, oreColours[(int) (noise(bx, by, 9) * oreColours.length) % oreColours.length], bx * 31 + by);
            }
        }
        dim(px, 0.45);
    }

    /** Deep dark cave: deepslate, a few ores and warm torch light pools. */
    private static void cave(int[] px, int w, int h) {
        for (int by = 0; by < h; by += 16) {
            for (int bx = 0; bx < w; bx += 16) {
                block(px, w, h, bx, by, 0x3A3A44, 0.30, 13);
                if (noise(bx / 16, by / 16, 17) > 0.88) ore(px, w, h, bx, by, noise(bx, by, 19) > 0.5 ? 0x5DECF5 : 0x34D27A, bx + by * 7);
            }
        }
        dim(px, 0.35);
        for (int i = 0; i < Math.max(3, w / 90); i++) {                    // torches
            int tx = (int) ((i + 0.5) * w / Math.max(3, w / 90)), ty = (int) (h * (0.15 + noise(i, 4, 23) * 0.7));
            for (int y = Math.max(0, ty - 40); y < Math.min(h, ty + 40); y++) {
                for (int x = Math.max(0, tx - 40); x < Math.min(w, tx + 40); x++) {
                    double d = Math.hypot(x - tx, y - ty) / 40.0;
                    if (d < 1) px[y * w + x] = mix(px[y * w + x], 0xFFB04A, (1 - d) * (1 - d) * 0.45);
                }
            }
            for (int y = 0; y < 6; y++) set(px, w, h, tx, ty + y, 0x6B4A2B);  // stick
            set(px, w, h, tx, ty - 1, 0xFFE07A);
            set(px, w, h, tx, ty - 2, 0xFFF4C0);
        }
    }

    /** A glowing purple portal inside an obsidian frame. */
    private static void portal(int[] px, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double swirl = Math.sin((x + Math.sin(y / 9.0) * 8) / 7.0) * 0.5 + Math.sin((y + Math.sin(x / 11.0) * 6) / 5.0) * 0.5;
                px[y * w + x] = mix(0x3A0A6E, 0xB04CFF, (swirl + 1) / 2 * 0.8 + noise(x, y, 29) * 0.15);
            }
        }
        for (int by = 0; by < h; by += 16) {                                // obsidian frame
            for (int bx = 0; bx < w; bx += 16) {
                if (bx < 16 || by < 16 || bx >= w - 16 || by >= h - 16) block(px, w, h, bx, by, 0x140C22, 0.5, 31);
            }
        }
        dim(px, 0.35);
    }

    /** Netherrack ground, flickering fire and a lava glow at the bottom. */
    private static void nether(int[] px, int w, int h) {
        for (int by = 0; by < h; by += 16) {
            for (int bx = 0; bx < w; bx += 16) block(px, w, h, bx, by, 0x6E2626, 0.45, 37);
        }
        for (int y = (int) (h * 0.80); y < h; y++) {                         // lava sea
            for (int x = 0; x < w; x++) {
                double n = smooth(x / 9.0, y / 5.0, 41);
                px[y * w + x] = mix(0xD9480F, 0xFFC23D, n);
            }
        }
        for (int i = 0; i < w / 40; i++) {                                    // fires
            int fx = (int) (noise(i, 1, 43) * w), fy = (int) (h * 0.80);
            for (int y = 0; y < 14; y++) {
                for (int x = -3; x <= 3; x++) {
                    if (noise(fx + x, y, 47) > 0.55 - y * 0.03) set(px, w, h, fx + x, fy - y, y < 5 ? 0xFFD24A : 0xFF6A1F);
                }
            }
        }
        dim(px, 0.42);
    }

    /** The void with floating end stone islands and purple particles. */
    private static void end(int[] px, int w, int h) {
        vertical(px, w, h, 0x0A0614, 0x150A26);
        stars(px, w, h, 0.996, 53);
        for (int i = 0; i < (w * h) / 900; i++) {                              // particles
            int x = (int) (noise(i, 1, 59) * w), y = (int) (noise(i, 2, 59) * h);
            set(px, w, h, x, y, mix(0xC77DFF, 0xFFFFFF, noise(i, 3, 59) * 0.5));
        }
        for (int k = 0; k < 4; k++) {                                          // islands
            int cx = (int) ((k + 0.5) * w / 4.0), cy = (int) (h * (0.25 + noise(k, 1, 61) * 0.6)), rw = 20 + (int) (noise(k, 2, 61) * 28);
            for (int y = 0; y < rw / 2; y++) {
                int half = (int) (rw * (1 - (double) y / (rw / 2.0)));
                for (int x = -half; x <= half; x++) {
                    double n = noise(cx + x, cy + y, 67);
                    set(px, w, h, cx + x, cy + y, y == 0 ? 0xE9EAB4 : mix(0xC6C786, 0x8A8B5A, n * 0.6 + (double) y / rw));
                }
            }
        }
        dim(px, 0.25);
    }

    /** A wall of grass, dirt, stone and wooden plank blocks. */
    private static void blocks(int[] px, int w, int h) {
        int[] kinds = {0x5FA638, 0x7E5836, 0x7A7A7A, 0xA8834E, 0x5FA638, 0x7E5836};
        for (int by = 0; by < h; by += 16) {
            for (int bx = 0; bx < w; bx += 16) {
                int kind = (int) (noise(bx / 16, by / 16, 71) * kinds.length) % kinds.length;
                block(px, w, h, bx, by, kinds[kind], kind == 3 ? 0.2 : 0.4, 73 + kind);
                if (kind == 3) {                                                // plank lines
                    for (int x = 0; x < 16; x++) {
                        set(px, w, h, bx + x, by + 3, 0x6E5232);
                        set(px, w, h, bx + x, by + 11, 0x6E5232);
                    }
                }
            }
        }
        dim(px, 0.45);
    }

    /** Colourful wool blocks. */
    private static void wool(int[] px, int w, int h) {
        int[] colours = {0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C627, 0x70B919, 0xED8DAC, 0x3E4447,
                0x8E8E86, 0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA12722, 0x141519};
        for (int by = 0; by < h; by += 16) {
            for (int bx = 0; bx < w; bx += 16) {
                int c = colours[(int) (noise(bx / 16, by / 16, 79) * colours.length) % colours.length];
                block(px, w, h, bx, by, c, 0.25, 83);
            }
        }
        dim(px, 0.50);
    }

    /** Obsidian with glowing crying-obsidian spots. */
    private static void obsidian(int[] px, int w, int h) {
        for (int by = 0; by < h; by += 16) {
            for (int bx = 0; bx < w; bx += 16) {
                block(px, w, h, bx, by, 0x1A1028, 0.55, 89);
                if (noise(bx / 16, by / 16, 97) > 0.7) {
                    for (int k = 0; k < 7; k++) {
                        int x = bx + 2 + (int) (noise(bx + k, by, 101) * 12), y = by + 2 + (int) (noise(bx, by + k, 103) * 12);
                        set(px, w, h, x, y, 0xA43CFF);
                        set(px, w, h, x, y + 1, 0x6A1FBF);
                    }
                }
            }
        }
        dim(px, 0.2);
    }

    // ------------------------------------------------------------ helpers

    private static void base(int[] px, int w, int h, int bg) {
        vertical(px, w, h, mix(bg, 0xFFFFFF, 0.06), mix(bg, 0x000000, 0.40));
    }

    private static void vertical(int[] px, int w, int h, int top, int bottom) {
        for (int y = 0; y < h; y++) {
            int c = mix(top, bottom, (double) y / Math.max(1, h - 1));
            for (int x = 0; x < w; x++) px[y * w + x] = c;
        }
    }

    private static void fill(int[] px, int c) {
        java.util.Arrays.fill(px, c);
    }

    private static void dim(int[] px, double t) {
        for (int i = 0; i < px.length; i++) px[i] = mix(px[i], 0x000000, t);
    }

    private static void vignette(int[] px, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - w / 2.0) / (w / 2.0), dy = (y - h / 2.0) / (h / 2.0);
                px[y * w + x] = mix(px[y * w + x], 0x000000, Math.max(0, Math.min(0.5, (dx * dx + dy * dy - 0.5) * 0.5)));
            }
        }
    }

    private static void stars(int[] px, int w, int h, double threshold, int seed) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double n = noise(x, y, seed);
                if (n > threshold) px[y * w + x] = mix(px[y * w + x], 0xFFFFFF, 0.4 + (n - threshold) / (1 - threshold) * 0.6);
            }
        }
    }

    /** Rolling hills from base*h downwards, in one colour. */
    private static void silhouette(int[] px, int w, int h, double base, int colour, int seed) {
        for (int x = 0; x < w; x++) {
            int top = (int) (h * base + Math.sin(x / (31.0 + seed)) * h * 0.04 + Math.sin(x / 13.0 + seed) * h * 0.015);
            for (int y = Math.max(0, top); y < h; y++) px[y * w + x] = colour;
        }
    }

    private static void ring(int[] px, int w, int h, int cx, int cy, int r, int colour, double alpha) {
        for (int y = cy - r - 1; y <= cy + r + 1; y++) {
            for (int x = cx - r - 1; x <= cx + r + 1; x++) {
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                double d = Math.abs(Math.hypot(x - cx, y - cy) - r);
                if (d < 0.8) px[y * w + x] = mix(px[y * w + x], colour, alpha);
            }
        }
    }

    private static void disc(int[] px, int w, int h, int cx, int cy, int r, int colour) {
        for (int y = cy - r; y <= cy + r; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                if (Math.hypot(x - cx, y - cy) <= r) set(px, w, h, x, y, colour);
            }
        }
    }

    private static void set(int[] px, int w, int h, int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < w && y < h) px[y * w + x] = c;
    }

    private static double[] hexRound(double q, double r) {
        double s = -q - r;
        double rq = Math.round(q), rr = Math.round(r), rs = Math.round(s);
        double dq = Math.abs(rq - q), dr = Math.abs(rr - r), ds = Math.abs(rs - s);
        if (dq > dr && dq > ds) rq = -rr - rs;
        else if (dr > ds) rr = -rq - rs;
        return new double[]{rq, rr};
    }

    /** Distances to the nearest and second-nearest random point (cellular noise). */
    private static double[] worley(int x, int y, int cell, int seed) {
        int cx = Math.floorDiv(x, cell), cy = Math.floorDiv(y, cell);
        double d1 = 1e9, d2 = 1e9;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                int gx = cx + i, gy = cy + j;
                double fx = (gx + noise(gx, gy, seed)) * cell, fy = (gy + noise(gx, gy, seed + 1)) * cell;
                double d = Math.hypot(x - fx, y - fy);
                if (d < d1) {
                    d2 = d1;
                    d1 = d;
                } else if (d < d2) {
                    d2 = d;
                }
            }
        }
        return new double[]{d1, d2};
    }

    /** Smooth value noise, a few octaves, 0..1. */
    private static double fbm(double x, double y, int seed) {
        double sum = 0, amp = 0.5, f = 1, norm = 0;
        for (int o = 0; o < 4; o++) {
            sum += amp * smooth(x * f, y * f, seed + o * 17);
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }

    private static double smooth(double x, double y, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        double sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
        double a = noise(x0, y0, seed), b = noise(x0 + 1, y0, seed), c = noise(x0, y0 + 1, seed), d = noise(x0 + 1, y0 + 1, seed);
        return (a + (b - a) * sx) * (1 - sy) + (c + (d - c) * sx) * sy;
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
