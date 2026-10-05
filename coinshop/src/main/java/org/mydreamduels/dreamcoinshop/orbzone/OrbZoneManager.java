package org.mydreamduels.dreamcoinshop.orbzone;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.config.MessagesConfig;
import org.mydreamduels.dreamcoinshop.gui.IconSupport;

/**
 * Orb zones: areas selected with /orbwand where a player earns Orbs every
 * {@code orbzones.interval-seconds} while standing inside, with an action bar countdown.
 * <p>
 * Progress is per player and is thrown away the moment they leave every zone (or log out),
 * so stepping out and back in restarts the countdown - there is no way to bank progress
 * outside a zone.
 */
public class OrbZoneManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Dreamcoinshop plugin;
    private final NamespacedKey wandKey;
    private final File file;
    private final Map<String, OrbZone> zones = new LinkedHashMap<>();
    /** seconds already spent inside a zone towards the next payout */
    private final Map<UUID, Integer> progress = new HashMap<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();

    private boolean enabled = true;
    private int intervalSeconds = 60;
    private double amount = 1.0;
    private int iconChangeSeconds = 3;
    private List<Material> icons = List.of(Material.EXPERIENCE_BOTTLE);
    private BukkitTask task;
    private long ticks;

    public OrbZoneManager(Dreamcoinshop plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "orb_wand");
        this.file = new File(plugin.getDataFolder(), "orbzones.yml");
        this.loadZones();
    }

    /** Reads the orbzones: section of config.yml and (re)starts the 1-second tick. */
    public void reloadSettings() {
        FileConfiguration cfg = this.plugin.getConfig();
        this.enabled = cfg.getBoolean("orbzones.enabled", true);
        this.intervalSeconds = Math.max(1, cfg.getInt("orbzones.interval-seconds", 60));
        this.amount = cfg.getDouble("orbzones.amount", 1.0);
        this.iconChangeSeconds = Math.max(1, cfg.getInt("orbzones.icon-change-seconds", 3));
        List<Material> parsed = new ArrayList<>();
        for (String name : cfg.getStringList("orbzones.actionbar-icons")) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                parsed.add(m);
            } else {
                this.plugin.getLogger().warning("config.yml orbzones.actionbar-icons: unknown item '" + name + "' - skipping.");
            }
        }
        this.icons = parsed.isEmpty() ? List.of(Material.EXPERIENCE_BOTTLE) : parsed;

        if (this.task != null) {
            this.task.cancel();
        }
        // Interval changed by /coinshop reload - old progress could now be past the new interval.
        this.clearAllProgress();
        this.task = this.plugin.getServer().getScheduler().runTaskTimer((Plugin) this.plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.clearAllProgress();
    }

    private void clearAllProgress() {
        for (UUID uuid : this.progress.keySet()) {
            Player p = this.plugin.getServer().getPlayer(uuid);
            if (p != null) {
                p.sendActionBar(Component.empty());
            }
        }
        this.progress.clear();
    }

    private void tick() {
        this.ticks++;
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!this.enabled || player.isDead() || this.zoneAt(player.getLocation()) == null) {
                // Left the area (or zones are off): payouts stop at once and the countdown resets.
                if (this.progress.remove(uuid) != null) {
                    player.sendActionBar(Component.empty());
                }
                continue;
            }
            int elapsed = this.progress.getOrDefault(uuid, 0) + 1;
            if (elapsed >= this.intervalSeconds) {
                elapsed = 0;
                this.plugin.getOrbsManager().add(uuid, this.amount);
                this.plugin.getSounds().play(player, "orbzone-received");
            }
            this.progress.put(uuid, elapsed);
            this.sendActionBar(player, this.intervalSeconds - elapsed);
        }
    }

    private void sendActionBar(Player player, int secondsLeft) {
        IconSupport.setViewer(player);
        Material iconMat = this.icons.get((int) ((this.ticks / this.iconChangeSeconds) % this.icons.size()));
        Component icon = IconSupport.isBedrock(player) ? IconSupport.bedrockSmallGlyph(iconMat) : IconSupport.icon(iconMat);
        String time = (secondsLeft / 60) + ":" + String.format(Locale.ROOT, "%02d", secondsLeft % 60);
        String amountText = this.amount == Math.floor(this.amount) ? String.valueOf((long) this.amount) : String.valueOf(this.amount);
        MessagesConfig msg = this.plugin.getMessages();
        player.sendActionBar(msg.get("orbzone.actionbar",
                Placeholder.component("icon", icon == null ? Component.empty() : icon),
                MessagesConfig.ph("amount", amountText),
                MessagesConfig.ph("time", time)));
    }

    public OrbZone zoneAt(Location loc) {
        for (OrbZone zone : this.zones.values()) {
            if (zone.contains(loc)) {
                return zone;
            }
        }
        return null;
    }

    public void forget(Player player) {
        this.progress.remove(player.getUniqueId());
        this.selections.remove(player.getUniqueId());
    }

    // ---------------- wand / selection ----------------

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.displayName(Component.text("Orb Wand", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD));
        meta.lore(List.of(
                Component.text("Left click: set position 1", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right click: set position 2", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Then: /orbwand create <name>", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(this.wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(this.wandKey, PersistentDataType.BYTE);
    }

    /** @param index 0 for position 1, 1 for position 2 */
    public void setPosition(Player player, int index, Location loc) {
        Location[] sel = this.selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2]);
        sel[index] = loc.clone();
    }

    public Location[] getSelection(Player player) {
        return this.selections.get(player.getUniqueId());
    }

    // ---------------- zones ----------------

    public Collection<OrbZone> getZones() {
        return this.zones.values();
    }

    public OrbZone getZone(String name) {
        return this.zones.get(name.toLowerCase(Locale.ROOT));
    }

    public void addZone(OrbZone zone) {
        this.zones.put(zone.name().toLowerCase(Locale.ROOT), zone);
        this.saveZones();
    }

    public boolean removeZone(String name) {
        boolean removed = this.zones.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            this.saveZones();
        }
        return removed;
    }

    private void loadZones() {
        this.zones.clear();
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection sec = yaml.getConfigurationSection("zones");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection z = sec.getConfigurationSection(key);
            if (z == null) continue;
            OrbZone zone = new OrbZone(z.getString("name", key), z.getString("world", "world"),
                    z.getInt("min-x"), z.getInt("min-y"), z.getInt("min-z"),
                    z.getInt("max-x"), z.getInt("max-y"), z.getInt("max-z"));
            this.zones.put(key.toLowerCase(Locale.ROOT), zone);
        }
    }

    private void saveZones() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, OrbZone> e : this.zones.entrySet()) {
            OrbZone z = e.getValue();
            String p = "zones." + e.getKey() + ".";
            yaml.set(p + "name", z.name());
            yaml.set(p + "world", z.world());
            yaml.set(p + "min-x", z.minX());
            yaml.set(p + "min-y", z.minY());
            yaml.set(p + "min-z", z.minZ());
            yaml.set(p + "max-x", z.maxX());
            yaml.set(p + "max-y", z.maxY());
            yaml.set(p + "max-z", z.maxZ());
        }
        try {
            yaml.save(this.file);
        } catch (IOException ex) {
            this.plugin.getLogger().warning("Could not save orbzones.yml: " + ex.getMessage());
        }
    }
}
