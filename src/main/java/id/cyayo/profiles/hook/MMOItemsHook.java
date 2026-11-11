package id.cyayo.profiles.hook;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.manager.ClassManager;
import id.cyayo.profiles.model.ClassData;
import id.cyayo.profiles.model.PlayerProfileHolder;
import id.cyayo.profiles.model.Profile;
import id.cyayo.profiles.util.ColorUtils;
import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.player.modifier.ModifierType;
import io.lumine.mythic.lib.rpg.ClassModule;
import io.lumine.mythic.lib.rpg.LevelModule;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.player.PlayerData;
import net.Indyuce.mmoitems.api.player.RPGPlayer;
import net.Indyuce.mmoitems.stat.RequiredClass;
import net.Indyuce.mmoitems.stat.RequiredLevel;
import net.Indyuce.mmoitems.stat.type.ItemRestriction;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

public class MMOItemsHook {

    private final CyayoProfiles plugin;
    private boolean enabled = false;

    public MMOItemsHook(CyayoProfiles plugin) {
        this.plugin = plugin;
    }

    public void registerHook() {
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOItems") ||
            !Bukkit.getPluginManager().isPluginEnabled("MythicLib")) {
            return;
        }

        try {
            // 1. Hook MythicLib ClassModule
            ClassModule customClassModule = new ClassModule() {
                @Override
                public String getClass(MMOPlayerData mmoPlayerData) {
                    Player player = mmoPlayerData.getPlayer();
                    PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
                    if (holder != null && holder.getActiveProfile() != null) {
                        return holder.getActiveProfile().getClassId();
                    }
                    return "None";
                }
            };

            // 2. Hook MythicLib LevelModule
            LevelModule customLevelModule = new LevelModule() {
                @Override
                public int getLevel(MMOPlayerData mmoPlayerData) {
                    Player player = mmoPlayerData.getPlayer();
                    PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
                    if (holder != null && holder.getActiveProfile() != null) {
                        return holder.getActiveProfile().getLevel();
                    }
                    return 1;
                }
            };

            // Inject modules into MythicLib
            try {
                Field classField = MythicLib.class.getDeclaredField("classModule");
                classField.setAccessible(true);
                classField.set(MythicLib.plugin, customClassModule);

                Field levelField = MythicLib.class.getDeclaredField("levelModule");
                levelField.setAccessible(true);
                levelField.set(MythicLib.plugin, customLevelModule);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not inject custom ClassModule/LevelModule into MythicLib: " + e.getMessage());
            }

            // 3. Register unified ItemRestriction into MMOItems (Class + Dual-Weapon + Level)
            try {
                Field restrictionsField = MMOItems.plugin.getStats().getClass().getDeclaredField("itemRestrictions");
                restrictionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<ItemRestriction> list = (List<ItemRestriction>) restrictionsField.get(MMOItems.plugin.getStats());

                // Remove standard class & level checks so our unified dual-class, level & attribute check takes precedence
                list.removeIf(r -> r instanceof RequiredClass || r instanceof RequiredLevel);

                list.add(new ItemRestriction() {
                    @Override
                    public boolean canUse(RPGPlayer rpgPlayer, NBTItem nbtItem, boolean message) {
                        Player player = rpgPlayer.getPlayer();
                        if (player == null) return true;

                        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
                        if (holder == null) return true;

                        Profile profile = holder.getActiveProfile();
                        if (profile == null) {
                            if (message && plugin.getConfig().getBoolean("first_join.protect_no_profile.enabled", true)) {
                                int cd = plugin.getConfig().getInt("item_restrictions.actionbar_cooldown_seconds", 2);
                                if (holder.canSendActionbarWarning(cd)) {
                                    ColorUtils.sendActionBar(player, plugin.getMessagesConfig().getString("no_profile_warning", "none"));
                                }
                            }
                            return false;
                        }

                        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
                        if (classData == null) return true;

                        ItemStack item = nbtItem.toItem();
                        ClassManager.RestrictionResult result = plugin.getClassManager().checkRestriction(classData, profile, item);

                        if (!result.allowed && result.type == ClassManager.RestrictionType.LEVEL &&
                            player.hasPermission("mmoitems.bypass.level")) {
                            return true;
                        }

                        if (!result.allowed && message) {
                            int cd = plugin.getConfig().getInt("item_restrictions.actionbar_cooldown_seconds", 2);
                            if (holder.canSendActionbarWarning(cd)) {
                                if (result.type == ClassManager.RestrictionType.CLASS) {
                                    String msg = plugin.getMessagesConfig().getString("weapon_restricted_actionbar", "none");
                                    msg = ColorUtils.replaceBoth(msg, "required", result.requiredValue);
                                    msg = ColorUtils.replaceBoth(msg, "class", classData.getDisplayName());
                                    msg = ColorUtils.replaceBoth(msg, "class_plain", classData.getPlainDisplayName());
                                    ColorUtils.sendActionBar(player, msg);

                                    ColorUtils.playSound(player, plugin.getMessagesConfig().getString("weapon_restricted_sound", "none"));
                                } else if (result.type == ClassManager.RestrictionType.LEVEL) {
                                    String msg = plugin.getMessagesConfig().getString("level_restricted_actionbar", "none");
                                    msg = ColorUtils.replaceBoth(msg, "required", result.requiredValue);
                                    msg = ColorUtils.replaceBoth(msg, "class", classData.getDisplayName());
                                    msg = ColorUtils.replaceBoth(msg, "class_plain", classData.getPlainDisplayName());
                                    ColorUtils.sendActionBar(player, msg);

                                    ColorUtils.playSound(player, plugin.getMessagesConfig().getString("level_restricted_sound", "none"));
                                }
                            }
                        }

                        return result.allowed;
                    }

                    @Override
                    public boolean isDynamic() {
                        return true;
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Could not register custom ItemRestriction into MMOItems: " + e.getMessage());
            }

            this.enabled = true;
            plugin.getLogger().info("Successfully hooked into MMOItems & MythicLib!");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to hook into MMOItems: " + t.getMessage());
        }
    }

    private static final String MODE_MODIFIER_PREFIX = "CyayoProfiles_Mode_";

    public void removeGamemodeModifiers(Player player) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("MythicLib")) return;
        try {
            if (!MMOPlayerData.has(player)) return;
            MMOPlayerData data = MMOPlayerData.get(player);
            StatMap statMap = data.getStatMap();
            for (StatInstance instance : statMap.getInstances()) {
                instance.removeIf(key -> key.startsWith(MODE_MODIFIER_PREFIX) || key.startsWith("CyayoProfiles_Attr_"));
            }
        } catch (Throwable ignored) {
        }
    }

