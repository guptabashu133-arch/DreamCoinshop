package org.mydreamduels.dreamcoinshop.data;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerDataManager {
    private final JavaPlugin plugin;
    private final File folder;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
        if (!this.folder.exists()) {
            this.folder.mkdirs();
        }
    }

    public PlayerProfile get(UUID uuid) {
        return this.cache.computeIfAbsent(uuid, this::load);
    }

    private PlayerProfile load(UUID uuid) {
        PlayerProfile profile = new PlayerProfile(uuid);
        File file = new File(this.folder, uuid + ".yml");
        if (!file.exists()) {
            return profile;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        profile.getUnlockedChatColors().addAll(yaml.getStringList("unlocked.chatcolors"));
        profile.getUnlockedGlows().addAll(yaml.getStringList("unlocked.glows"));
        profile.getUnlockedPerks().addAll(yaml.getStringList("unlocked.perks"));
        profile.getUnlockedTags().addAll(yaml.getStringList("unlocked.tags"));
        profile.getUnlockedGradients().addAll(yaml.getStringList("unlocked.gradients"));
        profile.setActiveChatColor(yaml.getString("active.chatcolor", null));
        profile.setActiveGlow(yaml.getString("active.glow", null));
        profile.setActiveTag(yaml.getString("active.tag", null));
        profile.setActiveGradient(yaml.getString("active.gradient", null));
        return profile;
    }

    public void save(PlayerProfile profile) {
        File file = new File(this.folder, profile.getUuid() + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("unlocked.chatcolors", profile.getUnlockedChatColors().stream().toList());
        yaml.set("unlocked.glows", profile.getUnlockedGlows().stream().toList());
        yaml.set("unlocked.perks", profile.getUnlockedPerks().stream().toList());
        yaml.set("unlocked.tags", profile.getUnlockedTags().stream().toList());
        yaml.set("unlocked.gradients", profile.getUnlockedGradients().stream().toList());
        yaml.set("active.chatcolor", profile.getActiveChatColor());
        yaml.set("active.glow", profile.getActiveGlow());
        yaml.set("active.tag", profile.getActiveTag());
        yaml.set("active.gradient", profile.getActiveGradient());
        try {
            yaml.save(file);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save playerdata for " + profile.getUuid(), e);
        }
    }

    public void saveAll() {
        this.cache.values().forEach(this::save);
    }
}
