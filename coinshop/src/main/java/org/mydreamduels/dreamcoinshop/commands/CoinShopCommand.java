package org.mydreamduels.dreamcoinshop.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;

public class CoinShopCommand implements CommandExecutor {
    private final Dreamcoinshop plugin;

    public CoinShopCommand(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dreamcoinshop.admin")) {
                sender.sendMessage(this.plugin.getMessages().get("general.no-permission", new TagResolver[0]));
                return true;
            }
            this.plugin.reloadEverything();
            sender.sendMessage(Component.text("Dreamcoinshop reloaded (config.yml, messages.yml, sounds.yml).", (TextColor) NamedTextColor.GREEN));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.getMessages().get("general.player-only", new TagResolver[0]));
            return true;
        }
        Player player = (Player) sender;
        this.plugin.getCoinShopGUI().openMain(player);
        return true;
    }
}
