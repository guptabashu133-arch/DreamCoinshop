package org.mydreamduels.dreamcoinshop;

import org.bukkit.entity.Player;
import org.mydreamduels.dreamcoinshop.screen.ScreenHost;

import java.util.ArrayList;
import java.util.List;

/** What the wall screens (screen package) read from the shop and do in it. */
final class ScreenLink implements ScreenHost.Bridge {

    private final Dreamcoinshop plugin;

    ScreenLink(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    /** "<gold><bold>Chat Colours" -> "Chat Colours". */
    private static String plain(String mini) {
        return mini == null ? "" : mini.replaceAll("<[^>]*>", "").replaceAll("[§&][0-9a-fk-orx]", "").trim();
    }

    private String text(String path, String fallback) {
        String v = plugin.text().raw(path);
        return v == null || v.isBlank() ? fallback : v;
    }

    @Override
    public List<ScreenHost.Section> sections() {
        ShopConfig cfg = plugin.config();
        List<ScreenHost.Section> out = new ArrayList<>();
        for (String section : ShopConfig.SECTIONS) {
            if (!cfg.section(section)) continue;
            String name = plain(text("menu.button." + section, section));
            String custom = plugin.getConfig().getString("screens.values." + section, "");
            String value = custom != null && !custom.isBlank() ? custom : value(cfg, section);
            out.add(new ScreenHost.Section(section, name.isEmpty() ? section : name, value, null));
        }
        return out;
    }

    /** "24 items", "Top 10"... shown on the right of a section's row. */
    private String value(ShopConfig cfg, String section) {
        int n = switch (section) {
            case "chatcolors" -> cfg.items(Category.CHATCOLOR).size();
            case "glows" -> cfg.items(Category.GLOW).size();
            case "perks" -> cfg.items(Category.PERK).size();
            case "tags" -> cfg.items(Category.TAG).size();
            case "gradients" -> cfg.items(Category.GRADIENT).size();
            case "particles" -> cfg.items(Category.PARTICLE).size();
            case "flags" -> plugin.flags().forSale(cfg).size();
            case "sellwands" -> cfg.sellwands.size();
            case "spawnerwands" -> cfg.spawnerWands.size();
            default -> -1;
        };
        if (n >= 0) return plugin.getConfig().getString("screens.items-format", "%d items").replace("%d", String.valueOf(n));
        return switch (section) {
            case "buycoins" -> plugin.getConfig().getString("screens.value-buy", "Buy");
            case "topcoins" -> plugin.getConfig().getString("screens.value-top", "Top 10");
            case "orbshop" -> cfg.orbsName;
            default -> "Open";
        };
    }

    @Override
    public List<ScreenHost.Top> top(boolean orbs, int limit) {
        Currency c = orbs ? Currency.ORBS : Currency.COINS;
        plugin.data().refreshTop(c, Math.max(limit, 10), null); // keeps the cached list fresh
        List<ScreenHost.Top> out = new ArrayList<>();
        for (Storage.TopEntry e : plugin.data().top(c)) {
            if (out.size() >= limit) break;
            out.add(new ScreenHost.Top(e.uuid(), e.name(), Shop.format(Math.floor(e.balance())), e.flag()));
        }
        return out;
    }

    @Override
    public void open(Player player, String section) {
        plugin.menus().open(player, Pages.View.of(section));
    }

    @Override
    public void sound(Player player, String key) {
        plugin.sounds().play(player, key);
    }

    @Override
    public String currency(boolean orbs) {
        return orbs ? plugin.config().orbsName : plugin.config().coinsName;
    }
}
