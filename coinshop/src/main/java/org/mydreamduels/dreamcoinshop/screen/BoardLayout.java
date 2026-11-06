package org.mydreamduels.dreamcoinshop.screen;

import java.util.List;
import java.util.UUID;

/**
 * Draws a leaderboard screen: a title with a trophy, one row per player (rank badge, face,
 * country flag, name, value) and a footer (page dots on a cycling screen, or a hint). Pure
 * drawing - no Bukkit calls - so it can be tested and previewed.
 */
public final class BoardLayout {

    public enum Size {
        SMALL(2, 2), MEDIUM(3, 2), BIG(3, 3), HUGE(4, 3);

        public final int mapsWide, mapsHigh;

        Size(int w, int h) {
            this.mapsWide = w;
            this.mapsHigh = h;
        }

        public int width() {
            return mapsWide * 128;
        }

        public int height() {
            return mapsHigh * 128;
        }

        public static Size parse(String s, Size fallback) {
            if (s == null) return fallback;
            for (Size v : values()) if (v.name().equalsIgnoreCase(s)) return v;
            return fallback;
        }
    }

    /** One leaderboard row. face / flag: 8x8 RGB (-1 = transparent) or null. */
    public record Row(int rank, UUID id, String name, String value, int[] face, int[] flag) {
    }

    /** What to draw besides the rows. */
    public record Style(String title, String subtitle, int accent, int background, boolean heads, boolean flags,
                        boolean medals, boolean stripes, String footer, int page, int pages, int maxRows,
                        int dotOn, int dotOff) {
        /** Old shape: green current-page dot, grey others. */
        public Style(String title, String subtitle, int accent, int background, boolean heads, boolean flags,
                     boolean medals, boolean stripes, String footer, int page, int pages, int maxRows) {
            this(title, subtitle, accent, background, heads, flags, medals, stripes, footer, page, pages, maxRows, 0x55FF55, -1);
        }
    }

    public record Box(int x, int y, int w, int h) {
        public boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private static final int GOLD = 0xFFC23D, SILVER = 0xC9D1DC, BRONZE = 0xD08A4A, MUTED = 0x9AA3B2, TEXT = 0xE6EAF0;
    public static final int ROW_H = 22;
    public static final int DEFAULT_BACKGROUND = 0x141824;

    private BoardLayout() {
    }

    static int header(Size s) {
        return 46;
    }

    static int footer(Size s) {
        return 18;
    }

    /** How many rows fit (limited by maxRows when > 0). */
    public static int capacity(Size s, int maxRows) {
        int fit = (s.height() - header(s) - footer(s) - 4) / ROW_H;
        return maxRows > 0 ? Math.max(1, Math.min(fit, maxRows)) : fit;
    }

    public static Box[] rowBoxes(Size s, int rows) {
        Box[] out = new Box[rows];
        int y0 = header(s);
        for (int i = 0; i < rows; i++) out[i] = new Box(8, y0 + i * ROW_H, s.width() - 16, ROW_H - 2);
        return out;
    }

    public static int rowAt(Size s, int rows, int px, int py) {
        Box[] b = rowBoxes(s, rows);
        for (int i = 0; i < b.length; i++) if (b[i].contains(px, py)) return i;
        return -1;
    }

