package org.mydreamduels.dreamcoinshop.screen;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.GlowItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * One leaderboard screen: invisible glow item frames holding maps. Every viewer gets their own picture
 * (their hover highlight + cursor), drawn by a contextual map renderer from a shared base image.
 * Frames are never saved with the world - they're placed again whenever players come close.
 */
public final class BoardScreen {

    static final String[] CURSOR = {
            "X       ", "XX      ", "X.X     ", "X..X    ", "X...X   ", "X....X  ", "X.....X ", "X..XXXXX", "X.X     ", "XX      "};

    final ScreenHost plugin;
    final String name;
    String world;
    Geometry geo;
    BoardLayout.Size size;
    int background;
    /** Per-screen settings; null / -1 = use the default from config.yml. */
    Boolean cursor;
    /** Per-screen hover pop-out; null = screens.hover-grow from config.yml. */
    Boolean grow;
    /** Backdrop design or "image:<file.png>"; null = screens.backdrop from config.yml. */
    String backdrop;
    private volatile byte[] backdropPixels;
    private volatile String backdropKey;
    double viewRange = -1, clickRange = -1;
    /** A stat name (KILLS, MONEY...) or "cycle" (rotates through cycleStats). */
    volatile String stat = "cycle";
    /** Stats a cycling screen shows; null = screens.cycle.stats from config.yml. */
    java.util.List<String> cycleStats;
    /** Seconds per page of a cycling screen; -1 = config default. */
    int cycleSeconds = -1;
    /** Rows shown; 0 = as many as fit (screens.rows). */
    int maxRows = -1;
    /** Custom title (null = per-stat title from config) and accent colour (-1 = per-stat colour). */
    String title;
    int accent = -1;
    /** Page-change effect of a cycling screen (Transition id, "random" or "off"); null = config default. */
    String transition;
    private volatile String signature = "";
    // page-change effect in progress: the old page's picture, when it started, which effect
    private volatile byte[] prevBase;
    private volatile long transStart;
    private volatile String transEffect;
    private final Object transLock = new Object();
    private int transCachedStep = -1;
    private byte[] transCached;
    private volatile String lastPageKey;
    private volatile int lastPage = -1, lastPages;
    int[] mapIds;
    MapView[] views;
    volatile boolean ready;
    private final NamespacedKey tagKey;
    private AtomicReferenceArray<GlowItemFrame> frames;
    private long[] spawnAt;

    /** Shared picture (same for everyone), rebuilt every couple of seconds on the global thread. */
    private volatile byte[] base;
    private volatile int baseVersion;
    /** Hovered pictures, shared by every viewer of the current base picture. */
    private final Map<String, byte[]> hoverCache = new java.util.HashMap<>();
    private int hoverCacheVersion = -1;
    private volatile List<BoardLayout.Row> rows = List.of();
    private volatile BoardLayout.Style style;

    static final class View {
        int hover = -1, cursorX = -1, cursorY = -1;
        int builtBase = -1, builtHover = -2, builtTrans = -1;
        byte[] frame;
        int[] version, sent, drawn;
        MapCanvas[] canvas;
        /** What the player's client has per cell (sending only what changed); null = unknown. */
        byte[][] last;
        /** When to send every cell whole once more (a safety net right after the screen appears). */
        long fullAgain;
        long lastClick;
    }

    private final Map<UUID, View> viewers = new ConcurrentHashMap<>();

    BoardScreen(ScreenHost plugin, String name, String world, Geometry geo, BoardLayout.Size size, int background, int[] mapIds) {
        this.plugin = plugin;
        this.name = name;
        this.world = world;
        this.geo = geo;
        this.size = size;
        this.background = background;
        this.tagKey = new NamespacedKey(plugin.plugin(), "screen");
        int cells = size.mapsWide * size.mapsHigh;
        this.mapIds = mapIds != null && mapIds.length == cells ? mapIds : filled(cells);
        this.frames = new AtomicReferenceArray<>(cells);
        this.spawnAt = new long[cells];
    }

