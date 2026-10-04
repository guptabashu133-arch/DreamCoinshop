package org.mydreamduels.dreamcoinshop.orbzone;

import org.bukkit.Location;

/** A cuboid area (block coordinates, inclusive) where players earn Orbs while standing in it. */
public record OrbZone(String name, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static OrbZone of(String name, Location a, Location b) {
        return new OrbZone(name, a.getWorld().getName(),
                Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(this.world)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }
}
