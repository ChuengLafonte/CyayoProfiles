package id.cyayo.profiles.gui;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.manager.GamemodeManager;
import id.cyayo.profiles.model.PlayerProfileHolder;
import id.cyayo.profiles.model.Profile;
import id.cyayo.profiles.util.ColorUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class GUIListener implements Listener {

    private final CyayoProfiles plugin;

    public GUIListener(CyayoProfiles plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ProfileHolder profileHolder)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int clickedSlot = event.getSlot();
        String menuType = profileHolder.getMenuType();
        ProfileGUIManager guiManager = plugin.getGuiManager();

        if ("PROFILES".equals(menuType)) {
            handleProfilesClick(player, clickedSlot, event.getClick(), guiManager);
        } else if ("CLASS_SELECT".equals(menuType)) {
            handleClassSelectClick(player, clickedSlot, profileHolder.getTargetSlot(), guiManager);
        } else if ("GAMEMODE_SELECT".equals(menuType)) {
            handleGamemodeSelectClick(player, clickedSlot, profileHolder.getTargetSlot(), guiManager);
        } else if ("CONFIRM_DELETE".equals(menuType)) {
            handleConfirmDeleteClick(player, clickedSlot, profileHolder.getTargetSlot(), guiManager);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ProfileHolder) {
            event.setCancelled(true);
        }
    }

    private void handleProfilesClick(Player player, int clickedSlot, ClickType clickType, ProfileGUIManager guiManager) {
        FileConfiguration config = guiManager.getProfilesGuiConfig();

        ConfigurationSection closeSec = config.getConfigurationSection("items.close_button");
        if (closeSec != null && guiManager.getSlotsList(closeSec).contains(clickedSlot)) {
            player.closeInventory();
            return;
        }
        if (closeSec == null && clickedSlot == 40) {
            player.closeInventory();
            return;
        }

        ConfigurationSection slotLocs = config.getConfigurationSection("profile_slot_locations");
        if (slotLocs == null) return;

        int targetProfileSlot = -1;
        for (String key : slotLocs.getKeys(false)) {
            if (slotLocs.getInt(key) == clickedSlot) {
                try {
                    targetProfileSlot = Integer.parseInt(key);
                } catch (NumberFormatException ignored) {
                }
                break;
            }
        }

        if (targetProfileSlot <= 0) return;

        int maxSlots = plugin.getProfileManager().getMaxSlots(player);
        if (targetProfileSlot > maxSlots) {
            ColorUtils.sendMessage(player, plugin.getMessagesConfig().getString("no_permission"));
            return;
        }

        PlayerProfileHolder playerHolder = plugin.getProfileManager().getHolder(player);
        if (playerHolder == null) return;

        if (!playerHolder.hasProfile(targetProfileSlot)) {
            // Empty slot -> Open class selection
            String createPerm = plugin.getConfig().getString("permissions.create", "cyayoprofiles.create");
            if (!player.hasPermission(createPerm)) {
                ColorUtils.sendMessage(player, plugin.getMessagesConfig().getString("no_permission"));
                return;
            }
            guiManager.openClassSelectMenu(player, targetProfileSlot);
        } else {
            // Existing profile
            if (clickType == ClickType.SHIFT_RIGHT) {
                // Shift + Right Click -> Confirm Delete
                if (playerHolder.getActiveSlot() == targetProfileSlot) {
                    ColorUtils.sendMessage(player, plugin.getMessagesConfig().getString("profile_delete_active_error", "<red>You cannot delete the active profile!"));
                    return;
                }
                String deletePerm = plugin.getConfig().getString("permissions.delete", "cyayoprofiles.delete");
                if (!player.hasPermission(deletePerm)) {
                    ColorUtils.sendMessage(player, plugin.getMessagesConfig().getString("no_permission"));
                    return;
                }
                guiManager.openConfirmDeleteMenu(player, targetProfileSlot);
            } else if (clickType == ClickType.LEFT) {
                // Left Click -> Switch profile
                plugin.getProfileManager().switchProfile(player, targetProfileSlot);
            }
        }
    }

    private void handleClassSelectClick(Player player, int clickedSlot, int targetProfileSlot, ProfileGUIManager guiManager) {
        FileConfiguration config = guiManager.getClassSelectGuiConfig();

        ConfigurationSection backSec = config.getConfigurationSection("back_button");
        if (backSec != null && guiManager.getSlotsList(backSec).contains(clickedSlot)) {
            guiManager.openProfilesMenu(player);
            return;
        }
        if (backSec == null && clickedSlot == 31) {
            guiManager.openProfilesMenu(player);
            return;
        }

        ConfigurationSection classSection = classSelectGuiConfigOrNull(config, "classes");
        if (classSection != null) {
            for (String classId : classSection.getKeys(false)) {
                ConfigurationSection itemSec = classSection.getConfigurationSection(classId);
                if (itemSec != null && guiManager.getSlotsList(itemSec).contains(clickedSlot)) {
                    attemptSelectClass(player, targetProfileSlot, classId, guiManager);
                    return;
                }
            }
        }

        ConfigurationSection classSlots = config.getConfigurationSection("class_slots");
        if (classSlots == null) return;

        for (String classId : classSlots.getKeys(false)) {
            if (classSlots.getInt(classId) == clickedSlot) {
                attemptSelectClass(player, targetProfileSlot, classId, guiManager);
                return;
            }
        }
    }

    private void attemptSelectClass(Player player, int targetProfileSlot, String classId, ProfileGUIManager guiManager) {
        var classData = plugin.getClassManager().getClass(classId);
        if (classData == null) return;

        if (!classData.hasPermission(player)) {
            if (!classData.isHideWithoutPermission()) {
                plugin.getProfileManager().sendClassLockedMessage(player, classData, true);
            }
            return;
        }

        var session = plugin.getGamemodeManager().startCreationSession(player.getUniqueId(), targetProfileSlot, classId);
        guiManager.openGamemodeSelectMenu(player, session);
    }

    private ConfigurationSection classSelectGuiConfigOrNull(FileConfiguration cfg, String path) {
        return cfg != null ? cfg.getConfigurationSection(path) : null;
    }

    private void handleGamemodeSelectClick(Player player, int clickedSlot, int targetProfileSlot, ProfileGUIManager guiManager) {
        GamemodeManager gm = plugin.getGamemodeManager();
        if (gm == null) return;

        GamemodeManager.CreationSession session = gm.getCreationSession(player.getUniqueId());
        if (session == null || session.getTargetSlot() != targetProfileSlot) {
            guiManager.openClassSelectMenu(player, targetProfileSlot);
            return;
        }

        FileConfiguration config = guiManager.getGamemodeSelectGuiConfig();

        ConfigurationSection backSec = config.getConfigurationSection("back_button");
        if (backSec != null && guiManager.getSlotsList(backSec).contains(clickedSlot)) {
            gm.clearCreationSession(player.getUniqueId());
            guiManager.openClassSelectMenu(player, targetProfileSlot);
            return;
        }

        ConfigurationSection confirmSec = config.getConfigurationSection("confirm_button");
        if (confirmSec != null && guiManager.getSlotsList(confirmSec).contains(clickedSlot)) {
            player.closeInventory();
            plugin.getProfileManager().createProfile(
                    player,
                    session.getTargetSlot(),
                    session.getClassId(),
                    session.getSelectedModes(),
                    session.getHardcoreLives());
            return;
        }

        // Hardcore lives -1 / +1 buttons
        if (session.getSelectedModes().contains("hardcore") && gm.isHardcorePlayerSelectableLives()) {
            ConfigurationSection hcSec = config.getConfigurationSection("hardcore_lives_controls");
            if (hcSec != null) {
                ConfigurationSection decSec = hcSec.getConfigurationSection("decrease_lives");
                if (decSec != null && guiManager.getSlotsList(decSec).contains(clickedSlot)) {
                    session.setHardcoreLives(gm.clampLives(session.getHardcoreLives() - 1));
                    ColorUtils.playSound(player, plugin.getMessagesConfig().getString("gamemode_mode_selected_sound", "UI_BUTTON_CLICK:1.0:1.2"));
                    guiManager.openGamemodeSelectMenu(player, session);
                    return;
                }
                ConfigurationSection incSec = hcSec.getConfigurationSection("increase_lives");
                if (incSec != null && guiManager.getSlotsList(incSec).contains(clickedSlot)) {
                    session.setHardcoreLives(gm.clampLives(session.getHardcoreLives() + 1));
                    ColorUtils.playSound(player, plugin.getMessagesConfig().getString("gamemode_mode_selected_sound", "UI_BUTTON_CLICK:1.0:1.2"));
                    guiManager.openGamemodeSelectMenu(player, session);
                    return;
                }
            }
        }

        // Toggleable Challenge Modes
        ConfigurationSection modesSec = config.getConfigurationSection("modes");
        if (modesSec != null) {
            for (String modeId : modesSec.getKeys(false)) {
                ConfigurationSection modeGuiSec = modesSec.getConfigurationSection(modeId);
                if (modeGuiSec != null && guiManager.getSlotsList(modeGuiSec).contains(clickedSlot)) {
                    GamemodeManager.ToggleResult result = gm.toggleModeInSession(session, modeId);
                    if (result == GamemodeManager.ToggleResult.MAX_REACHED) {
                        String msg = ColorUtils.replaceBoth(
                                plugin.getMessagesConfig().getString("gamemode_max_reached",
                                        "<red>You can only select up to <yellow>{max} <red>challenge modes at once!"),
                                "max", String.valueOf(gm.getMaxSelectableModes()));
                        ColorUtils.sendMessage(player, msg);
                    } else if (result == GamemodeManager.ToggleResult.ADDED || result == GamemodeManager.ToggleResult.REMOVED) {
                        ColorUtils.playSound(player, plugin.getMessagesConfig().getString("gamemode_mode_selected_sound", "UI_BUTTON_CLICK:1.0:1.2"));
                        guiManager.openGamemodeSelectMenu(player, session);
                    }
                    return;
                }
            }
        }
    }

    private void handleConfirmDeleteClick(Player player, int clickedSlot, int targetProfileSlot, ProfileGUIManager guiManager) {
        FileConfiguration config = guiManager.getConfirmDeleteGuiConfig();

        ConfigurationSection cancelSec = config.getConfigurationSection("cancel_button");
        if (cancelSec != null && guiManager.getSlotsList(cancelSec).contains(clickedSlot)) {
            guiManager.openProfilesMenu(player);
            return;
        }

        ConfigurationSection confirmSec = config.getConfigurationSection("confirm_button");
        if (confirmSec != null && guiManager.getSlotsList(confirmSec).contains(clickedSlot)) {
            plugin.getProfileManager().deleteProfile(player, targetProfileSlot);
            guiManager.openProfilesMenu(player);
        }
    }
}

