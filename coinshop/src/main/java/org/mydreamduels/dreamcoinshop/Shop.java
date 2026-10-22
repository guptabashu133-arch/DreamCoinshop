package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/**
 * What every shop button does. Both menu types (dialog and chest) call these, so they always
 * behave the same. Safe to call from the player's thread.
 */
final class Shop {

    /** Same keys as version 1, so wands bought before the update keep working. */
    static final NamespacedKey WAND_MULTIPLIER = new NamespacedKey("dreamcoinshop", "wand_multiplier");
    static final NamespacedKey WAND_USES = new NamespacedKey("dreamcoinshop", "wand_uses");
    /** A random id per wand, so two wands never stack and share one use counter. */
    static final NamespacedKey WAND_ID = new NamespacedKey("dreamcoinshop", "wand_id");

    private final Dreamcoinshop plugin;
    private final Platform platform;

    Shop(Dreamcoinshop plugin, Platform platform) {
        this.plugin = plugin;
        this.platform = platform;
    }

    private Text text() {
        return plugin.text();
    }

    /** The player's data if the shop may be used right now; otherwise tells them why not. */
    Profile ready(Player player) {
        if (!plugin.data().isReady()) {
            text().send(player, "general.database-down", null);
            return null;
        }
        Profile p = plugin.data().profile(player);
        if (!p.isLoaded()) {
            text().send(player, "shop.loading", null);
            return null;
        }
        return p;
    }

    static String format(double amount) {
        return amount == Math.floor(amount) && !Double.isInfinite(amount) ? String.valueOf((long) amount) : String.valueOf(amount);
    }

    private void log(Player p, String what, double price, Currency c) {
        if (plugin.config().logPurchases) {
            plugin.getLogger().info(p.getName() + " bought " + what + " for " + format(price) + " " + c.column);
        }
    }

    // ------------------------------------------------------------------ cosmetics

    /** Price and display name of a colour / glow / tag / gradient / flag. */
    private record Thing(String display, double price) {
    }

    private Thing thing(Category c, String key) {
        if (c == Category.FLAG) {
            Flags.Country country = plugin.flags().get(key);
            return country == null || !Flags.sold(plugin.config(), country.code()) ? null
                    : new Thing(country.name() + " flag", plugin.config().flagPrice);
        }
        ShopConfig.Item item = plugin.config().item(c, key);
        return item == null ? null : new Thing(Text.plain(item.display()), item.price());
    }

    /**
     * A click on a colour / glow / tag / gradient / flag: buys it if not owned (and equips it if
     * shop.equip-on-purchase), otherwise equips it, or turns it off if it was already on.
     */
    void clickCosmetic(Player player, Category category, String key) {
        Profile p = ready(player);
        if (p == null) return;
        Thing thing = thing(category, key);
        if (thing == null) return;
        Ph ph = Ph.of("item", thing.display());
        DataManager data = plugin.data();
        if (!p.owns(category, key)) {
            if (!data.charge(p, Currency.COINS, thing.price())) {
                text().send(player, "shop.not-enough-coins", null);
                plugin.sounds().play(player, "purchase-fail");
                return;
            }
            data.unlock(p, category, key);
            log(player, category.id + " " + key, thing.price(), Currency.COINS);
            if (plugin.config().equipOnPurchase) {
                data.setActive(p, category, key);
                text().send(player, "shop.unlocked-equipped", ph);
            } else {
                text().send(player, "shop.unlocked", ph);
            }
            plugin.sounds().play(player, "purchase-success");
        } else if (key.equals(p.active(category))) {
            data.setActive(p, category, null);
            text().send(player, "shop.unequipped", ph);
            plugin.sounds().play(player, "unequip");
        } else {
            data.setActive(p, category, key);
            text().send(player, "shop.equipped", ph);
            plugin.sounds().play(player, "equip");
        }
        if (category == Category.GLOW || category == Category.GRADIENT) plugin.cosmetics().refresh(player);
    }

    void buyPerk(Player player, String key) {
        Profile p = ready(player);
        if (p == null) return;
        ShopConfig.Item perk = plugin.config().item(Category.PERK, key);
        if (perk == null || p.owns(Category.PERK, key)) return;
        if (!plugin.data().charge(p, Currency.COINS, perk.price())) {
            text().send(player, "shop.not-enough-coins", null);
            plugin.sounds().play(player, "purchase-fail");
            return;
        }
        plugin.data().unlock(p, Category.PERK, key);
        plugin.cosmetics().applyPerks(player);
        log(player, "perk " + key, perk.price(), Currency.COINS);
        text().send(player, "shop.unlocked-perk", Ph.of("item", Text.plain(perk.display())));
        plugin.sounds().play(player, "purchase-success");
    }

    // ------------------------------------------------------------------ wands

    void buySellwand(Player player, String key) {
        Profile p = ready(player);
        if (p == null) return;
        ShopConfig.Wand wand = plugin.config().sellwands.get(key);
        if (wand == null) return;
        if (!plugin.data().charge(p, Currency.COINS, wand.price())) {
            text().send(player, "shop.not-enough-coins", null);
            plugin.sounds().play(player, "purchase-fail");
            return;
        }
        give(player, sellwandItem(player, wand.display(), wand.multiplier(), wand.uses()));
        log(player, "sellwand " + key, wand.price(), Currency.COINS);
        text().send(player, "shop.purchased-wand", Ph.of("item", Text.plain(wand.display())));
        plugin.sounds().play(player, "purchase-success");
    }

