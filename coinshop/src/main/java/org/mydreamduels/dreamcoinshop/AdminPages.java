package org.mydreamduels.dreamcoinshop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The admin screens behind /coinshop settings: the Sale screen and the item editor (add, change
 * and delete shop items - names, prices, icons, colours, tags, particles...). Everything is
 * written to config.yml through {@link Settings#apply}, which checks the permission again.
 */
final class AdminPages {

    enum Kind { TEXT, NUMBER, BOOL, CHOICE, LIST }

    /** One editable value of an item. list = how it's typed: "a, b" (gradient) or "a | b" (commands). */
    record Field(String key, Kind kind, List<String> options, String separator) {
        static Field text(String key) {
            return new Field(key, Kind.TEXT, List.of(), null);
        }

        static Field number(String key) {
            return new Field(key, Kind.NUMBER, List.of(), null);
        }
    }

    /** An editable section of config.yml. id = menu id, path = config.yml section. */
    record Section(String id, String path, String icon, List<Field> fields) {
        boolean hasIcon() {
            return fields.stream().anyMatch(f -> f.key().equals("icon"));
        }
    }

    private static final Field DISPLAY = Field.text("display"), PRICE = Field.number("price"), ICON = Field.text("icon");

    static final List<Section> SECTIONS = List.of(
            new Section("chatcolors", "chatcolors", "NAME_TAG", List.of(DISPLAY, PRICE, ICON, Field.text("color"),
                    new Field("gradient", Kind.LIST, List.of(), ","))),
            new Section("glows", "glows", "GLOWSTONE_DUST", List.of(DISPLAY, PRICE, ICON, Field.text("color"),
                    new Field("rainbow", Kind.BOOL, List.of(), null))),
            new Section("tags", "tags", "PAPER", List.of(DISPLAY, PRICE, ICON, Field.text("text"))),
            new Section("gradients", "namegradients", "FIREWORK_STAR", List.of(DISPLAY, PRICE, ICON, Field.text("from"), Field.text("to"))),
            new Section("particles", "particles", "BLAZE_POWDER", List.of(DISPLAY, PRICE, ICON,
                    new Field("effect", Kind.CHOICE, Arrays.stream(ParticleEffects.Style.values()).map(Enum::name).toList(), null))),
            new Section("perks", "perks", "NETHER_STAR", List.of(DISPLAY, PRICE, ICON, Field.text("permission"))),
            new Section("sellwands", "sellwands", "BLAZE_ROD", List.of(DISPLAY, PRICE, Field.number("multiplier"), Field.number("uses"))),
            new Section("spawnerwands", "spawnerwands", "SPAWNER", List.of(DISPLAY, PRICE, Field.number("multiplier"))),
            new Section("orbshop", "orbshop", "ENDER_PEARL", List.of(DISPLAY, PRICE, ICON, new Field("commands", Kind.LIST, List.of(), "|"))));

    static Section section(String id) {
        for (Section s : SECTIONS) if (s.id().equals(id)) return s;
        return null;
    }

    private static final List<String> SALE_SECTIONS;

    static {
        List<String> list = new ArrayList<>();
        list.add("all");
        list.addAll(Sales.SECTIONS);
        SALE_SECTIONS = List.copyOf(list);
    }

    private final Dreamcoinshop plugin;
    private final Menus menus;
    private final Pages pages;

    AdminPages(Dreamcoinshop plugin, Menus menus, Pages pages) {
        this.plugin = plugin;
        this.menus = menus;
        this.pages = pages;
    }

    private Text text() {
        return plugin.text();
    }

    private String msg(String path, Object... kv) {
        return pages.msg(path, kv);
    }

    private Menu denied(Menu m, Player player) {
        if (player.hasPermission("dreamcoinshop.settings")) return null;
        m.body(msg("general.no-permission"));
        return m;
    }

    // ------------------------------------------------------------------ sale

    /** Chest menus keep the choice in the view: arg = "percent:hours:section index". */
    Menu sale(Player player, Pages.View view, boolean dialog) {
        Menu m = new Menu(plugin.config().title("settings", "<red><bold>Coinshop Settings"));
        m.back = pages.back(Pages.View.of("settings"));
        m.infoIcon = Menu.Icon.of("CLOCK");
        if (denied(m, player) != null) return m;
        Sales sales = plugin.sales();
        Sales.Sale now = sales.current();
        long t = System.currentTimeMillis();
        if (now.active(t)) {
            m.body(msg("menu.sale-line", "percent", now.percent(), "time", Sales.time(now.endsAt() - t),
                    "sections", Text.escape(sales.sectionNames(now))));
        } else {
            m.body(msg("sale-menu.none"));
        }
        m.body(msg("sale-menu.intro"));
        int[] choice = saleChoice(view);
        if (dialog) {
            m.inputs.add(new Menu.Input("sale_percent", Menu.InputType.NUMBER, msg("sale-menu.percent"), (double) choice[0], 5, 90, 5, List.of()));
            m.inputs.add(new Menu.Input("sale_hours", Menu.InputType.NUMBER, msg("sale-menu.hours"), (double) choice[1], 1, 168, 1, List.of()));
            m.inputs.add(new Menu.Input("sale_section", Menu.InputType.CHOICE, msg("sale-menu.section"), SALE_SECTIONS.get(choice[2]), 0, 0, 0, SALE_SECTIONS));
            m.add(Menu.Button.of(Menu.Icon.of("LIME_DYE"), msg("sale-menu.start"), null, null, (p, c, in) -> {
                int pct = (int) Math.round(number(in.get("sale_percent"), choice[0]));
                int hours = (int) Math.round(number(in.get("sale_hours"), choice[1]));
                String sec = String.valueOf(in.getOrDefault("sale_section", "all")).toLowerCase(Locale.ROOT);
                startSale(p, pct, hours, sec);
            }).width(150));
        } else {
            m.add(new Menu.Button(Menu.Icon.of("GOLD_NUGGET"), "<aqua>" + msg("sale-menu.percent"), "<white>" + choice[0] + "%", null,
                    List.of(msg("menu.value-lore", "value", choice[0], "step", 5)), false, 200,
                    (p, c, in) -> menus.open(p, saleView(choice[0] + (c.right() ? -5 : 5) * (c.shift() ? 2 : 1), choice[1], choice[2]))));
            m.add(new Menu.Button(Menu.Icon.of("CLOCK"), "<aqua>" + msg("sale-menu.hours"), "<white>" + choice[1] + "h", null,
                    List.of(msg("menu.value-lore", "value", choice[1], "step", 1)), false, 200,
                    (p, c, in) -> menus.open(p, saleView(choice[0], choice[1] + (c.right() ? -1 : 1) * (c.shift() ? 24 : 1), choice[2]))));
            String secName = sectionLabel(SALE_SECTIONS.get(choice[2]));
            m.add(new Menu.Button(Menu.Icon.of("CHEST"), "<aqua>" + msg("sale-menu.section"), "<white>" + secName, null,
                    List.of(msg("menu.choice-lore", "value", secName)), false, 200,
                    (p, c, in) -> menus.open(p, saleView(choice[0], choice[1], Math.floorMod(choice[2] + (c.right() ? -1 : 1), SALE_SECTIONS.size())))));
            m.add(Menu.Button.of(Menu.Icon.of("LIME_DYE"), msg("sale-menu.start"), null, null,
                    (p, c, in) -> startSale(p, choice[0], choice[1], SALE_SECTIONS.get(choice[2]))));
        }
        if (now.active(t)) {
            m.add(Menu.Button.of(Menu.Icon.of("BARRIER"), msg("sale-menu.stop"), null, null, (p, c, in) -> {
                if (!p.hasPermission("dreamcoinshop.settings")) return;
                plugin.sales().stop(() -> {
                    text().send(p, "sale.stopped", null);
                    menus.open(p, Pages.View.of("sale"));
                });
            }).width(150));
        }
        return m;
    }

    private static Pages.View saleView(int percent, int hours, int section) {
        int pct = Math.max(5, Math.min(90, percent));
        int h = Math.max(1, Math.min(168, hours));
        return new Pages.View("sale", 0, null, pct + ":" + h + ":" + section);
    }

    private static int[] saleChoice(Pages.View view) {
        int[] out = {20, 2, 0};
        if (view.arg() == null) return out;
        String[] p = view.arg().split(":");
        try {
            for (int i = 0; i < Math.min(3, p.length); i++) out[i] = Integer.parseInt(p[i]);
        } catch (NumberFormatException ignored) {
        }
        out[2] = Math.floorMod(out[2], SALE_SECTIONS.size());
        return out;
    }

    private void startSale(Player p, int percent, int hours, String section) {
        if (!p.hasPermission("dreamcoinshop.settings")) return;
        int pct = Math.max(1, Math.min(95, percent));
        long ms = Math.max(1, Math.min(24 * 366, hours)) * 3_600_000L;
        java.util.Set<String> sections = section == null || section.equals("all") || !Sales.SECTIONS.contains(section)
                ? java.util.Set.of() : java.util.Set.of(section);
        plugin.sales().start(pct, ms, sections, null);
        plugin.sounds().play(p, "settings-saved");
        menus.open(p, Pages.View.of("sale"));
    }

    private String sectionLabel(String id) {
        return id.equals("all") ? Text.plain(text().raw("sale.everything")) : Text.plain(text().raw("menu.button." + id));
    }

    private static double number(Object v, double fallback) {
        if (v instanceof Number n) return n.doubleValue();
        Double d = v == null ? null : Pages.parse(String.valueOf(v));
        return d == null ? fallback : d;
    }

    // ------------------------------------------------------------------ item editor: lists

    /** arg null: pick a section. arg = section id: its items and "Add new". */
    Menu items(Player player, Pages.View view, boolean dialog) {
        Menu m = new Menu(plugin.config().title("settings", "<red><bold>Coinshop Settings"));
        m.infoIcon = Menu.Icon.of("WRITABLE_BOOK");
        Section section = view.arg() == null ? null : section(view.arg());
        m.back = pages.back(section == null ? Pages.View.of("settings") : Pages.View.of("items"));
        if (denied(m, player) != null) return m;
        if (section == null) {
            m.body(msg("editor.pick"));
            for (Section s : SECTIONS) {
                m.add(Menu.Button.of(Menu.Icon.of(s.icon()), msg("menu.button." + s.id()), null, null,
                        (p, c, in) -> menus.open(p, new Pages.View("items", 0, null, s.id()))));
            }
            return m;
        }
        m.body(msg("editor.list-intro", "section", Text.plain(msg("menu.button." + section.id()))));
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(section.path());
        List<String> keys = sec == null ? List.of() : new ArrayList<>(sec.getKeys(false));
        m.nav.add(Menu.Button.of(Menu.Icon.of("LIME_DYE"), msg("editor.add"), null, null, (p, c, in) -> addNew(p, section, dialog)).width(150));
        for (String key : pages.paginate(m, view, keys, dialog ? 40 : Pages.CHEST_PAGE)) {
            String path = section.path() + "." + key;
            String display = plugin.getConfig().getString(path + ".display", key);
            double price = plugin.getConfig().getDouble(path + ".price", 0);
            String icon = plugin.getConfig().getString(path + ".icon", section.icon());
            m.add(new Menu.Button(Menu.Icon.of(icon), "<white>" + display, "<gray>" + Shop.format(price), msg("editor.click-edit"),
                    List.of("<dark_gray>" + Text.escape(key)), false, 220,
                    (p, c, in) -> menus.open(p, new Pages.View("item", 0, null, section.id() + ":" + key))));
        }
        if (keys.isEmpty()) m.body(msg("menu.empty"));
        return m;
    }

    private void addNew(Player p, Section section, boolean dialog) {
        if (dialog) {
            menus.open(p, new Pages.View("item", 0, null, section.id() + ":"));
            return;
        }
        plugin.prompts().ask(p, msg("editor.prompt-key"), answer -> {
            String key = answer.trim().toLowerCase(Locale.ROOT);
            if (!validKey(p, section, key)) {
                menus.open(p, new Pages.View("items", 0, null, section.id()), Menus.Kind.CHEST);
                return;
            }
            Map<String, Object> changes = new LinkedHashMap<>(defaults(section, key));
            plugin.settings().apply(changes, p, () -> {
                text().send(p, "editor.created", Ph.of("item", key));
                menus.open(p, new Pages.View("item", 0, null, section.id() + ":" + key), Menus.Kind.CHEST);
            });
        }, () -> menus.open(p, new Pages.View("items", 0, null, section.id()), Menus.Kind.CHEST));
    }

    /** The values a new item starts with. */
    private Map<String, Object> defaults(Section section, String key) {
        String path = section.path() + "." + key;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(path + ".display", key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1).replace('_', ' '));
        out.put(path + ".price", 1000L);
        if (section.hasIcon()) out.put(path + ".icon", section.icon());
        switch (section.id()) {
            case "chatcolors", "glows" -> out.put(path + ".color", "#FFFFFF");
            case "tags" -> out.put(path + ".text", "<gray>[<white>" + key.toUpperCase(Locale.ROOT) + "</white>]</gray>");
            case "gradients" -> {
                out.put(path + ".from", "#FFFFFF");
                out.put(path + ".to", "#AAAAAA");
            }
            case "particles" -> out.put(path + ".effect", ParticleEffects.Style.FLAME_TRAIL.name());
            case "perks" -> out.put(path + ".permission", "dreamcoinshop.perk." + key);
            case "sellwands" -> {
                out.put(path + ".multiplier", 1.5);
                out.put(path + ".uses", 100L);
            }
            case "spawnerwands" -> out.put(path + ".multiplier", 1.5);
            case "orbshop" -> out.put(path + ".commands", List.of("give %player% diamond 1"));
            default -> {
            }
        }
        return out;
    }

    private boolean validKey(Player p, Section section, String key) {
        if (!key.matches("[a-z0-9_]{1,32}")) {
            text().send(p, "editor.bad-key", null);
            return false;
        }
        if (plugin.getConfig().isConfigurationSection(section.path() + "." + key)) {
            text().send(p, "editor.key-taken", Ph.of("item", key));
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ item editor: one item

    /** arg = "section:key" ("section:" = a new one, dialogs only). query "delete" = confirm deleting. */
    Menu item(Player player, Pages.View view, boolean dialog) {
        Menu m = new Menu(plugin.config().title("settings", "<red><bold>Coinshop Settings"));
        m.infoIcon = Menu.Icon.of("WRITABLE_BOOK");
        String arg = view.arg() == null ? "" : view.arg();
        int colon = arg.indexOf(':');
        Section section = colon < 0 ? null : section(arg.substring(0, colon));
        if (section == null) return items(player, Pages.View.of("items"), dialog);
        String key = arg.substring(colon + 1);
        boolean isNew = key.isEmpty();
        Pages.View list = new Pages.View("items", 0, null, section.id());
        m.back = pages.back(list);
        if (denied(m, player) != null) return m;
        String path = section.path() + "." + key;
        if (!isNew && !plugin.getConfig().isConfigurationSection(path)) return items(player, list, dialog);
        String sectionName = Text.plain(msg("menu.button." + section.id()));

        if ("delete".equals(view.query())) {
            m.body(msg("editor.delete-confirm", "item", Text.escape(key)));
            m.add(Menu.Button.of(Menu.Icon.of("RED_DYE"), msg("editor.yes-delete"), null, null, (p, c, in) -> {
                Map<String, Object> changes = new HashMap<>();
                changes.put(path, null);
                plugin.settings().apply(changes, p, () -> {
                    text().send(p, "editor.deleted", Ph.of("item", key));
                    menus.open(p, list);
                });
            }).width(150));
            m.add(Menu.Button.of(Menu.Icon.of("LIME_DYE"), msg("editor.no-keep"), null, null,
                    (p, c, in) -> menus.open(p, new Pages.View("item", 0, null, arg))).width(150));
            return m;
        }

        m.body(isNew ? msg("editor.new", "section", sectionName)
                : msg("editor.editing", "item", Text.escape(key), "section", sectionName));
        if (section.hasIcon()) m.body(msg("editor.help-icon"));
        if (section.id().equals("orbshop")) m.body(msg("editor.help-commands"));
        if (section.id().equals("chatcolors")) m.body(msg("editor.help-gradient"));

        if (dialog) {
            if (isNew) m.inputs.add(new Menu.Input("key", Menu.InputType.TEXT, msg("editor.key-label"), "", 0, 32, 0, List.of()));
            for (Field f : section.fields()) m.inputs.add(input(f, path, isNew));
            m.add(Menu.Button.of(Menu.Icon.of("LIME_DYE"), msg("menu.button.save"), null, null,
                    (p, c, in) -> saveDialog(p, section, key, in)).width(150));
        } else {
            for (Field f : section.fields()) m.add(chestField(section, key, path, f));
        }
        if (!isNew && section.hasIcon()) {
            m.add(Menu.Button.of(Menu.Icon.of("ITEM_FRAME"), msg("editor.use-hand"), null, msg("editor.use-hand-tip"),
                    (p, c, in) -> useHand(p, section, key)).width(150));
        }
        if (!isNew) {
            m.add(Menu.Button.of(Menu.Icon.of("BARRIER"), msg("editor.delete"), null, null,
                    (p, c, in) -> menus.open(p, new Pages.View("item", 0, "delete", arg))).width(150));
        }
        return m;
    }

    private Menu.Input input(Field f, String path, boolean isNew) {
        String label = msg("editor.field." + f.key());
        Object current = isNew ? null : plugin.getConfig().get(path + "." + f.key());
        return switch (f.kind()) {
            case BOOL -> new Menu.Input(f.key(), Menu.InputType.BOOL, label, Boolean.TRUE.equals(current), 0, 1, 1, List.of());
            case CHOICE -> new Menu.Input(f.key(), Menu.InputType.CHOICE, label,
                    current == null ? f.options().get(0) : String.valueOf(current).toUpperCase(Locale.ROOT), 0, 0, 0, f.options());
            default -> new Menu.Input(f.key(), Menu.InputType.TEXT, label, shown(f, current, isNew), 0, 256, 0, List.of());
        };
    }

    /** A value as it's typed in a text box. */
    private static String shown(Field f, Object current, boolean isNew) {
        if (current == null) return isNew && f.key().equals("price") ? "1000" : "";
        if (current instanceof List<?> l) {
            StringBuilder sb = new StringBuilder();
            for (Object o : l) {
                if (sb.length() > 0) sb.append(f.separator().equals("|") ? " | " : ", ");
                sb.append(o);
            }
            return sb.toString();
        }
        if (current instanceof Number n) return Shop.format(n.doubleValue());
        return String.valueOf(current);
    }

    private Menu.Button chestField(Section section, String key, String path, Field f) {
        Object current = plugin.getConfig().get(path + "." + f.key());
        String label = "<aqua>" + msg("editor.field." + f.key());
        String value = shown(f, current, false);
        String status = "<white>" + (value.isEmpty() ? "-" : Text.escape(value.length() > 40 ? value.substring(0, 40) + "..." : value));
        Pages.View here = new Pages.View("item", 0, null, section.id() + ":" + key);
        return switch (f.kind()) {
            case BOOL -> new Menu.Button(Menu.Icon.of(Boolean.TRUE.equals(current) ? "LIME_DYE" : "GRAY_DYE"), label, status, msg("editor.click-toggle"),
                    List.of(), Boolean.TRUE.equals(current), 200, (p, c, in) -> save(p, Map.of(path + "." + f.key(), !Boolean.TRUE.equals(current)), key, here));
            case CHOICE -> new Menu.Button(Menu.Icon.of("COMPASS"), label, status, msg("editor.click-cycle"), List.of(), false, 200, (p, c, in) -> {
                List<String> o = f.options();
                int i = o.indexOf(String.valueOf(current).toUpperCase(Locale.ROOT));
                save(p, Map.of(path + "." + f.key(), o.get(Math.floorMod(i + (c.right() ? -1 : 1), o.size()))), key, here);
            });
            default -> new Menu.Button(Menu.Icon.of(f.key().equals("icon") && current != null ? String.valueOf(current) : "NAME_TAG"),
                    label, status, msg("editor.click-type"), List.of(), false, 200, (p, c, in) ->
                    plugin.prompts().ask(p, msg("editor.prompt", "field", Text.plain(msg("editor.field." + f.key())), "value", Text.escape(value)),
                            answer -> {
                                Map<String, Object> changes = new HashMap<>();
                                if (convert(p, f, answer, path, changes)) save(p, changes, key, here);
                                else menus.open(p, here, Menus.Kind.CHEST);
                            }, () -> menus.open(p, here, Menus.Kind.CHEST)));
        };
    }

    private void saveDialog(Player p, Section section, String key, Map<String, Object> in) {
        String k = key;
        if (k.isEmpty()) {
            k = String.valueOf(in.getOrDefault("key", "")).trim().toLowerCase(Locale.ROOT);
            if (!validKey(p, section, k)) return;
        }
        String path = section.path() + "." + k;
        Map<String, Object> changes = new HashMap<>();
        if (key.isEmpty()) changes.putAll(defaults(section, k));
        for (Field f : section.fields()) {
            Object v = in.get(f.key());
            if (v == null) continue;
            if (!convert(p, f, String.valueOf(v), path, changes)) return;
        }
        String finalKey = k;
        save(p, changes, finalKey, new Pages.View("item", 0, null, section.id() + ":" + finalKey));
    }

    /** Checks and converts a typed value into changes. False (and tells the player why) if it's wrong. */
    private boolean convert(Player p, Field f, String raw, String path, Map<String, Object> changes) {
        String v = raw.trim();
        String at = path + "." + f.key();
        switch (f.kind()) {
            case BOOL -> changes.put(at, Boolean.parseBoolean(v) || v.equals("1b") || v.equalsIgnoreCase("yes"));
            case CHOICE -> {
                String up = v.toUpperCase(Locale.ROOT);
                if (!f.options().contains(up)) {
                    text().send(p, "editor.bad-choice", Ph.of("value", v, "options", String.join(", ", f.options())));
                    return false;
                }
                changes.put(at, up);
            }
            case NUMBER -> {
                Double d = Pages.parse(v);
                if (d == null || d < 0) {
                    text().send(p, "settings.bad-number", Ph.of("setting", Text.plain(msg("editor.field." + f.key()))));
                    return false;
                }
                changes.put(at, switch (f.key()) {
                    case "uses" -> (Object) Math.max(1L, Math.round(d));
                    case "price" -> Settings.storePrice(d);
                    default -> (Object) d;
                });
            }
            case LIST -> {
                List<String> items = new ArrayList<>();
                for (String part : v.split(java.util.regex.Pattern.quote(f.separator()))) if (!part.isBlank()) items.add(part.trim());
                if (f.key().equals("gradient")) {
                    for (String hex : items) if (!hex.matches("#[0-9a-fA-F]{6}")) return badColor(p, hex);
                    if (items.size() == 1) return badColor(p, v);
                }
                changes.put(at, items.isEmpty() ? null : items);
            }
            default -> {
                switch (f.key()) {
                    case "color", "from", "to" -> {
                        if (v.isEmpty() && f.key().equals("color")) {
                            changes.put(at, null);
                            return true;
                        }
                        if (!v.matches("#[0-9a-fA-F]{6}")) return badColor(p, v);
                    }
                    case "icon" -> {
                        Material m = Material.matchMaterial(v);
                        if (m == null || !m.isItem() || m.isAir()) {
                            text().send(p, "editor.bad-icon", Ph.of("value", v));
                            return false;
                        }
                        v = m.name();
                    }
                    case "display", "text" -> {
                        if (v.isEmpty()) {
                            text().send(p, "editor.empty", Ph.of("field", Text.plain(msg("editor.field." + f.key()))));
                            return false;
                        }
                    }
                    default -> {
                    }
                }
                changes.put(at, v.isEmpty() ? null : v);
            }
        }
        return true;
    }

    private boolean badColor(Player p, String value) {
        text().send(p, "editor.bad-color", Ph.of("value", value));
        return false;
    }

    private void useHand(Player p, Section section, String key) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            text().send(p, "editor.empty-hand", null);
            return;
        }
        save(p, Map.of(section.path() + "." + key + ".icon", hand.getType().name()), key,
                new Pages.View("item", 0, null, section.id() + ":" + key));
    }

    private void save(Player p, Map<String, Object> changes, String key, Pages.View back) {
        plugin.settings().apply(changes, p, () -> {
            text().send(p, "editor.saved", Ph.of("item", key));
            plugin.sounds().play(p, "settings-saved");
            menus.open(p, back);
        });
    }
}
