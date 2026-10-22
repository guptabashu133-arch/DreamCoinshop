package org.mydreamduels.dreamcoinshop;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * The player's real client version through ViaVersion, when it's installed. Players joining an
 * up-to-date server with an older client can't see dialogs (1.21.6+) or sprite icons (1.21.9+).
 */
final class ClientInfo {

    private static final int PROTOCOL_1_21_6 = 771;
    private static final int PROTOCOL_1_21_9 = 773;

    private static boolean hooked;
    private static Object api;
    private static Method intVersion;
    private static Method protocolVersion;
    private static Method protocolNumber;

    private ClientInfo() {
    }

    static boolean supportsDialogs(Player player) {
        int v = protocol(player);
        return v < 0 || v >= PROTOCOL_1_21_6;
    }

    static boolean supportsSprites(Player player) {
        int v = protocol(player);
        return v < 0 || v >= PROTOCOL_1_21_9;
    }

    /** Client protocol number, or -1 when unknown (no ViaVersion = same as the server). */
    static synchronized int protocol(Player player) {
        hook();
        if (api == null) return -1;
        try {
            if (intVersion != null) return (Integer) intVersion.invoke(api, player.getUniqueId());
            Object pv = protocolVersion.invoke(api, player.getUniqueId());
            return (Integer) protocolNumber.invoke(pv);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void hook() {
        if (hooked) return;
        hooked = true;
        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            api = via.getMethod("getAPI").invoke(null);
            // Look methods up on the public interface: the implementation class may not be public.
            Class<?> apiType = Class.forName("com.viaversion.viaversion.api.ViaAPI");
            try {
                protocolVersion = apiType.getMethod("getPlayerProtocolVersion", UUID.class);
                protocolNumber = protocolVersion.getReturnType().getMethod("getVersion");
            } catch (NoSuchMethodException e) {
                intVersion = apiType.getMethod("getPlayerVersion", UUID.class);
            }
        } catch (Throwable t) {
            api = null;
        }
    }
}
