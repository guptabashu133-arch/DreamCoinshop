package org.mydreamduels.dreamcoinshop.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.dialog.DialogLike;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.config.MessagesConfig;
import org.mydreamduels.dreamcoinshop.data.PlayerProfile;
import org.mydreamduels.dreamcoinshop.shopdata.ShopConfig;
import org.mydreamduels.dreamcoinshop.shopdata.ShopOptions;

public class CoinShopGUI {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final NamespacedKey WAND_MULTIPLIER = new NamespacedKey("dreamcoinshop", "wand_multiplier");
    public static final NamespacedKey WAND_USES = new NamespacedKey("dreamcoinshop", "wand_uses");
    public static final NamespacedKey SPAWNER_WAND_MULTIPLIER = new NamespacedKey("dreamcoinshop", "spawner_wand_multiplier");
    public static final NamespacedKey SPAWNER_WAND_USES = new NamespacedKey("dreamcoinshop", "spawner_wand_uses");
    private final Dreamcoinshop plugin;
    private static final int TOP_COINS_LIMIT = 10;
    private static final int TOP_ORBS_LIMIT = 8;

    // Icon lookups keyed by the option's config key (chatcolors/glows/gradients keys in
    // config.yml) - a dye/material that visually matches that colour. DEFAULT_DYE is used for
    // any key not listed here so a newly-added colour still gets *some* icon.
    private static final Material DEFAULT_DYE = Material.WHITE_DYE;
    private static final Map<String, Material> CHAT_COLOR_ICONS = Map.ofEntries(
            Map.entry("red", Material.RED_DYE), Map.entry("gold", Material.ORANGE_DYE),
            Map.entry("yellow", Material.YELLOW_DYE), Map.entry("green", Material.LIME_DYE),
            Map.entry("aqua", Material.LIGHT_BLUE_DYE), Map.entry("blue", Material.BLUE_DYE),
            Map.entry("light_purple", Material.MAGENTA_DYE), Map.entry("white", Material.WHITE_DYE),
            Map.entry("dark_red", Material.RED_DYE), Map.entry("dark_green", Material.GREEN_DYE),
            Map.entry("dark_aqua", Material.CYAN_DYE), Map.entry("dark_purple", Material.PURPLE_DYE),
            Map.entry("sunset_gradient", Material.ORANGE_DYE), Map.entry("ocean_gradient", Material.LIGHT_BLUE_DYE),
            Map.entry("toxic_gradient", Material.LIME_DYE)
    );
    private static final Map<String, Material> GLOW_ICONS = Map.of(
            "red", Material.RED_DYE, "gold", Material.ORANGE_DYE, "green", Material.LIME_DYE,
            "aqua", Material.LIGHT_BLUE_DYE, "blue", Material.BLUE_DYE, "purple", Material.PURPLE_DYE,
            "white", Material.WHITE_DYE, "rainbow", Material.FIREWORK_STAR
    );
    private static final Map<String, Material> GRADIENT_ICONS = Map.of(
            "sunset", Material.ORANGE_DYE, "ocean", Material.LIGHT_BLUE_DYE, "fire", Material.BLAZE_POWDER,
            "toxic", Material.LIME_DYE, "galaxy", Material.PURPLE_DYE, "candy", Material.PINK_DYE,
            "gold", Material.GOLD_NUGGET, "ice", Material.BLUE_ICE
    );
    private static final Map<String, Material> TAG_ICONS = Map.ofEntries(
            Map.entry("phoenix", Material.BLAZE_POWDER), Map.entry("vip", Material.DIAMOND),
            Map.entry("queen", Material.PINK_DYE), Map.entry("pro", Material.EMERALD),
            Map.entry("legend", Material.GOLDEN_APPLE), Map.entry("god", Material.NETHER_STAR),
            Map.entry("king", Material.GOLD_BLOCK), Map.entry("angel", Material.PINK_TULIP),
            Map.entry("boss", Material.REDSTONE_BLOCK), Map.entry("dream", Material.AMETHYST_SHARD)
    );
    private static final Map<String, Material> PERK_ICONS = Map.of(
            "craft", Material.CRAFTING_TABLE, "smithingtable", Material.SMITHING_TABLE,
            "grindstone", Material.GRINDSTONE, "anvil", Material.ANVIL,
            "enderchest", Material.ENDER_EYE, "nick", Material.NAME_TAG
    );

