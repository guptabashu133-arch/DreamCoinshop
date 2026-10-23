package org.mydreamduels.dreamcoinshop;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Particle cosmetics: trails, halos, rings, wings... One timer per player wearing one, on that
 * player's own thread (Folia too). Particle names changed between versions (REDSTONE -> DUST,
 * VILLAGER_HAPPY -> HAPPY_VILLAGER ...), so they're looked up by name and an effect whose
 * particles a server doesn't have simply shows nothing.
 */
final class ParticleEffects {

    /** The effects; config.yml particles.*.effect names one of these. */
    enum Style {
        FLAME_TRAIL, SOUL_TRAIL, HEART_HALO, ANGEL_HALO, RAINBOW_RING, SNOW_CLOUD, RAIN_CLOUD, MUSIC_NOTES,
        ENCHANTED, ENDER_AURA, EMERALD_SPARKLE, CHERRY_BLOSSOM, TOTEM_SPIRAL, WITCH_MAGIC, GLOW_AURA, ELECTRIC,
        BUBBLES, LAVA_DRIP, GALAXY, ANGEL_WINGS, DEVIL_WINGS, SCULK_SOULS;

        static Style of(String name) {
            if (name == null) return null;
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final String[] WINGS = {
            "..xx........xx..",
            ".xxxx......xxxx.",
            "xxxxxx....xxxxxx",
            "xxxxxxx..xxxxxxx",
            ".xxxxxx..xxxxxx.",
            "..xxxxx..xxxxx..",
            "...xxxx..xxxx...",
            "....xx....xx....",
    };
    private static final Color[] RAINBOW = {Color.fromRGB(255, 0, 0), Color.fromRGB(255, 128, 0), Color.fromRGB(255, 255, 0),
            Color.fromRGB(0, 255, 0), Color.fromRGB(0, 200, 255), Color.fromRGB(80, 80, 255), Color.fromRGB(200, 0, 255)};

    private final Dreamcoinshop plugin;
    private final Platform platform;
    private final Map<UUID, Platform.Task> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> last = new ConcurrentHashMap<>();
    private final Map<String, Particle> particles = new HashMap<>();
    private final Map<UUID, Integer> ticks = new ConcurrentHashMap<>();

    ParticleEffects(Dreamcoinshop plugin, Platform platform) {
        this.plugin = plugin;
        this.platform = platform;
    }

    // ------------------------------------------------------------------ lifecycle

    /** The effect the player has on, or null. */
    Style style(Profile profile) {
        ShopConfig.Item item = plugin.cosmetics().active(profile, Category.PARTICLE, "particles");
        return item == null ? null : Style.of(item.effect());
    }

    /** Starts or stops the player's effect to match what they have on. Any thread. */
    void refresh(Player player) {
        Profile profile = plugin.data().online(player.getUniqueId());
        Style style = profile == null || !plugin.config().particlesEnabled ? null : style(profile);
        UUID id = player.getUniqueId();
        if (style == null) {
            stop(id);
            return;
        }
        if (tasks.containsKey(id)) return;
        int period = plugin.config().particlesIntervalTicks;
        Platform.Task task = platform.playerTimer(player, () -> tick(player), period, period);
        Platform.Task before = tasks.put(id, task);
        if (before != null) before.cancel();
    }

    void stop(UUID id) {
        Platform.Task t = tasks.remove(id);
        if (t != null) t.cancel();
        last.remove(id);
        ticks.remove(id);
    }

    void stopAll() {
        for (UUID id : tasks.keySet().toArray(new UUID[0])) stop(id);
    }

    /** After a reload: the interval may have changed, so every timer starts again. */
    void restartAll() {
        stopAll();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) refresh(p);
    }

    // ------------------------------------------------------------------ drawing

    /** The player's thread. */
    private void tick(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            stop(id);
            return;
        }
        Profile profile = plugin.data().online(id);
        Style style = profile == null || !plugin.config().particlesEnabled ? null : style(profile);
        if (style == null) {
            stop(id);
            return;
        }
        Location loc = player.getLocation();
        Location before = last.put(id, loc);
        boolean moving = before != null && before.getWorld() == loc.getWorld() && before.distanceSquared(loc) > 0.0025;
        if (hidden(player)) return;
        int t = ticks.merge(id, 1, Integer::sum);
        draw(player, style, loc, t, moving);
    }

