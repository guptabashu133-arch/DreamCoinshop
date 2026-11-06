package org.mydreamduels.dreamcoinshop.screen;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /coinscreen place <name> [small|big] | move <name> | rotate <name> | size <name> <small|big>
 *            | background [name] <colour> | remove <name> | list
 */
public final class ScreenCommand implements org.bukkit.command.TabExecutor {

    private static final List<String> SUBS = List.of("place", "move", "rotate", "size", "stat", "cycle", "cyclestats", "rows", "title", "accent", "background", "backdrop", "cursor", "hovergrow", "viewrange", "clickrange", "info", "remove", "transition", "list", "reload");
    private static final BlockFace[] CLOCKWISE = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final ScreenHost plugin;

    public ScreenCommand(ScreenHost plugin) {
        this.plugin = plugin;
    }

    private CoinScreens screens() {
        return plugin.getScreens();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] a) {
        if (!sender.hasPermission("dreamcoinshop.screen")) {
            msg(sender, "no-permission", null);
            return true;
        }
        if (a.length == 0 || !SUBS.contains(a[0].toLowerCase(Locale.ROOT))) {
            help(sender);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            plugin.reloadEverything();
            sender.sendMessage("§6§lCoinshop §8» §aScreens reloaded.");
            return true;
        }
        if (sub.equals("list")) {
            List<String> names = screens().names();
            sender.sendMessage(("§6Coin shop screens (" + names.size() + "): §f" + (names.isEmpty() ? "none - /coinscreen place <name>" : String.join("§7, §f", names))));
            return true;
        }
        // "/coinscreen background <colour>" without a name: the screen you look at / stand next to
        if (sub.equals("background") && sender instanceof Player bp && (a.length == 2 && screens().get(a[1]) == null && Colors.rgb(a[1]) >= 0)) {
            BoardScreen near = nearest(bp);
            if (near == null) {
                msg(bp, "screen-not-found", "-");
                return true;
            }
            a = new String[]{a[0], near.name(), a[1]};
        }
        // "/... place" without a name: the first free ready-made name
        if (sub.equals("place") && a.length == 1) a = new String[]{a[0], nameIdeas().get(0)};
        if (a.length < 2) {
            help(sender);
            return true;
        }
        String name = a[1];
        switch (sub) {
            case "place" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "players-only", null);
                    return true;
                }
                if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
                    help(sender);
                    return true;
                }
                if (screens().get(name) != null) {
                    msg(p, "screen-exists", name);
                    return true;
                }
                // no stat typed: "topkills" -> kills, "topfish" -> fish, anything else (e.g. "mix") cycles
                String third = a.length > 2 ? a[2] : null, sizeArg = a.length > 3 ? a[3] : null;
                if (third != null && BoardLayout.Size.parse(third, null) != null && ScreenStat.parse(third) == null) {
                    sizeArg = third; // "/coinscreen place topkills big"
                    third = null;
                }
                String stat = third != null ? mix(third) : statFromName(name);
                if (!stat.equalsIgnoreCase("cycle") && ScreenStat.parse(stat) == null) {
                    sender.sendMessage(("§cUnknown stat §f" + stat + "§c - use §f" + String.join("§7, §f", statNames()) + "§c or §fcycle"));
                    return true;
                }
                BoardLayout.Size size = BoardLayout.Size.parse(sizeArg,
                        BoardLayout.Size.parse(plugin.getConfig().getString("screens.default-size", "big"), BoardLayout.Size.BIG));
                Geometry g = aim(p, size, null);
                if (g == null) return true;
                String key = stat.equalsIgnoreCase("cycle") ? "cycle" : ScreenStat.parse(stat).key();
                screens().create(name, p.getWorld().getName(), g, size, key);
                screens().save();
                msg(p, "screen-placed", name);
                p.sendMessage((key.equals("cycle")
                        ? "§7 Shows: §fmix §7(shop, top coins, top shards one after another) - only one: §f/coinscreen stat " + name + " <" + String.join("|", statNames()) + ">"
                        : "§7 Shows: §f" + key + " §7only"));
            }
            case "move" -> {
                BoardScreen s = find(sender, name);
                if (s == null || !(sender instanceof Player p)) return true;
                Geometry g = aim(p, s.size, s);
                if (g == null) return true;
                screens().reshape(s, g, s.size, p.getWorld().getName());
                msg(p, "screen-moved", name);
            }
            case "rotate" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                int i = 0;
                for (int k = 0; k < 4; k++) if (CLOCKWISE[k] == s.geo.facing()) i = k;
                BlockFace next = CLOCKWISE[(i + 1) % 4];
                Geometry g = new Geometry(s.geo.x(), s.geo.y(), s.geo.z(), next, s.geo.width(), s.geo.height());
                screens().reshape(s, g, s.size, s.world);
                msg(sender, "screen-updated", name);
            }
            case "size" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                BoardLayout.Size size = a.length < 3 ? null : BoardLayout.Size.parse(a[2], null);
                if (size == null) {
                    sender.sendMessage(("§cUse §f/coinscreen size " + name + " <small|medium|big|huge>"));
                    return true;
                }
                Geometry g = new Geometry(s.geo.x(), s.geo.y(), s.geo.z(), s.geo.facing(), size.mapsWide, size.mapsHigh);
                screens().reshape(s, g, size, s.world);
                msg(sender, "screen-updated", name);
            }
            case "stat" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                String v = a.length > 2 ? mix(a[2]) : "";
                if (!v.equalsIgnoreCase("cycle") && ScreenStat.parse(v) == null) {
                    sender.sendMessage(("§cUse §f/coinscreen stat " + name + " <" + String.join("|", statNames()) + "|cycle>"));
                    return true;
                }
                s.stat = v.equalsIgnoreCase("cycle") ? "cycle" : ScreenStat.parse(v).key();
                apply(s);
                msg(sender, "screen-updated", name);
            }
            case "cycle" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                Integer secs = a.length > 2 && a[2].equalsIgnoreCase("default") ? Integer.valueOf(-1) : intArg(a, 2);
                if (secs == null) {
                    sender.sendMessage(("§cUse §f/coinscreen cycle " + name + " <seconds|default> §7(seconds per page, 3+)"));
                    return true;
                }
                s.cycleSeconds = secs < 0 ? -1 : Math.max(3, secs);
                s.stat = "cycle";
                apply(s);
                msg(sender, "screen-updated", name);
            }
            case "cyclestats" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                if (a.length < 3) {
                    sender.sendMessage(("§cUse §f/coinscreen cyclestats " + name + " <shop,coins,orbs|default>"));
                    return true;
                }
                if (a[2].equalsIgnoreCase("default")) {
                    s.cycleStats = null;
                } else {
                    List<String> list = new ArrayList<>();
                    for (int i = 2; i < a.length; i++) {
                        for (String part : a[i].split(",")) {
                            if (part.isBlank()) continue;
                            var t = ScreenStat.parse(part);
                            if (t == null) {
                                sender.sendMessage(("§cUnknown stat §f" + part));
                                return true;
                            }
                            if (!list.contains(t.key())) list.add(t.key());
                        }
                    }
                    s.cycleStats = list;
                }
                s.stat = "cycle";
                apply(s);
                msg(sender, "screen-updated", name);
            }
            case "rows" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                Integer n = a.length > 2 && a[2].equalsIgnoreCase("default") ? Integer.valueOf(-1) : intArg(a, 2);
                if (n == null) {
                    sender.sendMessage(("§cUse §f/coinscreen rows " + name + " <number|default> §7(0 = as many as fit)"));
                    return true;
                }
                s.maxRows = Math.max(-1, Math.min(50, n));
                apply(s);
                msg(sender, "screen-updated", name);
            }
            case "title" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                if (a.length < 3) {
                    sender.sendMessage(("§cUse §f/coinscreen title " + name + " <text...|default>"));
                    return true;
                }
                String text = String.join(" ", java.util.Arrays.copyOfRange(a, 2, a.length));
                s.title = text.equalsIgnoreCase("default") ? null : text.toUpperCase(Locale.ROOT);
                apply(s);
                msg(sender, "screen-updated", name);
            }
            case "accent" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                if (a.length > 2 && a[2].equalsIgnoreCase("default")) {
                    s.accent = -1;
                } else {
                    int rgb = a.length > 2 ? Colors.rgb(a[2]) : -1;
                    if (rgb < 0) {
                        sender.sendMessage(("§cUse a colour like §f#FFC23D§c, a name or §fdefault"));
                        return true;
                    }
                    s.accent = rgb;
                }
                apply(s);
                msg(sender, "screen-updated", name);
            }
            case "hovergrow" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                String v = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : (s.hoverGrow() ? "off" : "on"); // no value = toggle
                switch (v) {
                    case "on", "true", "yes" -> s.grow = true;
                    case "off", "false", "no" -> s.grow = false;
                    case "default" -> s.grow = null;
                    default -> {
                        sender.sendMessage(("§cUse §f/coinscreen hovergrow " + name + " <on|off|default>"));
                        return true;
                    }
                }
                screens().save();
                sender.sendMessage(("§aHover pop-out on §f" + name + "§a: §f" + (s.hoverGrow() ? "on" : "off")
                        + (s.grow == null ? " §7(default)" : "")));
            }
            case "transition" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                String v = a.length > 2 ? a[2] : "";
                String t = v.equalsIgnoreCase("default") ? "default" : Transition.parse(v);
                if (t == null) {
                    sender.sendMessage(("§cUse §f/coinscreen transition " + name + " <" + String.join("|", Transition.EFFECTS)
                            + "|random|off|default>"));
                    return true;
                }
                s.transition = t.equals("default") ? null : t;
                screens().save();
                sender.sendMessage(("§aPage effect on §f" + name + "§a: §f" + s.transitionStyle()
                        + (s.transition == null ? " §7(default)" : "") + (s.stat.equalsIgnoreCase("cycle") ? "" : " §7(only cycling screens turn pages)")));
            }
            case "info" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                String bd = s.backdropStyle();
                sender.sendMessage(("§6§lScreen §f" + name + " §8- §7" + s.world + " " + s.geo.x() + ", " + s.geo.y() + ", "
                        + s.geo.z() + " §8(" + s.geo.facing().name().toLowerCase(Locale.ROOT) + ")"));
                sender.sendMessage(("§7 Size §f" + s.size.name().toLowerCase(Locale.ROOT)
                        + "  §7Background §f" + String.format("#%06X", s.background)
                        + "  §7Backdrop §f" + (bd.equalsIgnoreCase("none") ? "off" : bd) + (s.backdrop == null ? " §8(default)" : "")));
                sender.sendMessage(("§7 Cursor §f" + (s.cursorOn() ? "on" : "off") + (s.cursor == null ? " §8(default)" : "")
                        + "  §7Hover pop-out §f" + (s.hoverGrow() ? "on" : "off") + (s.grow == null ? " §8(default)" : "")));
                sender.sendMessage(("§7 View range §f" + (int) s.viewRange() + "  §7Click range §f" + (int) s.clickRange()
                        + " §8blocks"));
                sender.sendMessage(("§7 Shows §f" + (s.stat.equalsIgnoreCase("cycle") ? "cycle §8(" + s.cycleSeconds()
                        + "s: " + s.stats() + ")" : s.stat) + "  §7Rows §f" + (s.maxRows() == 0 ? "auto" : s.maxRows())
                        + (s.title != null ? "  §7Title §f" + s.title : "") + (s.accent >= 0 ? "  §7Accent §f" + String.format("#%06X", s.accent) : "")));
                if (s.stat.equalsIgnoreCase("cycle")) sender.sendMessage(("§7 Page effect §f" + s.transitionStyle()
                        + (s.transition == null ? " §8(default)" : "")));
            }
            case "backdrop" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                String style = a.length > 2 ? a[2] : "";
                boolean image = style.toLowerCase(Locale.ROOT).startsWith("image:") && style.length() > 6;
                if (style.equalsIgnoreCase("off")) {
                    screens().setBackdrop(s, "none");
                } else if (style.equalsIgnoreCase("on")) {
                    // back to the config default - or a design, if the default itself is "none"
                    String def = plugin.getConfig().getString("screens.backdrop", "stars");
                    screens().setBackdrop(s, def == null || def.equalsIgnoreCase("none") ? "stars" : null);
                } else if (style.equalsIgnoreCase("default")) {
                    screens().setBackdrop(s, null);
                } else if (Backdrop.isStyle(style) || image) {
                    if (image && screens().image(style.substring(6)) == null) {
                        sender.sendMessage(("§cNo picture §f" + style.substring(6) + "§c in plugins/"
                                + plugin.getName() + "/backgrounds/ §7(PNG or JPG)"));
                        return true;
                    }
                    screens().setBackdrop(s, style.toLowerCase(Locale.ROOT).startsWith("image:") ? "image:" + style.substring(6) : style.toLowerCase(Locale.ROOT));
                } else {
                    sender.sendMessage(("§cUse §f" + String.join("§7, §f", Backdrop.STYLES)
                            + "§c, §fimage:<file.png>§c (from plugins/" + plugin.getName() + "/backgrounds/), §fon§c, §foff§c or §fdefault"));
                    return true;
                }
                msg(sender, "screen-updated", name);
            }
            case "background" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                int rgb = a.length > 2 ? Colors.rgb(a[2]) : -1;
                if (rgb < 0) {
                    sender.sendMessage(("§cUse a colour like §f#0F1A2E§c or a name: §f" + String.join("§7, §f", Colors.names())));
                    return true;
                }
                screens().setBackground(s, rgb);
                msg(sender, "screen-updated", name);
            }
            case "cursor" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                String v = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : (s.cursorOn() ? "off" : "on"); // no value = toggle
                switch (v) {
                    case "on", "true", "yes" -> s.cursor = true;
                    case "off", "false", "no" -> s.cursor = false;
                    case "default" -> s.cursor = null;
                    default -> {
                        sender.sendMessage(("§cUse §f/coinscreen cursor " + name + " <on|off|default>"));
                        return true;
                    }
                }
                screens().save();
                sender.sendMessage(("§aCursor on §f" + name + "§a: §f" + (s.cursorOn() ? "on" : "off")
                        + (s.cursor == null ? " §7(default)" : "")));
            }
            case "viewrange", "clickrange" -> {
                BoardScreen s = find(sender, name);
                if (s == null) return true;
                boolean view = sub.equals("viewrange");
                double v;
                if (a.length > 2 && a[2].equalsIgnoreCase("default")) v = -1;
                else {
                    try {
                        v = Double.parseDouble(a.length > 2 ? a[2] : "x");
                    } catch (NumberFormatException e) {
                        sender.sendMessage(("§cUse §f/coinscreen " + sub + " " + name + " <blocks|default>"
                                + (view ? " §7(8-128)" : " §7(2-32)")));
                        return true;
                    }
                    v = view ? Math.max(8, Math.min(128, v)) : Math.max(2, Math.min(32, v));
                }
                if (view) s.viewRange = v;
                else s.clickRange = v;
                screens().save();
                double now = view ? s.viewRange() : s.clickRange();
                sender.sendMessage(("§a" + (view ? "View" : "Click") + " range of §f" + name + "§a: §f"
                        + (now == Math.floor(now) ? String.valueOf((long) now) : String.valueOf(now)) + " blocks" + (v < 0 ? " §7(default)" : "")));
            }
            case "remove" -> {
                if (screens().remove(name)) msg(sender, "screen-removed", name);
                else msg(sender, "screen-not-found", name);
            }
            default -> help(sender);
        }
        return true;
    }

    /** The screen the player looks at, else the closest one within 24 blocks. */
    private BoardScreen nearest(Player p) {
        BoardScreen best = null;
        double bestD = 24 * 24;
        org.bukkit.Location eye = p.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection();
        for (BoardScreen s : screens().all()) {
            if (!p.getWorld().getName().equals(s.world)) continue;
            double dx = s.cx() - eye.getX(), dy = s.cy() - eye.getY(), dz = s.cz() - eye.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            double len = Math.sqrt(d);
            // looking roughly at it counts as being much closer
            if (len > 0 && (dx * dir.getX() + dy * dir.getY() + dz * dir.getZ()) / len > 0.9) d *= 0.25;
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    private BoardScreen find(CommandSender sender, String name) {
        BoardScreen s = screens().get(name);
        if (s == null) msg(sender, "screen-not-found", name);
        return s;
    }

    /** The wall block the player looks at becomes the top-left corner; the screen grows right and down. */
    private Geometry aim(Player p, BoardLayout.Size size, BoardScreen ignore) {
        RayTraceResult ray = p.rayTraceBlocks(10);
        Block target = ray == null ? null : ray.getHitBlock();
        BlockFace face = ray == null ? null : ray.getHitBlockFace();
        if (target == null || face == null || !Geometry.validFacing(face)) {
            msg(p, "screen-look-at-wall", null);
            return null;
        }
        Block start = target.getRelative(face);
        Geometry g = new Geometry(start.getX(), start.getY(), start.getZ(), face, size.mapsWide, size.mapsHigh);
        for (int c = 0; c < g.width(); c++) {
            for (int r = 0; r < g.height(); r++) {
                if (p.getWorld().getBlockAt(g.cellBlockX(c), g.cellBlockY(r), g.cellBlockZ(c)).getType().isSolid()) {
                    msg(p, "screen-blocked", null);
                    return null;
                }
            }
        }
        BoardScreen other = screens().overlapping(p.getWorld().getName(), g);
        if (other != null && other != ignore) {
            msg(p, "screen-blocked", null);
            return null;
        }
        return g;
    }

    private void help(CommandSender s) {
        s.sendMessage(("§6§lCoin shop screens §8- §7the shop and the top lists on a wall"));
        s.sendMessage(("§f /coinscreen place [name] [stat|mix] [small|medium|big|huge] §8- §7look at a wall (top-left corner)"));
        s.sendMessage(("§7   e.g. §f/coinscreen place shop §7(click a category to open it) §8|§f /coinscreen place topcoins §8|§f mix"));
        s.sendMessage(("§f /coinscreen stat <name> <stat|cycle> §8|§f cycle <name> <seconds> §8|§f cyclestats <name> <shop,coins,orbs>"));
        s.sendMessage(("§f /coinscreen rows <name> <n> §8|§f title <name> <text> §8|§f accent <name> <colour> §8|§f size <name> <size>"));
        s.sendMessage(("§f /coinscreen background [name] <colour> §8|§f backdrop <name> <design|image:file.png|on|off>"));
        s.sendMessage(("§f /coinscreen cursor|hovergrow <name> [on|off] §8|§f viewrange|clickrange <name> <blocks>"));
        s.sendMessage(("§f /coinscreen transition <name> <" + String.join("|", Transition.EFFECTS) + "|random|off> §8- §7page effect"));
        s.sendMessage(("§f /coinscreen move|rotate|info|remove <name> §8|§f list"));
        s.sendMessage(("§f /coinscreen reload §8- §7config.yml + every screen"));
        s.sendMessage(("§7 Stats: §f" + String.join("§7, §f", statNames())));
    }

    /** "mix" is another word for "cycle". */
    private static String mix(String s) {
        return s.equalsIgnoreCase("mix") ? "cycle" : s;
    }

    /** The stat a screen name points at: "topkills" / "top_kills" / "kills" -> kills, else cycle. */
    private String statFromName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("top")) n = n.substring(3);
        n = n.replaceAll("^[_-]+", "").replaceAll("[_-]?\\d+$", "");
        if (n.equals("mix") || n.equals("cycle")) return "cycle";
        var b = n.isEmpty() ? null : ScreenStat.parse(n);
        return b == null ? "shop" : b.key(); // anything else: the shop itself
    }

    /** Ready-made names for /coinscreen place: one per leaderboard (topkills, topfish...) and mix - free ones only. */
    private List<String> nameIdeas() {
        List<String> out = new ArrayList<>();
        List<String> ideas = new ArrayList<>();
        ideas.add("shop");
        ideas.add("topcoins");
        ideas.add("topshards");
        ideas.add("mix");
        for (String idea : ideas) {
            String n = idea;
            for (int i = 2; screens().get(n) != null; i++) n = idea + i;
            out.add(n);
        }
        return out;
    }

    private List<String> statNames() {
        List<String> out = new ArrayList<>();
        for (var st : ScreenStat.values()) out.add(st.key());
        return out;
    }

    private static Integer intArg(String[] a, int i) {
        try {
            return a.length > i ? Integer.parseInt(a[i]) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A content setting changed: redraw now and save. */
    private void apply(BoardScreen s) {
        Tasks.global(() -> {
            s.rebuild(true);
            screens().save();
        });
    }

    /** A message from messages.yml (<name> = the screen). */
    private static final java.util.Map<String, String> TEXTS = java.util.Map.ofEntries(
            java.util.Map.entry("no-permission", "§cYou don't have permission to do that."),
            java.util.Map.entry("players-only", "§cOnly players can do that."),
            java.util.Map.entry("screen-placed", "§aPlaced the coin shop screen §f%s§a."),
            java.util.Map.entry("screen-removed", "§aRemoved the screen §f%s§a."),
            java.util.Map.entry("screen-moved", "§aMoved the screen §f%s§a."),
            java.util.Map.entry("screen-updated", "§aUpdated the screen §f%s§a."),
            java.util.Map.entry("screen-not-found", "§cThere is no screen called §f%s§c."),
            java.util.Map.entry("screen-exists", "§cA screen called §f%s§c already exists."),
            java.util.Map.entry("screen-look-at-wall", "§cLook at the §fwall§c (the top-left corner of the screen) within 10 blocks."),
            java.util.Map.entry("screen-blocked", "§cThe screen doesn't fit there - it needs a flat wall with free space in front."));

    private void msg(CommandSender to, String key, String name) {
        String t = TEXTS.get(key);
        if (t != null) to.sendMessage("§6§lCoinshop §8» " + String.format(t, name == null ? "" : name));
    }



    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] a) {
        if (!sender.hasPermission("dreamcoinshop.screen")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(SUBS);
        else if (a.length == 2 && a[0].equalsIgnoreCase("place")) out.addAll(nameIdeas());
        else if (a.length == 2 && !a[0].equalsIgnoreCase("list")) out.addAll(screens().names());
        else if (a.length == 3 && a[0].equalsIgnoreCase("size")) out.addAll(List.of("small", "medium", "big", "huge"));
        else if (a.length == 3 && (a[0].equalsIgnoreCase("place") || a[0].equalsIgnoreCase("stat"))) {
            out.add("mix");
            out.addAll(statNames());
            if (a[0].equalsIgnoreCase("place")) out.addAll(List.of("small", "medium", "big", "huge"));
        } else if (a.length == 4 && a[0].equalsIgnoreCase("place")) out.addAll(List.of("small", "medium", "big", "huge"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("cycle")) out.addAll(List.of("5", "8", "10", "15", "default"));
        else if (a.length >= 3 && a[0].equalsIgnoreCase("cyclestats")) {
            out.addAll(statNames());
            out.add("default");
        } else if (a.length == 3 && a[0].equalsIgnoreCase("rows")) out.addAll(List.of("5", "8", "10", "0", "default"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("accent")) {
            out.addAll(Colors.names());
            out.add("default");
        }
        else if (a.length == 3 && a[0].equalsIgnoreCase("cursor")) out.addAll(List.of("on", "off", "default"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("transition")) {
            out.addAll(Transition.EFFECTS);
            out.addAll(List.of("random", "off", "default"));
        }
        else if (a.length == 3 && a[0].equalsIgnoreCase("viewrange")) out.addAll(List.of("16", "24", "40", "64", "default"));
        else if (a.length == 3 && a[0].equalsIgnoreCase("clickrange")) out.addAll(List.of("4", "6", "8", "12", "16", "default"));
        if (a.length == 3 && a[0].equalsIgnoreCase("hovergrow")) out.addAll(List.of("on", "off", "default"));
        if (a.length == 3 && a[0].equalsIgnoreCase("backdrop")) {
            out.addAll(List.of("on", "off"));
            out.addAll(List.of(Backdrop.STYLES));
            out.add("default");
            for (String f : screens().imageFiles()) out.add("image:" + f);
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("background")) out.addAll(Colors.names());
        else if (a.length == 3 && a[0].equalsIgnoreCase("background")) {
            out.addAll(Colors.names());
            out.add("#0F1A2E");
        }
        String typed = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(typed));
        return out;
    }
}