    private static int[] filled(int n) {
        int[] a = new int[n];
        Arrays.fill(a, -1);
        return a;
    }

    int cells() {
        return size.mapsWide * size.mapsHigh;
    }

    public String name() {
        return name;
    }

    /** The hovered world row pops out a little (config: screens.hover-grow). */
    boolean hoverGrow() {
        return grow != null ? grow : plugin.getConfig().getBoolean("screens.hover-grow", true);
    }

    boolean cursorOn() {
        return cursor != null ? cursor : plugin.getConfig().getBoolean("screens.cursor", true);
    }

    double viewRange() {
        double v = viewRange > 0 ? viewRange : plugin.getConfig().getDouble("screens.view-range", 40);
        return Math.max(8, Math.min(128, v));
    }

    double clickRange() {
        double v = clickRange > 0 ? clickRange
                : plugin.getConfig().getDouble("screens.click-range", plugin.getConfig().getDouble("screens.touch-range", 8));
        return Math.max(2, Math.min(32, v));
    }

    double cx() {
        return geo.x() + 0.5 + geo.rightX() * (geo.width() - 1) / 2.0;
    }

    double cy() {
        return geo.y() + 0.5 - (geo.height() - 1) / 2.0;
    }

    double cz() {
        return geo.z() + 0.5 + geo.rightZ() * (geo.width() - 1) / 2.0;
    }

    // ------------------------------------------------------------ maps

    /** Global thread: find or create the map views and hook the renderers in. */
    void attachMaps() {
        World base = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        MapView[] v = new MapView[cells()];
        for (int i = 0; i < v.length; i++) {
            MapView view = mapIds[i] >= 0 ? Bukkit.getMap(mapIds[i]) : null;
            if (view == null) {
                if (base == null) return;
                view = Bukkit.createMap(base);
                mapIds[i] = view.getId();
            }
            for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
            try {
                view.setScale(MapView.Scale.CLOSEST);
                view.setTrackingPosition(false);
                view.setUnlimitedTracking(false);
                view.setLocked(true);
            } catch (RuntimeException ignored) {
                // cosmetic
            }
            final int cell = i;
            view.addRenderer(new MapRenderer(true) {
                @Override
                public void render(MapView map, MapCanvas canvas, Player player) {
                    renderCell(cell, canvas, player);
                }
            });
            v[i] = view;
        }
        views = v;
        ready = true;
    }

    void detachMaps() {
        MapView[] v = views;
        ready = false;
        if (v == null) return;
        for (MapView view : v) {
            for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
        }
    }

    // ------------------------------------------------------------ frames

