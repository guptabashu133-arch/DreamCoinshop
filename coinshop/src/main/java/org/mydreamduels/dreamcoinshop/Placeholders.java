package org.mydreamduels.dreamcoinshop;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * PlaceholderAPI (optional). Only loaded when PlaceholderAPI is installed.
 *   %dreamcoinshop_coins%  %dreamcoinshop_coins_exact%  %dreamcoinshop_orbs%  %dreamcoinshop_orbs_exact%
 *   %dreamcoinshop_gradient_name%  player name in their gradient (MiniMessage, used by Dreamtab)
 *   %dreamcoinshop_gradient_name_legacy%  the same as colour codes
 *   %dreamcoinshop_tag%  %dreamcoinshop_tag_mini%  equipped tag (colour codes / MiniMessage)
 *   %dreamcoinshop_flag%  flag as text, e.g. [IN]    %dreamcoinshop_flag_code%  IN    %dreamcoinshop_flag_name%  India
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

    @Override
    public String onRequest(OfflinePlayer player, String params) {
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
                return player instanceof Player online && profile != null ? cos.nameMini(online, profile) : String.valueOf(player.getName());
            case "gradient_name_legacy":
                return player instanceof Player online && profile != null
                        ? plugin.text().legacy(cos.nameMini(online, profile), null) : String.valueOf(player.getName());
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
            default:
                return null;
        }
    }
}
