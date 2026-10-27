package org.mydreamduels.dreamcoinshop;

import io.papermc.paper.connection.PlayerCommonConnection;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Draws a {@link Menu} as a dialog (Paper 1.21.6+). Buttons send one custom-click id with a small
 * payload (which dialog, which button); clicks from an older dialog of the same player, or
 * forged ones, are ignored. Dialogs stay open after a click (afterAction NONE) and are redrawn
 * in place, so buying something just updates the screen.
 *
 * Only loaded when Capabilities.DIALOGS is true.
 */
final class DialogMenus implements Menus.Renderer, Listener {

    private static final Key CLICK = Key.key("dreamcoinshop", "click");
    /*
     * The client sends a button's payload merged with the dialog's input values in one compound,
     * so the ids use names no input can have (inputs never start with "dcs_").
     */
    private static final String TOKEN_KEY = "dcs_t", BUTTON_KEY = "dcs_b";
    private static final Pattern TOKEN = Pattern.compile("(?:^|[{,\\s])dcs_t\\s*:\\s*\"?(-?\\d+)");
    private static final Pattern BUTTON = Pattern.compile("(?:^|[{,\\s])dcs_b\\s*:\\s*\"?(-?\\d+)");
    /** A background redraw (e.g. a leaderboard that finished loading) only shows if the player clicked recently. */
    private static final long REDRAW_WINDOW_MS = 5000;

    private static final Key BLOCKS = Key.key("minecraft", "blocks");
    private static final Key ITEMS = Key.key("minecraft", "items");
    /** Texture names that aren't just the material name. */
    private final Dreamcoinshop plugin;
    private final Platform platform;
    private final Menus menus;
    private final AtomicInteger tokens = new AtomicInteger(1);
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Component> headCache = new ConcurrentHashMap<>();

    /** The dialog a player has open: its buttons and inputs. */
    private static final class Session {
        final int token;
        final Menu menu;
        final Pages.View view;
        final List<Menu.Button> buttons;
        volatile long lastUserAction;
        volatile long lastClick;
        Platform.Task animator;

        Session(int token, Menu menu, Pages.View view, List<Menu.Button> buttons) {
            this.token = token;
            this.menu = menu;
            this.view = view;
            this.buttons = buttons;
        }
    }

    private DialogMenus(Dreamcoinshop plugin, Platform platform, Menus menus) {
        this.plugin = plugin;
        this.platform = platform;
        this.menus = menus;
    }

    static DialogMenus create(Dreamcoinshop plugin, Platform platform, Menus menus) {
        DialogMenus d = new DialogMenus(plugin, platform, menus);
        plugin.getServer().getPluginManager().registerEvents(d, plugin);
        return d;
    }

