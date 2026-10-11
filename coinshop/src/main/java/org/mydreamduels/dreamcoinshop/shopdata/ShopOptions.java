package org.mydreamduels.dreamcoinshop.shopdata;

import java.util.List;

public final class ShopOptions {
    private ShopOptions() {
    }

    public record OrbShopItem(String key, String display, double price, List<String> commands) {
    }

    public record BuyCoinsTier(String key, String priceLabel, int coins, String storeUrl) {
    }

    public record SpawnerWandTier(String key, String display, double multiplier, int uses, double price) {
    }

    public record SellwandTier(String key, String display, double multiplier, int uses, double price) {
    }

    public record GradientOption(String key, String display, String from, String to, double price) {
    }

    /** eventOnly tags are never sold in /coinshop - they're only handed out by an event (see HeadHunt). */
    public record TagOption(String key, String display, String miniMessage, double price, boolean eventOnly) {
    }

    /**
     * A "find the hidden heads" event. Admins get numbered heads (1..heads) via /coinshop events,
     * place them around the map, and a player who clicks every one of them unlocks rewardTag.
     */
    public record HeadHunt(String key, boolean enabled, int heads, String rewardTag, String headName, String texture) {
    }

    public record PerkOption(String key, String display, String permission, double price) {
    }

    public record GlowOption(String key, String display, String colorHex, boolean rainbow, double price) {
    }

    public record ChatColorOption(String key, String display, String colorHex, List<String> gradient, double price) {
        public boolean isGradient() {
            return this.gradient != null && this.gradient.size() >= 2;
        }
    }
}
