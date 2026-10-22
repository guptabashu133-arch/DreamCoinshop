package org.mydreamduels.dreamcoinshop;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * The startup logo and status lines in the console. The logo uses block and box characters that
 * exist in every console code page (even Windows' cp437); links are full https:// URLs so
 * terminals and panels make them clickable.
 */
final class Banner {

    static final String SITE = "https://dreamscube.fun";

    private static final String[] LOGO = {
            "██████╗ ██████╗ ███████╗ █████╗ ███╗   ███╗ ██████╗ ██████╗ ██╗███╗   ██╗███████╗██╗  ██╗ ██████╗ ██████╗ ",
            "██╔══██╗██╔══██╗██╔════╝██╔══██╗████╗ ████║██╔════╝██╔═══██╗██║████╗  ██║██╔════╝██║  ██║██╔═══██╗██╔══██╗",
            "██║  ██║██████╔╝█████╗  ███████║██╔████╔██║██║     ██║   ██║██║██╔██╗ ██║███████╗███████║██║   ██║██████╔╝",
            "██║  ██║██╔══██╗██╔══╝  ██╔══██║██║╚██╔╝██║██║     ██║   ██║██║██║╚██╗██║╚════██║██╔══██║██║   ██║██╔═══╝ ",
            "██████╔╝██║  ██║███████╗██║  ██║██║ ╚═╝ ██║╚██████╗╚██████╔╝██║██║ ╚████║███████║██║  ██║╚██████╔╝██║     ",
            "╚═════╝ ╚═╝  ╚═╝╚══════╝╚═╝  ╚═╝╚═╝     ╚═╝ ╚═════╝ ╚═════╝ ╚═╝╚═╝  ╚═══╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝     "};
    private static final int WIDTH = 106;
    /** Columns where "Coin" and "Shop" start in the logo. */
    private static final int COIN_FROM = 43, SHOP_FROM = 73;

    private Banner() {
    }

    static void print(Dreamcoinshop plugin, String menus, String storage) {
        CommandSender c = Bukkit.getConsoleSender();
        String rule = "§8" + "-".repeat(WIDTH);
        c.sendMessage("");
        for (String line : LOGO) c.sendMessage(gradient(line));
        c.sendMessage("");
        c.sendMessage(center("§7Cosmetics shop §8|§7 Country flags §8|§7 Perks & sellwands §8|§7 Orbs §8|§7 Cross-server sync"));
        c.sendMessage(center("§7by §fBasuGamer56 §8-§7 v" + plugin.getDescription().getVersion()));
        c.sendMessage(rule);
        line(c, "Server", "§f" + Capabilities.describe());
        line(c, "Menus", "§f" + menus);
        line(c, "Data", storage);
        line(c, "License", "§7checking... §8|§7 manage at §b" + SITE);
        line(c, "Help", "§f/coinshop §7to open the shop §8|§f /coinshop settings §7to change it in-game");
        c.sendMessage(rule);
    }

    /** Filled blocks in the brand colours (Dream yellow, Coin gold, Shop orange-red), the 3D shadow dark grey. */
    private static String gradient(String line) {
        StringBuilder sb = new StringBuilder();
        String current = null;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            String color = ch == ' ' ? current
                    : ch != '\u2588' ? "§8"
                    : i < COIN_FROM ? "§e" : i < SHOP_FROM ? "§6" : "§c";
            if (color != null && !color.equals(current)) {
                sb.append(color);
                current = color;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private static String center(String text) {
        int visible = text.replaceAll("§.", "").length();
        return " ".repeat(Math.max(0, (WIDTH - visible) / 2)) + text;
    }

    private static void line(CommandSender c, String label, String value) {
        c.sendMessage("§8 > §7" + String.format("%-8s", label) + " " + value);
    }
}