    private AdventureText text() {
        return (AdventureText) plugin.text();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void show(Player player, Menu menu, Pages.View view, boolean userAction) {
        draw(player, menu, view, userAction, false);
    }

    /** keepToken: a redraw of the same screen (the changing flag) - a click already on its way still counts. */
    private void draw(Player player, Menu menu, Pages.View view, boolean userAction, boolean keepToken) {
        Session old = sessions.get(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (!userAction && (old == null || now - old.lastUserAction > REDRAW_WINDOW_MS)) return;

        boolean bedrock = plugin.bedrock().isBedrock(player);
        boolean icons = Capabilities.OBJECTS && !bedrock && ClientInfo.supportsSprites(player);

        List<Menu.Button> all = new ArrayList<>(menu.buttons);
        if (menu.previous != null) all.add(menu.previous);
        all.addAll(menu.nav);
        if (menu.next != null) all.add(menu.next);
        if (menu.back != null) all.add(menu.back);
        int token = keepToken && old != null ? old.token : tokens.getAndIncrement();
        Session session = new Session(token, menu, view, all);
        session.lastUserAction = userAction ? now : old != null ? old.lastUserAction : now;
        session.lastClick = old != null ? old.lastClick : 0;

        List<ActionButton> actions = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) actions.add(button(player, all.get(i), session.token, i, icons, bedrock));
        // Every dialog gets a Close footer: it is also what Escape sends, so we know the dialog closed
        Menu.Button close = menu.close != null ? menu.close
                : Menu.Button.of(Menu.Icon.of(plugin.config().guiIcon("close", "BARRIER")), text().raw("menu.button.close"), null, null, null).width(120);
        ActionButton exit = button(player, close, session.token, -1, icons, bedrock);

        List<DialogBody> body = new ArrayList<>();
        if (!menu.body.isEmpty()) {
            Component lines = Component.empty();
            for (int i = 0; i < menu.body.size(); i++) {
                if (i > 0) lines = lines.append(Component.newline());
                lines = lines.append(text().parse(menu.body.get(i), null));
            }
            body.add(DialogBody.plainMessage(lines, 300));
        }
        if (icons && !menu.preload.isEmpty()) {
            // The next flags of the Flags button, drawn now so their pictures are downloaded
            // before the button shows them (otherwise the client shows a plain head meanwhile)
            Component strip = text().parse(text().raw("menu.flags-next"), null);
            for (Flags.Country c : menu.preload) strip = strip.append(Component.space()).append(flagHead(c));
            body.add(DialogBody.plainMessage(strip, 300));
        }

        List<DialogInput> inputs = new ArrayList<>();
        for (Menu.Input in : menu.inputs) inputs.add(input(in));

        Component title = text().parse(menu.title, null);
        ActionButton exitAction = exit;
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        // Stay open after a click: the screen is redrawn in place instead of closing
                        // and reopening, which would flicker and move the mouse back to the centre.
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(DialogType.multiAction(actions).columns(Math.max(1, menu.columns)).exitAction(exitAction).build()));

        Session previous = sessions.put(player.getUniqueId(), session);
        if (previous != null && previous.animator != null) {
            if (view.id().equals("main") && previous.view.id().equals("main")) session.animator = previous.animator;
            else previous.animator.cancel();
        }
        player.showDialog(dialog);
        animate(player, session, icons);
    }

    private ActionButton button(Player player, Menu.Button b, int token, int index, boolean icons, boolean bedrock) {
        Component label = text().parse(b.label(), null);
        if (b.icon() != null) {
            Menu.Icon ic = b.icon();
            boolean hideBadge = ic.badge() == null || bedrock && plugin.config().flagsBedrock == ShopConfig.BedrockFlags.HIDE;
            Component badge = hideBadge ? null : icons ? flagHead(ic.badge()) : flagText(ic.badge(), bedrock);
            if (badge != null) label = badge.append(Component.space()).append(label);
            Component icon = icons ? icon(ic) : ic.flag() == null ? null : flagText(ic.flag(), bedrock);
            if (icon != null) label = icon.append(Component.space()).append(label);
        }
        if (b.status() != null && !b.status().isEmpty()) label = label.append(Component.text("  ")).append(text().parse(b.status(), null));
        ActionButton.Builder builder = ActionButton.builder(label).width(Math.max(60, Math.min(1024, b.width())));
        if (b.tooltip() != null && !b.tooltip().isEmpty()) builder.tooltip(text().parse(b.tooltip(), null));
        BinaryTagHolder payload = BinaryTagHolder.binaryTagHolder("{" + TOKEN_KEY + ":\"" + token + "\"," + BUTTON_KEY + ":\"" + index + "\"}");
        return builder.action(DialogAction.customClick(CLICK, payload)).build();
    }

