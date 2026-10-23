package org.mydreamduels.dreamcoinshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /coinshop [settings|sale|reload|import|gui|dialog|flags|particles] and /orbshop. */
final class ShopCommand implements TabExecutor {

    private final Dreamcoinshop plugin;

    ShopCommand(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Text text = plugin.text();
        boolean orbshop = command.getName().equalsIgnoreCase("orbshop");
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        if (!orbshop && sub.equals("reload")) {
            if (!sender.hasPermission("dreamcoinshop.admin")) {
                text.send(sender, "general.no-permission", null);
                return true;
            }
            int result = plugin.reloadAll();
            if (result < 0) {
                text.send(sender, "general.reload-broken", null);
                return true;
            }
            text.send(sender, "general.reloaded", null);
            if (result == 1) text.send(sender, "general.reload-storage", null);
            return true;
        }
        if (!orbshop && sub.equals("import")) {
            if (!sender.hasPermission("dreamcoinshop.admin")) {
                text.send(sender, "general.no-permission", null);
            } else if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                text.send(sender, "general.import-confirm", null);
            } else {
                plugin.data().forceImport().whenComplete((n, err) -> {
                    if (err != null) text.sendMini(sender, "<red>" + Text.escape(String.valueOf(err.getMessage())), null);
                    else text.send(sender, n > 0 ? "general.import-done" : "general.import-none", Ph.of("count", n));
                });
            }
            return true;
        }
        if (!orbshop && sub.equals("sale")) {
            if (!sender.hasPermission("dreamcoinshop.admin")) text.send(sender, "general.no-permission", null);
            else sale(sender, args);
            return true;
        }
        if (!(sender instanceof Player player)) {
            text.send(sender, "general.player-only", null);
            return true;
        }
        if (!player.hasPermission("dreamcoinshop.use")) {
            text.send(player, "general.no-permission", null);
            return true;
        }
        Menus menus = plugin.menus();
        if (orbshop) {
            menus.open(player, Pages.View.of("orbshop"), null);
            return true;
        }
        switch (sub) {
            case "settings", "config" -> {
                if (!player.hasPermission("dreamcoinshop.settings")) {
                    text.send(player, "general.no-permission", null);
                    return true;
                }
                menus.open(player, Pages.View.of("settings"), null);
            }
            case "gui", "chest" -> menus.openMain(player, Menus.Kind.CHEST);
            case "dialog" -> {
                if (!menus.canDialog(player)) {
                    text.send(player, "general.no-dialogs", null);
                    menus.openMain(player, Menus.Kind.CHEST);
                } else {
                    menus.openMain(player, Menus.Kind.DIALOG);
                }
            }
            case "flags", "flag" -> menus.open(player, Pages.View.of("flags"), null);
            case "particles", "particle" -> menus.open(player, Pages.View.of("particles"), null);
            default -> menus.openMain(player, null);
        }
        return true;
    }

    /** /coinshop sale [percent time [sections...] | stop] */
    private void sale(CommandSender sender, String[] args) {
        Text text = plugin.text();
        Sales sales = plugin.sales();
        if (args.length == 1) {
            Sales.Sale s = sales.current();
            long now = System.currentTimeMillis();
            if (!s.active(now)) text.send(sender, "sale.none", null);
            else text.send(sender, "sale.status", Ph.of("percent", s.percent(), "time", Sales.time(s.endsAt() - now), "sections", sales.sectionNames(s)));
            return;
        }
        if (args[1].equalsIgnoreCase("stop") || args[1].equalsIgnoreCase("end")) {
            sales.stop(() -> text.send(sender, "sale.stopped", null));
            return;
        }
        if (args.length < 3) {
            text.send(sender, "sale.usage", null);
            return;
        }
        int percent;
        try {
            percent = Integer.parseInt(args[1].replace("%", ""));
        } catch (NumberFormatException e) {
            text.send(sender, "sale.usage", null);
            return;
        }
        if (percent < 1 || percent > 95) {
            text.send(sender, "sale.bad-percent", null);
            return;
        }
        Long ms = Sales.parseTime(args[2]);
        if (ms == null) {
            text.send(sender, "sale.bad-time", null);
            return;
        }
        java.util.Set<String> sections = new java.util.LinkedHashSet<>();
        for (int i = 3; i < args.length; i++) {
            for (String part : args[i].split(",")) {
                String sec = part.trim().toLowerCase(Locale.ROOT);
                if (sec.isEmpty() || sec.equals("all")) continue;
                if (!Sales.SECTIONS.contains(sec)) {
                    text.send(sender, "sale.bad-section", Ph.of("section", sec));
                    return;
                }
                sections.add(sec);
            }
        }
        sales.start(percent, ms, sections, null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("orbshop")) return out;
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("gui", "dialog", "flags", "particles"));
            if (sender.hasPermission("dreamcoinshop.settings")) subs.add("settings");
            if (sender.hasPermission("dreamcoinshop.admin")) subs.addAll(List.of("sale", "reload", "import"));
            for (String s : subs) if (s.startsWith(last)) out.add(s);
        } else if (args[0].equalsIgnoreCase("sale") && sender.hasPermission("dreamcoinshop.admin")) {
            List<String> options = switch (args.length) {
                case 2 -> List.of("10", "20", "25", "30", "50", "stop");
                case 3 -> List.of("30m", "1h", "2h", "6h", "12h", "1d", "3d", "7d");
                default -> {
                    List<String> secs = new ArrayList<>(Sales.SECTIONS);
                    secs.add("all");
                    yield secs;
                }
            };
            for (String s : options) if (s.startsWith(last)) out.add(s);
        }
        return out;
    }
}
