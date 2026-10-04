package org.mydreamduels.dreamcoinshop.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.config.MessagesConfig;
import org.mydreamduels.dreamcoinshop.orbzone.OrbZone;
import org.mydreamduels.dreamcoinshop.orbzone.OrbZoneManager;

/** /orbwand [create <name>|delete <name>|list] - admin tool for orb zones, like WorldEdit's //wand. */
public class OrbWandCommand implements CommandExecutor, TabCompleter {
    private final Dreamcoinshop plugin;

    public OrbWandCommand(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessagesConfig msg = this.plugin.getMessages();
        if (!sender.hasPermission("dreamcoinshop.admin")) {
            sender.sendMessage(msg.get("general.no-permission", new TagResolver[0]));
            return true;
        }
        OrbZoneManager zones = this.plugin.getOrbZoneManager();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (zones.getZones().isEmpty()) {
                    sender.sendMessage(msg.get("orbzone.list-empty", new TagResolver[0]));
                    return true;
                }
                for (OrbZone z : zones.getZones()) {
                    sender.sendMessage(msg.get("orbzone.list-entry",
                            MessagesConfig.ph("name", z.name()), MessagesConfig.ph("world", z.world()),
                            MessagesConfig.ph("pos1", z.minX() + ", " + z.minY() + ", " + z.minZ()),
                            MessagesConfig.ph("pos2", z.maxX() + ", " + z.maxY() + ", " + z.maxZ())));
                }
                return true;
            }
            case "delete", "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(msg.get("orbzone.usage", new TagResolver[0]));
                    return true;
                }
                sender.sendMessage(zones.removeZone(args[1])
                        ? msg.get("orbzone.deleted", MessagesConfig.ph("name", args[1]))
                        : msg.get("orbzone.not-found", MessagesConfig.ph("name", args[1])));
                return true;
            }
            default -> {
            }
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.get("general.player-only", new TagResolver[0]));
            return true;
        }
        if (sub.isEmpty()) {
            player.getInventory().addItem(zones.createWand()).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            player.sendMessage(msg.get("orbzone.wand-given", new TagResolver[0]));
            return true;
        }
        if (sub.equals("create") && args.length >= 2) {
            Location[] sel = zones.getSelection(player);
            if (sel == null || sel[0] == null || sel[1] == null) {
                player.sendMessage(msg.get("orbzone.need-selection", new TagResolver[0]));
                return true;
            }
            if (!sel[0].getWorld().equals(sel[1].getWorld())) {
                player.sendMessage(msg.get("orbzone.different-worlds", new TagResolver[0]));
                return true;
            }
            String name = args[1];
            boolean existed = zones.getZone(name) != null;
            zones.addZone(OrbZone.of(name, sel[0], sel[1]));
            player.sendMessage(msg.get(existed ? "orbzone.updated" : "orbzone.created", MessagesConfig.ph("name", name)));
            return true;
        }
        player.sendMessage(msg.get("orbzone.usage", new TagResolver[0]));
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("dreamcoinshop.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("create", "delete", "list")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("remove"))) {
            for (OrbZone z : this.plugin.getOrbZoneManager().getZones()) {
                if (z.name().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(z.name());
            }
        }
        return out;
    }
}
