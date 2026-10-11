package org.mydreamduels.dreamcoinshop.events;

import com.destroystokyo.paper.profile.ProfileProperty;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.mydreamduels.dreamcoinshop.Dreamcoinshop;
import org.mydreamduels.dreamcoinshop.config.MessagesConfig;
import org.mydreamduels.dreamcoinshop.data.PlayerProfile;
import org.mydreamduels.dreamcoinshop.shopdata.ShopOptions;

/**
 * Head hunt event (e.g. the INDIA tag event).
 * <p>
 * /coinshop events gives an admin heads numbered 1..N. Each head carries its hunt id + number in
 * its PersistentDataContainer, and when it's placed that pair is copied onto the head block itself,
 * so the block knows which head it is - no locations file to keep in sync. A player clicking
 * a placed head records that number on their profile (playerdata/uuid.yml), so:
 * - every head counts once per player, even if they click it again or it gets re-placed,
 * - progress survives logging out and restarts (find 4, leave, come back -> still 4/25),
 * - once all N are found, the reward tag is unlocked and shows up in /coinshop -> Tags.
 */
public class HeadHuntManager implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final NamespacedKey HUNT_ID = new NamespacedKey("dreamcoinshop", "headhunt_id");
    public static final NamespacedKey HUNT_HEAD = new NamespacedKey("dreamcoinshop", "headhunt_head");
    /** holding a click on a block repeats the interaction every few ticks - don't spam chat for the same head */
    private static final long CLICK_COOLDOWN_MS = 1000L;
    private final Dreamcoinshop plugin;
    private final Map<UUID, LastClick> lastClick = new ConcurrentHashMap<>();

    public HeadHuntManager(Dreamcoinshop plugin) {
        this.plugin = plugin;
    }

    /** /coinshop events [hunt] - gives the sender every numbered head for that hunt. */
    public void giveHeads(Player player, String huntArg) {
        MessagesConfig msg = this.plugin.getMessages();
        Map<String, ShopOptions.HeadHunt> hunts = this.plugin.getShopConfig().getHeadHunts();
        ShopOptions.HeadHunt hunt = null;
        if (huntArg == null) {
            hunt = hunts.values().stream().findFirst().orElse(null);
        } else {
            for (ShopOptions.HeadHunt h : hunts.values()) {
                if (h.key().equalsIgnoreCase(huntArg)) {
                    hunt = h;
                    break;
                }
            }
        }
        if (hunt == null) {
            player.sendMessage(msg.get("headhunt.unknown-event", MessagesConfig.ph("event", huntArg == null ? "" : huntArg), MessagesConfig.ph("list", hunts.isEmpty() ? "none" : String.join(", ", hunts.keySet()))));
            return;
        }
        if (!hunt.enabled()) {
            player.sendMessage(msg.get("headhunt.event-disabled", MessagesConfig.ph("event", hunt.key())));
            return;
        }
        int dropped = 0;
        for (int number = 1; number <= hunt.heads(); ++number) {
            for (ItemStack leftover : player.getInventory().addItem(this.createHead(hunt, number)).values()) {
                player.getWorld().dropItem(player.getLocation(), leftover);
                dropped += leftover.getAmount();
            }
        }
        player.sendMessage(msg.get("headhunt.given", MessagesConfig.ph("amount", hunt.heads()), this.tagPlaceholder(hunt)));
        if (dropped > 0) {
            player.sendMessage(msg.get("headhunt.inventory-full", MessagesConfig.ph("amount", dropped)));
        }
    }

    public ItemStack createHead(ShopOptions.HeadHunt hunt, int number) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            String texture = textureValue(hunt.texture());
            if (texture != null) {
                try {
                    com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("dreamcoinshop:headhunt:" + hunt.key()).getBytes(StandardCharsets.UTF_8)), "dcs_headhunt");
                    profile.setProperty(new ProfileProperty("textures", texture));
                    skull.setPlayerProfile(profile);
                } catch (Exception e) {
                    this.plugin.getLogger().log(Level.WARNING, "head-hunts." + hunt.key() + ".texture is not a valid head texture - using a plain head instead.", e);
                }
            }
        }
        meta.displayName(MM.deserialize(hunt.headName()).append(Component.text(" #" + number, NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Event head " + number + "/" + hunt.heads(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Place it anywhere - players click it to collect.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(HUNT_ID, PersistentDataType.STRING, hunt.key());
        meta.getPersistentDataContainer().set(HUNT_HEAD, PersistentDataType.INTEGER, number);
        head.setItemMeta(meta);
        return head;
    }

    /**
     * config "texture" can be the texture hash (as on minecraft-heads.com / textures.minecraft.net
     * links), the full textures.minecraft.net URL, or an already-encoded base64 "Value".
     */
    private static String textureValue(String texture) {
        if (texture == null || texture.isBlank()) {
            return null;
        }
        String t = texture.trim();
        String url;
        if (t.matches("[0-9a-fA-F]{32,}")) {
            url = "http://textures.minecraft.net/texture/" + t;
        } else if (t.startsWith("http://") || t.startsWith("https://")) {
            url = t;
        } else {
            return t;
        }
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isHeadBlock(Material type) {
        return type == Material.PLAYER_HEAD || type == Material.PLAYER_WALL_HEAD;
    }

    /** {huntId, number} if this block is a placed event head, otherwise null. */
    private static HeadRef readHead(Block block) {
        if (block == null || !isHeadBlock(block.getType())) {
            return null;
        }
        BlockState state = block.getState(false);
        if (!(state instanceof Skull skull)) {
            return null;
        }
        PersistentDataContainer pdc = skull.getPersistentDataContainer();
        String huntId = pdc.get(HUNT_ID, PersistentDataType.STRING);
        Integer number = pdc.get(HUNT_HEAD, PersistentDataType.INTEGER);
        if (huntId == null || number == null) {
            return null;
        }
        return new HeadRef(huntId, number);
    }

    private static HeadRef readItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String huntId = pdc.get(HUNT_ID, PersistentDataType.STRING);
        Integer number = pdc.get(HUNT_HEAD, PersistentDataType.INTEGER);
        if (huntId == null || number == null) {
            return null;
        }
        return new HeadRef(huntId, number);
    }

    private TagResolver tagPlaceholder(ShopOptions.HeadHunt hunt) {
        ShopOptions.TagOption tag = this.plugin.getShopConfig().getTags().get(hunt.rewardTag());
        Component tagComponent = tag != null ? MM.deserialize(tag.miniMessage()) : Component.text(hunt.rewardTag(), NamedTextColor.WHITE);
        return Placeholder.component("tag", tagComponent);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        HeadRef ref = readItem(event.getItemInHand());
        if (ref == null) {
            return;
        }
        Player player = event.getPlayer();
        MessagesConfig msg = this.plugin.getMessages();
        // Only staff may place these - otherwise a player holding the items could place all of
        // them in their own base and collect the tag without actually hunting.
        if (!player.hasPermission("dreamcoinshop.admin")) {
            event.setCancelled(true);
            player.sendMessage(msg.get("headhunt.place-no-permission"));
            return;
        }
        BlockState state = event.getBlockPlaced().getState();
        if (!(state instanceof Skull skull)) {
            return;
        }
        skull.getPersistentDataContainer().set(HUNT_ID, PersistentDataType.STRING, ref.huntId());
        skull.getPersistentDataContainer().set(HUNT_HEAD, PersistentDataType.INTEGER, ref.number());
        skull.update(true, false);
        Location loc = event.getBlockPlaced().getLocation();
        player.sendMessage(msg.get("headhunt.placed", MessagesConfig.ph("number", ref.number()), MessagesConfig.ph("location", loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ())));
    }

    // Deliberately NOT ignoreCancelled: spawn protection (WorldGuard etc) usually denies block
    // interaction there, which marks this event cancelled - the hunt still has to work at spawn.
    // Left click counts too: vanilla spawn-protection (server.properties) swallows non-op right
    // clicks near world spawn before any event fires, but still fires LEFT_CLICK_BLOCK.
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if ((action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // an admin holding an event head is placing it against this one, not collecting
        if (readItem(event.getItem()) != null) {
            return;
        }
        HeadRef ref = readHead(event.getClickedBlock());
        if (ref == null) {
            return;
        }
        Player player = event.getPlayer();
        // admins left click to break/move heads - don't collect, don't stop the break
        if (action == Action.LEFT_CLICK_BLOCK && player.hasPermission("dreamcoinshop.admin")) {
            return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        LastClick last = this.lastClick.get(player.getUniqueId());
        if (last != null && last.head().equals(ref) && now - last.time() < CLICK_COOLDOWN_MS) {
            return;
        }
        this.lastClick.put(player.getUniqueId(), new LastClick(ref, now));
        this.claim(player, ref);
    }

    private void claim(Player player, HeadRef ref) {
        MessagesConfig msg = this.plugin.getMessages();
        ShopOptions.HeadHunt hunt = this.plugin.getShopConfig().getHeadHunts().get(ref.huntId());
        if (hunt == null || !hunt.enabled()) {
            player.sendMessage(msg.get("headhunt.event-over"));
            return;
        }
        PlayerProfile profile = this.plugin.getPlayerDataManager().get(player.getUniqueId());
        Set<Integer> found = profile.getFoundHuntHeads(hunt.key());
        int required = hunt.heads();
        TagResolver tag = this.tagPlaceholder(hunt);
        TagResolver requiredPh = MessagesConfig.ph("required", required);
        if (found.size() >= required) {
            // Already done. Normally the tag was unlocked at that moment; the check below only
            // matters if config was edited afterwards (e.g. reward-tag renamed).
            if (profile.getUnlockedTags().add(hunt.rewardTag())) {
                this.plugin.getPlayerDataManager().save(profile);
                player.sendMessage(msg.get("headhunt.completed", requiredPh, tag));
                this.plugin.getSounds().play(player, "headhunt-complete");
                return;
            }
            player.sendMessage(msg.get("headhunt.already-completed", requiredPh, tag));
            return;
        }
        if (!found.add(ref.number())) {
            player.sendMessage(msg.get("headhunt.already-found", MessagesConfig.ph("found", found.size()), requiredPh, MessagesConfig.ph("remaining", required - found.size()), tag));
            this.plugin.getSounds().play(player, "headhunt-already-found");
            return;
        }
        if (found.size() >= required) {
            profile.getUnlockedTags().add(hunt.rewardTag());
            this.plugin.getPlayerDataManager().save(profile);
            player.sendMessage(msg.get("headhunt.completed", requiredPh, tag));
            this.plugin.getSounds().play(player, "headhunt-complete");
            if (!msg.raw("headhunt.broadcast").isBlank()) {
                Bukkit.broadcast(msg.get("headhunt.broadcast", MessagesConfig.ph("player", player.getName()), requiredPh, tag));
            }
            return;
        }
        this.plugin.getPlayerDataManager().save(profile);
        player.sendMessage(msg.get("headhunt.found", MessagesConfig.ph("found", found.size()), requiredPh, MessagesConfig.ph("remaining", required - found.size()), tag));
        this.plugin.getSounds().play(player, "headhunt-found");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        HeadRef ref = readHead(event.getBlock());
        if (ref == null) {
            return;
        }
        Player player = event.getPlayer();
        MessagesConfig msg = this.plugin.getMessages();
        if (!player.hasPermission("dreamcoinshop.admin")) {
            event.setCancelled(true);
            player.sendMessage(msg.get("headhunt.protected"));
            return;
        }
        // Admin picking a head back up: hand back the same numbered head so it can be re-placed
        // without becoming a "new" head that players could claim twice.
        ShopOptions.HeadHunt hunt = this.plugin.getShopConfig().getHeadHunts().get(ref.huntId());
        if (hunt != null && player.getGameMode() != GameMode.CREATIVE) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().toCenterLocation(), this.createHead(hunt, ref.number()));
        }
        player.sendMessage(msg.get("headhunt.removed", MessagesConfig.ph("number", ref.number())));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> readHead(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> readHead(block) != null);
    }

    // heads pop off when pushed by a piston - those blocks are included in getBlocks()
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (containsHead(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (containsHead(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    // flowing water/lava washes heads away
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (readHead(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.lastClick.remove(event.getPlayer().getUniqueId());
    }

    private static boolean containsHead(List<Block> blocks) {
        for (Block block : blocks) {
            if (readHead(block) != null) {
                return true;
            }
        }
        return false;
    }

    private record HeadRef(String huntId, int number) {
    }

    private record LastClick(HeadRef head, long time) {
    }
}
