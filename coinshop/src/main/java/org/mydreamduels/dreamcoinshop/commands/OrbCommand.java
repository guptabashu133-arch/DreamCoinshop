package org.mydreamduels.dreamcoinshop.commands;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.config.MessagesConfig;

public class OrbCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("balance", "give", "giveall", "remove", "set", "help");
    private static final List<String> NEEDS_PLAYER_ARG = List.of("balance", "give", "remove", "set");
    private final Dreamcoinshop plugin;

    public OrbCommand(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessagesConfig msg = this.plugin.getMessages();
        if (args.length == 0) {
            this.sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "balance": {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(msg.get("general.not-online", new TagResolver[0]));
                        return true;
                    }
                } else if (sender instanceof Player) {
                    target = (Player) sender;
                } else {
                    sender.sendMessage(msg.get("orbcmd.usage-balance-console", new TagResolver[0]));
                    return true;
                }
                double bal = this.plugin.getOrbsManager().getBalance(target.getUniqueId());
                boolean self = sender instanceof Player p2 && p2.getUniqueId().equals(target.getUniqueId());
                String path = self ? "orbs.balance-self" : "orbs.balance-other";
                sender.sendMessage(msg.get(path, MessagesConfig.ph("player", target.getName()), MessagesConfig.ph("balance", (long) bal)));
                break;
            }
            case "give": {
                if (!sender.hasPermission("dreamcoinshop.admin")) {
                    sender.sendMessage(msg.get("general.no-permission", new TagResolver[0]));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(msg.get("orbcmd.usage-give", new TagResolver[0]));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(msg.get("general.not-online", new TagResolver[0]));
                    return true;
                }
                double amount = this.parseAmount(sender, args[2]);
                if (Double.isNaN(amount)) {
                    return true;
                }
                this.plugin.getOrbsManager().add(target.getUniqueId(), amount);
                sender.sendMessage(msg.get("orbcmd.gave", MessagesConfig.ph("amount", (long) amount), MessagesConfig.ph("player", target.getName())));
                target.sendMessage(msg.get("orbcmd.received", MessagesConfig.ph("amount", (long) amount)));
                break;
            }
            case "giveall": {
                if (!sender.hasPermission("dreamcoinshop.admin")) {
                    sender.sendMessage(msg.get("general.no-permission", new TagResolver[0]));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(msg.get("orbcmd.usage-giveall", new TagResolver[0]));
                    return true;
                }
                double amount = this.parseAmount(sender, args[1]);
                if (Double.isNaN(amount)) {
                    return true;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    this.plugin.getOrbsManager().add(p.getUniqueId(), amount);
                    p.sendMessage(msg.get("orbcmd.received-all", MessagesConfig.ph("amount", (long) amount)));
                }
                sender.sendMessage(msg.get("orbcmd.gave-all", MessagesConfig.ph("amount", (long) amount)));
                break;
            }
            case "remove": {
                if (!sender.hasPermission("dreamcoinshop.admin")) {
                    sender.sendMessage(msg.get("general.no-permission", new TagResolver[0]));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(msg.get("orbcmd.usage-remove", new TagResolver[0]));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(msg.get("general.not-online", new TagResolver[0]));
                    return true;
                }
                double amount = this.parseAmount(sender, args[2]);
                if (Double.isNaN(amount)) {
                    return true;
                }
                this.plugin.getOrbsManager().remove(target.getUniqueId(), amount);
                sender.sendMessage(msg.get("orbcmd.removed", MessagesConfig.ph("amount", (long) amount), MessagesConfig.ph("player", target.getName())));
                break;
            }
            case "set": {
                if (!sender.hasPermission("dreamcoinshop.admin")) {
                    sender.sendMessage(msg.get("general.no-permission", new TagResolver[0]));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(msg.get("orbcmd.usage-set", new TagResolver[0]));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(msg.get("general.not-online", new TagResolver[0]));
                    return true;
                }
                double amount = this.parseAmount(sender, args[2]);
                if (Double.isNaN(amount)) {
                    return true;
                }
                this.plugin.getOrbsManager().setBalance(target.getUniqueId(), amount);
                sender.sendMessage(msg.get("orbcmd.set", MessagesConfig.ph("player", target.getName()), MessagesConfig.ph("amount", (long) amount)));
                break;
            }
            case "help": {
                this.sendHelp(sender);
                break;
            }
            default: {
                this.sendHelp(sender);
            }
        }
        return true;
    }

    private double parseAmount(CommandSender sender, String raw) {
        try {
            double amount = Double.parseDouble(raw);
            if (amount < 0.0) {
                sender.sendMessage(this.plugin.getMessages().get("general.amount-must-be-positive", new TagResolver[0]));
                return Double.NaN;
            }
            return amount;
        } catch (NumberFormatException e) {
            sender.sendMessage(this.plugin.getMessages().get("general.invalid-amount", new TagResolver[0]));
            return Double.NaN;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(this.plugin.getMessages().get("orbcmd.help-header", new TagResolver[0]));
        sender.sendMessage(Component.text("/orb balance [player]", (TextColor) NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/orb give <player> <amount>", (TextColor) NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/orb giveall <amount>", (TextColor) NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/orb remove <player> <amount>", (TextColor) NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/orb set <player> <amount>", (TextColor) NamedTextColor.YELLOW));
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> out = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(partial)) out.add(sub);
            }
            return out;
        }
        if (args.length == 2 && NEEDS_PLAYER_ARG.contains(args[0].toLowerCase())) {
            String partial = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) out.add(p.getName());
            }
            return out;
        }
        return out;
    }
}
