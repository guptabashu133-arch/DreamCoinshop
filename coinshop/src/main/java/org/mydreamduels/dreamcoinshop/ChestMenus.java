package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a {@link Menu} as a chest menu: works on every server and client (Spigot, 1.20+, Bedrock).
 * Layout: a border, the info item at the top, the buttons inside, and the bottom row for paging,
 * Back and Close. Uses only InventoryEvent / Inventory methods: InventoryView changed from a class
 * to an interface in 1.21, so calling it would break the menus on 1.20.
 */
final class ChestMenus implements Menus.Renderer, Listener {

    /** Inner slots of a 6-row chest, row by row. */
    private static final int[] INNER = innerSlots(6);

    private final Dreamcoinshop plugin;
    private final Platform platform;
    private final Menus menus;
    private final Map<String, ItemStack> heads = new ConcurrentHashMap<>();
    private final Map<UUID, Holder> open = new ConcurrentHashMap<>();

    /** One open chest menu. */
    final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Menu.Button> slots = new HashMap<>();
        private Pages.View view;
        private long lastClick = System.currentTimeMillis(); // the second half of a double click must not hit the new menu
        private Platform.Task ticker;
        private boolean closed;

        Holder(Pages.View view) {
            this.view = view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    ChestMenus(Dreamcoinshop plugin, Platform platform, Menus menus) {
        this.plugin = plugin;
        this.platform = platform;
        this.menus = menus;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static int[] innerSlots(int rows) {
        List<Integer> list = new ArrayList<>();
        for (int r = 1; r < rows - 1; r++) for (int c = 1; c < 8; c++) list.add(r * 9 + c);
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void show(Player player, Menu menu, Pages.View view, boolean userAction) {
        Holder current = open.get(player.getUniqueId());
        int rows = menu.rows > 0 ? menu.rows : rowsFor(menu.buttons.size());
        boolean stillOpen = current != null && !current.closed && current.inventory.getViewers().contains(player);
        if (!userAction && !stillOpen) return; // background redraw of a menu that is no longer open

        // Same screen (or another page of it): redraw in place, so the mouse doesn't jump
        // (the title can't change in place, and it differs per arg - e.g. each price section)
        boolean inPlace = stillOpen && current.view.id().equals(view.id())
                && java.util.Objects.equals(current.view.arg(), view.arg()) && current.inventory.getSize() == rows * 9;
        Holder holder;
        if (inPlace) {
            holder = current;
            holder.view = view;
            holder.inventory.clear();
            holder.slots.clear();
        } else {
            holder = new Holder(view);
            if (current != null) holder.lastClick = current.lastClick;
            holder.inventory = plugin.text().inventory(holder, rows * 9, player, menu.title, null);
        }
        Inventory inv = holder.inventory;
        fill(inv, rows);
        int size = rows * 9;

        // Info item: the body lines
        if (!menu.body.isEmpty()) inv.setItem(4, item(player, menu.infoIcon, plugin.text().raw("menu.info"), menu.body, false));

        int[] inner = rows == 6 ? INNER : innerSlots(rows);
        for (int i = 0; i < menu.buttons.size() && i < inner.length; i++) place(player, holder, inner[i], menu.buttons.get(i));

        // Bottom row: previous | nav | back (or close) | nav | next
        int bottom = size - 9;
        List<Menu.Button> nav = new ArrayList<>(menu.nav);
        Menu.Button previous = null, next = null;
        for (Menu.Button b : menu.nav) {
            if (b.icon() != null && "ARROW".equals(b.icon().material())) previous = b;
            if (b.icon() != null && "SPECTRAL_ARROW".equals(b.icon().material())) next = b;
        }
        nav.remove(previous);
        nav.remove(next);
        if (previous != null) place(player, holder, bottom, previous);
        if (next != null) place(player, holder, bottom + 8, next);
        int[] middle = {bottom + 2, bottom + 6, bottom + 1, bottom + 7};
        for (int i = 0; i < nav.size() && i < middle.length; i++) place(player, holder, middle[i], nav.get(i));
        if (menu.back != null) place(player, holder, bottom + 4, menu.back);
        else if (menu.close != null) place(player, holder, bottom + 4, menu.close);

        if (!inPlace) {
            Holder replaced = open.put(player.getUniqueId(), holder);
            if (replaced != null && replaced.ticker != null) replaced.ticker.cancel();
            player.openInventory(inv);
        } else if (holder.ticker != null) {
            holder.ticker.cancel();
            holder.ticker = null;
        }
        animate(player, holder, view);
    }

    private void place(Player player, Holder holder, int slot, Menu.Button b) {
        if (slot < 0 || slot >= holder.inventory.getSize()) return;
        List<String> lore = new ArrayList<>();
        if (b.status() != null && !b.status().isEmpty()) lore.add(b.status());
        lore.addAll(b.lore());
        if (b.tooltip() != null && !b.tooltip().isEmpty()) lore.add(b.tooltip());
        holder.inventory.setItem(slot, item(player, b.icon(), b.label(), lore, b.glow()));
        holder.slots.put(slot, b);
    }

    private static int rowsFor(int buttons) {
        int innerRows = Math.max(1, (buttons + 6) / 7);
        return Math.max(3, Math.min(6, innerRows + 2));
    }

    private void fill(Inventory inv, int rows) {
        ItemStack glass = new ItemStack(material("BLACK_STAINED_GLASS_PANE", Material.GLASS_PANE));
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        for (int i = 0; i < rows * 9; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == rows - 1 || c == 0 || c == 8) inv.setItem(i, glass);
        }
    }

    // ------------------------------------------------------------------ items

    private ItemStack item(Player viewer, Menu.Icon icon, String name, List<String> lore, boolean glow) {
        ItemStack stack;
        if (icon != null && icon.flag() != null) {
            stack = head(icon.flag()).clone();
        } else {
            stack = new ItemStack(material(icon == null ? null : icon.material(), Material.PAPER));
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        plugin.text().itemText(meta, viewer, name, lore, null);
        meta.addItemFlags(ItemFlag.values());
        if (glow && Capabilities.GLINT) meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Material material(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name);
        return m == null || !m.isItem() || m.isAir() ? fallback : m;
    }

    /** A head with the flag picture (cached, cloned for each use). */
    private ItemStack head(Flags.Country country) {
        return heads.computeIfAbsent(country.code(), code -> {
            ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
            if (!Capabilities.PROFILES) return stack;
            try {
                SkullMeta meta = (SkullMeta) stack.getItemMeta();
                UUID id = UUID.nameUUIDFromBytes(("dreamcoinshop:flag_" + code).getBytes(StandardCharsets.UTF_8));
                PlayerProfile profile = Bukkit.createPlayerProfile(id, "flag_" + code);
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(new URL(country.textureUrl()));
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
                stack.setItemMeta(meta);
            } catch (Exception e) {
                plugin.getLogger().fine("Could not make the " + code + " flag head: " + e.getMessage());
            }
            return stack;
        });
    }

    // ------------------------------------------------------------------ the changing flag

    /** Main menu: the Flags button shows another country every flags.rotate.seconds. */
    private void animate(Player player, Holder holder, Pages.View view) {
        ShopConfig cfg = plugin.config();
        if (!view.id().equals("main") || !cfg.flagsRotate || !cfg.section("flags")) return;
        Integer flagSlot = null;
        for (Map.Entry<Integer, Menu.Button> e : holder.slots.entrySet()) {
            if (e.getValue().icon() != null && e.getValue().icon().flag() != null) flagSlot = e.getKey();
        }
        if (flagSlot == null) return;
        int slot = flagSlot;
        long period = Math.max(1, cfg.flagsRotateSeconds) * 20L;
        holder.ticker = platform.playerTimer(player, () -> {
            if (holder.closed || !player.isOnline()) {
                if (holder.ticker != null) holder.ticker.cancel();
                return;
            }
            Flags.Country c = plugin.flags().rotating(plugin.config(), System.currentTimeMillis());
            Menu.Button b = holder.slots.get(slot);
            if (c == null || b == null) return;
            Menu.Button turned = new Menu.Button(Menu.Icon.flag(c), b.label(), b.status(), b.tooltip(), b.lore(), b.glow(), b.width(), b.action());
            holder.slots.put(slot, turned);
            List<String> lore = new ArrayList<>();
            if (b.tooltip() != null && !b.tooltip().isEmpty()) lore.add(b.tooltip());
            holder.inventory.setItem(slot, item(player, turned.icon(), turned.label(), lore, false));
        }, period, period);
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != top || !(event.getWhoClicked() instanceof Player player)) return;
        ClickType type = event.getClick();
        // Only plain and shift clicks are buttons (no number keys, drops, swaps or creative middle click)
        if (type != ClickType.LEFT && type != ClickType.RIGHT && type != ClickType.SHIFT_LEFT && type != ClickType.SHIFT_RIGHT) return;
        Menu.Button b = holder.slots.get(event.getRawSlot());
        if (b == null || b.action() == null) return;
        long now = System.currentTimeMillis();
        boolean repeatable = holder.view.id().equals("settings") || holder.view.id().equals("prices");
        if (!repeatable && now - holder.lastClick < plugin.config().clickCooldownMs) return;
        holder.lastClick = now;
        Menu.Click click = switch (type) {
            case RIGHT -> Menu.Click.RIGHT;
            case SHIFT_LEFT -> Menu.Click.SHIFT_LEFT;
            case SHIFT_RIGHT -> Menu.Click.SHIFT_RIGHT;
            default -> Menu.Click.LEFT;
        };
        // Opening/closing inventories inside the click event is unsafe: do it on the next tick
        platform.forPlayerLater(player, 1, () -> {
            if (holder.closed || !player.isOnline()) return;
            plugin.sounds().play(player, "gui-click");
            try {
                b.action().run(player, click, Map.of());
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Error in a menu button", e);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        holder.closed = true;
        if (holder.ticker != null) holder.ticker.cancel();
        if (event.getPlayer() instanceof Player p) {
            // Only if this was the player's current menu (opening the next one also closes the last)
            if (open.remove(p.getUniqueId(), holder)) menus.closedByPlayer(p, Menus.Kind.CHEST);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void close(Player player) {
        Holder h = open.remove(player.getUniqueId());
        if (h != null) {
            h.closed = true;
            if (h.ticker != null) h.ticker.cancel();
            if (h.inventory.getViewers().contains(player)) player.closeInventory();
        }
    }

    @Override
    public void forget(Player player) {
        Holder h = open.remove(player.getUniqueId());
        if (h != null) {
            h.closed = true;
            if (h.ticker != null) h.ticker.cancel();
        }
    }

    @Override
    public void closeAll() {
        for (Holder h : new ArrayList<>(open.values())) {
            h.closed = true;
            if (h.ticker != null) h.ticker.cancel();
            for (HumanEntity viewer : new ArrayList<>(h.inventory.getViewers())) viewer.closeInventory();
        }
        open.clear();
    }
}
