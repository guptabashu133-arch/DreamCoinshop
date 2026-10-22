package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Spigot and Paper: one main thread, the classic Bukkit scheduler. */
final class BukkitPlatform implements Platform {

    private final Plugin plugin;

    BukkitPlatform(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Task timer(Runnable task, long delayTicks, long periodTicks) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
        return t::cancel;
    }

    @Override
    public Task asyncTimer(Runnable task, long delayMs, long periodMs) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, ticks(delayMs), ticks(periodMs));
        return t::cancel;
    }

    @Override
    public void global(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void globalLater(long delayTicks, Runnable task) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks));
    }

    @Override
    public void forPlayer(Player player, Runnable task) {
        global(task);
    }

    @Override
    public void forPlayerLater(Player player, long delayTicks, Runnable task) {
        globalLater(delayTicks, task);
    }

    @Override
    public Task playerTimer(Player player, Runnable task, long delayTicks, long periodTicks) {
        return timer(task, delayTicks, periodTicks);
    }

    @Override
    public boolean isOwnedByCurrentThread(Player player) {
        return Bukkit.isPrimaryThread();
    }

    private static long ticks(long ms) {
        return Math.max(1, ms / 50);
    }
}
