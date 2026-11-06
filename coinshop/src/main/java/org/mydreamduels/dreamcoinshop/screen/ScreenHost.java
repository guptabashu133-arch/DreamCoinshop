package org.mydreamduels.dreamcoinshop.screen;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Everything the screens need from the coin shop - so the screen code stays in its own package.
 * The shop hands over a {@link Bridge}; the rest (faces, screens, settings) lives here.
 */
public final class ScreenHost {

    /** One shop section on a screen row. icon = the material whose picture is drawn. */
    public record Section(String id, String name, String value, String icon) {
    }

    /** One player on a top list. value is already formatted ("1.2K"). */
    public record Top(UUID uuid, String name, String value, String flag) {
    }

    /** What the shop provides. Called on the global / main thread. */
    public interface Bridge {
        /** The enabled sections, in main menu order. */
        List<Section> sections();

        /** The top players (cached by the shop; may be empty while it loads). */
        List<Top> top(boolean orbs, int limit);

        /** Opens a section of the shop for the player (player's thread). */
        void open(Player player, String section);

        /** sounds.yml */
        void sound(Player player, String key);

        /** "Coins" / "Shards" - the configured currency names. */
        String currency(boolean orbs);
    }

    private final JavaPlugin plugin;
    private final Bridge bridge;
    private Faces faces;
    private CoinScreens screens;

    public ScreenHost(JavaPlugin plugin, Bridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    /** Starts the screens (onEnable, after the shop is up). */
    public void start() {
        Tasks.init(plugin);
        faces = new Faces(plugin);
        screens = new CoinScreens(this);
        faces.onChange(screens::picturesChanged);
        plugin.getServer().getPluginManager().registerEvents(screens, plugin);
        screens.load();
    }

    /** The /coinscreen command. */
    public ScreenCommand command() {
        return new ScreenCommand(this);
    }

    public void stop() {
        if (screens != null) screens.shutdown();
        if (faces != null) faces.shutdown();
    }

    /** After /coinshop reload. */
    public void reload() {
        plugin.reloadConfig();
        if (screens != null) screens.reload();
    }

    public int count() {
        return screens == null ? 0 : screens.names().size();
    }

    JavaPlugin plugin() {
        return plugin;
    }

    Bridge bridge() {
        return bridge;
    }

    FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    File getDataFolder() {
        return plugin.getDataFolder();
    }

    Logger getLogger() {
        return plugin.getLogger();
    }

    String getName() {
        return plugin.getName();
    }

    InputStream getResource(String name) {
        return plugin.getResource(name);
    }

    Faces getFaces() {
        return faces;
    }

    CoinScreens getScreens() {
        return screens;
    }

    void reloadEverything() {
        reload();
    }
}
