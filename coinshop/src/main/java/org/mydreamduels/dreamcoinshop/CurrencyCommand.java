package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /coin and /orb: balance for everyone, give / giveall / remove / set for admins. Works for
 * players on another server or offline too (looked up in the database).
 */
final class CurrencyCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("balance", "give", "giveall", "remove", "set", "help");
    private static final List<String> WITH_PLAYER = List.of("balance", "give", "remove", "set");

    private final Dreamcoinshop plugin;
    private final Currency currency;
    /** messages.yml section: coin.* or orbcmd.* */
    private final String section;

    CurrencyCommand(Dreamcoinshop plugin, Currency currency) {
        this.plugin = plugin;
        this.currency = currency;
        this.section = currency == Currency.COINS ? "coin" : "orbcmd";
    }

    private Text text() {
        return plugin.text();
    }

    private String balancePath(boolean self) {
        if (currency == Currency.COINS) return self ? "coin.balance-self" : "coin.balance-other";
        return self ? "orbs.balance-self" : "orbs.balance-other";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("balance") || sub.equals("bal")) {
            balance(sender, args);
            return true;
        }
        if (!SUBCOMMANDS.contains(sub)) {
            help(sender, label);
            return true;
        }
        if (!sender.hasPermission("dreamcoinshop.admin")) {
            text().send(sender, "general.no-permission", null);
            return true;
        }
        if (!plugin.data().isReady()) {
            text().send(sender, "general.database-down", null); // a change now couldn't be saved
            return true;
        }
        if (sub.equals("giveall")) {
            if (args.length < 2) {
                text().send(sender, section + ".usage-giveall", null);
                return true;
            }
            Double amount = amount(sender, args[1]);
            if (amount == null) return true;
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.data().add(plugin.data().profile(p), currency, amount);
                text().send(p, section + ".received-all", Ph.of("amount", Shop.format(amount)));
            }
            text().send(sender, section + ".gave-all", Ph.of("amount", Shop.format(amount)));
            return true;
        }
        if (args.length < 3) {
            text().send(sender, section + ".usage-" + sub, null);
            return true;
        }
        Double amount = amount(sender, args[2]);
        if (amount == null) return true;
        String targetName = args[1];
        plugin.data().resolve(targetName).thenAccept(uuid -> run(sender, sub, targetName, uuid, amount));
        return true;
    }

    private void run(CommandSender sender, String sub, String targetName, UUID uuid, double amount) {
        if (uuid == null) {
            text().send(sender, "general.unknown-player", Ph.of("player", targetName));
            return;
        }
        DataManager data = plugin.data();
        Player online = Bukkit.getPlayer(uuid);
        String name = online != null ? online.getName() : targetName;
        Ph ph = Ph.of("amount", Shop.format(amount), "player", name);
        switch (sub) {
            case "give" -> {
                data.addAnywhere(uuid, currency, amount);
                text().send(sender, section + ".gave", ph);
                if (online != null) text().send(online, section + ".received", ph);
            }
            case "remove" -> {
                data.addAnywhere(uuid, currency, -amount);
                text().send(sender, section + ".removed", ph);
            }
            case "set" -> {
                data.setAnywhere(uuid, currency, amount);
                text().send(sender, section + ".set", ph);
            }
            default -> help(sender, currency == Currency.COINS ? "coin" : "orb");
        }
    }

    private void balance(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player p)) {
                text().send(sender, section + ".usage-balance-console", null);
                return;
            }
            Profile profile = plugin.data().profile(p);
            if (!profile.isLoaded()) {
                text().send(p, "shop.loading", null);
                return;
            }
            text().send(p, balancePath(true), Ph.of("player", p.getName(), "balance", Shop.format(Math.floor(profile.balance(currency)))));
            return;
        }
        String targetName = args[1];
        if (!sender.hasPermission("dreamcoinshop.admin")) {
            // Players may look up anyone online; offline lookups (a database query each) are for admins
            Player target = Bukkit.getPlayerExact(targetName);
            Profile profile = target == null ? null : plugin.data().online(target.getUniqueId());
            if (profile == null || !profile.isLoaded()) {
                text().send(sender, "general.unknown-player", Ph.of("player", targetName));
                return;
            }
            boolean self = sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId());
            text().send(sender, balancePath(self), Ph.of("player", target.getName(),
                    "balance", Shop.format(Math.floor(profile.balance(currency)))));
            return;
        }
        plugin.data().resolve(targetName).thenAccept(uuid -> {
            if (uuid == null) {
                text().send(sender, "general.unknown-player", Ph.of("player", targetName));
                return;
            }
            plugin.data().balance(uuid, currency).thenAccept(bal -> {
                boolean self = sender instanceof Player p && p.getUniqueId().equals(uuid);
                Player online = Bukkit.getPlayer(uuid);
                text().send(sender, balancePath(self), Ph.of("player", online != null ? online.getName() : targetName,
                        "balance", Shop.format(Math.floor(bal == null ? 0 : bal))));
            });
        });
    }

    private Double amount(CommandSender sender, String raw) {
        Double d = Pages.parse(raw);
        if (d == null) {
            text().send(sender, "general.invalid-amount", null);
            return null;
        }
        if (d < 0) {
            text().send(sender, "general.amount-must-be-positive", null);
            return null;
        }
        return d;
    }

    private void help(CommandSender sender, String label) {
        text().send(sender, section + ".help-header", null);
        String l = "/" + label;
        for (String line : List.of(l + " balance [player]", l + " give <player> <amount>", l + " giveall <amount>",
                l + " remove <player> <amount>", l + " set <player> <amount>", "/coinshop", "/coinshop settings", "/coinshop reload")) {
            text().sendMini(sender, "<yellow>" + Text.escape(line), null);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (!sub.equals("balance") && !sub.equals("help") && !sender.hasPermission("dreamcoinshop.admin")) continue;
                if (sub.startsWith(partial)) out.add(sub);
            }
        } else if (args.length == 2 && WITH_PLAYER.contains(args[0].toLowerCase(Locale.ROOT))) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(p.getName());
        }
        return out;
    }
}
