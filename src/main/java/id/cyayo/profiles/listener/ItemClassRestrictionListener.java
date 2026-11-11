package id.cyayo.profiles.listener;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.manager.ClassManager;
import id.cyayo.profiles.model.ClassData;
import id.cyayo.profiles.model.PlayerProfileHolder;
import id.cyayo.profiles.model.Profile;
import id.cyayo.profiles.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ItemClassRestrictionListener implements Listener {

    private final CyayoProfiles plugin;

    public ItemClassRestrictionListener(CyayoProfiles plugin) {
        this.plugin = plugin;
    }

    private boolean isRestrictionsEnabled() {
        return plugin.getConfig().getBoolean("item_restrictions.enabled", true);
    }

    private boolean isArmor(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
               name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") ||
               mat == Material.ELYTRA || mat == Material.TURTLE_HELMET;
    }

    private boolean isAllowed(Player player, ClassManager.RestrictionResult res) {
        if (res.allowed) return true;
        return res.type == ClassManager.RestrictionType.LEVEL && player.hasPermission("mmoitems.bypass.level");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!isRestrictionsEnabled()) return;
        if (!plugin.getConfig().getBoolean("item_restrictions.check_weapons", true)) return;

        Player player = null;
        Entity damager = event.getDamager();

        if (damager instanceof Player p) {
            player = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            player = p;
        }

        if (player == null) return;

        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
        if (holder == null) return;

        Profile profile = holder.getActiveProfile();
        if (profile == null) return;

        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
        if (classData == null) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR || isArmor(weapon.getType())) return;

        ClassManager.RestrictionResult res = plugin.getClassManager().checkRestriction(classData, profile, weapon);
        if (!isAllowed(player, res)) {
            event.setCancelled(true);
            sendWeaponWarning(player, holder, res, classData);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!isRestrictionsEnabled()) return;

        Action action = event.getAction();
        if (action == Action.PHYSICAL) return;

        Player player = event.getPlayer();
        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
        if (holder == null) return;

        Profile profile = holder.getActiveProfile();
        if (profile == null) return;

        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
        if (classData == null) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        if (event.getHand() == EquipmentSlot.OFF_HAND &&
            !plugin.getConfig().getBoolean("item_restrictions.check_offhand", true)) {
            return;
        }

        // Izinkan armor tetap dipakai lewat klik kanan, tapi beri tahu bahwa atributnya tidak aktif
        if (isArmor(item.getType())) {
            if (plugin.getConfig().getBoolean("item_restrictions.check_armor", true)) {
                ClassManager.RestrictionResult res = plugin.getClassManager().checkRestriction(classData, profile, item);
                if (!isAllowed(player, res)) {
                    sendArmorWarning(player, holder, res, classData);
                    if (plugin.getMmoItemsHook() != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> plugin.getMmoItemsHook().updateInventory(player));
                    }
                }
            }
            return;
        }

        if (!plugin.getConfig().getBoolean("item_restrictions.check_weapons", true)) return;

        ClassManager.RestrictionResult res = plugin.getClassManager().checkRestriction(classData, profile, item);
        if (!isAllowed(player, res)) {
            event.setCancelled(true);
            sendWeaponWarning(player, holder, res, classData);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorEquipClick(InventoryClickEvent event) {
        if (!isRestrictionsEnabled()) return;
        if (!plugin.getConfig().getBoolean("item_restrictions.check_armor", true)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getSlotType() == InventoryType.SlotType.ARMOR || event.isShiftClick()) {
            PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
            if (holder == null) return;
            Profile profile = holder.getActiveProfile();
            if (profile == null) return;
            ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
            if (classData == null) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (plugin.getMmoItemsHook() != null) {
                    plugin.getMmoItemsHook().updateInventory(player);
                }
                for (ItemStack armor : player.getInventory().getArmorContents()) {
                    if (armor != null && armor.getType() != Material.AIR) {
                        ClassManager.RestrictionResult res = plugin.getClassManager().checkRestriction(classData, profile, armor);
                        if (!isAllowed(player, res)) {
                            sendArmorWarning(player, holder, res, classData);
                            break;
                        }
                    }
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!isRestrictionsEnabled()) return;
        if (!plugin.getConfig().getBoolean("item_restrictions.check_weapons", true)) return;

        if (!(event.getEntity() instanceof Player player)) return;

        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
        if (holder == null) return;

        Profile profile = holder.getActiveProfile();
        if (profile == null) return;

        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
        if (classData == null) return;

        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() == Material.AIR) return;

        ClassManager.RestrictionResult res = plugin.getClassManager().checkRestriction(classData, profile, bow);
        if (!isAllowed(player, res)) {
            event.setCancelled(true);
            sendWeaponWarning(player, holder, res, classData);
        }
    }

    private void sendWeaponWarning(Player player, PlayerProfileHolder holder, ClassManager.RestrictionResult res, ClassData classData) {
        int cd = plugin.getConfig().getInt("item_restrictions.actionbar_cooldown_seconds", 2);
        if (!holder.canSendActionbarWarning(cd)) return;

        FileConfiguration messages = plugin.getMessagesConfig();
        if (res.type == ClassManager.RestrictionType.LEVEL) {
            String msg = messages.getString("level_restricted_actionbar", "none");
            msg = ColorUtils.replaceBoth(msg, "required", res.requiredValue);
            msg = ColorUtils.replaceBoth(msg, "class", classData.getDisplayName());
            msg = ColorUtils.replaceBoth(msg, "class_plain", classData.getPlainDisplayName());
            ColorUtils.sendActionBar(player, msg);

            ColorUtils.playSound(player, messages.getString("level_restricted_sound", "none"));
        } else {
            String msg = messages.getString("weapon_restricted_actionbar", "none");
            msg = ColorUtils.replaceBoth(msg, "required", res.requiredValue);
            msg = ColorUtils.replaceBoth(msg, "class", classData.getDisplayName());
            msg = ColorUtils.replaceBoth(msg, "class_plain", classData.getPlainDisplayName());
            ColorUtils.sendActionBar(player, msg);

            ColorUtils.playSound(player, messages.getString("weapon_restricted_sound", "none"));
        }
    }

    private void sendArmorWarning(Player player, PlayerProfileHolder holder, ClassManager.RestrictionResult res, ClassData classData) {
        int cd = plugin.getConfig().getInt("item_restrictions.actionbar_cooldown_seconds", 2);
        if (!holder.canSendActionbarWarning(cd)) return;

        FileConfiguration messages = plugin.getMessagesConfig();
        String reqDisplay;
        if (res.type == ClassManager.RestrictionType.LEVEL) {
            reqDisplay = "Level " + res.requiredValue;
        } else {
            reqDisplay = res.requiredValue;
        }

        String msg = messages.getString("armor_restricted_actionbar", "none");
        msg = ColorUtils.replaceBoth(msg, "required", reqDisplay);
        msg = ColorUtils.replaceBoth(msg, "class", classData.getDisplayName());
        msg = ColorUtils.replaceBoth(msg, "class_plain", classData.getPlainDisplayName());
        ColorUtils.sendActionBar(player, msg);

        ColorUtils.playSound(player, messages.getString("armor_restricted_sound", "none"));
    }
}
