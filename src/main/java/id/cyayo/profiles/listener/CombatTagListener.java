package id.cyayo.profiles.listener;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.model.PlayerProfileHolder;
import id.cyayo.profiles.util.ColorUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class CombatTagListener implements Listener {

    private final CyayoProfiles plugin;

    public CombatTagListener(CyayoProfiles plugin) {
        this.plugin = plugin;
    }

    private boolean isNoProfileProtected(Player player) {
        if (player == null) return false;
        if (!plugin.getConfig().getBoolean("first_join.protect_no_profile.enabled", true)) return false;
        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
        return holder != null && !holder.hasActiveProfile();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNoProfileDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && isNoProfileProtected(victim)) {
            if (plugin.getConfig().getBoolean("first_join.protect_no_profile.prevent_damage", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNoProfileAttack(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;

        if (attacker != null && isNoProfileProtected(attacker)) {
            if (plugin.getConfig().getBoolean("first_join.protect_no_profile.prevent_damage", true)) {
                event.setCancelled(true);
                ColorUtils.sendActionBar(attacker, plugin.getMessagesConfig().getString("no_profile_warning", "none"));
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNoProfileDrop(PlayerDropItemEvent event) {
        if (isNoProfileProtected(event.getPlayer()) &&
            plugin.getConfig().getBoolean("first_join.protect_no_profile.prevent_item_drop", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNoProfilePickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isNoProfileProtected(player) &&
            plugin.getConfig().getBoolean("first_join.protect_no_profile.prevent_item_pickup", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onNoProfileInteract(PlayerInteractEvent event) {
        if (isNoProfileProtected(event.getPlayer()) &&
            plugin.getConfig().getBoolean("first_join.protect_no_profile.prevent_interact", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("security.combat_tag.enabled", true)) return;

        Player attacker = null;
        Entity damager = event.getDamager();
        if (damager instanceof Player p) attacker = p;
        else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;

        Entity victimEntity = event.getEntity();
        Player victim = victimEntity instanceof Player p ? p : null;

        boolean isPvp = (attacker != null && victim != null);
        boolean tagPvp = config.getBoolean("security.combat_tag.tag_on_pvp", true);
        boolean tagPve = config.getBoolean("security.combat_tag.tag_on_pve", true);

        if (isPvp && !tagPvp) return;
        if (!isPvp && !tagPve) return;

        if (attacker != null) {
            PlayerProfileHolder holder = plugin.getProfileManager().getHolder(attacker);
            if (holder != null && holder.hasActiveProfile()) {
                holder.tagCombat();
            }
        }
        if (victim != null) {
            PlayerProfileHolder holder = plugin.getProfileManager().getHolder(victim);
            if (holder != null && holder.hasActiveProfile()) {
                holder.tagCombat();
            }
        }
    }
}

