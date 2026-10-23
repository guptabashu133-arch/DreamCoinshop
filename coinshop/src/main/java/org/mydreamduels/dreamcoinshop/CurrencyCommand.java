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
 * /coin and /orb: balance and pay for everyone, give / giveall / remove / set for admins. Works
 * for players on another server or offline too (looked up in the database).
 */
final class CurrencyCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("balance", "pay", "give", "giveall", "remove", "set", "help");
    private static final List<String> WITH_PLAYER = List.of("balance", "pay", "give", "remove", "set");
    /** Subcommands everyone may use (pay also needs dreamcoinshop.pay). */
    private static final List<String> PUBLIC = List.of("balance", "pay", "help");

    private final Dreamcoinshop plugin;
    private final Currency currency;
    /** messages.yml section: coin.* or orbcmd.* */
    private final String section;
    private final java.util.Map<UUID, Long> lastPay = new java.util.concurrent.ConcurrentHashMap<>();

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

    /** Placeholders every message of this command gets: the command typed and the currency name. */
    private Ph ph(String label) {
        return Ph.of("cmd", label, "currency", plugin.config().currencyName(currency).toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("balance") || sub.equals("bal")) {
            balance(sender, label, args);
            return true;
        }
        if (sub.equals("pay")) {
            pay(sender, label, args);
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
                text().send(sender, section + ".usage-giveall", ph(label));
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
            text().send(sender, section + ".usage-" + sub, ph(label));
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

    private void balance(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            if (!(sender instanceof Player p)) {
                text().send(sender, section + ".usage-balance-console", ph(label));
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

    // ------------------------------------------------------------------ pay

    /** /coin pay <player> <amount>: from the sender's balance to another player's, minus the tax. */
    private void pay(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            text().send(sender, "general.player-only", null);
            return;
        }
        ShopConfig.Pay rules = plugin.config().pay(currency);
        if (!rules.enabled()) {
            text().send(player, "pay.disabled", ph(label));
            return;
        }
        if (!player.hasPermission("dreamcoinshop.pay")) {
            text().send(player, "general.no-permission", null);
            return;
        }
        if (args.length < 3) {
            text().send(player, "pay.usage", ph(label));
            return;
        }
        Double amount = amount(player, args[2]);
        if (amount == null) return;
        amount = Math.floor(amount * 100) / 100; // no tiny fractions
        if (amount <= 0 || amount < rules.min()) {
            text().send(player, "pay.too-little", ph(label).set("min", Shop.format(rules.min())));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastPay.get(player.getUniqueId());
        if (last != null && now - last < rules.cooldownSeconds() * 1000L) {
            long wait = (rules.cooldownSeconds() * 1000L - (now - last) + 999) / 1000;
            text().send(player, "pay.cooldown", ph(label).set("seconds", wait));
            return;
        }
        Profile profile = plugin.shop().ready(player);
        if (profile == null) return;
        String targetName = args[1];
        double total = amount;
        plugin.data().resolve(targetName).thenAccept(uuid -> plugin.platform().forPlayer(player, () -> {
            if (!player.isOnline()) return;
            if (uuid == null) {
                text().send(player, "general.unknown-player", Ph.of("player", targetName));
                return;
            }
            if (uuid.equals(player.getUniqueId())) {
                text().send(player, "pay.self", ph(label));
                return;
            }
            if (!plugin.data().charge(profile, currency, total)) {
                text().send(player, "pay.not-enough", ph(label));
                plugin.sounds().play(player, "purchase-fail");
                return;
            }
            lastPay.put(player.getUniqueId(), System.currentTimeMillis());
            double tax = Math.floor(total * rules.taxPercent()) / 100;
            double received = total - tax;
            plugin.data().addAnywhere(uuid, currency, received);
            Player target = Bukkit.getPlayer(uuid);
            String name = target != null ? target.getName() : targetName;
            Ph done = ph(label).set("player", name).set("amount", Shop.format(total))
                    .set("received", Shop.format(received)).set("tax", Shop.format(tax));
            text().send(player, tax > 0 ? "pay.sent-taxed" : "pay.sent", done);
            plugin.sounds().play(player, "purchase-success");
            if (target != null) {
                text().send(target, "pay.received", ph(label).set("player", player.getName()).set("amount", Shop.format(received)));
                plugin.sounds().play(target, "pay-received");
            }
            if (plugin.config().logPurchases) {
                plugin.getLogger().info(player.getName() + " paid " + Shop.format(total) + " " + currency.column + " to " + name
                        + (tax > 0 ? " (tax " + Shop.format(tax) + ")" : ""));
            }
        }));
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
        text().send(sender, section + ".help-header", ph(label));
        String l = "/" + label;
        List<String> lines = new ArrayList<>(List.of(l + " balance [player]"));
        if (plugin.config().pay(currency).enabled() && sender.hasPermission("dreamcoinshop.pay")) lines.add(l + " pay <player> <amount>");
        if (sender.hasPermission("dreamcoinshop.admin")) {
            lines.addAll(List.of(l + " give <player> <amount>", l + " giveall <amount>", l + " remove <player> <amount>",
                    l + " set <player> <amount>", "/coinshop settings", "/coinshop sale", "/coinshop reload"));
        }
        lines.add("/coinshop");
        for (String line : lines) text().sendMini(sender, "<yellow>" + Text.escape(line), null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBCOMMANDS) {
                if (!PUBLIC.contains(sub) && !sender.hasPermission("dreamcoinshop.admin")) continue;
                if (sub.equals("pay") && (!plugin.config().pay(currency).enabled() || !sender.hasPermission("dreamcoinshop.pay"))) continue;
                if (sub.startsWith(partial)) out.add(sub);
            }
        } else if (args.length == 2 && WITH_PLAYER.contains(args[0].toLowerCase(Locale.ROOT))) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) out.add(p.getName());
        }
        return out;
    }
}