    /** Spectators, invisible and vanished players show nothing. */
    private boolean hidden(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) return true;
        if (!plugin.config().particlesWhenInvisible && player.hasPotionEffect(PotionEffectType.INVISIBILITY)) return true;
        for (MetadataValue v : player.getMetadata("vanished")) if (v.asBoolean()) return true;
        return false;
    }

    private void draw(Player p, Style style, Location l, int t, boolean moving) {
        double yaw = Math.toRadians(l.getYaw());
        switch (style) {
            case FLAME_TRAIL -> {
                if (moving) emit(p, l, 0.05, 2, 0.12, 0.02, 0.12, 0.01, "FLAME");
            }
            case SOUL_TRAIL -> {
                if (moving) emit(p, l, 0.05, 2, 0.12, 0.02, 0.12, 0.01, "SOUL_FIRE_FLAME");
                if (t % 4 == 0) emit(p, l, 0.2, 1, 0.2, 0.1, 0.2, 0.01, "SOUL");
            }
            case HEART_HALO -> {
                if (t % 5 == 0) ring(p, l, 2.35, 0.45, 6, t * 0.2, "HEART", null);
            }
            case ANGEL_HALO -> ring(p, l, 2.25, 0.35, 10, t * 0.15, "DUST", Color.fromRGB(255, 240, 140));
            case RAINBOW_RING -> {
                for (int i = 0; i < 8; i++) {
                    double a = t * 0.25 + i * Math.PI / 4;
                    dust(p, l.clone().add(Math.cos(a) * 0.8, 1.0, Math.sin(a) * 0.8), RAINBOW[(i + t / 2) % RAINBOW.length], 1.0f);
                }
            }
            case SNOW_CLOUD -> {
                emit(p, l, 2.7, 3, 0.3, 0.05, 0.3, 0, "CLOUD");
                if (t % 2 == 0) emit(p, l, 2.5, 1, 0.25, 0, 0.25, 0, "SNOWFLAKE", "ITEM_SNOWBALL", "SNOWBALL");
            }
            case RAIN_CLOUD -> {
                emit(p, l, 2.7, 3, 0.3, 0.05, 0.3, 0, "CLOUD");
                emit(p, l, 2.5, 1, 0.25, 0, 0.25, 0, "FALLING_WATER", "DRIPPING_WATER", "DRIP_WATER");
            }
            case MUSIC_NOTES -> {
                if (t % 3 == 0) {
                    double a = t * 0.7;
                    emit(p, l.clone().add(Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6), 2.1, 1, 0, 0, 0, 0, "NOTE");
                }
            }
            case ENCHANTED -> emit(p, l, 1.4, 4, 0.5, 0.6, 0.5, 0.6, "ENCHANT", "ENCHANTMENT_TABLE");
            case ENDER_AURA -> emit(p, l, 0.9, 4, 0.35, 0.6, 0.35, 0.3, "PORTAL");
            case EMERALD_SPARKLE -> {
                if (t % 2 == 0) emit(p, l, 1.0, 2, 0.45, 0.6, 0.45, 0, "HAPPY_VILLAGER", "VILLAGER_HAPPY");
            }
            case CHERRY_BLOSSOM -> {
                if (t % 2 == 0) emit(p, l, 2.4, 1, 0.8, 0.1, 0.8, 0, "CHERRY_LEAVES", "FALLING_SPORE_BLOSSOM");
            }
            case TOTEM_SPIRAL -> helix(p, l, t, 0.7, "TOTEM_OF_UNDYING", "TOTEM");
            case WITCH_MAGIC -> emit(p, l, 1.0, 2, 0.4, 0.5, 0.4, 0, "WITCH", "SPELL_WITCH");
            case GLOW_AURA -> {
                if (t % 2 == 0) emit(p, l, 1.0, 2, 0.45, 0.6, 0.45, 0, "GLOW");
            }
            case ELECTRIC -> emit(p, l, 1.0, 3, 0.45, 0.7, 0.45, 0.05, "ELECTRIC_SPARK");
            case BUBBLES -> emit(p, l, 0.6, 2, 0.4, 0.3, 0.4, 0.02, "BUBBLE_POP");
            case LAVA_DRIP -> {
                if (t % 2 == 0) emit(p, l, 2.0, 1, 0.35, 0.1, 0.35, 0, "DRIPPING_LAVA", "DRIP_LAVA");
                if (t % 6 == 0) emit(p, l, 0.1, 1, 0.2, 0, 0.2, 0, "LAVA");
            }
            case GALAXY -> {
                helix(p, l, t, 0.75, "END_ROD");
                double a = -t * 0.3;
                dust(p, l.clone().add(Math.cos(a) * 0.9, 1.2, Math.sin(a) * 0.9), Color.fromRGB(150, 60, 255), 1.2f);
            }
            case ANGEL_WINGS -> {
                if (t % 2 == 0) wings(p, l, yaw, Color.fromRGB(255, 255, 255), Color.fromRGB(200, 230, 255));
            }
            case DEVIL_WINGS -> {
                if (t % 2 == 0) wings(p, l, yaw, Color.fromRGB(150, 0, 0), Color.fromRGB(30, 0, 0));
            }
            case SCULK_SOULS -> {
                if (t % 3 == 0) emit(p, l, 0.3, 1, 0.4, 0.2, 0.4, 0.02, "SCULK_SOUL", "SOUL");
            }
        }
    }

    /** count particles around loc, height above the feet. The first particle name this server has is used. */
    private void emit(Player p, Location loc, double height, int count, double dx, double dy, double dz, double speed, String... names) {
        Particle particle = particle(names);
        if (particle == null) return;
        spawn(p, particle, loc.clone().add(0, height, 0), count, dx, dy, dz, speed, data(particle, null));
    }

    private void ring(Player p, Location l, double height, double radius, int points, double turn, String name, Color color) {
        for (int i = 0; i < points; i++) {
            double a = turn + i * 2 * Math.PI / points;
            Location at = l.clone().add(Math.cos(a) * radius, height, Math.sin(a) * radius);
            if (color != null) dust(p, at, color, 0.8f);
            else emit(p, at, 0, 1, 0, 0, 0, 0, name);
        }
    }

    /** Two points going round and up the player. */
    private void helix(Player p, Location l, int t, double radius, String... names) {
        Particle particle = particle(names);
        if (particle == null) return;
        double y = (t % 20) / 10.0;
        for (int i = 0; i < 2; i++) {
            double a = t * 0.35 + i * Math.PI;
            spawn(p, particle, l.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius), 1, 0, 0, 0, 0, data(particle, null));
        }
    }

    /** The WINGS picture, behind the back, turned with the player. */
    private void wings(Player p, Location l, double yaw, Color edge, Color fill) {
        double rightX = Math.cos(yaw), rightZ = Math.sin(yaw);
        double backX = Math.sin(yaw) * 0.35, backZ = -Math.cos(yaw) * 0.35;
        double flap = Math.sin(System.currentTimeMillis() / 250.0) * 0.08;
        for (int r = 0; r < WINGS.length; r++) {
            String row = WINGS[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) != 'x') continue;
                double side = (c - (row.length() - 1) / 2.0) * 0.14;
                double depth = Math.abs(side) * flap * 4;
                Location at = l.clone().add(rightX * side + backX + Math.sin(yaw) * depth, 1.85 - r * 0.14,
                        rightZ * side + backZ - Math.cos(yaw) * depth);
                boolean border = c == 0 || c == row.length() - 1 || row.charAt(c - 1) != 'x' || row.charAt(c + 1) != 'x';
                dust(p, at, border ? edge : fill, 0.7f);
            }
        }
    }

    private void dust(Player p, Location at, Color color, float size) {
        Particle dust = particle("DUST", "REDSTONE");
        if (dust == null) return;
        spawn(p, dust, at, 1, 0, 0, 0, 0, data(dust, new Particle.DustOptions(color, size)));
    }

    private static final Object NO_DATA = new Object();

    /** The data a particle needs: none, dust colour, a default number... NO_DATA if it needs something else. */
    private static Object data(Particle particle, Object wanted) {
        Class<?> type = particle.getDataType();
        if (type == Void.class) return null;
        if (wanted != null && type.isInstance(wanted)) return wanted;
        if (type == Float.class) return 1.0f;
        if (type == Integer.class) return 0;
        if (type == Color.class) return Color.WHITE;
        if (type == Particle.DustOptions.class) return new Particle.DustOptions(Color.WHITE, 1f);
        return NO_DATA;
    }

    private void spawn(Player p, Particle particle, Location at, int count, double dx, double dy, double dz, double speed, Object data) {
        if (data == NO_DATA) return;
        try {
            if (plugin.config().particlesVisibleToOthers) {
                World w = at.getWorld();
                if (w != null) w.spawnParticle(particle, at, count, dx, dy, dz, speed, data);
            } else {
                p.spawnParticle(particle, at, count, dx, dy, dz, speed, data);
            }
        } catch (RuntimeException e) {
            // A particle that changed what it needs in some version: skip it rather than spam the log
            plugin.getLogger().fine("Particle " + particle + " failed: " + e.getMessage());
        }
    }

    /** The first of these particle names that exists on this server (cached). */
    private Particle particle(String... names) {
        String key = String.join("|", names);
        synchronized (particles) {
            if (particles.containsKey(key)) return particles.get(key);
            Particle found = null;
            for (String n : names) {
                try {
                    Object v = Particle.class.getField(n).get(null);
                    if (v instanceof Particle pt) {
                        found = pt;
                        break;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            particles.put(key, found);
            return found;
        }
    }
}