    ItemStack sellwandItem(Player viewer, String display, double multiplier, int uses) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        Ph ph = Ph.of("multiplier", format(multiplier), "uses", uses).snippet("name", display);
        text().itemText(meta, viewer, text().raw("sellwand.item-name"), List.of(text().raw("sellwand.item-lore")), ph);
        meta.getPersistentDataContainer().set(WAND_MULTIPLIER, PersistentDataType.DOUBLE, multiplier);
        meta.getPersistentDataContainer().set(WAND_USES, PersistentDataType.INTEGER, uses);
        meta.getPersistentDataContainer().set(WAND_ID, PersistentDataType.STRING, java.util.UUID.randomUUID().toString());
        item.setItemMeta(meta);
        return item;
    }

    /** Into the inventory; whatever doesn't fit is dropped at the player's feet. Player's thread. */
    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        if (!left.isEmpty()) {
            for (ItemStack rest : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
            text().send(player, "shop.inventory-full", null);
        }
    }

    void buySpawnerWand(Player player, String key) {
        Profile p = ready(player);
        if (p == null) return;
        ShopConfig.Wand wand = plugin.config().spawnerWands.get(key);
        if (wand == null) return;
        if (!plugin.data().charge(p, Currency.COINS, wand.price())) {
            text().send(player, "shop.not-enough-coins", null);
            plugin.sounds().play(player, "purchase-fail");
            return;
        }
        String command = plugin.config().spawnerWandCommand
                .replace("%player%", player.getName())
                .replace("%multiplier%", format(wand.multiplier()) + "x");
        runCommands(List.of(command), ok -> {
            if (ok == null || ok) {
                log(player, "spawner sellwand " + key, wand.price(), Currency.COINS);
                text().send(player, "shop.purchased-wand", Ph.of("item", Text.plain(wand.display())));
                plugin.sounds().play(player, "purchase-success");
            } else {
                refund(player, Currency.COINS, wand.price());
                text().send(player, "shop.give-failed", Ph.of("item", Text.plain(wand.display())));
                plugin.sounds().play(player, "purchase-fail");
            }
        });
    }

    // ------------------------------------------------------------------ orbshop

    void buyOrbItem(Player player, String key) {
        Profile p = ready(player);
        if (p == null) return;
        ShopConfig.OrbItem item = plugin.config().orbItems.get(key);
        if (item == null) return;
        if (!plugin.data().charge(p, Currency.ORBS, item.price())) {
            text().send(player, "orbs.not-enough-orbs", null);
            plugin.sounds().play(player, "purchase-fail");
            return;
        }
        List<String> commands = item.commands().stream().map(c -> c.replace("%player%", player.getName())).toList();
        runCommands(commands, ok -> {
            if (ok == null) {
                log(player, "orbshop " + key + " (partly given)", item.price(), Currency.ORBS);
                text().send(player, "shop.give-partly", Ph.of("item", Text.plain(item.display())));
                plugin.sounds().play(player, "purchase-fail");
            } else if (ok) {
                log(player, "orbshop " + key, item.price(), Currency.ORBS);
                text().send(player, "orbs.purchased", Ph.of("item", Text.plain(item.display()), "price", format(item.price())));
                plugin.sounds().play(player, "purchase-success");
            } else {
                refund(player, Currency.ORBS, item.price());
                text().send(player, "shop.give-failed", Ph.of("item", Text.plain(item.display())));
                plugin.sounds().play(player, "purchase-fail");
            }
        });
    }

    private void refund(Player player, Currency c, double amount) {
        plugin.data().addAnywhere(player.getUniqueId(), c, amount);
    }

    /**
     * Console commands on the main/global thread, stopping at the first one that fails. Reports
     * true when all ran, false when the first one failed (nothing was given - refund), and null
     * when a later one failed (part of it was given - no refund, the console log says what).
     */
    private void runCommands(List<String> commands, java.util.function.Consumer<Boolean> done) {
        platform.global(() -> {
            Boolean ok = true;
            for (int i = 0; i < commands.size(); i++) {
                String command = commands.get(i);
                boolean ran;
                try {
                    ran = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                } catch (Exception ex) {
                    ran = false;
                    plugin.getLogger().warning("\"" + command + "\" failed: " + ex);
                }
                if (!ran) {
                    plugin.getLogger().warning("The command \"" + command + "\" didn't run (is the plugin for it installed?)");
                    if (i > 0) plugin.getLogger().warning("The commands before it did run, so no refund was given - check the purchase by hand.");
                    ok = i == 0 ? false : null;
                    break;
                }
            }
            done.accept(ok);
        });
    }

    // ------------------------------------------------------------------ store

    void visitStore(Player player) {
        String url = plugin.config().storeUrl;
        if (url == null || url.isBlank()) {
            text().send(player, "buycoins.no-url-configured", null);
            return;
        }
        text().sendLink(player, text().raw("buycoins.visit-store"), Ph.of("url", url), url);
        plugin.sounds().play(player, "buycoins-select");
    }
}
