package org.mydreamduels.dreamcoinshop.gui;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;

/**
 * Sends a Coinshop menu to a Bedrock player as a NATIVE form instead of a Java dialog.
 *
 * Geyser can translate a dialog into a form, but it can't give the buttons pictures - the item
 * glyph ends up inside the button text and the picture box on the left stays empty. Here every
 * dialog button becomes a form button with the item's picture from the TaraashMC pack, and
 * pressing it runs exactly the same click code as the Java button.
 *
 * Needs Floodgate on this (backend) server; without it this returns false and the dialog is used.
 */
final class BedrockMenus {

    private static final String THEME_MARK = "§r§r§r"; // TaraashMC pack: gold "shop" panel
    private static Map<Integer, String> glyphToItem;
    private static Boolean available;

    private BedrockMenus() {
    }

    static boolean send(Dreamcoinshop plugin, Player player, List<ActionButton> buttons,
                        Map<DialogAction, DialogActionCallback> callbacks) {
        if (!IconSupport.isBedrock(player) || !available()) {
            return false;
        }
        try {
            SimpleForm.Builder form = SimpleForm.builder()
                    .title("§6§lCoinshop" + THEME_MARK)
                    .content("§7Coins: §6" + (long) plugin.getCoinsManager().getBalance(player.getUniqueId())
                            + "   §7Orbs: §d" + (long) plugin.getOrbsManager().getBalance(player.getUniqueId()));
            List<DialogActionCallback> actions = new ArrayList<>();
            for (ActionButton button : buttons) {
                String text = LegacyComponentSerializer.legacySection().serialize(button.label());
                String image = null;
                // the label starts with the item's pack glyph (see IconSupport) -> use it as the picture
                String stripped = text.replaceAll("§.", "").trim();
                if (!stripped.isEmpty()) {
                    String item = items().get(stripped.codePointAt(0));
                    if (item != null) {
                        image = "textures/dreamhomes/icons/" + item.toLowerCase(Locale.ROOT) + ".png";
                        text = text.replaceFirst(new String(Character.toChars(stripped.codePointAt(0))) + "\\s*", "");
                    }
                }
                if (image == null) {
                    form.button(text);
                } else {
                    form.button(text, FormImage.Type.PATH, image);
                }
                actions.add(button.action() == null ? null : callbacks.get(button.action()));
            }
            form.validResultHandler(response -> {
                int id = response.clickedButtonId();
                if (id < 0 || id >= actions.size() || actions.get(id) == null || !plugin.isEnabled()) {
                    return;
                }
                DialogActionCallback action = actions.get(id);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        action.accept(null, player);
                    }
                });
            });
            return FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
        } catch (Throwable t) {
            plugin.getLogger().warning("Bedrock form failed for " + player.getName() + ", using the dialog: " + t);
            return false;
        }
    }

    private static boolean available() {
        if (available == null) {
            boolean ok;
            try {
                Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Class.forName("org.geysermc.cumulus.form.SimpleForm");
                ok = Bukkit.getPluginManager().getPlugin("floodgate") != null;
            } catch (Throwable t) {
                ok = false;
            }
            available = ok;
        }
        return available;
    }

    /** glyph codepoint -> MATERIAL, the reverse of item-glyphs.properties */
    private static Map<Integer, String> items() {
        if (glyphToItem == null) {
            Map<Integer, String> map = new HashMap<>();
            try (InputStream in = BedrockMenus.class.getClassLoader().getResourceAsStream("item-glyphs.properties")) {
                Properties p = new Properties();
                if (in != null) p.load(in);
                for (String k : p.stringPropertyNames()) {
                    map.put(Integer.parseInt(p.getProperty(k).trim(), 16), k);
                }
            } catch (Exception ignored) {
            }
            glyphToItem = map;
        }
        return glyphToItem;
    }
}
