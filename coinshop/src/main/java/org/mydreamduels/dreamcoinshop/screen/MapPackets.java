package org.mydreamduels.dreamcoinshop.screen;

import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

/**
 * Sends only the part of a map that changed (a cursor move is ~10x12 pixels, not the whole
 * 128x128 map that Player#sendMap always sends). Uses the server's own map packet, found by name
 * on Paper / Folia / Purpur 1.20.5+ (Mojang names); anywhere else {@link #available()} is false and
 * the screens fall back to Player#sendMap. Any problem switches it off for good - never a crash.
 */
final class MapPackets {

    private static volatile boolean broken;
    private static boolean looked;
    private static Method getHandle;
    private static Field connection;
    private static Method send;
    private static Constructor<?> mapId, patch, packet;
    private static boolean optionalPacket;

    private MapPackets() {
    }

    static boolean available() {
        if (broken) return false;
        if (!looked) look();
        return !broken;
    }

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        try {
            Class<?> idClass = Class.forName("net.minecraft.world.level.saveddata.maps.MapId");
            Class<?> patchClass = Class.forName("net.minecraft.world.level.saveddata.maps.MapItemSavedData$MapPatch");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundMapItemDataPacket");
            mapId = idClass.getDeclaredConstructor(int.class);
            patch = patchClass.getDeclaredConstructor(int.class, int.class, int.class, int.class, byte[].class);
            for (Constructor<?> c : packetClass.getDeclaredConstructors()) {
                Class<?>[] t = c.getParameterTypes();
                if (t.length != 5 || t[0] != idClass || t[1] != byte.class || t[2] != boolean.class) continue;
                if (Collection.class.isAssignableFrom(t[3]) && t[4] == patchClass) {
                    packet = c;
                    optionalPacket = false;
                    break;
                }
                if (t[3] == Optional.class && t[4] == Optional.class) {
                    packet = c;
                    optionalPacket = true;
                }
            }
            if (packet == null) throw new IllegalStateException("no map packet constructor");
            packet.setAccessible(true);
            mapId.setAccessible(true);
            patch.setAccessible(true);
        } catch (Throwable t) {
            broken = true;
        }
    }

    /** The player's connection + its send method, looked up once. */
    private static Object connection(Player p) throws Exception {
        if (getHandle == null) getHandle = p.getClass().getMethod("getHandle");
        Object handle = getHandle.invoke(p);
        if (connection == null) {
            Class<?> c = handle.getClass();
            while (c != null && connection == null) {
                try {
                    connection = c.getDeclaredField("connection");
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
            if (connection == null) throw new IllegalStateException("no connection field");
            connection.setAccessible(true);
        }
        Object conn = connection.get(handle);
        if (conn != null && send == null) {
            Class<?> packetIface = Class.forName("net.minecraft.network.protocol.Packet");
            for (Class<?> c = conn.getClass(); c != null && send == null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("send") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == packetIface) {
                        m.setAccessible(true);
                        send = m;
                        break;
                    }
                }
            }
            if (send == null) throw new IllegalStateException("no send method");
        }
        return conn;
    }

    /**
     * Sends the pixels that differ between before (what the player has; null = nothing yet) and
     * now. Returns false when it couldn't (then the caller sends the whole map the normal way).
     */
    static boolean sendDiff(Player p, int id, byte[] before, byte[] now) {
        if (!available()) return false;
        int minX = 128, minY = 128, maxX = -1, maxY = -1;
        if (before == null) {
            minX = 0;
            minY = 0;
            maxX = 127;
            maxY = 127;
        } else {
            for (int y = 0; y < 128; y++) {
                int row = y * 128;
                for (int x = 0; x < 128; x++) {
                    if (before[row + x] != now[row + x]) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        maxY = y;
                    }
                }
            }
            if (maxX < 0) return true; // nothing changed
        }
        int w = maxX - minX + 1, h = maxY - minY + 1;
        byte[] colors = new byte[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(now, (minY + y) * 128 + minX, colors, y * w, w);
        try {
            Object conn = connection(p);
            if (conn == null) return false;
            Object mid = mapId.newInstance(id);
            Object pt = patch.newInstance(minX, minY, w, h, colors);
            Object pkt = optionalPacket
                    ? packet.newInstance(mid, (byte) 0, true, Optional.empty(), Optional.of(pt))
                    : packet.newInstance(mid, (byte) 0, true, null, pt);
            send.invoke(conn, pkt);
            return true;
        } catch (Throwable t) {
            broken = true;
            return false;
        }
    }
}