    /** Global thread: place missing frames (only called while a player is near). */
    void ensureFrames() {
        World w = Bukkit.getWorld(world);
        MapView[] v = views;
        if (w == null || v == null) return;
        long now = System.currentTimeMillis();
        for (int i = 0; i < cells(); i++) {
            GlowItemFrame f = frames.get(i);
            if (f != null && f.isValid()) continue;
            if (now - spawnAt[i] < 5000) continue;
            spawnAt[i] = now;
            int col = i % geo.width(), row = i / geo.width();
            Location at = new Location(w, geo.cellBlockX(col), geo.cellBlockY(row), geo.cellBlockZ(col));
            ItemStack map = new ItemStack(Material.FILLED_MAP);
            if (map.getItemMeta() instanceof MapMeta meta) {
                meta.setMapView(v[i]);
                map.setItemMeta(meta);
            }
            final int cell = i;
            final BlockFace facing = geo.facing();
            final Geometry placedFor = geo;
            Tasks.region(at, () -> {
                try {
                    GlowItemFrame frame = w.spawn(at, GlowItemFrame.class, fr -> {
                        fr.setPersistent(false);
                        fr.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, name + ":" + cell);
                        fr.setFacingDirection(facing, true);
                        fr.setItem(map, false);
                        fr.setRotation(Rotation.NONE);
                        fr.setVisible(false);
                        fr.setFixed(true);
                        fr.setInvulnerable(true);
                        fr.setSilent(true);
                        fr.setItemDropChance(0f);
                    });
                    if (geo != placedFor || !ready) {
                        frame.remove(); // moved/removed meanwhile
                        return;
                    }
                    GlowItemFrame old = frames.getAndSet(cell, frame);
                    if (old != null && old != frame) Tasks.entity(old, old::remove);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Screen " + name + ": couldn't place a frame (" + t.getMessage() + ")");
                }
            });
        }
    }

    void removeFrames() {
        for (int i = 0; i < cells(); i++) {
            GlowItemFrame f = frames.getAndSet(i, null);
            if (f != null) Tasks.entity(f, f::remove);
            spawnAt[i] = 0;
        }
    }

    /** New position / size: frames go and come back at the new place. */
    void reshape(Geometry g, BoardLayout.Size s, String w) {
        removeFrames();
        boolean sizeChanged = s != size;
        geo = g;
        world = w;
        if (sizeChanged) {
            int[] old = mapIds;
            size = s;
            mapIds = filled(cells());
            System.arraycopy(old, 0, mapIds, 0, Math.min(old.length, mapIds.length));
            frames = new AtomicReferenceArray<>(cells());
            spawnAt = new long[cells()];
            detachMaps();
        }
        viewers.clear();
        base = null;
    }

    boolean isOurs(org.bukkit.entity.Entity e) {
        if (!(e instanceof GlowItemFrame)) return false;
        String tag = e.getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
        return tag != null && tag.startsWith(name + ":");
    }

    static String tagOf(ScreenHost plugin, org.bukkit.entity.Entity e) {
        if (!(e instanceof GlowItemFrame)) return null;
        return e.getPersistentDataContainer().get(new NamespacedKey(plugin.plugin(), "screen"), PersistentDataType.STRING);
    }

    // ------------------------------------------------------------ picture

    /** Global thread: refresh the numbers (players, border) and redraw the shared picture. */
    String backdropStyle() {
        return backdrop != null ? backdrop : plugin.getConfig().getString("screens.backdrop", "stars");
    }

    // ------------------------------------------------------------ what it shows

    /** Leaderboards this screen shows (one, or the cycle list) - built-in or custom. */
    java.util.List<ScreenStat> stats() {
        java.util.List<ScreenStat> out = new java.util.ArrayList<>();
        if (!"cycle".equalsIgnoreCase(stat)) {
            var b = ScreenStat.parse(stat);
            if (b != null) out.add(b);
        } else {
            java.util.List<String> list = cycleStats != null ? cycleStats : plugin.getConfig().getStringList("screens.cycle.stats");
            for (String n : list) {
                var b = ScreenStat.parse(n);
                if (b != null && !out.contains(b)) out.add(b);
            }
        }
        if (out.isEmpty()) out.add(ScreenStat.SHOP);
        return out;
    }

    int cycleSeconds() {
        return Math.max(3, cycleSeconds > 0 ? cycleSeconds : plugin.getConfig().getInt("screens.cycle.seconds", 8));
    }

    int maxRows() {
        return maxRows >= 0 ? maxRows : Math.max(0, plugin.getConfig().getInt("screens.rows", 0));
    }

    /** Page shown right now - from the clock, so every viewer and every screen stays in step. */
    int page(int pages) {
        return pages <= 1 ? 0 : (int) ((System.currentTimeMillis() / (cycleSeconds() * 1000L)) % pages);
    }

    /** This screen's page-change effect: an effect id, "random" or "off". */
    String transitionStyle() {
        if (!plugin.getConfig().getBoolean("screens.cycle.transition.enabled", true)) return "off";
        String t = Transition.parse(transition != null ? transition : plugin.getConfig().getString("screens.cycle.transition.effect", "page"));
        return t == null ? "page" : t;
    }

    long transitionMillis() {
        return Math.max(200, Math.min(3000, plugin.getConfig().getLong("screens.cycle.transition.duration-ms", 700)));
    }

    /** A cycling screen whose next page is due (global thread, cheap: no lookups). */
    boolean pageDue() {
        int n = lastPages;
        return n > 1 && page(n) != lastPage;
    }

    /** Step of the page effect running now (one per 100 ms), or -1. */
    int transitionStep() {
        byte[] from = prevBase;
        if (from == null) return -1;
        long el = System.currentTimeMillis() - transStart;
        if (el >= transitionMillis()) {
            prevBase = null;
            return -1;
        }
        return (int) (el / 100);
    }

    public boolean inTransition() {
        return transitionStep() >= 0;
    }

    /** The mixed picture for one step - worked out once, shared by every viewer. */
    private byte[] transitionFrame(int step) {
        synchronized (transLock) {
            if (step == transCachedStep && transCached != null) return transCached;
            byte[] from = prevBase, to = base;
            if (from == null || to == null || from.length != to.length) return null;
            BoardLayout.Style sty = style;
            byte accent = Palette.of(sty == null ? 0xFFFFFF : sty.accent());
            double steps = Math.max(1, transitionMillis() / 100.0);
            transCached = Transition.frame(transEffect, from, to, size.width(), size.height(), (step + 1) / steps, accent);
            transCachedStep = step;
            return transCached;
        }
    }

    /** The backdrop in map colours, built once per style / colour / size. */
    byte[] backdropPixels() {
        String style = backdropStyle();
        String key = style + "|" + background + "|" + size;
        if (!key.equals(backdropKey)) {
            java.awt.image.BufferedImage img = null;
            String design = style;
            if (style.toLowerCase(java.util.Locale.ROOT).startsWith("image:")) {
                img = plugin.getScreens().image(style.substring(6));
                design = "image";
            }
            backdropPixels = Backdrop.build(design, size.width(), size.height(), background, img);
            backdropKey = key;
        }
        return backdropPixels;
    }

    /** Forget the cached backdrop (style, colour or the image file changed). */
    void resetBackdrop() {
        backdropKey = null;
    }

    /**
     * Global thread: fresh rows (top players, faces, flags, page of a cycling screen) and the
     * shared picture - only redrawn when something on it actually changed (force = always).
     */
    void rebuild(boolean force) {
        var types = stats();
        int page = page(types.size());
        var type = types.get(Math.min(page, types.size() - 1));
        CoinScreens mgr = plugin.getScreens();
        BoardLayout.Style st = mgr.style(this, type, page, types.size());
        List<BoardLayout.Row> newRows = mgr.rows(type, BoardLayout.capacity(size, st.maxRows()), st.heads(), st.flags());
        StringBuilder sig = new StringBuilder(type.key()).append(page).append('|').append(st.title()).append(st.accent())
                .append(background).append(backdropStyle());
        for (BoardLayout.Row r : newRows) {
            sig.append(r.id()).append(r.value()).append(r.name()).append(r.face() != null).append(r.flag() != null);
        }
        String now = sig.toString();
        if (!force && now.equals(signature) && base != null) return;
        signature = now;
        byte[] old = base;
        String effect = types.size() > 1 ? transitionStyle() : "off";
        boolean turned = lastPageKey != null && !lastPageKey.equals(type.key());
        lastPageKey = type.key();
        lastPage = page;
        lastPages = types.size();
        rows = newRows;
        style = st;
        byte[] fresh = BoardLayout.render(size, st, newRows, -1, false, backdropPixels()).pixels;
        if (turned && old != null && old.length == fresh.length && !"off".equals(effect)) {
            synchronized (transLock) {
                transCachedStep = -1;
                transCached = null;
                transEffect = Transition.pick(effect);
                transStart = System.currentTimeMillis();
                prevBase = old;
            }
        }
        base = fresh;
        baseVersion++;
    }

    Geometry.Hit look(Player p, double range) {
        Location eye = p.getEyeLocation();
        if (eye.getWorld() == null || !eye.getWorld().getName().equals(world)) return null;
        Vector d = eye.getDirection();
        return geo.hit(eye.getX(), eye.getY(), eye.getZ(), d.getX(), d.getY(), d.getZ(), range);
    }

    /** Player's thread: update hover/cursor and push changed maps. */
    void tick(Player p, double range) {
        MapView[] v = views;
        byte[] b = base;
        if (!ready || v == null || b == null) return;
        View st = viewers.computeIfAbsent(p.getUniqueId(), k -> {
            View n = new View();
            n.version = new int[cells()];
            n.sent = new int[cells()];
            n.drawn = new int[cells()];
            n.canvas = new MapCanvas[cells()];
            n.last = new byte[cells()][];
            n.fullAgain = System.currentTimeMillis() + 2000;
            Arrays.fill(n.version, 1);
            return n;
        });
        List<Integer> send = new ArrayList<>(2);
        synchronized (st) {
            // two seconds after the screen appears for this player: everything whole once more,
            // then only changes - so a screen is always fully loaded before it gets updated bit by bit
            if (st.fullAgain > 0 && System.currentTimeMillis() >= st.fullAgain) {
                st.fullAgain = 0;
                for (int c = 0; c < cells(); c++) {
                    st.version[c]++;
                    st.last[c] = null;
                }
            }
            Geometry.Hit hit = look(p, range);
            BoardLayout.Style sty = style;
            int step = transitionStep();
            byte[] mixed = step >= 0 ? transitionFrame(step) : null;
            int shown = sty == null ? 0 : Math.min(rows.size(), BoardLayout.capacity(size, sty.maxRows()));
            int hover = hit == null || mixed != null ? -1 : BoardLayout.rowAt(size, shown, hit.px(), hit.py());
            if (hover != st.hover && hover >= 0) CoinScreens.sound(plugin, p, "hover");
            st.hover = hover;
            if (mixed != null) {
                // page effect running: everyone sees the same mixed picture, no hover
                if (st.builtTrans != step || st.frame == null) {
                    for (int c = 0; c < cells(); c++) {
                        if (st.frame == null || differs(st.frame, mixed, c)) st.version[c]++;
                    }
                    st.frame = mixed;
                    st.builtTrans = step;
                    st.builtBase = -1;
                }
            } else if (st.builtBase != baseVersion || st.builtHover != hover || st.frame == null) {
                st.builtTrans = -1;
                final BoardLayout.Style hs = sty;
                byte[] nw = hover < 0 || sty == null ? b
                        : hoverFrame(String.valueOf(hover), () -> BoardLayout.render(size, hs, rows, hover, hoverGrow(), backdropPixels()).pixels);
                for (int c = 0; c < cells(); c++) {
                    if (st.frame == null || differs(st.frame, nw, c)) st.version[c]++;
                }
                st.frame = nw;
                st.builtBase = baseVersion;
                st.builtHover = hover;
            }
            boolean showCursor = hit != null && cursorOn();
            int cx = showCursor ? hit.px() : -1, cy = showCursor ? hit.py() : -1;
            if (cx != st.cursorX || cy != st.cursorY) {
                bump(st, st.cursorX, st.cursorY);
                bump(st, cx, cy);
                st.cursorX = cx;
                st.cursorY = cy;
            }
            for (int c = 0; c < cells(); c++) {
                if (st.sent[c] != st.version[c]) {
                    st.sent[c] = st.version[c];
                    send.add(c);
                }
            }
        }
        for (int c : send) {
            // only the pixels that changed (a cursor step is a few pixels, not a whole map)
            if (MapPackets.available() && plugin.getConfig().getBoolean("screens.partial-updates", true)) {
                byte[] now, before;
                synchronized (st) {
                    now = cellPixels(st, c);
                    before = st.last[c];
                    st.last[c] = now;
                }
                if (MapPackets.sendDiff(p, v[c].getId(), before, now)) continue;
                synchronized (st) {
                    st.last[c] = null;
                }
            }
            p.sendMap(v[c]);
        }
    }

    private boolean differs(byte[] a, byte[] b, int cell) {
        int W = size.width(), col = cell % geo.width(), row = cell / geo.width();
        for (int y = 0; y < 128; y++) {
            int from = (row * 128 + y) * W + col * 128;
            if (!Arrays.equals(a, from, from + 128, b, from, from + 128)) return true;
        }
        return false;
    }

    private void bump(View st, int x, int y) {
        if (x < 0 || y < 0) return;
        int c0 = x / 128, c1 = Math.min(geo.width() - 1, (x + 8) / 128), r0 = y / 128, r1 = Math.min(geo.height() - 1, (y + 10) / 128);
        for (int r = r0; r <= r1; r++) for (int c = c0; c <= c1; c++) st.version[r * geo.width() + c]++;
    }

    void forget(UUID id) {
        viewers.remove(id);
    }

    /** Sentinel: the click hit the screen, but not a player row. */
    static final UUID NO_ROW = new UUID(0, 0);

    /** The player row that was clicked: null = not this screen, NO_ROW = screen but no row. Player's thread. */
    UUID clickedPlayer(Player p, double range) {
        Geometry.Hit hit = look(p, range);
        if (hit == null) return null;
        BoardLayout.Style sty = style;
        int shown = sty == null ? 0 : Math.min(rows.size(), BoardLayout.capacity(size, sty.maxRows()));
        int row = BoardLayout.rowAt(size, shown, hit.px(), hit.py());
        if (row < 0) return NO_ROW;
        View st = viewers.get(p.getUniqueId());
        if (st != null) {
            synchronized (st) {
                long now = System.currentTimeMillis();
                if (now - st.lastClick < 400) return NO_ROW;
                st.lastClick = now;
            }
        }
        return rows.get(row).id();
    }

    /** The picture with a row hovered: drawn once per base picture, then shared. */
    private byte[] hoverFrame(String key, java.util.function.Supplier<byte[]> draw) {
        synchronized (hoverCache) {
            if (hoverCacheVersion != baseVersion) {
                hoverCache.clear();
                hoverCacheVersion = baseVersion;
            }
            byte[] got = hoverCache.get(key);
            if (got == null) {
                got = draw.get();
                if (hoverCache.size() < 64) hoverCache.put(key, got);
            }
            return got;
        }
    }

    /** The picture of one cell for this viewer: their frame plus their cursor. */
    private byte[] cellPixels(View st, int cell) {
        byte[] out = new byte[128 * 128];
        int W = size.width(), ox = (cell % geo.width()) * 128, oy = (cell / geo.width()) * 128;
        for (int y = 0; y < 128; y++) System.arraycopy(st.frame, (oy + y) * W + ox, out, y * 128, 128);
        if (st.cursorX >= 0) {
            byte outline = Palette.of(0x000000), fill = Palette.of(st.hover >= 0 ? 0xFFFF55 : 0xFFFFFF);
            int lx = st.cursorX - ox, ly = st.cursorY - oy;
            for (int sy = 0; sy < CURSOR.length; sy++) {
                int y = ly + sy;
                if (y < 0 || y >= 128) continue;
                String line = CURSOR[sy];
                for (int sx = 0; sx < line.length(); sx++) {
                    int x = lx + sx;
                    if (x < 0 || x >= 128) continue;
                    char ch = line.charAt(sx);
                    if (ch == 'X') out[y * 128 + x] = outline;
                    else if (ch == '.') out[y * 128 + x] = fill;
                }
            }
        }
        return out;
    }

    /**
     * The server asks for a cell (when a player starts seeing the screen, every half second for
     * frames, or Player#sendMap). The canvas always gets the newest picture, so whatever the
     * server sends is right - it only sends the pixels that changed in it.
     */
    @SuppressWarnings("deprecation")
    void renderCell(int cell, MapCanvas canvas, Player player) {
        View st = viewers.get(player.getUniqueId());
        if (st == null) return;
        synchronized (st) {
            if (st.frame == null || cell >= st.version.length) return;
            if (st.drawn[cell] == st.version[cell] && st.canvas[cell] == canvas) return;
            st.drawn[cell] = st.version[cell];
            st.canvas[cell] = canvas;
            byte[] px = cellPixels(st, cell);
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) canvas.setPixel(x, y, px[y * 128 + x]);
            }
        }
    }

    // ------------------------------------------------------------ storage

    void save(ConfigurationSection s) {
        s.set("world", world);
        s.set("x", geo.x());
        s.set("y", geo.y());
        s.set("z", geo.z());
        s.set("facing", geo.facing().name());
        s.set("size", size.name().toLowerCase(java.util.Locale.ROOT));
        s.set("background", String.format("#%06X", background));
        s.set("backdrop", backdrop);
        s.set("hover-grow", grow);
        s.set("cursor", cursor);
        s.set("view-range", viewRange > 0 ? viewRange : null);
        s.set("stat", stat);
        s.set("cycle-stats", cycleStats);
        s.set("cycle-seconds", cycleSeconds > 0 ? cycleSeconds : null);
        s.set("rows", maxRows >= 0 ? maxRows : null);
        s.set("title", title);
        s.set("accent", accent >= 0 ? String.format("#%06X", accent) : null);
        s.set("transition", transition);
        s.set("click-range", clickRange > 0 ? clickRange : null);
        List<Integer> ids = new ArrayList<>();
        for (int id : mapIds) ids.add(id);
        s.set("maps", ids);
    }

    static BoardScreen load(ScreenHost plugin, String name, ConfigurationSection s) {
        BoardLayout.Size size = BoardLayout.Size.parse(s.getString("size"), BoardLayout.Size.BIG);
        BlockFace face;
        try {
            face = BlockFace.valueOf(s.getString("facing", "SOUTH").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            face = BlockFace.SOUTH;
        }
        if (!Geometry.validFacing(face)) face = BlockFace.SOUTH;
        Geometry g = new Geometry(s.getInt("x"), s.getInt("y"), s.getInt("z"), face, size.mapsWide, size.mapsHigh);
        List<Integer> raw = s.getIntegerList("maps");
        int[] ids = new int[raw.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = raw.get(i);
        int bg = Colors.rgb(s.getString("background", ""));
        if (bg < 0) bg = BoardLayout.DEFAULT_BACKGROUND;
        BoardScreen r = new BoardScreen(plugin, name, s.getString("world", "world"), g, size, bg, ids);
        if (s.isBoolean("cursor")) r.cursor = s.getBoolean("cursor");
        r.backdrop = s.getString("backdrop", null);
        if (s.isBoolean("hover-grow")) r.grow = s.getBoolean("hover-grow");
        r.viewRange = s.getDouble("view-range", -1);
        r.clickRange = s.getDouble("click-range", -1);
        r.stat = s.getString("stat", "cycle");
        r.cycleStats = s.isList("cycle-stats") ? s.getStringList("cycle-stats") : null;
        r.cycleSeconds = s.getInt("cycle-seconds", -1);
        r.maxRows = s.getInt("rows", -1);
        r.title = s.getString("title", null);
        r.accent = s.isString("accent") ? Colors.rgb(s.getString("accent")) : -1;
        r.transition = s.getString("transition", null);
        return r;
    }
}
