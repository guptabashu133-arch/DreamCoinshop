package org.mydreamduels.dreamcoinshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Extra names for a command from config.yml (currencies.*.commands), e.g. /shard for /orb after
 * Orbs were renamed to Shards. Registered once at startup.
 */
final class CommandAliases {

    private CommandAliases() {
    }

    /** One extra name: everything goes to the real command (permissions, executor, tab completion). */
    private static final class Alias extends Command {
        private final PluginCommand target;

        Alias(String name, PluginCommand target) {
            super(name, target.getDescription(), "/" + name, List.of());
            this.target = target;
            setPermission(target.getPermission());
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return target.execute(sender, label, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return target.tabComplete(sender, alias, args);
        }
    }

    static void register(JavaPlugin plugin, String command, List<String> names) {
        if (names.isEmpty()) return;
        PluginCommand target = plugin.getCommand(command);
        CommandMap map = commandMap(plugin);
        if (target == null || map == null) {
            plugin.getLogger().warning("Could not add the extra command names " + names + " for /" + command + ".");
            return;
        }
        String prefix = plugin.getName().toLowerCase(Locale.ROOT);
        for (String name : names) {
            Command existing = map.getCommand(name);
            if (existing != null && !(existing instanceof Alias)) {
                plugin.getLogger().warning("/" + name + " is already a command (" + existing.getName() + ") - not adding it for /" + command + ".");
                continue;
            }
            if (existing == null) map.register(prefix, new Alias(name, target));
        }
    }

    /** Paper has Server#getCommandMap(); Spigot's server class has the same public method. */
    private static CommandMap commandMap(JavaPlugin plugin) {
        try {
            Object map = plugin.getServer().getClass().getMethod("getCommandMap").invoke(plugin.getServer());
            return map instanceof CommandMap m ? m : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
