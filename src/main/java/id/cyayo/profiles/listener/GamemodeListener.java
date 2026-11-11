package id.cyayo.profiles.listener;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.gui.ProfileHolder;
import id.cyayo.profiles.manager.GamemodeManager;
import id.cyayo.profiles.model.ClassData;
import id.cyayo.profiles.model.Profile;
import id.cyayo.profiles.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Lectern;
import org.bukkit.block.TileState;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Allay;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class GamemodeListener implements Listener {

    public static class IronmanEnderChestHolder implements InventoryHolder {
        private final UUID playerUuid;
        private final int slot;
        private Inventory inventory;

        public IronmanEnderChestHolder(UUID playerUuid, int slot) {
            this.playerUuid = playerUuid;
            this.slot = slot;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public int getSlot() {
            return slot;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static class RecentDeathRecord {
        final UUID worldUuid;
        final double x;
        final double y;
        final double z;
        final UUID victimUuid;
        final boolean ironman;
        final long timestamp;

        RecentDeathRecord(Location loc, UUID victimUuid, boolean ironman) {
            this.worldUuid = loc.getWorld().getUID();
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.victimUuid = victimUuid;
            this.ironman = ironman;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final CyayoProfiles plugin;
    private final NamespacedKey keyDropOwner;
    private final NamespacedKey keyDropIronman;
    private final NamespacedKey keyBlockProfile;
    private final NamespacedKey keyBlockIronman;

    private final Map<UUID, Long> pickupWarningCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> interactWarningCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pvpWarningCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingFallenRespawn = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> crystalDetonators = new ConcurrentHashMap<>();
    private final List<RecentDeathRecord> recentPlayerDeaths = new CopyOnWriteArrayList<>();

    public GamemodeListener(CyayoProfiles plugin) {
        this.plugin = plugin;
        this.keyDropOwner = new NamespacedKey(plugin, "drop_owner");
        this.keyDropIronman = new NamespacedKey(plugin, "drop_ironman");
        this.keyBlockProfile = new NamespacedKey(plugin, "owner_profile");
        this.keyBlockIronman = new NamespacedKey(plugin, "owner_ironman");
    }

    private Profile getActiveProfile(Player player) {
        return plugin.getProfileManager().getActiveProfile(player);
    }

    private String msg(String key, String fallback) {
        return plugin.getMessagesConfig().getString(key, fallback);
    }

    private boolean canWarn(Map<UUID, Long> cooldownMap, UUID uuid, long intervalMs) {
        long now = System.currentTimeMillis();
        Long last = cooldownMap.get(uuid);
        if (last != null && now - last < intervalMs) {
            return false;
        }
        cooldownMap.put(uuid, now);
        return true;
    }

    // =========================================================================
    // 1. IRONMAN: DROP / PICKUP / MERGE ISOLATION
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Profile profile = getActiveProfile(player);
        boolean isIronman = profile != null && profile.isIronman();
        tagDroppedItem(event.getItemDrop(), player.getUniqueId(), isIronman);
    }

    private void tagDroppedItem(Item itemEntity, UUID ownerUuid, boolean isIronman) {
        if (itemEntity == null || ownerUuid == null) return;
        PersistentDataContainer pdc = itemEntity.getPersistentDataContainer();
        pdc.set(keyDropOwner, PersistentDataType.STRING, ownerUuid.toString());
        if (isIronman) {
            pdc.set(keyDropIronman, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (recentPlayerDeaths.isEmpty()) return;
        Location loc = event.getLocation();
        if (loc.getWorld() == null) return;

        long now = System.currentTimeMillis();
        UUID worldId = loc.getWorld().getUID();
        RecentDeathRecord matched = null;

        for (RecentDeathRecord rec : recentPlayerDeaths) {
            if (now - rec.timestamp > 250L) {
                recentPlayerDeaths.remove(rec);
                continue;
            }
            if (rec.worldUuid.equals(worldId)) {
                double dx = loc.getX() - rec.x;
                double dy = loc.getY() - rec.y;
                double dz = loc.getZ() - rec.z;
                if ((dx * dx + dy * dy + dz * dz) <= 16.0) {
                    matched = rec;
                    break;
                }
            }
        }

        if (matched != null) {
            tagDroppedItem(event.getEntity(), matched.victimUuid, matched.ironman);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        PersistentDataContainer pdcA = event.getEntity().getPersistentDataContainer();
        PersistentDataContainer pdcB = event.getTarget().getPersistentDataContainer();

        String ownerA = pdcA.get(keyDropOwner, PersistentDataType.STRING);
        String ownerB = pdcB.get(keyDropOwner, PersistentDataType.STRING);
        Byte ironA = pdcA.get(keyDropIronman, PersistentDataType.BYTE);
        Byte ironB = pdcB.get(keyDropIronman, PersistentDataType.BYTE);

        if (!Objects.equals(ownerA, ownerB) || !Objects.equals(ironA, ironB)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player picker)) return;

        GamemodeManager gm = plugin.getGamemodeManager();
        Profile pickerProfile = getActiveProfile(picker);
        Item item = event.getItem();
        PersistentDataContainer pdc = item.getPersistentDataContainer();

        String ownerStr = pdc.get(keyDropOwner, PersistentDataType.STRING);
        Byte ironFlag = pdc.get(keyDropIronman, PersistentDataType.BYTE);

        // Rule A: Other players cannot pick up items dropped by an Ironman player
        if (ironFlag != null && ironFlag == (byte) 1 && gm.isIronmanPreventOthersPickupDrops()) {
            if (ownerStr == null || !ownerStr.equals(picker.getUniqueId().toString())) {
                event.setCancelled(true);
                if (canWarn(pickupWarningCooldown, picker.getUniqueId(), 1500L)) {
                    ColorUtils.sendActionBar(picker, msg("ironman_drop_others_blocked",
                            "<red>This item was dropped by an Ironman player and cannot be picked up!"));
                }
                return;
            }
        }

        // Rule B: Ironman players cannot pick up items dropped by other players
        if (pickerProfile != null && pickerProfile.isIronman() && gm.isIronmanBlockDropPickupFromOthers()) {
            String effectiveOwner = ownerStr;
            if (effectiveOwner == null && item.getThrower() != null) {
                Player throwerOnline = Bukkit.getPlayer(item.getThrower());
                if (throwerOnline != null) {
                    effectiveOwner = throwerOnline.getUniqueId().toString();
                }
            }
            if (effectiveOwner != null && !effectiveOwner.equals(picker.getUniqueId().toString())) {
                event.setCancelled(true);
                if (canWarn(pickupWarningCooldown, picker.getUniqueId(), 1500L)) {
                    ColorUtils.sendActionBar(picker, msg("ironman_pickup_blocked",
                            "<red>Ironman Mode: You cannot pick up items owned by other players!"));
                }
            }
        }
    }

    // =========================================================================
    // 2. IRONMAN: ENDER CHEST, CONTAINER, INTERAKSI PLAYER & BLOCKED COMMANDS
    // =========================================================================

    private static final BlockFace[] CHEST_NEIGHBORS = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private boolean isTrackedContainerState(BlockState state) {
        return state instanceof Container
                || state instanceof Lectern
                || state instanceof ChiseledBookshelf
                || state instanceof Jukebox
                || state.getType() == Material.DECORATED_POT;
    }

    private void stampOwnership(PersistentDataContainer pdc, Profile profile) {
        if (pdc == null || profile == null) return;
        pdc.set(keyBlockProfile, PersistentDataType.STRING, profile.getProfileUuid().toString());
        if (profile.isIronman()) {
            pdc.set(keyBlockIronman, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(keyBlockIronman);
        }
    }

    private boolean canAccessOwnedPdc(Player player, Profile profile, PersistentDataContainer pdc, boolean allowUnowned, String blockMsgKey, String protectMsgKey) {
        GamemodeManager gm = plugin.getGamemodeManager();
        String ownerProfile = pdc.get(keyBlockProfile, PersistentDataType.STRING);
        Byte isIron = pdc.get(keyBlockIronman, PersistentDataType.BYTE);
        String myProfileStr = profile != null ? profile.getProfileUuid().toString() : null;

        if (gm.isIronmanProtectContainers() && isIron != null && isIron == (byte) 1) {
            if (myProfileStr == null || !myProfileStr.equals(ownerProfile)) {
                if (canWarn(interactWarningCooldown, player.getUniqueId(), 1500L)) {
                    ColorUtils.sendActionBar(player, msg(protectMsgKey,
                            "<red>This container belongs to an Ironman character and cannot be accessed!"));
                }
                return false;
            }
        }

        if (profile != null && profile.isIronman() && gm.isIronmanBlockExternalContainers()) {
            if (ownerProfile != null && ownerProfile.equals(myProfileStr)) {
                return true;
            }
            if (allowUnowned && ownerProfile == null) {
                return true;
            }
            if (canWarn(interactWarningCooldown, player.getUniqueId(), 1500L)) {
                ColorUtils.sendActionBar(player, msg(blockMsgKey,
                        "<red>Ironman Mode: You can only access containers owned by this profile!"));
            }
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block placed = event.getBlockPlaced();
        Material type = placed.getType();
        Profile profile = getActiveProfile(player);
        GamemodeManager gm = plugin.getGamemodeManager();

        // Prevent merging a Single Chest into a DoubleChest owned by another player/profile when Ironman is involved
        if ((type == Material.CHEST || type == Material.TRAPPED_CHEST)
                && (gm.isIronmanBlockExternalContainers() || gm.isIronmanProtectContainers())) {
            String myProfileStr = profile != null ? profile.getProfileUuid().toString() : null;
            boolean placerIron = profile != null && profile.isIronman();

            for (BlockFace face : CHEST_NEIGHBORS) {
                Block neighbor = placed.getRelative(face);
                if (neighbor.getType() == type && neighbor.getState() instanceof TileState relTs) {
                    PersistentDataContainer relPdc = relTs.getPersistentDataContainer();
                    String relProfile = relPdc.get(keyBlockProfile, PersistentDataType.STRING);
                    Byte relIron = relPdc.get(keyBlockIronman, PersistentDataType.BYTE);
                    boolean neighborIron = relIron != null && relIron == (byte) 1;

                    if ((placerIron || neighborIron) && !Objects.equals(relProfile, myProfileStr)) {
                        event.setCancelled(true);
                        if (canWarn(interactWarningCooldown, player.getUniqueId(), 1500L)) {
                            ColorUtils.sendActionBar(player, msg(neighborIron ? "ironman_container_protected" : "ironman_container_blocked",
                                    "<red>Cannot merge a chest with a container owned by another profile!"));
                        }
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onContainerBlockPlacedMonitor(BlockPlaceEvent event) {
        Profile profile = getActiveProfile(event.getPlayer());
        if (profile == null) return;

        BlockState state = event.getBlockPlaced().getState();
        if (state instanceof TileState ts && isTrackedContainerState(state)) {
            stampOwnership(ts.getPersistentDataContainer(), profile);
            ts.update(false, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        Profile profile = getActiveProfile(player);
        if (profile == null) return;

        Entity ent = event.getEntity();
        if (ent instanceof InventoryHolder || ent instanceof ArmorStand || ent instanceof ItemFrame) {
            stampOwnership(ent.getPersistentDataContainer(), profile);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        Profile profile = getActiveProfile(player);
        if (profile == null) return;

        Entity ent = event.getEntity();
        if (ent instanceof ItemFrame) {
            stampOwnership(ent.getPersistentDataContainer(), profile);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (!plugin.getGamemodeManager().isIronmanAllowWorldgenLootChests()) return;
        if (!(event.getEntity() instanceof Player looter)) return;

        Profile profile = getActiveProfile(looter);
        if (profile == null) return;

        InventoryHolder invHolder = event.getInventoryHolder();
        if (invHolder instanceof TileState ts) {
            PersistentDataContainer pdc = ts.getPersistentDataContainer();
            if (!pdc.has(keyBlockProfile, PersistentDataType.STRING)) {
                stampOwnership(pdc, profile);
                ts.update(false, false);
            }
        } else if (invHolder instanceof Entity ent) {
            PersistentDataContainer pdc = ent.getPersistentDataContainer();
            if (!pdc.has(keyBlockProfile, PersistentDataType.STRING)) {
                stampOwnership(pdc, profile);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BlockState state = event.getBlock().getState();
        if (!(state instanceof TileState ts) || !isTrackedContainerState(state)) return;

        Profile profile = getActiveProfile(player);
        boolean isWorldgenLoot = state instanceof Lootable lootable && lootable.getLootTable() != null;

        if (!canAccessOwnedPdc(player, profile, ts.getPersistentDataContainer(), isWorldgenLoot,
                "ironman_container_break_blocked", "ironman_container_protected")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        GamemodeManager gm = plugin.getGamemodeManager();
        if (!gm.isIronmanProtectContainers() && !gm.isIronmanBlockExternalContainers()) return;

        ContainerOwnership src = getHolderOwnership(event.getSource().getHolder());
        ContainerOwnership dst = getHolderOwnership(event.getDestination().getHolder());

        if ((src.ironman || dst.ironman) && !Objects.equals(src.profileUuid, dst.profileUuid)) {
            event.setCancelled(true);
        }
    }

    private static class ContainerOwnership {
        final String profileUuid;
        final boolean ironman;

        ContainerOwnership(String profileUuid, boolean ironman) {
            this.profileUuid = profileUuid;
            this.ironman = ironman;
        }
    }

    private ContainerOwnership getHolderOwnership(InventoryHolder holder) {
        if (holder instanceof DoubleChest dc) {
            if (dc.getLeftSide() instanceof TileState left) {
                PersistentDataContainer pdc = left.getPersistentDataContainer();
                String prof = pdc.get(keyBlockProfile, PersistentDataType.STRING);
                Byte iron = pdc.get(keyBlockIronman, PersistentDataType.BYTE);
                if (prof != null) return new ContainerOwnership(prof, iron != null && iron == (byte) 1);
            }
            if (dc.getRightSide() instanceof TileState right) {
                PersistentDataContainer pdc = right.getPersistentDataContainer();
                String prof = pdc.get(keyBlockProfile, PersistentDataType.STRING);
                Byte iron = pdc.get(keyBlockIronman, PersistentDataType.BYTE);
                return new ContainerOwnership(prof, iron != null && iron == (byte) 1);
            }
        } else if (holder instanceof TileState ts) {
            PersistentDataContainer pdc = ts.getPersistentDataContainer();
            String prof = pdc.get(keyBlockProfile, PersistentDataType.STRING);
            Byte iron = pdc.get(keyBlockIronman, PersistentDataType.BYTE);
            return new ContainerOwnership(prof, iron != null && iron == (byte) 1);
        } else if (holder instanceof Entity ent) {
            PersistentDataContainer pdc = ent.getPersistentDataContainer();
            String prof = pdc.get(keyBlockProfile, PersistentDataType.STRING);
            Byte iron = pdc.get(keyBlockIronman, PersistentDataType.BYTE);
            return new ContainerOwnership(prof, iron != null && iron == (byte) 1);
        }
        return new ContainerOwnership(null, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractBlockDirectContainer(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        BlockState state = block.getState();
        if (!(state instanceof TileState ts)) return;
        if (!(state instanceof ChiseledBookshelf || state instanceof Lectern || state instanceof Jukebox || state.getType() == Material.DECORATED_POT)) {
            return;
        }

        Player player = event.getPlayer();
        Profile profile = getActiveProfile(player);
        if (!canAccessOwnedPdc(player, profile, ts.getPersistentDataContainer(), false,
                "ironman_container_blocked", "ironman_container_protected")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntityIronman(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getRightClicked();
        if (target.hasMetadata("NPC")) return;

        Profile profile = getActiveProfile(player);
        GamemodeManager gm = plugin.getGamemodeManager();

        // 1. Direct Player-to-Player Interaction (e.g. Shift+Click Trade GUIs)
        if (target instanceof Player targetPlayer && !targetPlayer.getUniqueId().equals(player.getUniqueId())) {
            Profile targetProf = getActiveProfile(targetPlayer);
            boolean eitherIronman = (profile != null && profile.isIronman()) || (targetProf != null && targetProf.isIronman());
            if (eitherIronman && gm.isIronmanBlockPlayerInteractEntities()) {
                event.setCancelled(true);
                if (canWarn(interactWarningCooldown, player.getUniqueId(), 1500L)) {
                    ColorUtils.sendActionBar(player, msg("ironman_player_interact_blocked",
                            "<red>Ironman Mode: You cannot interact directly with other players!"));
                }
            }
            return;
        }

        // 2. Entity Containers / Display Entities (ItemFrame, ArmorStand, Allay, ChestBoat, StorageMinecart, Donkey/Mule/Llama)
        if (!gm.isIronmanBlockExternalContainers() && !gm.isIronmanProtectContainers()) return;

        if (target instanceof ArmorStand stand && (stand.isMarker() || !stand.isVisible())) {
            return;
        }

        if (target instanceof Allay) {
            boolean ironOwner = Byte.valueOf((byte) 1).equals(target.getPersistentDataContainer().get(keyBlockIronman, PersistentDataType.BYTE));
            if ((profile != null && profile.isIronman()) || ironOwner) {
                if (!Objects.equals(target.getPersistentDataContainer().get(keyBlockProfile, PersistentDataType.STRING),
                        profile != null ? profile.getProfileUuid().toString() : null)) {
                    event.setCancelled(true);
                    if (canWarn(interactWarningCooldown, player.getUniqueId(), 1500L)) {
                        ColorUtils.sendActionBar(player, msg("ironman_entity_interact_blocked",
                                "<red>Ironman Mode: You cannot use an Allay owned by another player!"));
                    }
                }
            }
            return;
        }

        if (target instanceof ItemFrame || target instanceof ArmorStand
                || (target instanceof InventoryHolder && !(target instanceof Merchant))) {
            boolean allowUnowned = false;
            if (target instanceof Tameable tame) {
                if (tame.isTamed() && tame.getOwner() != null && !tame.getOwner().getUniqueId().equals(player.getUniqueId())) {
                    if (profile != null && profile.isIronman()) {
                        event.setCancelled(true);
                        if (canWarn(interactWarningCooldown, player.getUniqueId(), 1500L)) {
                            ColorUtils.sendActionBar(player, msg("ironman_entity_interact_blocked",
                                    "<red>Ironman Mode: This mount belongs to another player!"));
                        }
                        return;
                    }
                }
                allowUnowned = !tame.isTamed();
            } else if (target instanceof Lootable lootable && lootable.getLootTable() != null && gm.isIronmanAllowWorldgenLootChests()) {
                allowUnowned = true;
            }

            if (!canAccessOwnedPdc(player, profile, target.getPersistentDataContainer(), allowUnowned,
                    "ironman_entity_interact_blocked", "ironman_container_protected")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageStorageOrDisplayEntity(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (!(target instanceof ItemFrame) && !(target instanceof ArmorStand)) return;
        if (target.hasMetadata("NPC")) return;

        Player attacker = getPlayerDamager(event.getDamager());
        if (attacker == null) return;

        Profile profile = getActiveProfile(attacker);
        if (!canAccessOwnedPdc(attacker, profile, target.getPersistentDataContainer(), false,
                "ironman_entity_interact_blocked", "ironman_container_protected")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        InventoryHolder invHolder = event.getInventory().getHolder();
        if (invHolder instanceof IronmanEnderChestHolder || invHolder instanceof ProfileHolder) {
            return;
        }

        GamemodeManager gm = plugin.getGamemodeManager();
        Profile profile = getActiveProfile(player);

        // A. Ironman Isolated Ender Chest
        if (event.getInventory().getType() == InventoryType.ENDER_CHEST) {
            if (!gm.isIronmanSeparateEnderChest()) return;
            if (profile == null || !profile.isIronman()) return;

            event.setCancelled(true);

            final int slot = profile.getSlot();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                Profile current = getActiveProfile(player);
                if (current == null || !current.isIronman() || current.getSlot() != slot) return;

                String titleTemplate = msg("ironman_enderchest_title", "<dark_gray><bold>Ironman Vault (Slot #{slot})</bold>");
                String title = ColorUtils.color(ColorUtils.replaceBoth(titleTemplate, "slot", String.valueOf(slot)));

                IronmanEnderChestHolder holder = new IronmanEnderChestHolder(player.getUniqueId(), slot);
                Inventory virtualEc = Bukkit.createInventory(holder, 27, title);
                holder.setInventory(virtualEc);

                ItemStack[] saved = current.getIronmanEnderChest();
                for (int i = 0; i < 27 && i < saved.length; i++) {
                    if (saved[i] != null && saved[i].getType() != Material.AIR) {
                        virtualEc.setItem(i, saved[i].clone());
                    }
                }

                player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.0f);
                player.openInventory(virtualEc);
            });
            return;
        }

        // B. Physical Container Protection & External Container Blocking for Ironman
        if (!gm.isIronmanBlockExternalContainers() && !gm.isIronmanProtectContainers()) return;

        if (invHolder instanceof DoubleChest dc) {
            if (dc.getLeftSide() instanceof TileState left) {
                boolean loot = left instanceof Lootable l && l.getLootTable() != null && gm.isIronmanAllowWorldgenLootChests();
                if (!canAccessOwnedPdc(player, profile, left.getPersistentDataContainer(), loot,
                        "ironman_container_blocked", "ironman_container_protected")) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (dc.getRightSide() instanceof TileState right) {
                boolean loot = right instanceof Lootable l && l.getLootTable() != null && gm.isIronmanAllowWorldgenLootChests();
                if (!canAccessOwnedPdc(player, profile, right.getPersistentDataContainer(), loot,
                        "ironman_container_blocked", "ironman_container_protected")) {
                    event.setCancelled(true);
                }
            }
        } else if (invHolder instanceof TileState ts && isTrackedContainerState(ts)) {
            boolean loot = ts instanceof Lootable l && l.getLootTable() != null && gm.isIronmanAllowWorldgenLootChests();
            if (!canAccessOwnedPdc(player, profile, ts.getPersistentDataContainer(), loot,
                    "ironman_container_blocked", "ironman_container_protected")) {
                event.setCancelled(true);
            }
        } else if (invHolder instanceof Entity ent && !(ent instanceof Player) && !(ent instanceof Merchant)) {
            boolean allowUnowned = ent instanceof Tameable tame && !tame.isTamed();
            if (ent instanceof Lootable l && l.getLootTable() != null && gm.isIronmanAllowWorldgenLootChests()) {
                allowUnowned = true;
            }
            if (!canAccessOwnedPdc(player, profile, ent.getPersistentDataContainer(), allowUnowned,
                    "ironman_entity_interact_blocked", "ironman_container_protected")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof IronmanEnderChestHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        saveVirtualEnderChest(player, holder.getSlot(), event.getInventory(), true);
    }

    public void flushOpenIronmanEnderChest(Player player) {
        flushOpenIronmanEnderChest(player, false);
    }

    public void flushOpenIronmanEnderChest(Player player, boolean triggerSqlSave) {
        if (player == null) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof IronmanEnderChestHolder holder) {
            saveVirtualEnderChest(player, holder.getSlot(), top, triggerSqlSave);
        }
    }

    private void saveVirtualEnderChest(Player player, int slot, Inventory inv, boolean triggerSqlSave) {
        var playerHolder = plugin.getProfileManager().getHolder(player);
        if (playerHolder == null) return;

        Profile profile = playerHolder.getProfile(slot);
        if (profile == null) return;

        ItemStack[] cloned = new ItemStack[27];
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < 27 && i < contents.length; i++) {
            cloned[i] = contents[i] != null ? contents[i].clone() : null;
        }
        profile.setIronmanEnderChest(cloned);
        if (triggerSqlSave) {
            plugin.getProfileStorage().saveProfile(profile);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        Profile profile = getActiveProfile(player);
        if (profile == null || !profile.isIronman()) return;

        GamemodeManager gm = plugin.getGamemodeManager();
        Set<String> blocked = gm.getIronmanBlockedCommands();
        if (blocked.isEmpty()) return;

        String raw = event.getMessage().trim();
        if (!raw.startsWith("/")) return;
        String withoutSlash = raw.substring(1).trim();
        if (withoutSlash.isEmpty()) return;

        String firstToken = withoutSlash.split("\\s+")[0].toLowerCase();
        String pluginPrefix = firstToken.contains(":") ? firstToken.substring(0, firstToken.indexOf(':')) : "";
        String baseCmd = firstToken.contains(":") ? firstToken.substring(firstToken.indexOf(':') + 1) : firstToken;

        boolean isBlocked = blocked.contains(firstToken) || blocked.contains(baseCmd);

        if (!isBlocked && !pluginPrefix.isEmpty()) {
            if (blocked.contains(pluginPrefix + ":*") || blocked.contains("plugin:" + pluginPrefix)) {
                isBlocked = true;
            }
        }

        if (!isBlocked) {
            PluginCommand pc = Bukkit.getPluginCommand(firstToken);
            if (pc == null) {
                pc = Bukkit.getPluginCommand(baseCmd);
            }
            if (pc != null) {
                String pcPlugin = pc.getPlugin().getName().toLowerCase();
                String pcName = pc.getName().toLowerCase();
                if (blocked.contains(pcName) || blocked.contains(pcPlugin + ":*") || blocked.contains("plugin:" + pcPlugin)) {
                    isBlocked = true;
                } else {
                    for (String alias : pc.getAliases()) {
                        if (blocked.contains(alias.toLowerCase())) {
                            isBlocked = true;
                            break;
                        }
                    }
                }
            }
        }

        if (isBlocked) {
            event.setCancelled(true);
            String m = msg("ironman_command_blocked", "<red>Ironman Mode: The command <yellow>/{command} <red>is blocked!");
            m = ColorUtils.replaceBoth(m, "command", baseCmd);
            ColorUtils.sendMessage(player, m);
        }
    }

    // =========================================================================
    // 3. HUNTED PvP RULES & CURSED DAMAGE MULTIPLIERS
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntityPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = getPlayerDamager(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        Profile attackerProf = getActiveProfile(attacker);
        Profile victimProf = getActiveProfile(victim);
        boolean attackerHunted = attackerProf != null && attackerProf.isHunted();
        boolean victimHunted = victimProf != null && victimProf.isHunted();

        if (!attackerHunted && !victimHunted) return;

        GamemodeManager gm = plugin.getGamemodeManager();

        // Safezone protects Hunted players & prevents Hunted fights in safe worlds / pvp-deny WG regions
        if (gm.isSafeZone(victim.getLocation()) || gm.isSafeZone(attacker.getLocation())) {
            event.setCancelled(true);
            return;
        }

        if (victimHunted) {
            // Everyone can attack a Hunted player outside Safezones; record self-defense permission for victim
            boolean alreadyAllowed = gm.canHuntedRetaliate(victim.getUniqueId(), attacker.getUniqueId());
            gm.recordHuntedAttack(victim.getUniqueId(), attacker.getUniqueId());
            if (!alreadyAllowed && !attackerHunted) {
                String m = msg("hunted_retaliate_unlocked",
                        "<gold>⚔ You were attacked by <yellow>{attacker}<gold>! Retaliation mode active for <yellow>{seconds}s<gold>.");
                m = ColorUtils.replaceBoth(m, "attacker", attacker.getName());
                m = ColorUtils.replaceBoth(m, "seconds", String.valueOf(gm.getHuntedSelfDefenseDurationSeconds()));
                ColorUtils.sendActionBar(victim, m);
            }
            return;
        }

        // Attacker is Hunted, Victim is NOT Hunted -> check self-defense window
        if (!gm.canHuntedRetaliate(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            if (canWarn(pvpWarningCooldown, attacker.getUniqueId(), 1500L)) {
                ColorUtils.sendActionBar(attacker, msg("hunted_pvp_unprovoked",
                        "<red>Hunted Mode: You can only retaliate against non-Hunted players after they attack you first!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCursedOutgoingDamage(EntityDamageByEntityEvent event) {
        Player attacker = getPlayerDamager(event.getDamager());
        if (attacker == null) return;
        if (event.getEntity() instanceof Player victim && victim.getUniqueId().equals(attacker.getUniqueId())) return;

        Profile attackerProf = getActiveProfile(attacker);
        if (attackerProf != null && attackerProf.isCursed()) {
            double mult = plugin.getGamemodeManager().getCursedOutgoingDamageMultiplier();
            if (mult != 1.0) {
                event.setDamage(event.getDamage() * mult);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCursedIncomingDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Profile victimProf = getActiveProfile(victim);
        if (victimProf != null && victimProf.isCursed()) {
            double mult = plugin.getGamemodeManager().getCursedIncomingDamageMultiplier();
            if (mult != 1.0) {
                event.setDamage(event.getDamage() * mult);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCursedNaturalRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;

        GamemodeManager gm = plugin.getGamemodeManager();
        if (!gm.isCursedDisableNaturalRegen()) return;

        Profile profile = getActiveProfile(player);
        if (profile != null && profile.isCursed()) {
            event.setCancelled(true);
        }
    }

    private Player getPlayerDamager(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p) return p;
        if (damager instanceof EnderCrystal crystal) {
            UUID detonator = crystalDetonators.remove(crystal.getUniqueId());
            if (detonator != null) {
                return Bukkit.getPlayer(detonator);
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrackPvpAttribution(EntityDamageByEntityEvent event) {
        Player attacker = getPlayerDamager(event.getDamager());
        if (attacker == null) return;

        if (event.getEntity() instanceof EnderCrystal crystal) {
            if (crystalDetonators.size() > 256) {
                crystalDetonators.clear();
            }
            crystalDetonators.put(crystal.getUniqueId(), attacker.getUniqueId());
            return;
        }

        if (event.getEntity() instanceof Player victim && !victim.getUniqueId().equals(attacker.getUniqueId())) {
            plugin.getGamemodeManager().recordPvpAttribution(victim.getUniqueId(), attacker);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLavaBucketPlaceAttribution(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.LAVA_BUCKET) return;
        Player placer = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        if (loc.getWorld() == null) return;

        GamemodeManager gm = plugin.getGamemodeManager();
        for (Player nearby : loc.getWorld().getPlayers()) {
            if (nearby.getUniqueId().equals(placer.getUniqueId())) continue;
            if (nearby.getLocation().distanceSquared(loc) <= 20.25) { // 4.5 blocks radius
                gm.recordPvpAttribution(nearby.getUniqueId(), placer);
            }
        }
    }

    // =========================================================================
    // 4. HUNTED DEATH DROPS & HARDCORE PERMADEATH
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Profile profile = getActiveProfile(victim);
        if (profile == null) return;

        GamemodeManager gm = plugin.getGamemodeManager();
        Player killer = victim.getKiller();
        GamemodeManager.PvpAttribution pvpAttr = gm.getRecentPvpAttacker(victim.getUniqueId());

        UUID resolvedKillerUuid = (killer != null && !killer.getUniqueId().equals(victim.getUniqueId()))
                ? killer.getUniqueId()
                : (pvpAttr != null ? pvpAttr.attackerUuid : null);
        String resolvedKillerName = (killer != null)
                ? killer.getName()
                : (pvpAttr != null ? pvpAttr.attackerName : null);

        gm.clearPvpAttribution(victim.getUniqueId());

        // Record recent death so ItemSpawnEvent can tag vanilla death drops with drop_owner / drop_ironman
        if (!event.getKeepInventory() && !event.getDrops().isEmpty()) {
            recentPlayerDeaths.add(new RecentDeathRecord(victim.getLocation(), victim.getUniqueId(), profile.isIronman()));
        }

        // --- A. Hunted Mode Death Drops ---
        if (profile.isHunted()) {
            boolean qualifies = !gm.isHuntedDropOnlyOnPlayerKill()
                    || (resolvedKillerUuid != null && !resolvedKillerUuid.equals(victim.getUniqueId()));
            if (qualifies) {
                handleHuntedDeathDrops(event, victim, profile, gm);
            }
        }

        // --- B. Hardcore Mode Lives & Fallen Handling ---
        if (profile.isHardcore() && !profile.isDead()) {
            int remaining = profile.getCurrentLives() - 1;
            profile.setCurrentLives(remaining);

            if (remaining > 0) {
                String m = msg("hardcore_life_lost",
                        "<red>❤ You lost a life! <yellow>{lives}/{max_lives} <red>lives remaining in Hardcore mode.");
                m = ColorUtils.replaceBoth(m, "lives", String.valueOf(remaining));
                m = ColorUtils.replaceBoth(m, "max_lives", String.valueOf(profile.getMaxLives()));
                ColorUtils.sendMessage(victim, m);
                ColorUtils.playSound(victim, msg("hardcore_life_lost_sound", "ENTITY_WITHER_HURT:0.8:0.8"));
                plugin.getProfileStorage().saveProfile(profile);
            } else {
                // 0 lives left -> Permanently FALLEN [FALLEN]
                profile.setCurrentLives(0);
                profile.setDead(true);
                profile.setFallenAt(System.currentTimeMillis());

                String reason = "Perished in adventure";
                if (resolvedKillerName != null) {
                    reason = "Killed by " + resolvedKillerName;
                } else {
                    String rawMsg = event.getDeathMessage();
                    if (rawMsg != null && !rawMsg.isBlank()) {
                        reason = ChatColor.stripColor(rawMsg);
                    }
                }
                profile.setFallenReason(reason);

                // Clear active profile's inventory if keepInventory kept items inside the fallen slot
                if (event.getKeepInventory()) {
                    victim.getInventory().clear();
                    victim.getInventory().setArmorContents(new ItemStack[4]);
                    victim.getInventory().setItemInOffHand(null);
                    profile.setInventoryContents(new ItemStack[36]);
                    profile.setArmorContents(new ItemStack[4]);
                    profile.setOffhandContent(null);
                }

                plugin.getProfileStorage().saveProfile(profile);
                pendingFallenRespawn.put(victim.getUniqueId(), profile.getSlot());

                ClassData cd = plugin.getClassManager().getClass(profile.getClassId());
                String classDisplay = cd != null ? cd.getDisplayName() : profile.getClassId();
                String classPlain = cd != null ? cd.getPlainDisplayName() : profile.getClassId();

                String fallenMsg = msg("hardcore_fallen_player",
                        "<dark_red><bold>[FALLEN]</bold> <red>All lives on Hardcore profile Slot #{slot} ({class}) have run out!");
                fallenMsg = ColorUtils.replaceBoth(fallenMsg, "slot", String.valueOf(profile.getSlot()));
                fallenMsg = ColorUtils.replaceBoth(fallenMsg, "class", classDisplay);
                fallenMsg = ColorUtils.replaceBoth(fallenMsg, "class_plain", classPlain);
                fallenMsg = ColorUtils.replaceBoth(fallenMsg, "level", String.valueOf(profile.getLevel()));
                ColorUtils.sendMessage(victim, fallenMsg);

                String title = ColorUtils.replaceBoth(msg("hardcore_fallen_title", "<dark_red><bold>PROFILE FALLEN</bold>"), "class", classDisplay);
                title = ColorUtils.replaceBoth(title, "class_plain", classPlain);
                String subtitle = ColorUtils.replaceBoth(
                        ColorUtils.replaceBoth(msg("hardcore_fallen_subtitle", "<gray>Class {class} Lv.{level} has fallen permanently"),
                                "class", classDisplay),
                        "level", String.valueOf(profile.getLevel()));
                subtitle = ColorUtils.replaceBoth(subtitle, "class_plain", classPlain);
                ColorUtils.sendTitle(victim, title, subtitle, 10, 80, 20);
                ColorUtils.playSound(victim, msg("hardcore_fallen_sound", "ENTITY_WITHER_DEATH:1.0:0.8"));

                if (gm.isHardcoreBroadcastOnFallen()) {
                    List<String> bcLines = ColorUtils.getMessageLines(plugin.getMessagesConfig(), "hardcore_fallen_broadcast");
                    for (String bcLine : bcLines) {
                        String parsed = ColorUtils.replaceBoth(bcLine, "player", victim.getName());
                        parsed = ColorUtils.replaceBoth(parsed, "class", classDisplay);
                        parsed = ColorUtils.replaceBoth(parsed, "class_plain", classPlain);
                        parsed = ColorUtils.replaceBoth(parsed, "level", String.valueOf(profile.getLevel()));
                        parsed = ColorUtils.replaceBoth(parsed, "slot", String.valueOf(profile.getSlot()));
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            ColorUtils.sendMessage(online, parsed);
                        }
                        ColorUtils.sendMessage(Bukkit.getConsoleSender(), parsed);
                    }
                }
            }
        }
    }

    private void handleHuntedDeathDrops(PlayerDeathEvent event, Player victim, Profile profile, GamemodeManager gm) {
        boolean destroyDrops = profile.isIronman() && gm.isHuntedDestroyDropsWhenIronmanDies();

        if (!event.getKeepInventory()) {
            // Vanilla drops everything in event.getDrops()
            if (destroyDrops && !event.getDrops().isEmpty()) {
                event.getDrops().clear();
                ColorUtils.sendMessage(victim, msg("hunted_ironman_drops_destroyed",
                        "<gray>⚔ Items from the Ironman+Hunted victim were destroyed and cannot be looted!"));
            }
            return;
        }

        // keepInventory == true: roll percentage drop across storage, armor, main hand, and offhand
        PlayerInventory inv = victim.getInventory();
        World world = victim.getWorld();
        Location dropLoc = victim.getLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int heldSlot = inv.getHeldItemSlot();
        int droppedCount = 0;

        // 1. Storage slots (0..35)
        ItemStack[] storage = inv.getStorageContents();
        boolean storageChanged = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack item = storage[i];
            if (item == null || item.getType() == Material.AIR) continue;

            double chance = (i == heldSlot) ? gm.getHuntedDropChanceMainHand() : gm.getHuntedDropChanceInventory();
            if (chance > 0.0 && rng.nextDouble() < chance) {
                ItemStack toDrop = item.clone();
                storage[i] = null;
                storageChanged = true;
                droppedCount++;
                spawnHuntedDrop(world, dropLoc, toDrop, victim, profile, destroyDrops);
            }
        }
        if (storageChanged) {
            inv.setStorageContents(storage);
        }

        // 2. Armor slots
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        double armorChance = gm.getHuntedDropChanceArmor();
        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (item == null || item.getType() == Material.AIR) continue;

            if (armorChance > 0.0 && rng.nextDouble() < armorChance) {
                ItemStack toDrop = item.clone();
                armor[i] = null;
                armorChanged = true;
                droppedCount++;
                spawnHuntedDrop(world, dropLoc, toDrop, victim, profile, destroyDrops);
            }
        }
        if (armorChanged) {
            inv.setArmorContents(armor);
        }

        // 3. Offhand slot
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && offhand.getType() != Material.AIR) {
            double offChance = gm.getHuntedDropChanceOffhand();
            if (offChance > 0.0 && rng.nextDouble() < offChance) {
                ItemStack toDrop = offhand.clone();
                inv.setItemInOffHand(null);
                droppedCount++;
                spawnHuntedDrop(world, dropLoc, toDrop, victim, profile, destroyDrops);
            }
        }

        if (droppedCount > 0) {
            if (destroyDrops) {
                ColorUtils.sendMessage(victim, msg("hunted_ironman_drops_destroyed",
                        "<gray>⚔ Items from the Ironman+Hunted victim were destroyed and cannot be looted!"));
            } else {
                ColorUtils.sendMessage(victim, msg("hunted_death_dropped",
                        "<red>⚔ As a Hunted player, some items from your inventory were dropped upon death!"));
            }
        }
    }

    private void spawnHuntedDrop(World world, Location loc, ItemStack item, Player victim, Profile profile, boolean destroyDrops) {
        if (destroyDrops) {
            return;
        }
        Item dropped = world.dropItemNaturally(loc, item);
        // Tag owner so an Ironman killer (or Ironman victim if somehow not destroyed) obeys Ironman pickup rules
        tagDroppedItem(dropped, victim.getUniqueId(), profile.isIronman());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Integer fallenSlot = pendingFallenRespawn.remove(player.getUniqueId());
        if (fallenSlot == null) return;

        // Clear any pending fallen state 1 tick after respawn completes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getProfileManager().handleHardcoreFallenRespawn(player, fallenSlot);
            }
        }, 1L);
    }
}

