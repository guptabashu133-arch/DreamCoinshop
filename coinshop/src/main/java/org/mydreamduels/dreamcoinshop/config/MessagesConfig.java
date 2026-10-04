package org.mydreamduels.dreamcoinshop.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MessagesConfig {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final JavaPlugin plugin;
    private YamlConfiguration yaml;

    public MessagesConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.yaml = DefaultsMergingConfig.loadWithMergedDefaults(this.plugin, "messages.yml");
    }

    public String raw(String path) {
        String value = this.yaml.getString(path);
        return value != null ? value : "<red>Missing message: " + path;
    }

    public Component get(String path, TagResolver... placeholders) {
        return MM.deserialize(this.raw(path), placeholders);
    }

    public static TagResolver ph(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }
}
