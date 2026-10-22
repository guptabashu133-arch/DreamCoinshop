package org.mydreamduels.dreamcoinshop;

import java.util.Locale;

/** The kinds of things a player can unlock. {@link #id} is what the database stores. */
enum Category {
    CHATCOLOR("chatcolor", true),
    GLOW("glow", true),
    PERK("perk", false),
    TAG("tag", true),
    GRADIENT("gradient", true),
    FLAG("flag", true);

    final String id;
    /** Equippable: one of them can be "active" at a time. Perks are just owned. */
    final boolean equippable;

    Category(String id, boolean equippable) {
        this.id = id;
        this.equippable = equippable;
    }

    static Category byId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase(Locale.ROOT);
        for (Category c : values()) if (c.id.equals(lower)) return c;
        return null;
    }
}
