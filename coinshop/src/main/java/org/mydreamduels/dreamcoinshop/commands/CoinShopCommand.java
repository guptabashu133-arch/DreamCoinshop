package org.mydreamduels.dreamcoinshop.commands;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;

public class CoinShopCommand implements CommandExecutor, TabCompleter {
    private final Dreamcoinshop plugin;

    public CoinShopCommand(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dreamcoinshop.admin")) {
                sender.sendMessage(this.plugin.getMessages().get("general.no-permission", new TagResolver[0]));
                return true;
            }
            this.plugin.reloadEverything();
            sender.sendMessage(Component.text("Dreamcoinshop reloaded (config.yml, messages.yml, sounds.yml).", (TextColor) NamedTextColor.GREEN));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("events")) {
            if (!sender.hasPermission("dreamcoinshop.admin")) {
                sender.sendMessage(this.plugin.getMessages().get("general.no-permission", new TagResolver[0]));
                return true;
            }
            // /coinshop events reset [event] confirm
            if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                String huntArg = null;
                boolean confirmed = false;
                for (int i = 2; i < args.length; ++i) {
                    if (args[i].equalsIgnoreCase("confirm")) {
                        confirmed = true;
                    } else if (huntArg == null) {
                        huntArg = args[i];
                    }
                }
                this.plugin.getHeadHuntManager().resetHunt(sender, huntArg, confirmed);
                return true;
            }
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(this.plugin.getMessages().get("general.player-only", new TagResolver[0]));
                return true;
            }
            this.plugin.getHeadHuntManager().giveHeads(admin, args.length >= 2 ? args[1] : null);
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.getMessages().get("general.player-only", new TagResolver[0]));
            return true;
        }
        Player player = (Player) sender;
        this.plugin.getCoinShopGUI().openMain(player);
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("dreamcoinshop.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String sub : List.of("reload", "events")) {
                if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("events")) {
            List<String> options = new ArrayList<>(this.plugin.getShopConfig().getHeadHunts().keySet());
            options.add("reset");
            for (String option : options) {
                if (option.toLowerCase().startsWith(args[1].toLowerCase())) out.add(option);
            }
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("events") && args[1].equalsIgnoreCase("reset")) {
            List<String> options = new ArrayList<>();
            if (args.length == 3) options.addAll(this.plugin.getShopConfig().getHeadHunts().keySet());
            options.add("confirm");
            for (String option : options) {
                if (option.toLowerCase().startsWith(args[args.length - 1].toLowerCase())) out.add(option);
            }
        }
        return out;
    }
}
