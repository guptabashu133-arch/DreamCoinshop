package org.mydreamduels.dreamcoinshop.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
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
        ConfigurationSection hunts = yaml.getConfigurationSection("headhunt");
        if (hunts != null) {
            for (String huntId : hunts.getKeys(false)) {
                profile.getFoundHuntHeads(huntId).addAll(hunts.getIntegerList(huntId));
            }
        }
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
        for (Map.Entry<String, Set<Integer>> hunt : profile.getAllFoundHuntHeads().entrySet()) {
            if (hunt.getValue().isEmpty()) continue;
            yaml.set("headhunt." + hunt.getKey(), new ArrayList<>(hunt.getValue()));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save playerdata for " + profile.getUuid(), e);
        }
    }

    public void saveAll() {
        this.cache.values().forEach(this::save);
    }

    /**
     * Wipes one head hunt for EVERY player who has ever joined, online or not: their found
     * heads, and the hunt's reward tag (unequipped too if they were wearing it).
     * <p>
     * Profiles already in the cache are reset in memory - that copy is what gets written back
     * on save/shutdown, so editing only their file would be undone later. Everyone else is
     * edited straight in their file, which is only parsed if it mentions the hunt or tag at all.
     */
    public HuntResetResult resetHeadHunt(String huntId, String rewardTag) {
        int progressCleared = 0;
        int tagsRemoved = 0;
        Set<UUID> handled = new HashSet<>();
        for (PlayerProfile profile : this.cache.values()) {
            handled.add(profile.getUuid());
            Set<Integer> found = profile.getAllFoundHuntHeads().remove(huntId);
            boolean hadProgress = found != null && !found.isEmpty();
            boolean hadTag = profile.getUnlockedTags().remove(rewardTag);
            boolean wasWearing = rewardTag.equals(profile.getActiveTag());
            if (wasWearing) {
                profile.setActiveTag(null);
            }
            if (hadProgress) ++progressCleared;
            if (hadTag) ++tagsRemoved;
            if (hadProgress || hadTag || wasWearing) {
                this.save(profile);
            }
        }
        File[] files = this.folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (handled.contains(uuid)) continue;
                try {
                    String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    if (!content.contains(huntId) && !content.contains(rewardTag)) continue;
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                    boolean hadProgress = yaml.contains("headhunt." + huntId);
                    if (hadProgress) {
                        yaml.set("headhunt." + huntId, null);
                    }
                    List<String> tags = new ArrayList<>(yaml.getStringList("unlocked.tags"));
                    boolean hadTag = tags.remove(rewardTag);
                    if (hadTag) {
                        yaml.set("unlocked.tags", tags);
                    }
                    boolean wasWearing = rewardTag.equals(yaml.getString("active.tag"));
                    if (wasWearing) {
                        yaml.set("active.tag", null);
                    }
                    if (hadProgress) ++progressCleared;
                    if (hadTag) ++tagsRemoved;
                    if (hadProgress || hadTag || wasWearing) {
                        yaml.save(file);
                    }
                } catch (IOException e) {
                    this.plugin.getLogger().log(Level.WARNING, "Could not reset head hunt '" + huntId + "' in " + file.getName(), e);
                }
            }
        }
        return new HuntResetResult(progressCleared, tagsRemoved);
    }

    public record HuntResetResult(int progressCleared, int tagsRemoved) {
    }
}