    static int lighter(int rgb, double t) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += (int) ((255 - r) * t);
        g += (int) ((255 - g) * t);
        b += (int) ((255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** hover = highlighted row (-1 none), grow = it pops out a little, backdrop may be null. */
    public static Canvas render(Size size, Style st, List<Row> rows, int hover, boolean grow, byte[] backdrop) {
        int W = size.width(), H = size.height();
        Canvas c = new Canvas(W, H);
        c.fill(Palette.TRANSPARENT);
        c.roundRect(0, 0, W, H, Palette.of(st.background()));
        if (backdrop != null && backdrop.length == W * H) {
            byte[] p = c.pixels;
            for (int i = 0; i < p.length; i++) if (p[i] != Palette.TRANSPARENT) p[i] = backdrop[i];
        }
        c.border(1, 1, W - 2, H - 2, 1, Palette.of(st.accent()));
        c.border(3, 3, W - 6, H - 6, 1, Palette.of(mix(st.accent(), st.background(), 0.6)));

        // title: trophy + text, centred together
        String title = "§l" + st.title();
        int scale = Canvas.textWidth(title, 2) + 22 > W - 16 ? 1 : 2;
        int tw = Canvas.textWidth(title, scale), total = 16 + 6 + tw, x0 = (W - total) / 2;
        int ty = scale == 2 ? 9 : 13;
        c.blit(BoardArt.toMap(BoardArt.trophy(), 16, 16), 16, 16, x0, ty + (7 * scale) / 2 - 8);
        c.text(title, x0 + 22, ty, scale, st.accent(), true, Canvas.Align.LEFT);
        if (st.subtitle() != null && !st.subtitle().isEmpty()) {
            c.text(st.subtitle(), W / 2, 31, 1, MUTED, false, Canvas.Align.CENTER);
        }

        int n = Math.min(rows.size(), capacity(size, st.maxRows()));
        Box[] boxes = rowBoxes(size, n);
        if (n == 0) {
            c.text("No players yet", W / 2, header(size) + 30, 1, MUTED, false, Canvas.Align.CENTER);
        }
        for (int i = 0; i < n; i++) if (i != hover) drawRow(c, st, rows.get(i), boxes[i], i, false, false);
        if (hover >= 0 && hover < n) drawRow(c, st, rows.get(hover), boxes[hover], hover, true, grow);

        // footer: page dots (cycling screen) and/or a hint
        int fy = H - footer(size) + 2;
        if (st.pages() > 1) {
            int dot = 5, gap = 4, dw = st.pages() * dot + (st.pages() - 1) * gap + 2, dx = (W - dw) / 2;
            int off = st.dotOff() >= 0 ? st.dotOff() : mix(MUTED, st.background(), 0.5);
            for (int i = 0, x = dx; i < st.pages(); i++) {
                // the current page's dot is a little wider and in its own colour (green by default)
                int w = i == st.page() ? dot + 2 : dot;
                c.roundRect(x, fy + 3, w, dot, Palette.of(i == st.page() ? st.dotOn() : off));
                x += w + gap;
            }
        } else if (st.footer() != null && !st.footer().isEmpty()) {
            c.text(st.footer(), W / 2, fy + 1, 1, MUTED, false, Canvas.Align.CENTER);
        }
        return c;
    }

    private static void drawRow(Canvas c, Style st, Row r, Box box, int index, boolean hot, boolean grow) {
        int g = grow ? 2 : 0;
        Box b = new Box(box.x() - g * 2, box.y() - g, box.w() + g * 4, box.h() + g * 2);
        int base = st.stripes() && index % 2 == 1 ? lighter(st.background(), 0.10) : lighter(st.background(), 0.05);
        int medal = r.rank() <= 0 ? -1 : r.rank() == 1 ? GOLD : r.rank() == 2 ? SILVER : r.rank() == 3 ? BRONZE : -1;
        c.roundRect(b.x(), b.y(), b.w(), b.h(), Palette.of(hot ? lighter(st.background(), 0.20) : base));
        if (hot) c.border(b.x(), b.y(), b.w(), b.h(), 1, Palette.of(0xFFFFFF));
        else if (st.medals() && medal >= 0) c.border(b.x(), b.y(), b.w(), b.h(), 1, Palette.of(mix(medal, st.background(), 0.35)));

        int cy = b.y() + b.h() / 2;
        int x = b.x() + 4;
        // rank: coloured badge for the top 3, plain number for the rest (rank 0 = a list without ranks)
        if (r.rank() > 0) {
            String rank = String.valueOf(r.rank());
            if (st.medals() && medal >= 0) {
                c.roundRect(x, cy - 7, 18, 14, Palette.of(medal));
                c.text("§l" + rank, x + 9, cy - 3, 1, 0x1A1A1A, false, Canvas.Align.CENTER);
            } else {
                c.text(rank, x + 9, cy - 3, 1, MUTED, false, Canvas.Align.CENTER);
            }
            x += 24;
        } else {
            x += 4;
        }
        if (st.heads()) {
            int[] face = r.face() != null ? r.face() : BoardArt.defaultFace();
            // 8x8 faces are drawn twice as big, 16x16 pictures (shop icons) as they are
            c.blit(face.length == 256 ? BoardArt.toMap(face, 16, 16) : BoardArt.toMap(face, 8, 16), 16, 16, x, cy - 8);
            x += 22;
        }
        if (st.flags()) {
            // the space is kept even without a flag, so every name starts at the same place
            if (r.flag() != null) c.blit(flag(r.flag()), 16, 12, x, cy - 6);
            x += 20;
        }
        // value on the right, name gets the space that's left
        String value = r.value();
        int vw = Canvas.textWidth(value, 1);
        int vx = b.x() + b.w() - 6;
        c.text(value, vx, cy - 3, 1, st.accent(), true, Canvas.Align.RIGHT);
        int room = vx - vw - 8 - x;
        String name = fit(r.name(), room);
        c.text(name, x, cy - 3, 1, hot ? 0xFFFFFF : st.medals() && r.rank() == 1 ? GOLD : TEXT, true, Canvas.Align.LEFT);
    }

    /** The 8x8 flag picture stretched to 16x12 (flags are wider than tall) with a dark outline. */
    private static byte[] flag(int[] src) {
        int w = 16, h = 12;
        byte[] out = new byte[w * h];
        byte edge = Palette.of(0x101010);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean border = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                if (border) {
                    out[y * w + x] = edge;
                    continue;
                }
                int c = src[Math.min(7, (y - 1) * 8 / (h - 2)) * 8 + Math.min(7, (x - 1) * 8 / (w - 2))];
                out[y * w + x] = c < 0 ? Palette.of(0x303030) : Palette.of(c);
            }
        }
        return out;
    }

    private static String fit(String s, int room) {
        if (s == null) return "?";
        if (Canvas.textWidth(s, 1) <= room) return s;
        String t = s;
        while (t.length() > 1 && Canvas.textWidth(t + "..", 1) > room) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