    /** A sprite of an item, or the flag head. Null if there's no picture for it. */
    private Component icon(Menu.Icon icon) {
        if (icon.flag() != null) return flagHead(icon.flag());
        if (icon.player() != null) {
            return ModernText.playerHead(icon.player(), icon.playerName(), org.bukkit.Bukkit.getPlayer(icon.player()));
        }
        if (icon.material() == null) return null;
        Material m = Material.matchMaterial(icon.material());
        if (m == null) return null;
        String path = Sprites.path(m.name());
        if (path == null) return null; // heads, banners, chests...: no flat picture, the label stays without an icon
        boolean block = path.startsWith("block/");
        Key atlas = block || !Capabilities.isAtLeast(1, 21, 11) ? BLOCKS : ITEMS;
        try {
            return ModernText.sprite(atlas, Key.key("minecraft", path));
        } catch (Exception e) {
            return null;
        }
    }

    private Component flagHead(Flags.Country c) {
        return headCache.computeIfAbsent(c.code(), k -> ModernText.head("flag_" + k, c.texture()));
    }

    /** Players who can't see heads in text get the flag as "[IN]" (Bedrock: a glyph if set up). */
    private Component flagText(Flags.Country c, boolean bedrock) {
        ShopConfig cfg = plugin.config();
        if (bedrock && cfg.flagsBedrock == ShopConfig.BedrockFlags.GLYPH && c.bedrockGlyph() > 0) {
            return Component.text(new String(Character.toChars(c.bedrockGlyph())), NamedTextColor.WHITE);
        }
        return text().parse(plugin.cosmetics().flagTextMini(c), null);
    }

    private DialogInput input(Menu.Input in) {
        Component label = text().parse(in.label(), null);
        return switch (in.type()) {
            case BOOL -> DialogInput.bool(in.key(), label).initial(Boolean.TRUE.equals(in.value())).build();
            case NUMBER -> {
                double v = in.value() instanceof Number n ? n.doubleValue() : in.min();
                if (slider(in)) {
                    yield DialogInput.numberRange(in.key(), label, (float) in.min(), (float) in.max())
                            .step((float) in.step())
                            .initial((float) Math.max(in.min(), Math.min(in.max(), v)))
                            .width(300)
                            .build();
                }
                yield DialogInput.text(in.key(), label).initial(Shop.format(v)).maxLength(32).width(300).build();
            }
            case CHOICE -> {
                List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                for (String o : in.options()) {
                    entries.add(SingleOptionDialogInput.OptionEntry.create(o.toLowerCase(Locale.ROOT), Component.text(o),
                            o.equalsIgnoreCase(String.valueOf(in.value()))));
                }
                yield DialogInput.singleOption(in.key(), label, entries).width(300).build();
            }
            case TEXT -> DialogInput.text(in.key(), label)
                    .initial(in.value() == null ? "" : String.valueOf(in.value()))
                    .maxLength(in.max() > 0 ? (int) in.max() : 256)
                    .width(300)
                    .build();
        };
    }

    // ------------------------------------------------------------------ the changing flag