    public void applyGamemodeModifiers(Player player, Profile profile) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("MythicLib")) return;
        try {
            if (!MMOPlayerData.has(player)) return;
            MMOPlayerData data = MMOPlayerData.get(player);
            StatMap statMap = data.getStatMap();

            for (StatInstance instance : statMap.getInstances()) {
                instance.removeIf(key -> key.startsWith(MODE_MODIFIER_PREFIX) || key.startsWith("CyayoProfiles_Attr_"));
            }

            if (profile == null || plugin.getGamemodeManager() == null) return;

            Map<String, Double> modeBonuses = plugin.getGamemodeManager().getMythicStatBonuses(profile);
            for (Map.Entry<String, Double> entry : modeBonuses.entrySet()) {
                String statName = entry.getKey().toUpperCase();
                double value = entry.getValue();
                if (value != 0.0) {
                    StatModifier mod = new StatModifier(MODE_MODIFIER_PREFIX + statName, statName, value, ModifierType.FLAT);
                    mod.register(data);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void updateInventory(Player player) {
        if (!enabled || player == null) return;
        try {
            PlayerData data = PlayerData.getOrNull(player);
            if (data != null) {
                data.updateInventory();
            }
        } catch (Throwable ignored) {
        }
    }

    public ItemStack buildMMOItem(String typeStr, String idStr, int amount) {
        if (typeStr == null || idStr == null || !Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            return null;
        }
        try {
            String cleanType = typeStr.trim();
            String cleanId = idStr.trim();
            net.Indyuce.mmoitems.api.Type type = MMOItems.plugin.getTypes().get(cleanType.toUpperCase(java.util.Locale.ROOT));
            if (type == null) {
                type = MMOItems.plugin.getTypes().get(cleanType);
            }
            if (type == null) {
                plugin.getLogger().warning("MMOItems type not found for starter kit: " + cleanType);
                return null;
            }

            ItemStack item = MMOItems.plugin.getItem(type, cleanId.toUpperCase(java.util.Locale.ROOT));
            if (item == null) {
                item = MMOItems.plugin.getItem(type, cleanId);
            }
            if (item == null) {
                plugin.getLogger().warning("MMOItems item not found for starter kit: " + type.getId() + ":" + cleanId);
                return null;
            }

            ItemStack result = item.clone();
            if (amount > 0) {
                result.setAmount(Math.min(amount, Math.max(1, result.getMaxStackSize())));
            }
            return result;
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to build MMOItem starter kit (" + typeStr + ":" + idStr + "): " + t.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
