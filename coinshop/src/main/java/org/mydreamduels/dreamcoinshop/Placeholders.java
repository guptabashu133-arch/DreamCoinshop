package org.mydreamduels.dreamcoinshop;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI (optional). Only loaded when PlaceholderAPI is installed.
 *   %dreamcoinshop_coins%  %dreamcoinshop_coins_exact%  %dreamcoinshop_orbs%  %dreamcoinshop_orbs_exact%
 *   %dreamcoinshop_gradient_name%  player name in their gradient (MiniMessage, used by Dreamtab)
 *   %dreamcoinshop_gradient_name_legacy%  the same as colour codes
 *   %dreamcoinshop_tag%  %dreamcoinshop_tag_mini%  equipped tag (colour codes / MiniMessage)
 *   %dreamcoinshop_flag%  flag as text, e.g. [IN]    %dreamcoinshop_flag_code%  IN    %dreamcoinshop_flag_name%  India
 *   %dreamcoinshop_coins_formatted%  1,234    %dreamcoinshop_orbs_formatted%
 *   %dreamcoinshop_particle%  equipped particle's name
 *   Leaderboards (holograms): %dreamcoinshop_top_coins_<n>_<what>%, %dreamcoinshop_top_orbs_<n>_<what>%
 *     what = name, amount, amount_formatted, flag, flag_code, flag_name, uuid  (n = 1 ... top.full-size)
 *   %dreamcoinshop_rank_coins%  %dreamcoinshop_rank_orbs%  the player's place (or top.placeholder-none)
 *   %dreamcoinshop_sale_percent%  %dreamcoinshop_sale_time%  %dreamcoinshop_sale_active% (true/false)
 *   %dreamcoinshop_coins_name%  %dreamcoinshop_orbs_name%  the currency names
 */
final class Placeholders extends PlaceholderExpansion {

    private final Dreamcoinshop plugin;

    Placeholders(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "dreamcoinshop";
    }

    @Override
    public String getAuthor() {
        return "BasuGamer56";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private static final java.util.regex.Pattern TOP = java.util.regex.Pattern.compile("top_(coins|orbs)_(\\d{1,3})_([a-z_]+)");

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String lower = params.toLowerCase(Locale.ROOT);
        String global = global(lower);
        if (global != null) return global;
        if (player == null) return "";
        Profile profile = plugin.data().online(player.getUniqueId());
        Cosmetics cos = plugin.cosmetics();
        switch (params.toLowerCase(Locale.ROOT)) {
            case "coins":
                return profile == null ? "0" : String.valueOf((long) profile.balance(Currency.COINS));
            case "coins_exact":
                return profile == null ? "0" : String.valueOf(profile.balance(Currency.COINS));
            case "orbs":
                return profile == null ? "0" : String.valueOf((long) profile.balance(Currency.ORBS));
            case "orbs_exact":
                return profile == null ? "0" : String.valueOf(profile.balance(Currency.ORBS));
            case "gradient_name":
                return player instanceof Player online && profile != null ? cos.nameMini(online, profile) : player.getName() == null ? "" : player.getName();
            case "gradient_name_legacy":
                return player instanceof Player online && profile != null
                        ? plugin.text().legacy(cos.nameMini(online, profile), null) : player.getName() == null ? "" : player.getName();
            case "tag": {
                String tag = cos.tagMini(profile);
                return tag == null ? "" : plugin.text().legacy(tag, null);
            }
            case "tag_mini": {
                String tag = cos.tagMini(profile);
                return tag == null ? "" : tag;
            }
            case "flag": {
                Flags.Country c = cos.flag(profile);
                return c == null ? "" : plugin.text().legacy(cos.flagTextMini(c), null);
            }
            case "flag_code": {
                Flags.Country c = cos.flag(profile);
                return c == null ? "" : c.code();
            }
            case "flag_name": {
                Flags.Country c = cos.flag(profile);
                return c == null ? "" : c.name();
            }
            case "coins_formatted":
                return profile == null ? "0" : grouped(profile.balance(Currency.COINS));
            case "orbs_formatted":
                return profile == null ? "0" : grouped(profile.balance(Currency.ORBS));
            case "particle": {
                ShopConfig.Item item = cos.active(profile, Category.PARTICLE, "particles");
                return item == null ? "" : Text.plain(item.display());
            }
            case "rank_coins":
                return rank(player, Currency.COINS);
            case "rank_orbs":
                return rank(player, Currency.ORBS);
            default:
                return null;
        }
    }

    /** Placeholders that don't need a player (leaderboards, sale, names). Null if it isn't one. */
    private String global(String params) {
        ShopConfig cfg = plugin.config();
        switch (params) {
            case "coins_name":
                return cfg.coinsName;
            case "orbs_name":
                return cfg.orbsName;
            case "sale_active":
                return String.valueOf(plugin.sales().current().active(System.currentTimeMillis()));
            case "sale_percent": {
                Sales.Sale s = plugin.sales().current();
                return s.active(System.currentTimeMillis()) ? String.valueOf(s.percent()) : "0";
            }
            case "sale_time": {
                Sales.Sale s = plugin.sales().current();
                long now = System.currentTimeMillis();
                return s.active(now) ? Sales.time(s.endsAt() - now) : "";
            }
            default:
                break;
        }
        java.util.regex.Matcher m = TOP.matcher(params);
        if (!m.matches()) return null;
        Currency currency = m.group(1).equals("coins") ? Currency.COINS : Currency.ORBS;
        int n = Integer.parseInt(m.group(2));
        List<Storage.TopEntry> top = top(currency);
        Storage.TopEntry e = n >= 1 && n <= top.size() ? top.get(n - 1) : null;
        String what = m.group(3);
        if (e == null) return what.startsWith("amount") ? "0" : what.equals("name") ? cfg.topPlaceholderNone : "";
        Flags.Country flag = e.flag() == null || !cfg.section("flags") ? null : plugin.flags().get(e.flag());
        return switch (what) {
            case "name" -> e.name() == null ? cfg.topPlaceholderNone : e.name();
            case "amount" -> String.valueOf((long) Math.floor(e.balance()));
            case "amount_formatted" -> grouped(e.balance());
            case "amount_exact" -> String.valueOf(e.balance());
            case "flag" -> flag == null ? "" : plugin.text().legacy(plugin.cosmetics().flagTextMini(flag), null);
            case "flag_code" -> flag == null ? "" : flag.code();
            case "flag_name" -> flag == null ? "" : flag.name();
            case "uuid" -> e.uuid().toString();
            default -> null;
        };
    }

    /** The cached leaderboard; asks for a fresh one when it's old (never waits for it). */
    private List<Storage.TopEntry> top(Currency c) {
        plugin.data().refreshTop(c, plugin.config().topFullSize, null);
        return plugin.data().top(c);
    }

    private String rank(OfflinePlayer player, Currency c) {
        List<Storage.TopEntry> top = top(c);
        for (int i = 0; i < top.size(); i++) if (top.get(i).uuid().equals(player.getUniqueId())) return String.valueOf(i + 1);
        return plugin.config().topPlaceholderNone;
    }

    private static String grouped(double v) {
        return String.format(Locale.US, "%,d", (long) Math.floor(v));
    }
}
