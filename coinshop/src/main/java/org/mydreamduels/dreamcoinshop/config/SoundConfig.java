package org.mydreamduels.dreamcoinshop.config;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class SoundConfig {
    private final JavaPlugin plugin;
    private final Map<String, SoundEntry> sounds = new HashMap<>();
    private boolean enabled = true;

    public SoundConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(boolean soundsEnabled) {
        this.enabled = soundsEnabled;
        this.sounds.clear();
        YamlConfiguration yaml = DefaultsMergingConfig.loadWithMergedDefaults(this.plugin, "sounds.yml");
        ConfigurationSection sec = yaml.getConfigurationSection("sounds");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            String soundName = s.getString("sound", "");
            if (soundName == null || soundName.isBlank()) continue;
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                this.sounds.put(key, new SoundEntry(sound, (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0)));
            } catch (IllegalArgumentException e) {
                this.plugin.getLogger().log(Level.WARNING, "sounds.yml: unknown sound '" + soundName + "' for key '" + key + "' - skipping.");
            }
        }
    }

    public void play(Player player, String key) {
        if (!this.enabled || player == null) {
            return;
        }
        SoundEntry entry = this.sounds.get(key);
        if (entry == null) {
            return;
        }
        player.playSound(player.getLocation(), entry.sound(), entry.volume(), entry.pitch());
    }

    private record SoundEntry(Sound sound, float volume, float pitch) {
    }
}
