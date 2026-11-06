package org.mydreamduels.dreamcoinshop.screen;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** All leaderboard screens: screens.yml, the update timers, and clicks / protection for their frames. */
public final class CoinScreens implements Listener {

    private final ScreenHost plugin;
    private final File file;
    private final Map<String, BoardScreen> screens = new ConcurrentHashMap<>();
    private Tasks.Task slow, fast;

    public CoinScreens(ScreenHost plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "screens.yml");
    }



    public Collection<BoardScreen> all() {
        return screens.values();
    }

    public BoardScreen get(String name) {
        return name == null ? null : screens.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (BoardScreen s : screens.values()) out.add(s.name);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    // ------------------------------------------------------------ lifecycle

    public void load() {
        backgroundsFolder(); // so buyers find the folder (with its readme) right away
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("screens");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(name);
                if (s != null) screens.put(name.toLowerCase(Locale.ROOT), BoardScreen.load(plugin, name, s));
            }
        }
        Tasks.global(() -> {
            boolean changed = false;
            for (BoardScreen s : screens.values()) {
                int[] before = s.mapIds.clone();
                s.attachMaps();
                if (!java.util.Arrays.equals(before, s.mapIds)) changed = true;
            }
            if (changed) save();
        });
        // numbers + frames every second, hover/cursor every 2 ticks for players right in front
        slow = Tasks.globalTimer(this::slowTick, 20L, 20L);
        fast = Tasks.globalTimer(this::fastTick, 5L, 2L);
    }

    public void reload() {
        images.clear(); // pick up changed picture files
        for (BoardScreen s : screens.values()) {
            s.resetBackdrop();
            s.rebuild(true);
        }
    }

    // ------------------------------------------------------------ background pictures

    /** Loaded pictures; an empty Optional = missing/unreadable (so the warning shows once). */
    private final Map<String, java.util.Optional<java.awt.image.BufferedImage>> images = new ConcurrentHashMap<>();

    /** plugins/Dreamcoinshop/backgrounds/ (made on startup with a short readme). */
    public File backgroundsFolder() {
        File dir = new File(plugin.getDataFolder(), "backgrounds");
        if (!dir.exists() && dir.mkdirs()) {
            try {
                java.nio.file.Files.writeString(new File(dir, "README.txt").toPath(),
                        "Put PNG or JPG pictures here, then: /coinscreen backdrop <screen> image:<file.png>\n"
                                + "Any size works (it is scaled to cover the screen and darkened so the text stays readable).\n"
                                + "Wide pictures (3:2) fit a big screen best. After replacing a file: /coinshop reload\n");
            } catch (IOException ignored) {
            }
        }
        return dir;
    }

    /** The named picture, or null if it is missing / unreadable (a warning is logged once). */
    public java.awt.image.BufferedImage image(String name) {
        String file = name.trim();
        if (file.contains("/") || file.contains("\\") || file.contains("..")) return null; // stay inside the folder
        return images.computeIfAbsent(file, f -> java.util.Optional.ofNullable(loadImage(f))).orElse(null);
    }

    private java.awt.image.BufferedImage loadImage(String f) {
        File img = new File(backgroundsFolder(), f);
        try {
            java.awt.image.BufferedImage b = img.isFile() ? javax.imageio.ImageIO.read(img) : null;
            if (b == null) plugin.getLogger().warning("Leaderboard screen picture '" + f + "' is missing or not a PNG/JPG (plugins/"
                    + plugin.getName() + "/backgrounds/).");
            return b;
        } catch (Throwable t) {
            plugin.getLogger().warning("Couldn't read queue screen picture '" + f + "': " + t.getMessage());
            return null;
        }
    }

    /** Picture files in the backgrounds folder (tab completion). */
    public List<String> imageFiles() {
        String[] names = backgroundsFolder().list((d, n) -> {
            String l = n.toLowerCase(Locale.ROOT);
            return l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg");
        });
        return names == null ? List.of() : List.of(names);
    }

    public void setBackdrop(BoardScreen s, String style) {
        s.backdrop = style;
        s.resetBackdrop();
        Tasks.global(() -> {
            s.rebuild(true);
            save();
        });
    }

    public void shutdown() {
        if (slow != null) slow.cancel();
        if (fast != null) fast.cancel();
        for (BoardScreen s : screens.values()) {
            s.removeFrames();
            try {
                s.detachMaps();
            } catch (RuntimeException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of("Coin shop screens - change them in game with /coinscreen (map ids are managed by the plugin)."));
        for (BoardScreen s : screens.values()) s.save(y.createSection("screens." + s.name));
        String text = y.saveToString();
        Tasks.async(() -> {
            try {
                java.nio.file.Files.writeString(file.toPath(), text);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save screens.yml: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------ editing (global thread)

    public BoardScreen create(String name, String world, Geometry g, BoardLayout.Size size) {
        return create(name, world, g, size, "cycle");
    }

    /** stat is set before the first picture is drawn (Folia draws on another thread right away). */
    public BoardScreen create(String name, String world, Geometry g, BoardLayout.Size size, String stat) {
        int bg = Colors.rgb(plugin.getConfig().getString("screens.background", "#2A1A0E"));
        BoardScreen s = new BoardScreen(plugin, name, world, g, size, bg >= 0 ? bg : BoardLayout.DEFAULT_BACKGROUND, null);
        s.stat = stat == null ? "cycle" : stat;
        screens.put(name.toLowerCase(Locale.ROOT), s);
        Tasks.global(() -> {
            s.attachMaps();
            s.rebuild(true);
            save();
        });
        return s;
    }

    public boolean remove(String name) {
        BoardScreen s = screens.remove(name.toLowerCase(Locale.ROOT));
        if (s == null) return false;
        s.removeFrames();
        Tasks.global(() -> {
            s.detachMaps();
            save();
        });
        return true;
    }

    public void reshape(BoardScreen s, Geometry g, BoardLayout.Size size, String world) {
        s.reshape(g, size, world);
        Tasks.global(() -> {
            if (!s.ready) s.attachMaps();
            s.rebuild(true);
            save();
        });
    }

    public void setBackground(BoardScreen s, int rgb) {
        s.background = rgb;
        s.resetBackdrop();
        Tasks.global(() -> {
            s.rebuild(true);
            save();
        });
    }

    public BoardScreen overlapping(String world, Geometry g) {
        for (BoardScreen s : screens.values()) {
            if (!s.world.equals(world)) continue;
            for (int c = 0; c < g.width(); c++)
                for (int r = 0; r < g.height(); r++)
                    for (int c2 = 0; c2 < s.geo.width(); c2++)
                        for (int r2 = 0; r2 < s.geo.height(); r2++)
                            if (g.cellBlockX(c) == s.geo.cellBlockX(c2) && g.cellBlockY(r) == s.geo.cellBlockY(r2)
                                    && g.cellBlockZ(c) == s.geo.cellBlockZ(c2)) return s;
        }
        return null;
    }

    // ------------------------------------------------------------ timers

    // ------------------------------------------------------------ content

    /** Title, colours and switches for one page of a screen. */
    BoardLayout.Style style(BoardScreen s, ScreenStat b, int page, int pages) {
        var c = plugin.getConfig();
        boolean shop = b == ScreenStat.SHOP;
        String def = switch (b) {
            case SHOP -> "COIN SHOP";
            case COINS -> "TOP " + plugin.bridge().currency(false).toUpperCase(Locale.ROOT);
            case ORBS -> "TOP " + plugin.bridge().currency(true).toUpperCase(Locale.ROOT);
        };
        String title = s.title != null && pages <= 1 ? s.title : c.getString("screens.titles." + b.key(), def);
        int color = Colors.rgb(c.getString("screens.colors." + b.key(), shop ? "#FFC23D" : b == ScreenStat.ORBS ? "#C77DFF" : "#FFD24D"));
        int accent = s.accent >= 0 && pages <= 1 ? s.accent : color >= 0 ? color : 0xFFC23D;
        String subtitle = shop ? c.getString("screens.subtitle-shop", "Click a category to open it")
                : c.getString("screens.subtitle-top", "Click to open the leaderboard");
        String footer = c.getString("screens.footer", "Updates live");
        // the shop list: pictures of the sections, no ranks or flags
        return new BoardLayout.Style(title, subtitle, accent, s.background, shop || c.getBoolean("screens.show-faces", true),
                !shop && c.getBoolean("screens.show-flags", true), !shop && c.getBoolean("screens.medals", true),
                c.getBoolean("screens.stripes", true), footer, page, pages, s.maxRows(),
                color(c.getString("screens.cycle.dots.current", "#55FF55"), 0x55FF55), color(c.getString("screens.cycle.dots.other", ""), -1));
    }

    private static int color(String s, int fallback) {
        int v = Colors.rgb(s == null ? "" : s);
        return v >= 0 ? v : fallback;
    }

    /** What clicking a row does: open this shop section (row id -> section). */
    private final java.util.Map<UUID, String> rowActions = new java.util.concurrent.ConcurrentHashMap<>();

    private static UUID sectionId(String section) {
        return UUID.nameUUIDFromBytes(("section:" + section).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** The shop's sections (with their pictures), or the top players (face, flag, name, balance). */
    List<BoardLayout.Row> rows(ScreenStat b, int limit, boolean heads, boolean flags) {
        List<BoardLayout.Row> out = new ArrayList<>();
        if (b == ScreenStat.SHOP) {
            for (ScreenHost.Section sec : plugin.bridge().sections()) {
                if (out.size() >= limit) break;
                UUID id = sectionId(sec.id());
                rowActions.put(id, sec.id());
                out.add(new BoardLayout.Row(0, id, sec.name(), sec.value(), SectionArt.icon(sec.id(), sec.icon()), null));
            }
            return out;
        }
        boolean orbs = b == ScreenStat.ORBS;
        int rank = 1;
        for (ScreenHost.Top t : plugin.bridge().top(orbs, limit)) {
            int[] face = heads ? plugin.getFaces().face(t.uuid(), t.name()) : null;
            int[] flag = flags && t.flag() != null && !t.flag().isEmpty() ? plugin.getFaces().flag(t.flag()) : null;
            rowActions.put(t.uuid(), orbs ? "orbstop" : "topcoins");
            out.add(new BoardLayout.Row(rank++, t.uuid(), t.name() == null ? "?" : t.name(), t.value(), face, flag));
        }
        return out;
    }

    /** A new face or flag arrived: redraw on the next round. */
    private volatile boolean picturesChanged;

    public void picturesChanged() {
        picturesChanged = true;
    }

    /** Screen sounds from sounds.yml (screen-hover / screen-click). */
    static void sound(ScreenHost plugin, Player p, String id) {
        plugin.bridge().sound(p, "screen-" + id);
    }


    private void slowTick() {
        boolean force = picturesChanged;
        picturesChanged = false;
        for (BoardScreen s : screens.values()) {
            if (!s.ready) continue;
            double view = s.viewRange();
            boolean near = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (dist2(p, s) <= view * view) {
                    near = true;
                    break;
                }
            }
            if (!near) continue;
            s.rebuild(force);
            s.ensureFrames();
            // players who see the screen from further away get the fresh numbers too
            double reach = s.clickRange() + 4;
            for (Player p : Bukkit.getOnlinePlayers()) {
                double d2 = dist2(p, s);
                if (d2 > reach * reach && d2 <= view * view) Tasks.entity(p, () -> {
                    if (p.isOnline()) s.tick(p, 0.01);
                });
            }
        }
    }

    private void fastTick() {
        if (screens.isEmpty()) return;
        // a cycling screen turns its page right on time (not up to a second late)
        for (BoardScreen s : screens.values()) if (s.ready && s.pageDue()) s.rebuild(false);
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (BoardScreen s : screens.values()) {
                if (!s.ready) continue;
                double touch = s.clickRange(), reach = touch + 4;
                double d2 = dist2(p, s);
                // while a page effect runs, everyone who can see the screen gets every step
                if (s.inTransition() && d2 <= s.viewRange() * s.viewRange()) reach = s.viewRange();
                if (d2 <= reach * reach) {
                    Tasks.entity(p, () -> {
                        if (p.isOnline()) s.tick(p, touch);
                    });
                } else if (d2 > s.viewRange() * s.viewRange() * 1.5) {
                    s.forget(p.getUniqueId());
                }
            }
        }
    }

    private static double dist2(Player p, BoardScreen s) {
        Location l = p.getLocation();
        if (l.getWorld() == null || !l.getWorld().getName().equals(s.world)) return Double.MAX_VALUE;
        double dx = l.getX() - s.cx(), dy = l.getY() - s.cy(), dz = l.getZ() - s.cz();
        return dx * dx + dy * dy + dz * dz;
    }

    // ------------------------------------------------------------ clicks + protection

    private BoardScreen screenOf(Entity e) {
        String tag = BoardScreen.tagOf(plugin, e);
        if (tag == null) return null;
        int c = tag.lastIndexOf(':');
        return c <= 0 ? null : get(tag.substring(0, c));
    }

    private boolean isScreenFrame(Entity e) {
        return BoardScreen.tagOf(plugin, e) != null;
    }

    /** True if the click landed on a screen (the caller cancels the event). Player's thread. */
    private boolean click(Player p, BoardScreen only) {
        for (BoardScreen s : only != null ? List.of(only) : screens.values()) {
            if (!s.ready) continue;
            double touch = s.clickRange();
            UUID id = s.clickedPlayer(p, only != null ? Math.max(touch, 6) : touch);
            if (id == null) continue;
            if (!id.equals(BoardScreen.NO_ROW) && plugin.getConfig().getBoolean("screens.click-opens", true)) {
                sound(plugin, p, "click");
                // that section of the shop (or the leaderboard menu for a player row)
                String section = rowActions.get(id);
                if (section != null) Tasks.entityLater(p, () -> {
                    if (p.isOnline()) plugin.bridge().open(p, section);
                }, 1L);
            }
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!isScreenFrame(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) click(e.getPlayer(), screenOf(e.getRightClicked()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (isScreenFrame(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(HangingBreakEvent e) {
        if (isScreenFrame(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL || screens.isEmpty()) return;
        if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) return;
        if (click(e.getPlayer(), null)) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** Left clicks in the air or on a frame only arrive as an arm swing on some servers. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent e) {
        if (screens.isEmpty() || e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        click(e.getPlayer(), null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        for (BoardScreen s : screens.values()) s.forget(e.getPlayer().getUniqueId());
    }

    /** A new world / a respawn: the client may have dropped its maps - send them whole again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        for (BoardScreen s : screens.values()) s.forget(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        for (BoardScreen s : screens.values()) s.forget(e.getPlayer().getUniqueId());
    }
}
