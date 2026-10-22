package org.mydreamduels.dreamcoinshop;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.StyleBuilderApplicable;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Text features only newer Paper has: coloured shadows (1.21.4+), sprite icons and player-head
 * icons (1.21.9+). Kept in their own class and only called when Capabilities says they exist, so
 * older servers never load these classes.
 */
final class ModernText {

    private ModernText() {
    }

    static StyleBuilderApplicable shadow(TextColor color) {
        return ShadowColor.shadowColor(color, 0xFF);
    }

    static Component sprite(Key atlas, Key sprite) {
        return Component.object(ObjectContents.sprite(atlas, sprite));
    }

    /**
     * A player's own head inline in text. Online players' skins are sent with it (works on
     * offline-mode servers and with skin plugins too); others are looked up by the client.
     */
    static Component playerHead(UUID id, String name, org.bukkit.entity.Player online) {
        PlayerHeadObjectContents.Builder b = ObjectContents.playerHead();
        if (online == null) {
            // The client looks the skin up: by account id on online-mode servers; offline-mode
            // ids aren't accounts, so there the name is used (the skin of that premium name)
            if (org.bukkit.Bukkit.getOnlineMode() || name == null || name.isEmpty()) b.id(id);
            else b.name(name);
            return Component.object(b.build());
        }
        b.id(id);
        if (name != null && !name.isEmpty()) b.name(name);
        for (com.destroystokyo.paper.profile.ProfileProperty p : online.getPlayerProfile().getProperties()) {
            if (p.getName().equals("textures")) b.profileProperty(PlayerHeadObjectContents.property("textures", p.getValue(), p.getSignature()));
        }
        return Component.object(b.build());
    }

    /** A head with this skin inline in text, e.g. a country flag. texture = base64 "textures" value. */
    static Component head(String id, String texture) {
        UUID uuid = UUID.nameUUIDFromBytes(("dreamcoinshop:" + id).getBytes(StandardCharsets.UTF_8));
        return Component.object(ObjectContents.playerHead()
                .id(uuid)
                .profileProperty(PlayerHeadObjectContents.property("textures", texture))
                .build());
    }
}
