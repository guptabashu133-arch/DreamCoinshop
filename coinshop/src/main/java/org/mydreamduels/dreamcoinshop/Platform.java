package org.mydreamduels.dreamcoinshop;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Scheduling that works on every server type. Spigot and Paper have one main thread; Folia has a
 * thread per region, so player work must run on the thread that owns that player.
 */
interface Platform {

    interface Task {
        void cancel();
    }

    /** Repeating task on the main thread (Folia: the global region). */
    Task timer(Runnable task, long delayTicks, long periodTicks);

    /** Repeating task off the main thread. */
    Task asyncTimer(Runnable task, long delayMs, long periodMs);

    /** Main thread (Folia: global region). Spigot/Paper: runs right away when already there. */
    void global(Runnable task);

    /** Main thread (Folia: global region), after at least the given number of ticks. */
    void globalLater(long delayTicks, Runnable task);

    /** On the thread that owns this player (main thread outside Folia). Runs right away when already there. */
    void forPlayer(Player player, Runnable task);

    /** On the player's thread, after at least the given number of ticks (at least 1). */
    void forPlayerLater(Player player, long delayTicks, Runnable task);

    Task playerTimer(Player player, Runnable task, long delayTicks, long periodTicks);

    /** True when the current thread may touch this player. */
    boolean isOwnedByCurrentThread(Player player);

    static Platform create(Plugin plugin) {
        return Capabilities.FOLIA ? new FoliaPlatform(plugin) : new BukkitPlatform(plugin);
    }
}
