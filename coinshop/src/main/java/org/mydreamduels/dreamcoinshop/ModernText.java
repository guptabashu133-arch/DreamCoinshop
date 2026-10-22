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

    /** A head with this skin inline in text, e.g. a country flag. texture = base64 "textures" value. */
    static Component head(String id, String texture) {
        UUID uuid = UUID.nameUUIDFromBytes(("dreamcoinshop:" + id).getBytes(StandardCharsets.UTF_8));
        return Component.object(ObjectContents.playerHead()
                .id(uuid)
                .profileProperty(PlayerHeadObjectContents.property("textures", texture))
                .build());
    }
}
