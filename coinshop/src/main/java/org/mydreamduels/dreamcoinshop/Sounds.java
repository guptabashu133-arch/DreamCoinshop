package org.mydreamduels.dreamcoinshop;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * sounds.yml. Names are resolved once per reload. Sound is an enum up to 1.21.2 and an interface
 * after, so names are looked up as fields (which works for both) instead of with Sound.valueOf.
 */
final class Sounds {

    private record Entry(Sound sound, float volume, float pitch) {
    }

    private final Dreamcoinshop plugin;
    private final Platform platform;
    private final Map<String, Entry> entries = new HashMap<>();
    private YamlConfiguration yaml;
    private volatile boolean enabled = true;

    Sounds(Dreamcoinshop plugin, Platform platform) {
        this.plugin = plugin;
        this.platform = platform;
    }

    void reload() {
        yaml = MessageFile.load(plugin, "sounds.yml");
        enabled = yaml.getBoolean("enabled", true);
        Map<String, Entry> loaded = new HashMap<>();
        ConfigurationSection sec = yaml.getConfigurationSection("sounds");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(key);
                if (s == null) continue;
                String name = s.getString("sound", "");
                if (name == null || name.isBlank() || name.equalsIgnoreCase("NONE")) continue;
                try {
                    Sound sound = (Sound) Sound.class.getField(name.trim().toUpperCase(Locale.ROOT)).get(null);
                    loaded.put(key, new Entry(sound, (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0)));
                } catch (ReflectiveOperationException | ClassCastException ex) {
                    plugin.getLogger().warning("sounds.yml: unknown sound '" + name + "' for '" + key + "' (muted)");
                }
            }
        }
        synchronized (entries) {
            entries.clear();
            entries.putAll(loaded);
        }
    }

    boolean isEnabled() {
        return enabled;
    }

    /** Turns every sound on or off and saves it in sounds.yml. Call on the main/global thread. */
    void setEnabled(boolean on) {
        enabled = on;
        yaml.set("enabled", on);
        try {
            yaml.save(new File(plugin.getDataFolder(), "sounds.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save sounds.yml: " + e.getMessage());
        }
    }

    void play(Player player, String key) {
        if (!enabled || player == null) return;
        Entry e;
        synchronized (entries) {
            e = entries.get(key);
        }
        if (e == null) return;
        platform.forPlayer(player, () -> {
            if (player.isOnline()) player.playSound(player.getLocation(), e.sound(), e.volume(), e.pitch());
        });
    }
}
