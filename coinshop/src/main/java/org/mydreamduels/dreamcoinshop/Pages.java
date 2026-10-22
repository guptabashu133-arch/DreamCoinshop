package org.mydreamduels.dreamcoinshop;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds every screen of the shop for one player. The same {@link Menu} is drawn as a dialog or
 * as a chest menu ({@code dialog} only changes what can't be the same: text inputs, page sizes).
 */
final class Pages {

    /** Which screen, and its page / search / sub-section. */
    record View(String id, int page, String query, String arg) {
        static View of(String id) {
            return new View(id, 0, null, null);
        }

        View page(int p) {
            return new View(id, p, query, arg);
        }
    }

    /** Fallback icons by item key (version 1 had these built in; new items can set icon:). */
    private static final Map<String, String> CHAT_ICONS = Map.ofEntries(
            Map.entry("red", "RED_DYE"), Map.entry("gold", "ORANGE_DYE"), Map.entry("yellow", "YELLOW_DYE"),
            Map.entry("green", "LIME_DYE"), Map.entry("aqua", "LIGHT_BLUE_DYE"), Map.entry("blue", "BLUE_DYE"),
            Map.entry("light_purple", "MAGENTA_DYE"), Map.entry("white", "WHITE_DYE"), Map.entry("dark_red", "RED_DYE"),
            Map.entry("dark_green", "GREEN_DYE"), Map.entry("dark_aqua", "CYAN_DYE"), Map.entry("dark_purple", "PURPLE_DYE"),
            Map.entry("purple", "PURPLE_DYE"), Map.entry("rainbow", "FIREWORK_STAR"), Map.entry("sunset", "ORANGE_DYE"),
            Map.entry("ocean", "LIGHT_BLUE_DYE"), Map.entry("fire", "BLAZE_POWDER"), Map.entry("toxic", "LIME_DYE"),
            Map.entry("galaxy", "PURPLE_DYE"), Map.entry("candy", "PINK_DYE"), Map.entry("ice", "BLUE_ICE"),
            Map.entry("phoenix", "BLAZE_POWDER"), Map.entry("vip", "DIAMOND"), Map.entry("queen", "PINK_DYE"),
            Map.entry("pro", "EMERALD"), Map.entry("legend", "GOLDEN_APPLE"), Map.entry("god", "NETHER_STAR"),
            Map.entry("king", "GOLD_BLOCK"), Map.entry("angel", "PINK_TULIP"), Map.entry("boss", "REDSTONE_BLOCK"),
            Map.entry("dream", "AMETHYST_SHARD"), Map.entry("craft", "CRAFTING_TABLE"), Map.entry("smithingtable", "SMITHING_TABLE"),
            Map.entry("grindstone", "GRINDSTONE"), Map.entry("anvil", "ANVIL"), Map.entry("enderchest", "ENDER_EYE"),
            Map.entry("nick", "NAME_TAG"), Map.entry("sunset_gradient", "ORANGE_DYE"), Map.entry("ocean_gradient", "LIGHT_BLUE_DYE"),
            Map.entry("toxic_gradient", "LIME_DYE"));

    private static final Map<String, String> SECTION_ICONS = Map.ofEntries(
            Map.entry("chatcolors", "NAME_TAG"), Map.entry("glows", "GLOWSTONE_DUST"), Map.entry("perks", "NETHER_STAR"),
            Map.entry("tags", "PAPER"), Map.entry("gradients", "FIREWORK_STAR"), Map.entry("flags", "WHITE_BANNER"),
            Map.entry("sellwands", "BLAZE_ROD"), Map.entry("spawnerwands", "SPAWNER"), Map.entry("buycoins", "GOLD_INGOT"),
            Map.entry("topcoins", "DIAMOND"), Map.entry("orbshop", "ENDER_PEARL"), Map.entry("settings", "COMPARATOR"));

    /** Items per page of a chest menu (4 rows of 7). */
    static final int CHEST_PAGE = 28;

    private final Dreamcoinshop plugin;
    private final Menus menus;

    Pages(Dreamcoinshop plugin, Menus menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    private Text text() {
        return plugin.text();
    }

    private ShopConfig cfg() {
        return plugin.config();
    }

    private String msg(String path) {
        return text().raw(path);
    }

    /** A message with its placeholders filled in, still as MiniMessage (for menu lines). */
    private String msg(String path, Object... keyValues) {
        String raw = msg(path);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            raw = raw.replace("<" + keyValues[i] + ">", String.valueOf(keyValues[i + 1]));
        }
        return raw;
    }

    Menu build(Player player, View view, boolean dialog) {
        Profile profile = plugin.data().profile(player);
        return switch (view.id()) {
            case "chatcolors" -> cosmetics(player, profile, view, dialog, Category.CHATCOLOR, "chatcolors", "<gold><bold>Chat Colour");
            case "glows" -> cosmetics(player, profile, view, dialog, Category.GLOW, "glows", "<gold><bold>Glow");
            case "tags" -> cosmetics(player, profile, view, dialog, Category.TAG, "tags", "<gold><bold>Tags");
            case "gradients" -> cosmetics(player, profile, view, dialog, Category.GRADIENT, "gradients", "<gold><bold>Name Gradient");
            case "perks" -> perks(profile, view, dialog);
            case "flags" -> flags(profile, view, dialog);
            case "sellwands" -> wands(view, dialog, false);
            case "spawnerwands" -> wands(view, dialog, true);
            case "buycoins" -> buyCoins();
            case "topcoins" -> top(player, Currency.COINS);
            case "orbstop" -> top(player, Currency.ORBS);
            case "orbshop" -> orbShop(profile, view, dialog);
            case "settings" -> settings(player, view, dialog);
            case "prices" -> view.arg() == null ? pricePick() : prices(view, dialog);
            default -> main(player, profile);
        };
    }

    // ------------------------------------------------------------------ main

    private Menu main(Player player, Profile profile) {
        ShopConfig cfg = cfg();
        Menu m = new Menu(cfg.title("main", "<gold><bold>Coinshop"));
        if (cfg.showBalance) m.body(balanceLine(profile));
        m.infoIcon = Menu.Icon.of("GOLD_INGOT");
        for (String section : ShopConfig.SECTIONS) {
            if (!cfg.section(section)) continue;
            Menu.Icon icon = Menu.Icon.of(SECTION_ICONS.get(section));
            if (section.equals("flags")) {
                Flags.Country shown = plugin.flags().rotating(cfg, System.currentTimeMillis());
                if (shown != null) icon = Menu.Icon.flag(shown);
            }
            m.add(Menu.Button.of(icon, msg("menu.button." + section), null,
                    "<gray>" + Text.escape(cfg.description(section, "")),
                    (p, c, in) -> menus.open(p, View.of(section))));
        }
        if (player.hasPermission("dreamcoinshop.settings")) {
            m.add(Menu.Button.of(Menu.Icon.of(SECTION_ICONS.get("settings")), msg("menu.button.settings"), null,
                    "<gray>" + Text.escape(cfg.description("settings", "")), (p, c, in) -> menus.open(p, View.of("settings"))));
        }
        m.close = Menu.Button.of(Menu.Icon.of("BARRIER"), msg("menu.button.close"), null, null, (p, c, in) -> menus.close(p)).width(120);
        m.rows = 0;
        return m;
    }

    private String balanceLine(Profile profile) {
        if (!profile.isLoaded()) return msg("menu.loading");
        return msg("menu.balance", "coins", Shop.format(Math.floor(profile.balance(Currency.COINS))),
                "orbs", Shop.format(Math.floor(profile.balance(Currency.ORBS))));
    }

    private Menu.Button back(View to) {
        return Menu.Button.of(Menu.Icon.of("ARROW"), msg("menu.button.back"), null, msg("menu.tooltip.back"),
                (p, c, in) -> menus.open(p, to)).width(120);
    }

    // ------------------------------------------------------------------ cosmetics

    private Menu cosmetics(Player player, Profile profile, View view, boolean dialog, Category category, String section, String title) {
        Menu m = new Menu(cfg().title(section, title));
        m.back = back(View.of("main"));
        m.infoIcon = Menu.Icon.of(SECTION_ICONS.get(section));
        m.body(balanceLine(profile));
        List<ShopConfig.Item> items = new ArrayList<>(cfg().items(category).values());
        List<ShopConfig.Item> page = paginate(m, view, items, dialog ? 40 : CHEST_PAGE);
        for (ShopConfig.Item item : page) {
            String label = switch (category) {
                case CHATCOLOR -> colored(item.display(), item.color(), item.gradient(), false);
                case GLOW -> colored(item.display(), item.color(), null, item.rainbow());
                case TAG -> msg("menu.tag-preview", "player", Text.escape(player.getName()), "tag", item.text());
                case GRADIENT -> colored(item.display(), null, item.gradient(), false);
                default -> item.display();
            };
            m.add(cosmeticButton(profile, category, item.key(), icon(item), label, item.price()).width(240));
        }
        if (items.isEmpty()) m.body(msg("menu.empty"));
        return m;
    }

    private static String colored(String display, String color, List<String> gradient, boolean rainbow) {
        if (rainbow) return "<rainbow>" + display + "</rainbow>";
        if (gradient != null && gradient.size() >= 2) return "<gradient:" + String.join(":", gradient) + ">" + display + "</gradient>";
        if (color != null && color.matches("#[0-9a-fA-F]{6}")) return "<color:" + color + ">" + display + "</color>";
        return "<white>" + display;
    }

    private static Menu.Icon icon(ShopConfig.Item item) {
        String material = item.icon() != null ? item.icon() : CHAT_ICONS.getOrDefault(item.key(), defaultIcon(item.category()));
        return Menu.Icon.of(material);
    }

    private static String defaultIcon(Category c) {
        return switch (c) {
            case CHATCOLOR, GLOW, GRADIENT -> "WHITE_DYE";
            case PERK -> "NETHER_STAR";
            case TAG -> "PAPER";
            case FLAG -> "WHITE_BANNER";
        };
    }

    private Menu.Button cosmeticButton(Profile profile, Category category, String key, Menu.Icon icon, String label, double price) {
        boolean owned = profile.owns(category, key);
        boolean active = owned && key.equals(profile.active(category));
        String status = !owned ? msg("menu.status.price", "price", Shop.format(price))
                : active ? msg("menu.status.active") : msg("menu.status.owned");
        String tooltip = !owned ? msg("menu.tooltip.buy") : active ? msg("menu.tooltip.unequip") : msg("menu.tooltip.equip");
        return new Menu.Button(icon, label, status, tooltip, List.of(), active, 240, (p, c, in) -> {
            plugin.shop().clickCosmetic(p, category, key);
            menus.refresh(p);
        });
    }

    // ------------------------------------------------------------------ perks

    private Menu perks(Profile profile, View view, boolean dialog) {
        Menu m = new Menu(cfg().title("perks", "<gold><bold>Perks"));
        m.back = back(View.of("main"));
        m.infoIcon = Menu.Icon.of("NETHER_STAR");
        m.body(balanceLine(profile));
        List<ShopConfig.Item> items = new ArrayList<>(cfg().items(Category.PERK).values());
        for (ShopConfig.Item perk : paginate(m, view, items, dialog ? 40 : CHEST_PAGE)) {
            boolean owned = profile.owns(Category.PERK, perk.key());
            String status = owned ? msg("menu.status.perk-owned") : msg("menu.status.price", "price", Shop.format(perk.price()));
            m.add(new Menu.Button(icon(perk), "<yellow>" + perk.display(), status,
                    owned ? msg("menu.tooltip.owned") : msg("menu.tooltip.buy"), List.of(), owned, 220, (p, c, in) -> {
                plugin.shop().buyPerk(p, perk.key());
                menus.refresh(p);
            }));
        }
        if (items.isEmpty()) m.body(msg("menu.empty"));
        return m;
    }

    // ------------------------------------------------------------------ flags

    private Menu flags(Profile profile, View view, boolean dialog) {
        ShopConfig cfg = cfg();
        Menu m = new Menu(cfg.title("flags", "<gold><bold>Flags"));
        m.back = back(View.of("main"));
        m.body(balanceLine(profile));
        List<Flags.Country> list = plugin.flags().search(cfg, view.query());
        Flags.Country shown = plugin.flags().rotating(cfg, System.currentTimeMillis());
        m.infoIcon = shown != null ? Menu.Icon.flag(shown) : Menu.Icon.of("WHITE_BANNER");
        if (view.query() != null && !view.query().isBlank()) {
            m.body(msg("menu.search-results", "query", Text.escape(view.query()), "count", list.size()));
        }
        if (dialog) {
            m.inputs.add(new Menu.Input("search", Menu.InputType.TEXT, msg("menu.search-label"),
                    view.query() == null ? "" : view.query(), 0, 32, 0, List.of()));
            m.nav.add(Menu.Button.of(Menu.Icon.of("SPYGLASS"), msg("menu.button.search"), null, msg("menu.tooltip.search"),
                    (p, c, in) -> {
                        Object q = in.get("search");
                        String query = q == null ? null : String.valueOf(q).trim();
                        menus.open(p, new View("flags", 0, query == null || query.isEmpty() ? null : query, null));
                    }).width(120));
        }
        if (view.query() != null) {
            m.nav.add(Menu.Button.of(Menu.Icon.of("BOOK"), msg("menu.button.clear-search"), null, null,
                    (p, c, in) -> menus.open(p, View.of("flags"))).width(120));
        }
        int perPage = dialog ? cfg.flagsPerPage : CHEST_PAGE;
        for (Flags.Country country : paginate(m, view, list, perPage)) {
            String label = msg("menu.flag-label", "country", Text.escape(country.name()), "code", country.code());
            m.add(cosmeticButton(profile, Category.FLAG, country.code(), Menu.Icon.flag(country), label, cfg.flagPrice).width(200));
        }
        if (list.isEmpty()) m.body(msg("menu.empty"));
        return m;
    }

    // ------------------------------------------------------------------ wands

    private Menu wands(View view, boolean dialog, boolean spawner) {
        String section = spawner ? "spawnerwands" : "sellwands";
        Menu m = new Menu(cfg().title(section, spawner ? "<gold><bold>Spawner Sellwands" : "<gold><bold>Sellwands"));
        m.back = back(View.of("main"));
        m.infoIcon = Menu.Icon.of(spawner ? "SPAWNER" : "BLAZE_ROD");
        Profile profile = null;
        Map<String, ShopConfig.Wand> wands = spawner ? cfg().spawnerWands : cfg().sellwands;
        List<ShopConfig.Wand> list = new ArrayList<>(wands.values());
        for (ShopConfig.Wand wand : paginate(m, view, list, dialog ? 40 : CHEST_PAGE)) {
            String extra = spawner ? msg("menu.spawnerwand", "multiplier", Shop.format(wand.multiplier()))
                    : msg("menu.wand", "multiplier", Shop.format(wand.multiplier()), "uses", wand.uses());
            String label = (spawner ? "<light_purple>" : "<gold>") + wand.display() + " " + extra;
            m.add(new Menu.Button(Menu.Icon.of(spawner ? "SPAWNER" : "BLAZE_ROD"), label,
                    msg("menu.status.price", "price", Shop.format(wand.price())),
                    msg(spawner ? "menu.tooltip.spawnerwand" : "menu.tooltip.sellwand"), List.of(), false, 260, (p, c, in) -> {
                if (spawner) plugin.shop().buySpawnerWand(p, wand.key());
                else plugin.shop().buySellwand(p, wand.key());
                menus.refresh(p);
            }));
        }
        if (list.isEmpty()) m.body(msg("menu.empty"));
        return m;
    }

    // ------------------------------------------------------------------ buy coins

    private Menu buyCoins() {
        Menu m = new Menu(cfg().title("buycoins", "<gold><bold>Buy Coins"));
        m.back = back(View.of("main"));
        m.infoIcon = Menu.Icon.of("GOLD_INGOT");
        for (ShopConfig.BuyTier tier : cfg().buyTiers.values()) {
            m.body(msg("buycoins.tier-line", "price", Text.escape(tier.priceLabel()), "coins", tier.coins()));
        }
        m.add(Menu.Button.of(Menu.Icon.of("GOLD_INGOT"), msg("menu.button.visit-store"), null, msg("menu.tooltip.store"),
                (p, c, in) -> plugin.shop().visitStore(p)));
        return m;
    }

    // ------------------------------------------------------------------ top lists

    private Menu top(Player player, Currency currency) {
        boolean orbs = currency == Currency.ORBS;
        Menu m = new Menu(orbs ? cfg().title("orbstop", "<light_purple><bold>Orbs Top") : cfg().title("topcoins", "<gold><bold>Top Coins"));
        m.back = back(View.of(orbs ? "orbshop" : "main"));
        m.infoIcon = Menu.Icon.of(orbs ? "ENDER_PEARL" : "DIAMOND");
        DataManager data = plugin.data();
        int size = cfg().topSize;
        data.refreshTop(currency, size, () -> menus.refreshIfViewing(player, orbs ? "orbstop" : "topcoins"));
        if (!data.topLoaded(currency)) {
            m.body(msg("menu.top-loading"));
            return m;
        }
        List<Storage.TopEntry> top = data.top(currency);
        if (top.isEmpty()) {
            m.body(msg(orbs ? "menu.top-empty-orbs" : "menu.top-empty"));
            return m;
        }
        boolean shownSelf = false;
        int rank = 1;
        for (Storage.TopEntry e : top) {
            if (rank > size) break;
            boolean self = e.uuid().equals(player.getUniqueId());
            shownSelf |= self;
            String color = switch (rank) {
                case 1 -> "<gold>";
                case 2 -> "<gray>";
                case 3 -> "<yellow>";
                default -> "<white>";
            };
            String name = e.name() == null ? "?" : Text.escape(e.name());
            m.body(msg(orbs ? "menu.top-line-orbs" : "menu.top-line", "rank", color + "#" + rank,
                    "player", (self ? "<aqua>" : "<white>") + name + "</" + (self ? "aqua" : "white") + ">",
                    "amount", Shop.format(Math.floor(e.balance()))));
            rank++;
        }
        if (!shownSelf) {
            Profile p = data.profile(player);
            m.body("<dark_gray>...");
            m.body(msg(orbs ? "menu.top-you-orbs" : "menu.top-you", "amount", Shop.format(Math.floor(p.balance(currency)))));
        }
        return m;
    }

    // ------------------------------------------------------------------ orbshop

    private Menu orbShop(Profile profile, View view, boolean dialog) {
        Menu m = new Menu(cfg().title("orbshop", "<light_purple><bold>Orbshop"));
        m.back = back(View.of("main"));
        m.infoIcon = Menu.Icon.of("ENDER_PEARL");
        m.body(profile.isLoaded() ? msg("menu.orbs-balance", "orbs", Shop.format(Math.floor(profile.balance(Currency.ORBS)))) : msg("menu.loading"));
        List<ShopConfig.OrbItem> items = new ArrayList<>(cfg().orbItems.values());
        for (ShopConfig.OrbItem item : paginate(m, view, items, dialog ? 40 : CHEST_PAGE)) {
            m.add(new Menu.Button(Menu.Icon.of(item.icon()), "<light_purple>" + item.display(),
                    msg("menu.status.price-orbs", "price", Shop.format(item.price())), msg("menu.tooltip.buy"), List.of(), false, 220,
                    (p, c, in) -> {
                        plugin.shop().buyOrbItem(p, item.key());
                        menus.refresh(p);
                    }));
        }
        m.nav.add(Menu.Button.of(Menu.Icon.of("ENDER_EYE"), msg("menu.button.orbs-top"), null, msg("menu.tooltip.orbs-top"),
                (p, c, in) -> menus.open(p, View.of("orbstop"))).width(120));
        return m;
    }

    // ------------------------------------------------------------------ settings

    private Menu settings(Player player, View view, boolean dialog) {
        Menu m = new Menu(cfg().title("settings", "<red><bold>Coinshop Settings"));
        m.back = back(View.of("main"));
        m.infoIcon = Menu.Icon.of("COMPARATOR");
        m.body(msg("menu.settings-intro"));
        if (!player.hasPermission("dreamcoinshop.settings")) {
            m.body(msg("general.no-permission"));
            return m;
        }
        Settings settings = plugin.settings();
        if (dialog) {
            for (Settings.Def d : Settings.TOGGLES) {
                m.inputs.add(new Menu.Input(d.inputKey(), Menu.InputType.BOOL, msg("menu.setting." + d.id()), settings.get(d), 0, 1, 1, List.of()));
            }
            for (Settings.Def d : Settings.VALUES) {
                Menu.InputType type = switch (d.kind()) {
                    case NUMBER -> Menu.InputType.NUMBER;
                    case CHOICE -> Menu.InputType.CHOICE;
                    default -> Menu.InputType.TEXT;
                };
                m.inputs.add(new Menu.Input(d.inputKey(), type, msg("menu.setting." + d.id()), settings.get(d), d.min(), d.max(), d.step(), d.options()));
            }
            m.add(Menu.Button.of(Menu.Icon.of("LIME_DYE"), msg("menu.button.save"), null, null,
                    (p, c, in) -> saveDialogSettings(p, in)).width(150));
            m.add(Menu.Button.of(Menu.Icon.of("GOLD_INGOT"), msg("menu.button.prices"), null, msg("menu.prices-pick"),
                    (p, c, in) -> menus.open(p, View.of("prices"))).width(150));
            return m;
        }
        m.rows = 6;
        // Chest menu: every switch and value is a button (two pages)
        List<Settings.Def> all = new ArrayList<>(Settings.TOGGLES);
        all.addAll(Settings.VALUES);
        for (Settings.Def d : paginate(m, view, all, CHEST_PAGE)) {
            if (d.kind() == Settings.Kind.TOGGLE) {
                boolean on = Boolean.TRUE.equals(settings.get(d));
                String state = msg(on ? "menu.setting-on" : "menu.setting-off");
                m.add(new Menu.Button(Menu.Icon.of(on ? d.icon() : "GRAY_DYE"), "<yellow>" + msg("menu.setting." + d.id()), state,
                        null, List.of(msg("menu.toggle-lore", "state", state)), on, 200, (p, c, in) ->
                        settings.apply(Map.of(d.path(), !on), p, () -> {
                            plugin.sounds().play(p, "settings-saved");
                            menus.refresh(p);
                        })));
                continue;
            }
            Object value = settings.get(d);
            String shown = value instanceof Double dv ? Shop.format(dv) : String.valueOf(value);
            String lore = switch (d.kind()) {
                case NUMBER -> msg("menu.value-lore", "value", shown, "step", Shop.format(d.step()));
                case CHOICE -> msg("menu.choice-lore", "value", shown);
                default -> msg("menu.text-lore", "value", Text.escape(shown));
            };
            m.add(new Menu.Button(Menu.Icon.of(d.icon()), "<aqua>" + msg("menu.setting." + d.id()), "<white>" + Text.escape(shown),
                    null, List.of(lore), false, 200, (p, c, in) -> clickValue(p, d, value, c)));
        }
        m.nav.add(Menu.Button.of(Menu.Icon.of("GOLD_INGOT"), msg("menu.button.prices"), null, msg("menu.prices-pick"),
                (p, c, in) -> menus.open(p, View.of("prices"))).width(150));
        return m;
    }

    private void clickValue(Player p, Settings.Def d, Object value, Menu.Click click) {
        Object next;
        switch (d.kind()) {
            case NUMBER -> {
                double step = d.step() * (click.shift() ? 10 : 1);
                double v = (value instanceof Number n ? n.doubleValue() : 0) + (click.right() ? -step : step);
                next = Settings.store(d, v);
            }
            case CHOICE -> {
                List<String> o = d.options();
                int i = o.indexOf(String.valueOf(value));
                next = o.get(Math.floorMod(i + (click.right() ? -1 : 1), o.size()));
            }
            default -> {
                text().send(p, "settings.only-dialog", null);
                return;
            }
        }
        plugin.settings().apply(Map.of(d.path(), next), p, () -> {
            plugin.sounds().play(p, "settings-saved");
            menus.refresh(p);
        });
    }

    private void saveDialogSettings(Player p, Map<String, Object> in) {
        if (!p.hasPermission("dreamcoinshop.settings")) return;
        Settings settings = plugin.settings();
        Map<String, Object> changes = new LinkedHashMap<>();
        for (Settings.Def d : Settings.TOGGLES) {
            Object v = in.get(d.inputKey());
            if (v instanceof Boolean b && !b.equals(settings.get(d))) changes.put(d.path(), b);
        }
        for (Settings.Def d : Settings.VALUES) {
            Object v = in.get(d.inputKey());
            if (v == null) continue;
            switch (d.kind()) {
                case NUMBER -> {
                    Double parsed = v instanceof Number n ? n.doubleValue() : parse(String.valueOf(v));
                    if (parsed == null) {
                        text().send(p, "settings.bad-number", Ph.of("setting", Text.plain(msg("menu.setting." + d.id()))));
                        continue;
                    }
                    Object stored = Settings.store(d, parsed);
                    Object current = settings.get(d);
                    if (!(current instanceof Number n) || n.doubleValue() != ((Number) stored).doubleValue()) changes.put(d.path(), stored);
                }
                case CHOICE -> {
                    String s = String.valueOf(v).trim().toUpperCase(java.util.Locale.ROOT);
                    if (d.options().contains(s) && !s.equals(String.valueOf(settings.get(d)))) changes.put(d.path(), s);
                }
                case TEXT -> {
                    String s = String.valueOf(v).trim();
                    if (!s.equals(String.valueOf(settings.get(d)))) changes.put(d.path(), s);
                }
                default -> {
                }
            }
        }
        settings.apply(changes, p, () -> {
            text().send(p, "settings.saved", null);
            plugin.sounds().play(p, "settings-saved");
            menus.refresh(p);
        });
    }

    static Double parse(String s) {
        try {
            double d = Double.parseDouble(s.trim().replace(",", ""));
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ prices

    private Menu pricePick() {
        Menu m = new Menu(cfg().title("settings", "<red><bold>Coinshop Settings"));
        m.back = back(View.of("settings"));
        m.infoIcon = Menu.Icon.of("GOLD_INGOT");
        m.body(msg("menu.prices-pick"));
        for (Settings.PriceSection s : Settings.PRICE_SECTIONS) {
            m.add(Menu.Button.of(Menu.Icon.of(s.icon()), msg("menu.button." + s.id()), null, null,
                    (p, c, in) -> menus.open(p, new View("prices", 0, null, s.id()))));
        }
        return m;
    }

    private Menu prices(View view, boolean dialog) {
        Settings.PriceSection section = Settings.priceSection(view.arg());
        if (section == null) return pricePick();
        Settings settings = plugin.settings();
        Menu m = new Menu(cfg().title(section.id(), section.id()));
        m.back = back(View.of("prices"));
        m.infoIcon = Menu.Icon.of(section.icon());
        m.body(msg("menu.prices-intro"));
        Map<String, Double> prices = settings.prices(section);
        if (dialog) {
            for (Map.Entry<String, Double> e : prices.entrySet()) {
                m.inputs.add(new Menu.Input("p_" + inputSafe(e.getKey()), Menu.InputType.TEXT,
                        Text.plain(settings.priceName(section, e.getKey())), Shop.format(e.getValue()), 0, 32, 0, List.of()));
            }
            m.add(Menu.Button.of(Menu.Icon.of("LIME_DYE"), msg("menu.button.save"), null, null, (p, c, in) -> {
                Map<String, Object> changes = new LinkedHashMap<>();
                for (Map.Entry<String, Double> e : prices.entrySet()) {
                    Object raw = in.get("p_" + inputSafe(e.getKey()));
                    if (raw == null) continue;
                    Double v = parse(String.valueOf(raw));
                    if (v == null) {
                        text().send(p, "settings.bad-number", Ph.of("setting", Text.plain(settings.priceName(section, e.getKey()))));
                    } else if (v.doubleValue() != e.getValue()) {
                        changes.put(section.path() + "." + e.getKey() + ".price", Settings.storePrice(v));
                    }
                }
                settings.apply(changes, p, () -> {
                    text().send(p, "settings.saved", null);
                    plugin.sounds().play(p, "settings-saved");
                    menus.refresh(p);
                });
            }).width(150));
            return m;
        }
        List<Map.Entry<String, Double>> list = new ArrayList<>(prices.entrySet());
        for (Map.Entry<String, Double> e : paginate(m, view, list, CHEST_PAGE)) {
            double price = e.getValue();
            double step = price >= 1000 ? 100 : price >= 100 ? 10 : 1;
            m.add(new Menu.Button(Menu.Icon.of(section.icon()), "<white>" + settings.priceName(section, e.getKey()),
                    (section.orbs() ? msg("menu.status.price-orbs", "price", Shop.format(price)) : msg("menu.status.price", "price", Shop.format(price))),
                    null, List.of(msg("menu.price-lore", "price", Shop.format(price), "step", Shop.format(step))), false, 200,
                    (p, c, in) -> {
                        double d = step * (c.shift() ? 10 : 1) * (c.right() ? -1 : 1);
                        settings.apply(Map.of(section.path() + "." + e.getKey() + ".price", Settings.storePrice(price + d)), p, () -> {
                            plugin.sounds().play(p, "settings-saved");
                            menus.refresh(p);
                        });
                    }));
        }
        return m;
    }

    /** Dialog input keys may only use letters, digits and _. */
    private static String inputSafe(String key) {
        return key.replaceAll("[^A-Za-z0-9_]", "_");
    }

    // ------------------------------------------------------------------ paging

    /** The items of the view's page, and the page line and previous/next buttons on the menu. */
    private <T> List<T> paginate(Menu m, View view, List<T> all, int perPage) {
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(view.page(), pages - 1));
        if (pages > 1) {
            m.body(msg("menu.page", "page", page + 1, "pages", pages));
            if (page > 0) {
                m.nav.add(Menu.Button.of(Menu.Icon.of("ARROW"), msg("menu.button.previous"), null, msg("menu.tooltip.page"),
                        (p, c, in) -> menus.open(p, view.page(page - 1))).width(120));
            }
            if (page < pages - 1) {
                m.nav.add(Menu.Button.of(Menu.Icon.of("SPECTRAL_ARROW"), msg("menu.button.next"), null, msg("menu.tooltip.page"),
                        (p, c, in) -> menus.open(p, view.page(page + 1))).width(120));
            }
        }
        return all.subList(page * perPage, Math.min(all.size(), (page + 1) * perPage));
    }
}