    /** Main menu: redraws it every flags.rotate.seconds so the Flags button shows another country. */
    private void animate(Player player, Session session, boolean icons) {
        ShopConfig cfg = plugin.config();
        boolean wanted = session.view.id().equals("main") && icons && cfg.flagsRotate && cfg.flagsRotateMaxSeconds > 0
                && cfg.section("flags");
        if (!wanted) {
            if (session.animator != null) session.animator.cancel();
            session.animator = null;
            return;
        }
        if (session.animator != null) return;
        long period = Math.max(1, cfg.flagsRotateSeconds) * 20L;
        session.animator = platform.playerTimer(player, () -> {
            Session now = sessions.get(player.getUniqueId());
            Menus.State state = menus.state(player);
            boolean stillMain = now != null && now.view.id().equals("main") && state != null
                    && state.kind() == Menus.Kind.DIALOG && state.view().id().equals("main");
            long idle = now == null ? Long.MAX_VALUE : System.currentTimeMillis() - now.lastUserAction;
            if (!player.isOnline() || !stillMain || idle > plugin.config().flagsRotateMaxSeconds * 1000L) {
                Platform.Task t = now != null ? now.animator : null;
                if (t != null) t.cancel();
                if (now != null) now.animator = null;
                return;
            }
            long lastAction = now.lastUserAction;
            draw(player, menus.build(player, Pages.View.of("main"), true), Pages.View.of("main"), true, true);
            Session after = sessions.get(player.getUniqueId());
            if (after != null) after.lastUserAction = lastAction; // a redraw is not a click
        }, period, period);
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!CLICK.equals(event.getIdentifier())) return;
        PlayerCommonConnection connection = event.getCommonConnection();
        if (!(connection instanceof PlayerGameConnection game)) return;
        Player player = game.getPlayer();
        DialogResponseView response = event.getDialogResponseView();
        BinaryTagHolder tag = event.getTag();
        String payload = tag == null ? "" : tag.string();
        Integer token = id(response, TOKEN_KEY, TOKEN, payload);
        Integer index = id(response, BUTTON_KEY, BUTTON, payload);
        if (token == null || index == null) return;
        platform.forPlayer(player, () -> click(player, token, index, response));
    }

    /** One of our ids from the click: read as a named value, or found in the raw payload. */
    private static Integer id(DialogResponseView response, String key, Pattern pattern, String payload) {
        String raw = null;
        if (response != null) {
            try {
                raw = response.getText(key);
            } catch (RuntimeException ignored) {
            }
        }
        if (raw == null) {
            Matcher m = pattern.matcher(payload);
            if (m.find()) raw = m.group(1);
        }
        if (raw == null) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The values of this dialog's own inputs. */
    private static Map<String, Object> readInputs(Menu menu, DialogResponseView view) {
        Map<String, Object> out = new HashMap<>();
        if (view == null) return out;
        for (Menu.Input in : menu.inputs) {
            Object v = null;
            try {
                v = switch (in.type()) {
                    case BOOL -> view.getBoolean(in.key());
                    case NUMBER -> slider(in) ? toDouble(view.getFloat(in.key())) : view.getText(in.key());
                    case TEXT, CHOICE -> view.getText(in.key());
                };
            } catch (RuntimeException ignored) {
            }
            if (v != null) out.put(in.key(), v);
        }
        return out;
    }

    private static Object toDouble(Float f) {
        return f == null ? null : (Object) f.doubleValue();
    }

    /** Small ranges are a slider; big ones (prices) a text box you can type in. */
    private static boolean slider(Menu.Input in) {
        double steps = in.step() > 0 ? (in.max() - in.min()) / in.step() : Double.MAX_VALUE;
        return steps <= 200;
    }

    /** Player's thread. */
    private void click(Player player, int token, int index, DialogResponseView response) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.token != token) return; // an older dialog, or not ours
        long now = System.currentTimeMillis();
        if (index == -1) { // Close / Escape: never dropped by the cooldown
            if (s.animator != null) s.animator.cancel();
            s.animator = null;
            sessions.remove(player.getUniqueId());
            menus.closedByPlayer(player, Menus.Kind.DIALOG);
            player.closeDialog();
            return;
        }
        if (now - s.lastClick < plugin.config().clickCooldownMs) return;
        s.lastClick = now;
        s.lastUserAction = now;
        if (index < 0 || index >= s.buttons.size()) return;
        Map<String, Object> mine = readInputs(s.menu, response);
        Menu.Button button = s.buttons.get(index);
        if (button.action() == null) return; // just information (e.g. a leaderboard row)
        try {
            button.action().run(player, Menu.Click.LEFT, mine);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Error in a menu button", e);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void close(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s != null && s.animator != null) s.animator.cancel();
        player.closeDialog();
    }

    @Override
    public void forget(Player player) {
        Session s = sessions.remove(player.getUniqueId());
        if (s != null && s.animator != null) s.animator.cancel();
    }

    @Override
    public void closeAll() {
        for (UUID uuid : sessions.keySet()) {
            Player p = plugin.getServer().getPlayer(uuid);
            Session s = sessions.remove(uuid);
            if (s != null && s.animator != null) s.animator.cancel();
            if (p != null) {
                try {
                    p.closeDialog();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
