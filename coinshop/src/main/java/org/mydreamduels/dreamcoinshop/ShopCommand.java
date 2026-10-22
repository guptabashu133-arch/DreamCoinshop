package org.mydreamduels.dreamcoinshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /coinshop [settings|reload|gui|dialog] and /orbshop. */
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
            default -> menus.openMain(player, null);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("orbshop") || args.length != 1) return out;
        List<String> subs = new ArrayList<>(List.of("gui", "dialog", "flags"));
        if (sender.hasPermission("dreamcoinshop.settings")) subs.add("settings");
        if (sender.hasPermission("dreamcoinshop.admin")) {
            subs.add("reload");
            subs.add("import");
        }
        for (String s : subs) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        return out;
    }
}
