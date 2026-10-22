package org.mydreamduels.dreamcoinshop;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/** Folia: region-aware schedulers. Only loaded on Folia, so Spigot never sees these Paper classes. */
final class FoliaPlatform implements Platform {

    private final Plugin plugin;

    FoliaPlatform(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Task timer(Runnable task, long delayTicks, long periodTicks) {
        ScheduledTask t = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, st -> task.run(),
                Math.max(1, delayTicks), Math.max(1, periodTicks));
        return t::cancel;
    }

    @Override
    public Task asyncTimer(Runnable task, long delayMs, long periodMs) {
        ScheduledTask t = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, st -> task.run(),
                Math.max(1, delayMs), Math.max(1, periodMs), TimeUnit.MILLISECONDS);
        return t::cancel;
    }

    @Override
    public void global(Runnable task) {
        // Always queued: Bukkit.isGlobalTickThread() only exists on newer Folia
        if (plugin.isEnabled()) Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void globalLater(long delayTicks, Runnable task) {
        if (plugin.isEnabled()) Bukkit.getGlobalRegionScheduler().runDelayed(plugin, st -> task.run(), Math.max(1, delayTicks));
    }

    @Override
    public void forPlayer(Player player, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
        } else if (plugin.isEnabled()) {
            player.getScheduler().run(plugin, st -> task.run(), null);
        }
    }

    @Override
    public void forPlayerLater(Player player, long delayTicks, Runnable task) {
        if (plugin.isEnabled()) player.getScheduler().runDelayed(plugin, st -> task.run(), null, Math.max(1, delayTicks));
    }

    @Override
    public Task playerTimer(Player player, Runnable task, long delayTicks, long periodTicks) {
        ScheduledTask t = player.getScheduler().runAtFixedRate(plugin, st -> task.run(), null,
                Math.max(1, delayTicks), Math.max(1, periodTicks));
        return t == null ? () -> { } : t::cancel;
    }

    @Override
    public boolean isOwnedByCurrentThread(Player player) {
        return Bukkit.isOwnedByCurrentRegion(player);
    }
}
