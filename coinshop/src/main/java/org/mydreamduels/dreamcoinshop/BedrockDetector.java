package org.mydreamduels.dreamcoinshop;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tells Bedrock (Geyser) players apart. Floodgate / Geyser are used through reflection, so the
 * plugin loads without them. The answer is cached per player.
 */
final class BedrockDetector implements Listener {

    private final Logger log;
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    private boolean hooked;
    private Object floodgateApi;
    private Method floodgateCheck;
    private Object geyserApi;
    private Method geyserCheck;

    BedrockDetector(Logger log) {
        this.log = log;
    }

    private synchronized void hook() {
        if (hooked) return;
        hooked = true;
        try {
            Class<?> c = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateApi = c.getMethod("getInstance").invoke(null);
            floodgateCheck = c.getMethod("isFloodgatePlayer", UUID.class);
            log.info("Hooked into Floodgate: Bedrock players get the chest menus.");
        } catch (Throwable ignored) {
            floodgateApi = null;
        }
        if (floodgateApi == null) {
            try {
                Class<?> c = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApi = c.getMethod("api").invoke(null);
                geyserCheck = c.getMethod("isBedrockPlayer", UUID.class);
                log.info("Hooked into Geyser: Bedrock players get the chest menus.");
            } catch (Throwable ignored) {
                geyserApi = null;
            }
        }
    }

    boolean isBedrock(Player player) {
        return player != null && cache.computeIfAbsent(player.getUniqueId(), this::detect);
    }

    private boolean detect(UUID uuid) {
        hook();
        try {
            if (floodgateApi != null) return (Boolean) floodgateCheck.invoke(floodgateApi, uuid);
            if (geyserApi != null) return (Boolean) geyserCheck.invoke(geyserApi, uuid);
        } catch (Throwable ignored) {
        }
        // Floodgate gives unlinked Bedrock players a UUID that starts with 8 zero bytes
        return uuid.getMostSignificantBits() == 0L;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
