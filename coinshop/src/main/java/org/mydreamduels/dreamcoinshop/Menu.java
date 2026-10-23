package org.mydreamduels.dreamcoinshop;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One screen of the shop, described once and drawn either as a dialog (Paper 1.21.6+) or as a
 * chest menu. Texts are MiniMessage. {@link Pages} builds these, {@link DialogMenus} and
 * {@link ChestMenus} draw them.
 */
final class Menu {

    /** How a button was clicked. Dialogs only ever send LEFT. */
    enum Click {
        LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT;

        boolean right() {
            return this == RIGHT || this == SHIFT_RIGHT;
        }

        boolean shift() {
            return this == SHIFT_LEFT || this == SHIFT_RIGHT;
        }
    }

    @FunctionalInterface
    interface Action {
        /** inputs: the dialog's input values by key (empty for chest menus). */
        void run(Player player, Click click, Map<String, Object> inputs);
    }

    /**
     * An item picture: a material name, a country flag (a head), or a player's head. badge: a
     * flag shown next to the picture (dialogs) / in the item lore (chest menus).
     */
    record Icon(String material, Flags.Country flag, java.util.UUID player, String playerName, Flags.Country badge) {
        static Icon of(String material) {
            return new Icon(material, null, null, null, null);
        }

        static Icon flag(Flags.Country flag) {
            return new Icon("PLAYER_HEAD", flag, null, null, null);
        }

        static Icon player(java.util.UUID player, String name, Flags.Country badge) {
            return new Icon("PLAYER_HEAD", null, player, name, badge);
        }
    }

    /**
     * @param label   name of the button / item
     * @param status  short text after the label on a dialog button, and the first lore line of the item
     * @param tooltip hover text of the dialog button, and the last lore line of the item
     * @param lore    extra item lore (chest menus only)
     * @param glow    item shine: the equipped one
     */
    record Button(Icon icon, String label, String status, String tooltip, List<String> lore, boolean glow, int width, Action action) {
        static Button of(Icon icon, String label, String status, String tooltip, Action action) {
            return new Button(icon, label, status, tooltip, List.of(), false, 200, action);
        }

        Button width(int w) {
            return new Button(icon, label, status, tooltip, lore, glow, w, action);
        }

        Button glowing(boolean g) {
            return new Button(icon, label, status, tooltip, lore, g, width, action);
        }

        Button withLore(List<String> extra) {
            return new Button(icon, label, status, tooltip, extra, glow, width, action);
        }
    }

    enum InputType { BOOL, NUMBER, TEXT, CHOICE }

    /** A setting the dialog shows as a checkbox / slider / text box / option list. */
    record Input(String key, InputType type, String label, Object value, double min, double max, double step,
                 List<String> options) {
    }

    final String title;
    final List<String> body = new ArrayList<>();
    final List<Button> buttons = new ArrayList<>();
    /** Page / search buttons, drawn in the bottom row of a chest menu. */
    final List<Button> nav = new ArrayList<>();
    Button back;
    /** Main menu only: a Close button (dialogs). */
    Button close;
    int columns = 2;
    /** Paging buttons (dialogs: around the nav buttons; chest menus: bottom corners). */
    Menu.Button previous, next;
    /** Chest menus: button index -> slot (gui.main-slots). Buttons not in here fill the inner slots. */
    final Map<Integer, Integer> slots = new java.util.HashMap<>();
    /** Flags drawn small under the body, so the client has their pictures before they're needed. */
    final List<Flags.Country> preload = new ArrayList<>();
    /** Inputs shown at the top of a dialog. */
    final List<Input> inputs = new ArrayList<>();
    /** The info item of a chest menu (top middle). */
    Icon infoIcon = Icon.of("BOOK");
    /** Chest menu rows (3-6). 0 = as many as the buttons need. */
    int rows;

    Menu(String title) {
        this.title = title;
    }

    Menu body(String line) {
        if (line != null && !line.isEmpty()) body.add(line);
        return this;
    }

    Menu add(Button button) {
        if (button != null) buttons.add(button);
        return this;
    }
}
