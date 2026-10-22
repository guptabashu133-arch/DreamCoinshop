package org.mydreamduels.dreamcoinshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens the shop for a player as a dialog or a chest menu (menu.style), and remembers which screen
 * they're on, so a purchase can redraw it with the new state.
 */
final class Menus implements Listener {

    enum Kind { DIALOG, CHEST }

    /** What a player has open. */
    record State(Pages.View view, Kind kind) {
    }

    /** Screens that belong to a shop section (hidden when categories.&lt;section&gt; is off). */
    private static final Set<String> SECTIONS = Set.copyOf(ShopConfig.SECTIONS);

    Menu build(Player player, Pages.View view, boolean dialog) {
        return pages.build(player, view, dialog);
    }

    /** Renders one Menu. */
    interface Renderer {
        /** userAction: opened by a click or command (not a background refresh). Player's thread. */
        void show(Player player, Menu menu, Pages.View view, boolean userAction);

        void close(Player player);

        void forget(Player player);

        void closeAll();
    }

    private final Dreamcoinshop plugin;
    private final Platform platform;
    private final Pages pages;
    private final Renderer dialogs;
    private final Renderer chests;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    Menus(Dreamcoinshop plugin, Platform platform) {
        this.plugin = plugin;
        this.platform = platform;
        this.pages = new Pages(plugin, this);
        this.chests = new ChestMenus(plugin, platform, this);
        this.dialogs = Capabilities.DIALOGS ? DialogMenus.create(plugin, platform, this) : null;
    }

    Renderer chestRenderer() {
        return chests;
    }

    Renderer dialogRenderer() {
        return dialogs;
    }

    /** /coinshop: the main menu in the configured style. */
    void openMain(Player player, Kind forced) {
        open(player, Pages.View.of("main"), forced);
    }

    /** A screen, in the style the player is already using. */
    void open(Player player, Pages.View view) {
        State s = states.get(player.getUniqueId());
        open(player, view, s != null ? s.kind() : null);
    }

    void open(Player player, Pages.View view, Kind forced) {
        Kind kind = forced != null ? forced : choose(player);
        if (kind == Kind.DIALOG && !canDialog(player)) kind = Kind.CHEST;
        Pages.View target = allowed(player, view) ? view : Pages.View.of("main");
        Kind k = kind;
        platform.forPlayer(player, () -> show(player, target, k, true));
    }

    /** Redraws what the player has open (after a purchase, a setting change...). */
    void refresh(Player player) {
        State s = states.get(player.getUniqueId());
        if (s == null) return;
        platform.forPlayer(player, () -> {
            State now = states.get(player.getUniqueId());
            if (now != null) show(player, allowed(player, now.view()) ? now.view() : Pages.View.of("main"), now.kind(), false);
        });
    }

    /** Redraws only if the player still has this screen open. */
    void refreshIfViewing(Player player, String viewId) {
        State s = states.get(player.getUniqueId());
        if (s != null && s.view().id().equals(viewId)) refresh(player);
    }

    /** The screen the player is on, or null. */
    State state(Player player) {
        return states.get(player.getUniqueId());
    }

    void close(Player player) {
        State s = states.remove(player.getUniqueId());
        if (s == null) return;
        Renderer r = s.kind() == Kind.DIALOG ? dialogs : chests;
        if (r != null) platform.forPlayer(player, () -> r.close(player));
    }

    /** A renderer tells us the player closed its menu themselves. */
    void closedByPlayer(Player player, Kind kind) {
        states.computeIfPresent(player.getUniqueId(), (u, s) -> s.kind() == kind ? null : s);
    }

    private void show(Player player, Pages.View view, Kind kind, boolean userAction) {
        if (!player.isOnline()) return;
        Renderer r = kind == Kind.DIALOG ? dialogs : chests;
        State previous = states.put(player.getUniqueId(), new State(view, kind));
        if (previous != null && previous.kind() != kind) {
            Renderer old = previous.kind() == Kind.DIALOG ? dialogs : chests;
            if (old != null) old.forget(player);
        }
        Menu menu = pages.build(player, view, kind == Kind.DIALOG);
        r.show(player, menu, view, userAction);
    }

    private boolean allowed(Player player, Pages.View view) {
        String id = view.id();
        if (id.equals("settings") || id.equals("prices")) return player.hasPermission("dreamcoinshop.settings");
        if (id.equals("orbstop")) return plugin.config().section("orbshop");
        if (SECTIONS.contains(id) && !plugin.config().section(id)) {
            plugin.text().send(player, "shop.section-off", null);
            return false;
        }
        return true;
    }

    /** Dialog or chest menu, from menu.style / menu.bedrock-style. */
    Kind choose(Player player) {
        ShopConfig cfg = plugin.config();
        if (cfg.menuStyle == ShopConfig.MenuStyle.GUI || dialogs == null) return Kind.CHEST;
        if (plugin.bedrock().isBedrock(player)) {
            return cfg.menuStyle == ShopConfig.MenuStyle.DIALOG || cfg.bedrockStyle == ShopConfig.MenuStyle.DIALOG
                    ? Kind.DIALOG : Kind.CHEST;
        }
        return ClientInfo.supportsDialogs(player) ? Kind.DIALOG : Kind.CHEST;
    }

    boolean canDialog(Player player) {
        return dialogs != null && (plugin.bedrock().isBedrock(player) || ClientInfo.supportsDialogs(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        states.remove(p.getUniqueId());
        chests.forget(p);
        if (dialogs != null) dialogs.forget(p);
    }

    /** On disable / reload: close every menu, so nobody keeps one whose clicks aren't handled. */
    void closeAll() {
        states.clear();
        chests.closeAll();
        if (dialogs != null) dialogs.closeAll();
    }
}
