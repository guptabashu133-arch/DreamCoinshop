package org.mydreamduels.dreamcoinshop.commands;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;

public class OrbShopCommand implements CommandExecutor {
    private final Dreamcoinshop plugin;

    public OrbShopCommand(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.getMessages().get("general.player-only", new TagResolver[0]));
            return true;
        }
        Player player = (Player) sender;
        this.plugin.getCoinShopGUI().openOrbShop(player);
        return true;
    }
}
