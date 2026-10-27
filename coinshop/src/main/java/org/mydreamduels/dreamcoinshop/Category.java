package org.mydreamduels.dreamcoinshop;

import java.util.Locale;

/** The kinds of things a player can unlock. {@link #id} is what the database stores. */
enum Category {
    CHATCOLOR("chatcolor", true, "chatcolors"),
    GLOW("glow", true, "glows"),
    PERK("perk", false, "perks"),
    TAG("tag", true, "tags"),
    GRADIENT("gradient", true, "gradients"),
    FLAG("flag", true, "flags"),
    PARTICLE("particle", true, "particles"),
    /** Not sold: the nickname set with the /nick perk (kept like an equipped item, so it syncs). */
    NICK("nick", true, "perks");

    final String id;
    /** Equippable: one of them can be "active" at a time. Perks are just owned. */
    final boolean equippable;
    /** The shop section it's sold in (categories.*, menu ids, sale sections). */
    final String section;

    Category(String id, boolean equippable, String section) {
        this.id = id;
        this.equippable = equippable;
        this.section = section;
    }

    static Category byId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase(Locale.ROOT);
        for (Category c : values()) if (c.id.equals(lower)) return c;
        return null;
    }
}