    public CoinShopGUI(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        IconSupport.setViewer(player);
        double balance = this.plugin.getCoinsManager().getBalance(player.getUniqueId());
        ShopConfig cfg = this.plugin.getShopConfig();
        ArrayList<ActionButton> buttons = new ArrayList<>();
        buttons.add(this.categoryButton(Material.NAME_TAG, "Chat Colour", cfg.menuDescription("chatcolors", "Unlock colours & gradients for your chat messages"), this::openChatColors));
        buttons.add(this.categoryButton(Material.GLOWSTONE_DUST, "Glow", cfg.menuDescription("glows", "Unlock a glowing name colour"), this::openGlows));
        buttons.add(this.categoryButton(Material.NETHER_STAR, "Perks", cfg.menuDescription("perks", "Unlock handy commands"), this::openPerks));
        buttons.add(this.categoryButton(Material.PAPER, "Tags", cfg.menuDescription("tags", "Unlock a tag shown next to your name in chat"), this::openTags));
        buttons.add(this.categoryButton(Material.FIREWORK_STAR, "Name Gradient", cfg.menuDescription("gradients", "Unlock a gradient colour for your name"), this::openGradients));
        buttons.add(this.categoryButton(Material.BLAZE_ROD, "Sellwands", cfg.menuDescription("sellwands", "Buy wands that instantly sell a chest's contents"), this::openSellwands));
        buttons.add(this.categoryButton(Material.SPAWNER, "Spawner Sellwands", cfg.menuDescription("spawnerwands", "Buy wands that instantly sell spawners at a bonus rate"), this::openSpawnerWands));
        buttons.add(this.categoryButton(Material.GOLD_INGOT, "Buy Coins", cfg.menuDescription("buycoins", "Top up your Coins balance with real money"), this::openBuyCoins));
        buttons.add(this.categoryButton(Material.DIAMOND, "Top Coins", cfg.menuDescription("topcoins", "See who has the most Coins on the server"), this::openTopCoins));
        buttons.add(this.categoryButton(Material.ENDER_PEARL, "Orbshop", cfg.menuDescription("orbshop", "Spend your Orbs on spawners"), this::openOrbShop));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(MM.deserialize(cfg.menuTitle("main", cfg.getGuiTitle())))
                        .body(List.of(DialogBody.plainMessage(Component.text("Your balance: ", NamedTextColor.GRAY).append(Component.text((long) balance + " coins", NamedTextColor.GOLD)))))
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        player.showDialog((DialogLike) dialog);
    }

