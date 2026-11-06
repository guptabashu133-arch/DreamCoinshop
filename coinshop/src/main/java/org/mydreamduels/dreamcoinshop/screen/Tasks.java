package org.mydreamduels.dreamcoinshop.screen;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/** The few kinds of scheduling the screens need, on Spigot / Paper (main thread) and Folia (regions). */
final class Tasks {

    interface Task {
        void cancel();
    }

    private static Plugin plugin;
    private static final boolean FOLIA = has("io.papermc.paper.threadedregions.RegionizedServer");

    private Tasks() {
    }

    static void init(Plugin p) {
        plugin = p;
    }

    private static boolean has(String c) {
        try {
            Class.forName(c);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean on() {
        return plugin != null && plugin.isEnabled();
    }

    static void global(Runnable r) {
        if (!on()) return;
        if (FOLIA) Bukkit.getGlobalRegionScheduler().execute(plugin, r);
        else if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    static Task globalTimer(Runnable r, long delay, long period) {
        if (!on()) return () -> { };
        if (FOLIA) {
            var t = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, st -> r.run(), Math.max(1, delay), Math.max(1, period));
            return t::cancel;
        }
        var t = Bukkit.getScheduler().runTaskTimer(plugin, r, delay, period);
        return t::cancel;
    }

    static void entity(Entity e, Runnable r) {
        if (!on()) return;
        if (FOLIA) e.getScheduler().run(plugin, st -> r.run(), null);
        else if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    static void entityLater(Entity e, Runnable r, long delay) {
        if (!on()) return;
        if (FOLIA) e.getScheduler().runDelayed(plugin, st -> r.run(), null, Math.max(1, delay));
        else Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(1, delay));
    }

    static void region(Location l, Runnable r) {
        if (!on()) return;
        if (FOLIA) Bukkit.getRegionScheduler().execute(plugin, l, r);
        else if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    static void async(Runnable r) {
        if (!on()) return;
        if (FOLIA) Bukkit.getAsyncScheduler().runNow(plugin, st -> r.run());
        else Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    @SuppressWarnings("unused")
    private static final TimeUnit MS = TimeUnit.MILLISECONDS;
}
