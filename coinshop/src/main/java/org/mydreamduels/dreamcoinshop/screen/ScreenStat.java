package org.mydreamduels.dreamcoinshop.screen;

import java.util.Locale;

/** What a screen shows: the shop's sections, or a top list. */
public enum ScreenStat {
    SHOP, COINS, ORBS;

    public static ScreenStat parse(String s) {
        if (s == null) return null;
        String n = s.trim().toUpperCase(Locale.ROOT);
        switch (n) {
            case "COINSHOP", "MENU", "CATEGORIES", "STORE", "SHOPS" -> n = "SHOP";
            case "COIN", "TOPCOINS", "TOP", "RICH", "BALTOP" -> n = "COINS";
            case "ORB", "SHARDS", "SHARD", "TOPORBS", "TOPSHARDS" -> n = "ORBS";
            default -> {
            }
        }
        try {
            return valueOf(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