    /**
     * Bedrock/PE (Geyser) fix: the Bedrock client drops a new form if it arrives while it is
     * still closing the previous one. That is why "Back" (and other buttons) sometimes did
     * nothing on PE. A few ticks of delay before re-opening makes it reliable. Java players
     * still get the instant, seamless swap.
     */
    private void openLater(Player player, Consumer<Player> target) {
        if (IconSupport.isBedrock(player)) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (player.isOnline()) {
                    target.accept(player);
                }
            }, 5L);
        } else {
            target.accept(player);
        }
    }

    private void reopenLater(Player player, Runnable reopen) {
        Bukkit.getScheduler().runTaskLater(this.plugin, reopen, IconSupport.isBedrock(player) ? 5L : 1L);
    }

    /**
     * Bedrock has no per-character gradients - Geyser flattens them into a mess of colour codes,
     * and the resource pack can't match an icon on text like that. So Bedrock players get the
     * plain display text in one solid colour; Java players keep the full gradient.
     */
    private static Component bedrockLabel(Player viewer, Component fancy, String plain, String hex) {
        if (!IconSupport.isBedrock(viewer)) {
            return fancy;
        }
        TextColor color = hex != null ? TextColor.fromHexString(hex) : null;
        return color != null ? Component.text(plain, color) : Component.text(plain, NamedTextColor.WHITE);
    }

    private ActionButton categoryButton(Material icon, String name, String description, Consumer<Player> openAction) {
        return ActionButton.builder(IconSupport.withIcon(icon, Component.text(name, NamedTextColor.YELLOW)))
                .tooltip(Component.text(description, NamedTextColor.GRAY))
                .width(200)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        CoinShopGUI.this.openLater(p, openAction);
                    }
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();
    }

    public void openChatColors(Player player) {
        IconSupport.setViewer(player);
        PlayerProfile profile = this.plugin.getPlayerDataManager().get(player.getUniqueId());
        ArrayList<ActionButton> buttons = new ArrayList<>();
        for (ShopOptions.ChatColorOption opt : this.plugin.getShopConfig().getChatColors().values()) {
            Component label;
            if (opt.isGradient()) {
                String tag = "<gradient:" + opt.gradient().get(0) + ":" + opt.gradient().get(1) + ">" + opt.display() + "</gradient>";
                label = MM.deserialize(tag);
                label = bedrockLabel(player, label, opt.display(), opt.gradient().get(0));
            } else {
                NamedTextColor color = opt.colorHex() != null ? (NamedTextColor) TextColor.fromHexString(opt.colorHex()) : NamedTextColor.WHITE;
                label = Component.text(opt.display(), (TextColor) color);
            }
            Material icon = CHAT_COLOR_ICONS.getOrDefault(opt.key(), DEFAULT_DYE);
            buttons.add(this.cosmeticButton(icon, label, opt.display(), opt.price(), profile.getUnlockedChatColors(), opt.key(), profile::getActiveChatColor, profile::setActiveChatColor, () -> this.openChatColors(player), false));
        }
        buttons.add(this.backButton(this::openMain));
        this.showMenu(player, "chatcolors", "<gold><bold>Chat Colour", buttons);
    }

    public void openGlows(Player player) {
        IconSupport.setViewer(player);
        PlayerProfile profile = this.plugin.getPlayerDataManager().get(player.getUniqueId());
        ArrayList<ActionButton> buttons = new ArrayList<>();
        for (ShopOptions.GlowOption opt : this.plugin.getShopConfig().getGlows().values()) {
            TextComponent label = Component.text(opt.display(), NamedTextColor.WHITE);
            Material icon = GLOW_ICONS.getOrDefault(opt.key(), DEFAULT_DYE);
            buttons.add(this.cosmeticButton(icon, label, opt.display(), opt.price(), profile.getUnlockedGlows(), opt.key(), profile::getActiveGlow, profile::setActiveGlow, () -> this.openGlows(player), true));
        }
        buttons.add(this.backButton(this::openMain));
        this.showMenu(player, "glows", "<gold><bold>Glow", buttons);
    }

    public void openTags(Player player) {
        IconSupport.setViewer(player);
        PlayerProfile profile = this.plugin.getPlayerDataManager().get(player.getUniqueId());
        ArrayList<ActionButton> buttons = new ArrayList<>();
        for (ShopOptions.TagOption opt : this.plugin.getShopConfig().getTags().values()) {
            Component preview = Component.text(player.getName() + " ", NamedTextColor.WHITE).append(MM.deserialize(opt.miniMessage()));
            preview = bedrockLabel(player, preview, player.getName() + " " + opt.display(), "#FFD700");
            Material icon = TAG_ICONS.getOrDefault(opt.key(), Material.PAPER);
            buttons.add(this.cosmeticButton(icon, preview, opt.display(), opt.price(), profile.getUnlockedTags(), opt.key(), profile::getActiveTag, profile::setActiveTag, () -> this.openTags(player), false));
        }
        buttons.add(this.backButton(this::openMain));
        this.showMenu(player, "tags", "<gold><bold>Tags", buttons);
    }

    public void openGradients(Player player) {
        IconSupport.setViewer(player);
        PlayerProfile profile = this.plugin.getPlayerDataManager().get(player.getUniqueId());
        ArrayList<ActionButton> buttons = new ArrayList<>();
        for (ShopOptions.GradientOption opt : this.plugin.getShopConfig().getNameGradients().values()) {
            String tag = "<gradient:" + opt.from() + ":" + opt.to() + ">" + opt.display() + "</gradient>";
            Component label = MM.deserialize(tag);
            label = bedrockLabel(player, label, opt.display(), opt.from());
            Material icon = GRADIENT_ICONS.getOrDefault(opt.key(), DEFAULT_DYE);
            buttons.add(this.cosmeticButton(icon, label, opt.display(), opt.price(), profile.getUnlockedGradients(), opt.key(), profile::getActiveGradient, profile::setActiveGradient, () -> this.openGradients(player), true));
        }
        buttons.add(this.backButton(this::openMain));
        this.showMenu(player, "gradients", "<gold><bold>Name Gradient", buttons);
    }

    public void openPerks(Player player) {
        IconSupport.setViewer(player);
        PlayerProfile profile = this.plugin.getPlayerDataManager().get(player.getUniqueId());
        MessagesConfig msg = this.plugin.getMessages();
        ArrayList<ActionButton> buttons = new ArrayList<>();
        for (ShopOptions.PerkOption opt : this.plugin.getShopConfig().getPerks().values()) {
            boolean owned = profile.getUnlockedPerks().contains(opt.key());
            Component label = Component.text(opt.display(), NamedTextColor.YELLOW)
                    .append(owned ? Component.text(" - OWNED", NamedTextColor.GREEN) : Component.text(" - " + (int) opt.price() + " coins", NamedTextColor.GRAY));
            label = IconSupport.withIcon(PERK_ICONS.getOrDefault(opt.key(), Material.NETHER_STAR), label);
            ActionButton button = ActionButton.builder(label)
                    .tooltip(owned ? msg.get("shop.already-unlocked", new TagResolver[0]) : Component.text("Click to purchase", NamedTextColor.YELLOW))
                    .width(220)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player p)) {
                            return;
                        }
                        PlayerProfile prof = this.plugin.getPlayerDataManager().get(p.getUniqueId());
                        if (prof.getUnlockedPerks().contains(opt.key())) {
                            this.reopenLater(p, () -> this.openPerks(p));
                            return;
                        }
                        if (this.plugin.getCoinsManager().remove(p.getUniqueId(), opt.price())) {
                            prof.getUnlockedPerks().add(opt.key());
                            this.plugin.getPlayerDataManager().save(prof);
                            this.plugin.applyPerkPermissions(p);
                            p.sendMessage(msg.get("shop.unlocked-perk", MessagesConfig.ph("item", opt.display())));
                            this.plugin.getSounds().play(p, "purchase-success");
                        } else {
                            p.sendMessage(msg.get("shop.not-enough-coins", new TagResolver[0]));
                            this.plugin.getSounds().play(p, "purchase-fail");
                        }
                        this.reopenLater(p, () -> this.openPerks(p));
                    }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                    .build();
            buttons.add(button);
        }
        buttons.add(this.backButton(this::openMain));
        this.showMenu(player, "perks", "<gold><bold>Perks", buttons);
    }

    public void openSpawnerWands(Player player) {
        IconSupport.setViewer(player);
        MessagesConfig msg = this.plugin.getMessages();
        ShopConfig cfg = this.plugin.getShopConfig();
        ArrayList<ActionButton> buttons = new ArrayList<>();
        for (ShopOptions.SpawnerWandTier tier : cfg.getSpawnerWands().values()) {
            Component label = Component.text(tier.display() + " (" + tier.multiplier() + "x)", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(" - " + (int) tier.price() + " coins", NamedTextColor.GRAY));
            label = IconSupport.withIcon(Material.SPAWNER, label);
            ActionButton button = ActionButton.builder(label)
                    .tooltip(Component.text("Click to purchase", NamedTextColor.YELLOW))
                    .width(220)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player p)) {
                            return;
                        }
                        if (this.plugin.getCoinsManager().remove(p.getUniqueId(), tier.price())) {
                            boolean ok;
                            String command = cfg.getSpawnerWandGiveCommand().replace("%player%", p.getName()).replace("%multiplier%", this.stripTrailingZero(tier.multiplier()) + "x");
                            try {
                                ok = Bukkit.dispatchCommand((CommandSender) Bukkit.getConsoleSender(), command);
                            } catch (Exception ex) {
                                ok = false;
                                this.plugin.getLogger().warning("[SpawnerWands] \"" + command + "\" threw an exception: " + ex);
                            }
                            this.plugin.getLogger().info("[SpawnerWands] " + p.getName() + " bought " + tier.display() + " -> ran \"" + command + "\" (dispatchCommand returned " + ok + ")");
                            if (ok) {
                                p.sendMessage(msg.get("shop.purchased-wand", MessagesConfig.ph("item", tier.display())));
                                this.plugin.getSounds().play(p, "purchase-success");
                            } else {
                                this.plugin.getCoinsManager().add(p.getUniqueId(), tier.price());
                                p.sendMessage(Component.text("Couldn't give your " + tier.display() + " (server command didn't run) - your coins were refunded.", NamedTextColor.RED));
                                this.plugin.getSounds().play(p, "purchase-fail");
                            }
                        } else {
                            p.sendMessage(msg.get("shop.not-enough-coins", new TagResolver[0]));
                            this.plugin.getSounds().play(p, "purchase-fail");
                        }
                        this.reopenLater(p, () -> this.openSpawnerWands(p));
                    }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                    .build();
            buttons.add(button);
        }
        buttons.add(this.backButton(this::openMain));
        this.showMenu(player, "spawnerwands", "<gold><bold>Spawner Sellwands", buttons);
    }

    private String stripTrailingZero(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    public void openSellwands(Player player) {
        IconSupport.setViewer(player);
        ArrayList<ActionButton> buttons = new ArrayList<>();
        for (ShopOptions.SellwandTier tier : this.plugin.getShopConfig().getSellwands().values()) {
            buttons.add(this.wandButton(tier.display(), tier.multiplier(), tier.uses(), tier.price(), "Right click a chest with this wand to sell its contents", () -> this.giveSellwand(player, tier), () -> this.openSellwands(player)));
        }
        buttons.add(this.backButton(this::openMain));
        this.showMenu(player, "sellwands", "<gold><bold>Sellwands", buttons);
    }

    private void giveSellwand(Player player, ShopOptions.SellwandTier tier) {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text(tier.display(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Multiplier: " + tier.multiplier() + "x", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Uses left: " + tier.uses(), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.text("Right click a chest to sell its contents!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(WAND_MULTIPLIER, PersistentDataType.DOUBLE, tier.multiplier());
        meta.getPersistentDataContainer().set(WAND_USES, PersistentDataType.INTEGER, tier.uses());
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
    }

    private ActionButton wandButton(String display, double multiplier, int uses, double price, String tooltipText, Runnable giveItem, Runnable reopen) {
        MessagesConfig msg = this.plugin.getMessages();
        Component label = Component.text(display + " (" + multiplier + "x | " + uses + " uses)", NamedTextColor.GOLD)
                .append(Component.text(" - " + (int) price + " coins", NamedTextColor.GRAY));
        label = IconSupport.withIcon(Material.BLAZE_ROD, label);
        return ActionButton.builder(label)
                .tooltip(Component.text(tooltipText, NamedTextColor.GRAY))
                .width(240)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    if (this.plugin.getCoinsManager().remove(p.getUniqueId(), price)) {
                        giveItem.run();
                        p.sendMessage(msg.get("shop.purchased-wand", MessagesConfig.ph("item", display)));
                        this.plugin.getSounds().play(p, "purchase-success");
                    } else {
                        p.sendMessage(msg.get("shop.not-enough-coins", new TagResolver[0]));
                        this.plugin.getSounds().play(p, "purchase-fail");
                    }
                    this.reopenLater(p, reopen);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();
    }

    public void openBuyCoins(Player player) {
        IconSupport.setViewer(player);
        MessagesConfig msg = this.plugin.getMessages();
        ShopConfig cfg = this.plugin.getShopConfig();
        ArrayList<Component> body = new ArrayList<>();
        for (ShopOptions.BuyCoinsTier tier : cfg.getBuyCoins().values()) {
            body.add(msg.get("buycoins.tier-line", MessagesConfig.ph("price", tier.priceLabel()), MessagesConfig.ph("coins", tier.coins())));
        }
        ArrayList<ActionButton> buttons = new ArrayList<>();
        ActionButton visitStore = ActionButton.builder(IconSupport.withIcon(Material.GOLD_INGOT, Component.text("Visit Store 🪙", NamedTextColor.GREEN)))
                .tooltip(Component.text("Get a clickable store link in chat", NamedTextColor.GRAY))
                .width(200)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    String url = cfg.getStoreUrl();
                    if (url == null || url.isBlank()) {
                        p.sendMessage(msg.get("buycoins.no-url-configured", new TagResolver[0]));
                        return;
                    }
                    Component link = msg.get("buycoins.visit-store", MessagesConfig.ph("url", url)).clickEvent(ClickEvent.openUrl(url));
                    p.sendMessage(link);
                    this.plugin.getSounds().play(p, "buycoins-select");
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();
        buttons.add(visitStore);
        buttons.add(this.backButton(this::openMain));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(MM.deserialize(cfg.menuTitle("buycoins", "<gold><bold>Buy Coins")))
                        .body(body.stream().map(DialogBody::plainMessage).toList())
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        player.showDialog((DialogLike) dialog);
    }

    public void openTopCoins(Player player) {
        IconSupport.setViewer(player);
        ShopConfig cfg = this.plugin.getShopConfig();
        List<Map.Entry<UUID, Double>> top = this.plugin.getCoinsManager().getTopBalances(TOP_COINS_LIMIT);
        ArrayList<Component> body = new ArrayList<>();
        if (top.isEmpty()) {
            body.add(Component.text("Nobody has any coins yet!", NamedTextColor.GRAY));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Double> entry : top) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getKey());
                String name = target.getName() != null ? target.getName() : "Unknown";
                boolean isViewer = entry.getKey().equals(player.getUniqueId());
                NamedTextColor rankColor = switch (rank) {
                    case 1 -> NamedTextColor.GOLD;
                    case 2 -> NamedTextColor.GRAY;
                    case 3 -> NamedTextColor.YELLOW;
                    default -> NamedTextColor.WHITE;
                };
                Component line = Component.text("#" + rank + " ", rankColor)
                        .append(Component.text(name, isViewer ? NamedTextColor.AQUA : NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text((long) entry.getValue().doubleValue() + " coins", NamedTextColor.GREEN));
                body.add(line);
                ++rank;
            }
            boolean viewerShown = top.stream().anyMatch(e -> e.getKey().equals(player.getUniqueId()));
            if (!viewerShown) {
                double myBalance = this.plugin.getCoinsManager().getBalance(player.getUniqueId());
                body.add(Component.text("...", NamedTextColor.DARK_GRAY));
                body.add(Component.text("You: ", NamedTextColor.AQUA).append(Component.text((long) myBalance + " coins", NamedTextColor.GREEN)));
            }
        }
        ArrayList<ActionButton> buttons = new ArrayList<>();
        buttons.add(this.backButton(this::openMain));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(MM.deserialize(cfg.menuTitle("topcoins", "<gold><bold>Top Coins")))
                        .body(body.stream().map(DialogBody::plainMessage).toList())
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        player.showDialog((DialogLike) dialog);
    }

    public void openOrbShop(Player player) {
        IconSupport.setViewer(player);
        double balance = this.plugin.getOrbsManager().getBalance(player.getUniqueId());
        ShopConfig cfg = this.plugin.getShopConfig();
        MessagesConfig msg = this.plugin.getMessages();
        ArrayList<ActionButton> buttons = new ArrayList<>();
        for (ShopOptions.OrbShopItem item : cfg.getOrbShopItems().values()) {
            Component label = Component.text(item.display(), NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text("  [" + (int) item.price() + " orbs]", NamedTextColor.GRAY));
            label = IconSupport.withIcon(Material.SPAWNER, label);
            ActionButton button = ActionButton.builder(label)
                    .tooltip(Component.text("Click to purchase", NamedTextColor.YELLOW))
                    .width(200)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (!(audience instanceof Player p)) {
                            return;
                        }
                        if (this.plugin.getOrbsManager().remove(p.getUniqueId(), item.price())) {
                            boolean allOk = true;
                            for (String rawCommand : item.commands()) {
                                boolean ok;
                                String command = rawCommand.replace("%player%", p.getName());
                                try {
                                    ok = Bukkit.dispatchCommand((CommandSender) Bukkit.getConsoleSender(), command);
                                } catch (Exception ex) {
                                    ok = false;
                                    this.plugin.getLogger().warning("[Orbshop] \"" + command + "\" threw an exception: " + ex);
                                }
                                this.plugin.getLogger().info("[Orbshop] " + p.getName() + " bought " + item.display() + " -> ran \"" + command + "\" (dispatchCommand returned " + ok + ")");
                                if (!ok) allOk = false;
                            }
                            if (allOk) {
                                p.sendMessage(msg.get("orbs.purchased", MessagesConfig.ph("item", item.display()), MessagesConfig.ph("price", (long) item.price())));
                                this.plugin.getSounds().play(p, "purchase-success");
                            } else {
                                this.plugin.getOrbsManager().add(p.getUniqueId(), item.price());
                                p.sendMessage(Component.text("Couldn't give your " + item.display() + " (server command didn't run) - your orbs were refunded.", NamedTextColor.RED));
                                this.plugin.getSounds().play(p, "purchase-fail");
                            }
                        } else {
                            p.sendMessage(msg.get("orbs.not-enough-orbs", new TagResolver[0]));
                            this.plugin.getSounds().play(p, "purchase-fail");
                        }
                        this.reopenLater(p, () -> this.openOrbShop(p));
                    }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                    .build();
            buttons.add(button);
        }
        buttons.add(this.orbsTopButton());
        buttons.add(this.backButton(this::openMain));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(MM.deserialize(cfg.getOrbShopTitle()))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(Component.text("Your Orbs: ", NamedTextColor.GRAY).append(Component.text((long) balance + " orbs", NamedTextColor.LIGHT_PURPLE)))))
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        player.showDialog((DialogLike) dialog);
    }

    private ActionButton orbsTopButton() {
        return ActionButton.builder(IconSupport.withIcon(Material.ENDER_PEARL, Component.text("Orbs Top", NamedTextColor.AQUA)))
                .tooltip(Component.text("See who has the most Orbs on the server", NamedTextColor.GRAY))
                .width(200)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        this.openLater(p, this::openOrbsTop);
                    }
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();
    }

    public void openOrbsTop(Player player) {
        IconSupport.setViewer(player);
        List<Map.Entry<UUID, Double>> top = this.plugin.getOrbsManager().getTopBalances(TOP_ORBS_LIMIT);
        ArrayList<Component> body = new ArrayList<>();
        if (top.isEmpty()) {
            body.add(Component.text("Nobody has any orbs yet!", NamedTextColor.GRAY));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Double> entry : top) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(entry.getKey());
                String name = target.getName() != null ? target.getName() : "Unknown";
                boolean isViewer = entry.getKey().equals(player.getUniqueId());
                NamedTextColor rankColor = switch (rank) {
                    case 1 -> NamedTextColor.GOLD;
                    case 2 -> NamedTextColor.GRAY;
                    case 3 -> NamedTextColor.YELLOW;
                    default -> NamedTextColor.WHITE;
                };
                Component line = Component.text("#" + rank + " ", rankColor)
                        .append(Component.text(name, isViewer ? NamedTextColor.AQUA : NamedTextColor.WHITE))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text((long) entry.getValue().doubleValue() + " orbs", NamedTextColor.LIGHT_PURPLE));
                body.add(line);
                ++rank;
            }
            boolean viewerShown = top.stream().anyMatch(e -> e.getKey().equals(player.getUniqueId()));
            if (!viewerShown) {
                double myBalance = this.plugin.getOrbsManager().getBalance(player.getUniqueId());
                body.add(Component.text("...", NamedTextColor.DARK_GRAY));
                body.add(Component.text("You: ", NamedTextColor.AQUA).append(Component.text((long) myBalance + " orbs", NamedTextColor.LIGHT_PURPLE)));
            }
        }
        ArrayList<ActionButton> buttons = new ArrayList<>();
        buttons.add(this.backButton(this::openOrbShop, "Return to the Orbshop"));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Orbs Top", NamedTextColor.LIGHT_PURPLE))
                        .body(body.stream().map(DialogBody::plainMessage).toList())
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        player.showDialog((DialogLike) dialog);
    }

    private ActionButton cosmeticButton(Material icon, Component label, String displayName, double price, Set<String> unlockedSet, String key, Supplier<String> activeGetter, Consumer<String> activeSetter, Runnable reopen, boolean refreshCosmetics) {
        TextComponent tooltip;
        Component buttonText;
        MessagesConfig msg = this.plugin.getMessages();
        boolean owned = unlockedSet.contains(key);
        boolean active = owned && key.equals(activeGetter.get());
        if (!owned) {
            buttonText = label.append(Component.text("  [" + (int) price + " coins]", NamedTextColor.GRAY));
            tooltip = Component.text("Click to purchase", NamedTextColor.YELLOW);
        } else if (active) {
            buttonText = label.append(Component.text("  [ACTIVE - click to disable]", NamedTextColor.GREEN));
            tooltip = Component.text("Click to turn off and go back to default", NamedTextColor.GRAY);
        } else {
            buttonText = label.append(Component.text("  [Owned - click to equip]", NamedTextColor.AQUA));
            tooltip = Component.text("Click to equip", NamedTextColor.GRAY);
        }
        buttonText = IconSupport.withIcon(icon, buttonText);
        return ActionButton.builder(buttonText)
                .tooltip(tooltip)
                .width(240)
                .action(DialogAction.customClick((view, audience) -> {
                    if (!(audience instanceof Player p)) {
                        return;
                    }
                    boolean isOwned = unlockedSet.contains(key);
                    if (!isOwned) {
                        if (this.plugin.getCoinsManager().remove(p.getUniqueId(), price)) {
                            unlockedSet.add(key);
                            p.sendMessage(msg.get("shop.unlocked", MessagesConfig.ph("item", displayName)));
                            this.plugin.getSounds().play(p, "purchase-success");
                        } else {
                            p.sendMessage(msg.get("shop.not-enough-coins", new TagResolver[0]));
                            this.plugin.getSounds().play(p, "purchase-fail");
                        }
                    } else {
                        boolean isActive = key.equals(activeGetter.get());
                        activeSetter.accept(isActive ? null : key);
                        if (isActive) {
                            p.sendMessage(msg.get("shop.unequipped", MessagesConfig.ph("item", displayName)));
                            this.plugin.getSounds().play(p, "unequip");
                        } else {
                            p.sendMessage(msg.get("shop.equipped", MessagesConfig.ph("item", displayName)));
                            this.plugin.getSounds().play(p, "equip");
                        }
                    }
                    this.plugin.getPlayerDataManager().save(this.plugin.getPlayerDataManager().get(p.getUniqueId()));
                    if (refreshCosmetics) {
                        this.plugin.refreshCosmetics(p);
                    }
                    this.reopenLater(p, reopen);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();
    }

    private ActionButton backButton(Consumer<Player> target) {
        return this.backButton(target, "Return to the main menu");
    }

    private ActionButton backButton(Consumer<Player> target, String tooltipText) {
        return ActionButton.builder(IconSupport.withIcon(Material.ARROW, Component.text("<< Back", NamedTextColor.RED)))
                .tooltip(Component.text(tooltipText, NamedTextColor.GRAY))
                .width(120)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) {
                        CoinShopGUI.this.openLater(p, target);
                    }
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()))
                .build();
    }

    private void showMenu(Player player, String titleKey, String fallbackTitle, List<ActionButton> buttons) {
        String title = this.plugin.getShopConfig().menuTitle(titleKey, fallbackTitle);
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(MM.deserialize(title))
                        .canCloseWithEscape(true)
                        // These two lines are the actual flicker/cursor-jump fix: without them
                        // the client's default behaviour on ANY button click is to first run its
                        // own CLOSE animation, then only after that does our server-side re-show
                        // land - that back-to-back close+reopen is exactly the blink + cursor
                        // recentering. pause(false) + afterAction(NONE) tell the client "keep
                        // this screen open, don't animate a close" so replacing it with updated
                        // content is instant and seamless.
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build())
                .type(DialogType.multiAction(buttons).build()));
        player.showDialog((DialogLike) dialog);
    }
}