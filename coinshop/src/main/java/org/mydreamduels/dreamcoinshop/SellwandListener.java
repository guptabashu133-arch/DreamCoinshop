package org.mydreamduels.dreamcoinshop;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Right click a chest (any container) with a Sellwand: sells everything that has a price in
 * sell-prices and pays Coins. Runs on the thread that owns the block (Folia too).
 */
final class SellwandListener implements Listener {

    private final Dreamcoinshop plugin;
    private final java.util.Map<java.util.UUID, Long> lastUse = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean warnedVault;

    SellwandListener(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        ItemStack hand = event.getItem();
        if (hand == null || !hand.hasItemMeta()) return;
        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Shop.WAND_MULTIPLIER, PersistentDataType.DOUBLE)) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        ShopConfig cfg = plugin.config();
        if (!cfg.sellContainer(block.getType().name())) return;
        if (cfg.sellRequireSneak && !event.getPlayer().isSneaking()) return;
        BlockState state = block.getState();
        if (!(state instanceof Container container)) return;

        // A protection plugin that already denied opening this chest also denies selling it
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        Player player = event.getPlayer();
        Text text = plugin.text();
        if (!plugin.config().section("sellwands")) {
            text.send(player, "shop.section-off", null);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < cfg.sellCooldownMs) return;
        lastUse.put(player.getUniqueId(), now);
        Profile profile = plugin.shop().ready(player);
        if (profile == null) return;
        double multiplier = pdc.getOrDefault(Shop.WAND_MULTIPLIER, PersistentDataType.DOUBLE, 1.0);
        int uses = pdc.getOrDefault(Shop.WAND_USES, PersistentDataType.INTEGER, 0);
        boolean unlimited = uses < 0;
        if (uses == 0) {
            text.send(player, "sellwand.no-uses", null);
            plugin.sounds().play(player, "sellwand-empty");
            return;
        }

        Inventory inv = container.getInventory();
        double total = 0;
        int count = 0;
        boolean sold = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (custom(item)) continue; // renamed / custom items are never sold
            double price = plugin.config().sellPrice(item.getType().name());
            if (price <= 0) continue;
            total += price * item.getAmount() * multiplier;
            count += item.getAmount();
            inv.setItem(i, null);
            sold = true;
        }
        if (!sold) {
            text.send(player, "sellwand.nothing-sellable", null);
            plugin.sounds().play(player, "sellwand-empty");
            return;
        }
        double paid = Math.round(total * 100) / 100.0;
        if (cfg.sellPayout == ShopConfig.Payout.VAULT && pay(player, paid)) {
            text.send(player, "sellwand.sold-money", Ph.of("amount", Shop.format(paid), "items", count));
        } else {
            plugin.data().add(profile, Currency.COINS, paid);
            text.send(player, "sellwand.sold", Ph.of("amount", Shop.format(paid), "items", count));
        }
        plugin.sounds().play(player, "sellwand-sell");
        if (plugin.config().logPurchases) plugin.getLogger().info(player.getName() + " sold " + count + " items with a sellwand for " + Shop.format(paid));

        if (unlimited) return;
        uses--;
        if (uses <= 0) {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
            text.send(player, "sellwand.broke", null);
            plugin.sounds().play(player, "sellwand-break");
            return;
        }
        pdc.set(Shop.WAND_USES, PersistentDataType.INTEGER, uses);
        Ph ph = Ph.of("multiplier", Shop.format(multiplier), "uses", uses)
                .snippet("name", meta.hasDisplayName() ? Text.escape(org.bukkit.ChatColor.stripColor(meta.getDisplayName())) : "Sellwand");
        text.itemText(meta, player, meta.hasDisplayName() ? null : text.raw("sellwand.item-name"),
                List.of(text.raw("sellwand.item-lore")), ph);
        hand.setItemMeta(meta);
        player.getInventory().setItemInMainHand(hand);
    }

    /** Into the server economy (Vault). False (and coins are paid instead) if there's none. */
    private boolean pay(Player player, double amount) {
        if (!VaultHook.available()) {
            if (!warnedVault) {
                warnedVault = true;
                plugin.getLogger().warning("sellwand-settings.payout is VAULT, but Vault or an economy plugin isn't installed - paying Coins.");
            }
            return false;
        }
        plugin.platform().global(() -> VaultHook.deposit(player, amount)); // economy plugins expect the main thread
        return true;
    }

    /** Items another plugin made or a player renamed: name, lore, model data or plugin data. */
    private static boolean custom(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() || meta.hasLore() || meta.hasCustomModelData()
                || !meta.getPersistentDataContainer().getKeys().isEmpty();
    }
}
