package org.mydreamduels.dreamcoinshop.config;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class DefaultsMergingConfig {
    private DefaultsMergingConfig() {
    }

    public static YamlConfiguration loadWithMergedDefaults(JavaPlugin plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource(fileName)) {
            if (in != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                yaml.setDefaults(defaults);
                yaml.options().copyDefaults(true);
                yaml.save(file);
                yaml = YamlConfiguration.loadConfiguration(file);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not merge new defaults into " + fileName + " - if something looks missing, delete " + fileName + " and let it regenerate.", e);
        }
        return yaml;
    }
}
