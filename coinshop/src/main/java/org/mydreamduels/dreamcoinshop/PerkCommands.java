package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The perk commands, built in: /craft, /anvil, /grindstone, /smithingtable, /loom, /stonecutter,
 * /enderchest, /trash, /hat, /nick - no EssentialsX or other plugin needed. A player may use one
 * when they bought the perk (they get its permission) or a rank gives them that permission.
 * When another plugin already has the command, ours takes the name (perk-settings.take-over) and
 * everyone who didn't buy the perk still gets the other plugin's command.
 */
final class PerkCommands implements Listener {

    enum Action {
        CRAFT("craft", "workbench", "wb"), ANVIL("anvil"), GRINDSTONE("grindstone"), SMITHING("smithingtable", "smithing"),
        LOOM("loom"), STONECUTTER("stonecutter"), CARTOGRAPHY("cartographytable", "cartography"),
        ENDERCHEST("enderchest", "ec", "echest"), TRASH("trash", "disposal"), HAT("hat", "head"), NICK("nick", "nickname"), NONE();

        final List<String> labels;

        Action(String... labels) {
            this.labels = List.of(labels);
        }

        static Action of(String name) {
            if (name == null) return null;
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        /** The action a perk does when its config has no action: by its key (craft -> CRAFT). */
        static Action byKey(String key) {
            String k = key.toLowerCase(Locale.ROOT);
            for (Action a : values()) if (a.labels.contains(k) || a.name().equalsIgnoreCase(k)) return a;
            return NONE;
        }
    }

    /** The trash can: whatever is in it when it's closed is gone. */
    static final class Trash implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final Dreamcoinshop plugin;
    private Map<String, Command> known;

    PerkCommands(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    /** One command name for one perk. previous = the other plugin's command it took the name from. */
    private final class PerkCommand extends Command {
        private final String perkKey;
        private final Action action;
        private final Command previous;

        PerkCommand(String label, String perkKey, Action action, Command previous) {
            super(label, "Coinshop perk: " + action.name().toLowerCase(Locale.ROOT), "/" + label, List.of());
            this.perkKey = perkKey;
            this.action = action;
            this.previous = previous;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                if (previous != null) return previous.execute(sender, label, args);
                plugin.text().send(sender, "general.player-only", null);
                return true;
            }
            ShopConfig.Item perk = plugin.config().item(Category.PERK, perkKey);
            boolean allowed = perk != null && plugin.config().section("perks") && player.hasPermission(perk.permission());
            if (!allowed) {
                Command other = previous != null ? previous : otherPlugins(getName());
                if (other != null) return other.execute(sender, label, args); // the other plugin decides
                plugin.text().send(player, "perk.not-owned", Ph.of("perk", perk == null ? label : Text.plain(perk.display())));
                return true;
            }
            try {
                run(player, action, args);
            } catch (RuntimeException e) {
                // a server that can't open this menu (a fork without it): a message, not an error
                plugin.getLogger().fine("/" + label + ": " + e);
                plugin.text().send(player, "perk.unsupported", null);
            }
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (action == Action.NICK && args.length == 1) return List.of("off");
            return List.of();
        }
    }

    /** Registers a command for every perk that has an action. Once, at startup. */
    void register() {
        ShopConfig cfg = plugin.config();
        if (!cfg.perkCommands) return;
        CommandMap map = CommandAliases.commandMap(plugin);
        known = knownCommands(map);
        if (map == null) {
            plugin.getLogger().warning("Could not add the perk commands (no command map).");
            return;
        }
        String prefix = plugin.getName().toLowerCase(Locale.ROOT);
        List<String> added = new ArrayList<>();
        for (ShopConfig.Item perk : cfg.items(Category.PERK).values()) {
            Action action = Action.of(perk.effect());
            if (action == null) action = Action.byKey(perk.key());
            if (action == Action.NONE) continue;
            for (String label : action.labels) {
                Command existing = map.getCommand(label);
                if (existing instanceof PerkCommand) continue;
                if (existing != null && (!cfg.perkTakeOver || known == null)) continue; // the other plugin keeps it
                PerkCommand cmd = new PerkCommand(label, perk.key(), action, existing);
                map.register(prefix, cmd);
                if (existing != null) {
                    known.put(label, cmd); // take the plain name; the other plugin keeps its "plugin:" name
                    known.put(label.toLowerCase(Locale.ROOT), cmd);
                }
                added.add("/" + label);
            }
        }
        if (!added.isEmpty()) plugin.getLogger().info("Perk commands: " + String.join(" ", added));
    }

