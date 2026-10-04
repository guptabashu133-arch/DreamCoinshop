package org.mydreamduels.dreamcoinshop.listeners;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.config.MessagesConfig;
import org.mydreamduels.dreamcoinshop.gui.CoinShopGUI;

public class SellwandListener implements Listener {
    private final Dreamcoinshop plugin;

    public SellwandListener(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = event.getItem();
        if (hand == null || !hand.hasItemMeta()) {
            return;
        }
        ItemMeta meta = hand.getItemMeta();
        if (!meta.getPersistentDataContainer().has(CoinShopGUI.WAND_MULTIPLIER, PersistentDataType.DOUBLE)) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        BlockState blockState = block.getState();
        if (!(blockState instanceof Container)) {
            return;
        }
        Container container = (Container) blockState;
        MessagesConfig msg = this.plugin.getMessages();
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
        double multiplier = meta.getPersistentDataContainer().getOrDefault(CoinShopGUI.WAND_MULTIPLIER, PersistentDataType.DOUBLE, 1.0);
        int usesLeft = meta.getPersistentDataContainer().getOrDefault(CoinShopGUI.WAND_USES, PersistentDataType.INTEGER, 0);
        if (usesLeft <= 0) {
            event.getPlayer().sendMessage(msg.get("sellwand.no-uses", new TagResolver[0]));
            this.plugin.getSounds().play(event.getPlayer(), "sellwand-empty");
            return;
        }
        Inventory inv = container.getInventory();
        double total = 0.0;
        boolean soldAnything = false;
        for (int i = 0; i < inv.getSize(); ++i) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            double unitPrice = this.plugin.getShopConfig().getSellPrice(item.getType().name());
            if (unitPrice <= 0.0) continue;
            total += unitPrice * item.getAmount() * multiplier;
            inv.setItem(i, null);
            soldAnything = true;
        }
        if (!soldAnything) {
            event.getPlayer().sendMessage(msg.get("sellwand.nothing-sellable", new TagResolver[0]));
            this.plugin.getSounds().play(event.getPlayer(), "sellwand-empty");
            return;
        }
        this.plugin.getCoinsManager().add(event.getPlayer().getUniqueId(), total);
        event.getPlayer().sendMessage(msg.get("sellwand.sold", MessagesConfig.ph("amount", Math.round(total))));
        this.plugin.getSounds().play(event.getPlayer(), "sellwand-sell");
        if (--usesLeft <= 0) {
            hand.setAmount(hand.getAmount() - 1);
            event.getPlayer().sendMessage(msg.get("sellwand.broke", new TagResolver[0]));
            this.plugin.getSounds().play(event.getPlayer(), "sellwand-break");
        } else {
            meta.getPersistentDataContainer().set(CoinShopGUI.WAND_USES, PersistentDataType.INTEGER, usesLeft);
            meta.lore(List.of(
                    meta.lore() != null && !meta.lore().isEmpty() ? meta.lore().get(0) : Component.text("Multiplier: " + multiplier + "x", NamedTextColor.GREEN),
                    Component.text("Uses left: " + usesLeft, (TextColor) NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                    Component.text("Right click a chest to sell its contents!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            hand.setItemMeta(meta);
        }
    }
}