    /** Another plugin's command with this name ("essentials:anvil"), for one that loaded after us. */
    private Command otherPlugins(String label) {
        if (known == null) return null;
        String suffix = ":" + label;
        String ours = plugin.getName().toLowerCase(Locale.ROOT) + suffix;
        for (Map.Entry<String, Command> e : known.entrySet()) {
            if (e.getKey().endsWith(suffix) && !e.getKey().equals(ours) && !(e.getValue() instanceof PerkCommand)) return e.getValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> knownCommands(CommandMap map) {
        if (map == null) return null;
        try {
            Method m = map.getClass().getMethod("getKnownCommands");
            Object o = m.invoke(map);
            return o instanceof Map<?, ?> mm ? (Map<String, Command>) mm : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ the actions

    /** The player's thread (commands run there, Folia too). */
    void run(Player p, Action action, String[] args) {
        switch (action) {
            case CRAFT -> p.openWorkbench(null, true);
            case ANVIL -> station(p, "openAnvil", "ANVIL");
            case GRINDSTONE -> station(p, "openGrindstone", "GRINDSTONE");
            case SMITHING -> station(p, "openSmithingTable", "SMITHING");
            case LOOM -> station(p, "openLoom", "LOOM");
            case STONECUTTER -> station(p, "openStonecutter", "STONECUTTER");
            case CARTOGRAPHY -> station(p, "openCartographyTable", "CARTOGRAPHY_TABLE");
            case ENDERCHEST -> p.openInventory(p.getEnderChest());
            case TRASH -> trash(p);
            case HAT -> hat(p);
            case NICK -> nick(p, args);
            default -> {
            }
        }
    }

    /**
     * Paper has player.openAnvil(location, force) and the rest; Spigot only has the newer MenuType
     * API (1.21+). Called by name, so neither missing one breaks the other server.
     */
    private void station(Player p, String paperMethod, String menuType) {
        try {
            Method m = HumanEntity.class.getMethod(paperMethod, org.bukkit.Location.class, boolean.class);
            m.invoke(p, null, true);
            return;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            Class<?> types = Class.forName("org.bukkit.inventory.MenuType");
            Object type = types.getField(menuType).get(null);
            Method create = null;
            for (Method m : type.getClass().getMethods()) {
                if (m.getName().equals("create") && m.getParameterCount() == 2 && m.getParameterTypes()[0] == HumanEntity.class
                        && m.getParameterTypes()[1] == String.class) create = m;
            }
            if (create != null) {
                create.setAccessible(true);
                Object view = create.invoke(type, p, menuType.charAt(0) + menuType.substring(1).toLowerCase(Locale.ROOT).replace('_', ' '));
                for (Method m : HumanEntity.class.getMethods()) {
                    if (m.getName().equals("openInventory") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(view)) {
                        m.invoke(p, view);
                        return;
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        plugin.text().send(p, "perk.unsupported", null);
    }

    private void trash(Player p) {
        Trash holder = new Trash();
        holder.inventory = plugin.text().inventory(holder, 36, p, plugin.text().raw("perk.trash-title"), null);
        p.openInventory(holder.inventory);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Trash) event.getInventory().clear();
    }

    /** The item in the hand goes on the head; what was on the head comes back to the hand. */
    private void hat(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack hand = inv.getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            plugin.text().send(p, "perk.hat-empty", null);
            return;
        }
        ItemStack old = inv.getHelmet();
        ItemStack one = hand.clone();
        one.setAmount(1);
        hand.setAmount(hand.getAmount() - 1);
        inv.setHelmet(one);
        if (hand.getAmount() <= 0) inv.setItemInMainHand(old);
        else {
            inv.setItemInMainHand(hand);
            if (old != null && old.getType() != Material.AIR) {
                for (ItemStack rest : inv.addItem(old).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
        }
        plugin.text().send(p, "perk.hat-on", Ph.of("item", one.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
    }

    // ------------------------------------------------------------------ nick

    private static final java.util.regex.Pattern ALLOWED_TAG = java.util.regex.Pattern.compile(
            "/?(#[0-9a-fA-F]{6}|black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|dark_gray|dark_grey|blue|green|aqua"
                    + "|red|light_purple|yellow|white|b|bold|i|italic|u|underlined|rainbow|gradient(:#[0-9a-fA-F]{6}){2,4}|color:#[0-9a-fA-F]{6})");

    private void nick(Player p, String[] args) {
        ShopConfig cfg = plugin.config();
        Profile profile = plugin.shop().ready(p);
        if (profile == null) return;
        if (args.length == 0) {
            plugin.text().send(p, "perk.nick-usage", null);
            return;
        }
        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("reset")) {
            plugin.data().setActive(profile, Category.NICK, null);
            plugin.cosmetics().refresh(p);
            plugin.text().send(p, "perk.nick-off", null);
            return;
        }
        String raw = String.join(" ", args).trim();
        boolean colors = p.hasPermission(cfg.nickColorsPermission);
        String mini = colors ? cleanTags(raw) : Text.escape(raw);
        String plain = Text.plain(mini);
        if (!plain.matches("[A-Za-z0-9_]+") || plain.length() < cfg.nickMinLength || plain.length() > cfg.nickMaxLength) {
            plugin.text().send(p, "perk.nick-bad", Ph.of("min", cfg.nickMinLength, "max", cfg.nickMaxLength));
            return;
        }
        if (mini.length() > 64) {
            plugin.text().send(p, "perk.nick-too-fancy", null);
            return;
        }
        // No pretending to be someone else who is online
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(p) && other.getName().equalsIgnoreCase(plain)) {
                plugin.text().send(p, "perk.nick-taken", Ph.of("nick", plain));
                return;
            }
        }
        plugin.data().setActive(profile, Category.NICK, mini);
        plugin.cosmetics().refresh(p);
        plugin.text().send(p, "perk.nick-set", new Ph().snippet("nick", mini));
    }

    /** Keeps only colour / bold / italic tags; anything else (click, hover, insert...) is shown as text. */
    static String cleanTags(String raw) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            int close = c == '<' ? raw.indexOf('>', i) : -1;
            if (close > i) {
                String tag = raw.substring(i + 1, close).toLowerCase(Locale.ROOT);
                if (ALLOWED_TAG.matcher(tag).matches()) {
                    out.append('<').append(tag).append('>');
                    i = close + 1;
                    continue;
                }
            }
            if (c == '<' || c == '\\') out.append('\\');
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
