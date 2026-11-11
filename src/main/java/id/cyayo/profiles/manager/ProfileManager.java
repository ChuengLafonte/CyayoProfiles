package id.cyayo.profiles.manager;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.api.event.ProfileCreateEvent;
import id.cyayo.profiles.api.event.ProfileDeleteEvent;
import id.cyayo.profiles.api.event.ProfileLoadEvent;
import id.cyayo.profiles.api.event.ProfileSwitchEvent;
import id.cyayo.profiles.model.ClassData;
import id.cyayo.profiles.model.PlayerProfileHolder;
import id.cyayo.profiles.model.Profile;
import id.cyayo.profiles.storage.ProfileStorage;
import id.cyayo.profiles.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileManager {

    private final CyayoProfiles plugin;
    private final ProfileStorage storage;
    private final Map<UUID, PlayerProfileHolder> holders = new ConcurrentHashMap<>();
    private final Map<UUID, Double> cachedGlobalBalances = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> vaultOwnerSlots = new ConcurrentHashMap<>();

    private static Attribute MAX_HEALTH_ATTR;
    static {
        try {
            MAX_HEALTH_ATTR = Attribute.valueOf("GENERIC_MAX_HEALTH");
        } catch (IllegalArgumentException e) {
            try {
                MAX_HEALTH_ATTR = Attribute.valueOf("MAX_HEALTH");
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public ProfileManager(CyayoProfiles plugin, ProfileStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public PlayerProfileHolder getHolder(UUID uuid) {
        return holders.get(uuid);
    }

    public PlayerProfileHolder getHolder(Player player) {
        return player != null ? getHolder(player.getUniqueId()) : null;
    }

    public Profile getActiveProfile(Player player) {
        PlayerProfileHolder holder = getHolder(player);
        return holder != null ? holder.getActiveProfile() : null;
    }

    private boolean isSeparateVaultEnabled() {
        return plugin.getVaultHook() != null && plugin.getVaultHook().isEnabled()
                && plugin.getGamemodeManager() != null && plugin.getGamemodeManager().isIronmanSeparateVaultEconomy();
    }

    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();

        storage.awaitPendingWrites(uuid).thenRun(() -> {
            storage.getActiveSlot(uuid).thenAccept(activeSlot -> {
                storage.loadAllProfiles(uuid).thenAccept(profiles -> {
                    storage.getGlobalVaultState(uuid).thenAccept(vaultState -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;

                        PlayerProfileHolder holder = new PlayerProfileHolder(uuid, activeSlot);
                        for (Map.Entry<Integer, Profile> entry : profiles.entrySet()) {
                            holder.setProfile(entry.getKey(), entry.getValue());
                        }

                        holders.put(uuid, holder);

                        double globalBal = vaultState.globalBalance;
                        if (isSeparateVaultEnabled()) {
                            double liveBal = plugin.getVaultHook().getBalance(player);
                            if (vaultState.vaultOwnerSlot <= 0) {
                                globalBal = liveBal;
                            } else {
                                Profile prevIron = holder.getProfile(vaultState.vaultOwnerSlot);
                                if (prevIron != null && prevIron.isIronman()) {
                                    prevIron.setIronmanBalance(liveBal);
                                    storage.saveProfile(prevIron);
                                }
                            }
                            cachedGlobalBalances.put(uuid, globalBal);
                            vaultOwnerSlots.put(uuid, vaultState.vaultOwnerSlot);
                        }

                        // Kasus 1: Pemain belum memiliki profil sama sekali saat pertama join
                        if (holder.getProfiles().isEmpty()) {
                            holder.setActiveSlot(0);

                            if (isSeparateVaultEnabled() && vaultState.vaultOwnerSlot != 0) {
                                plugin.getVaultHook().setBalance(player, globalBal);
                                vaultOwnerSlots.put(uuid, 0);
                                storage.saveGlobalVaultState(uuid, globalBal, 0);
                            }

                            clearPlayerInventoryAndEffects(player);

                            if (plugin.getMmoItemsHook() != null) {
                                plugin.getMmoItemsHook().removeGamemodeModifiers(player);
                            }

                            dispatchNoProfileCommands(player);
                            Bukkit.getPluginManager().callEvent(new ProfileLoadEvent(player, null));
                            return;
                        }

                        // Kasus 2: Pemain sudah memiliki profil -> muat profil terakhir yang hidup
                        Profile activeProfile = holder.getActiveProfile();
                        if (activeProfile == null || activeProfile.isDead()) {
                            Profile aliveFallback = null;
                            for (Profile p : holder.getProfiles().values()) {
                                if (!p.isDead()) {
                                    aliveFallback = p;
                                    break;
                                }
                            }
                            if (aliveFallback != null) {
                                activeProfile = aliveFallback;
                                holder.setActiveSlot(activeProfile.getSlot());
                                storage.setActiveSlot(uuid, activeProfile.getSlot());
                            } else {
                                // Semua profil berstatus GUGUR (Hardcore habis nyawa)
                                holder.setActiveSlot(0);
                                storage.setActiveSlot(uuid, 0);

                                if (isSeparateVaultEnabled() && vaultState.vaultOwnerSlot != 0) {
                                    plugin.getVaultHook().setBalance(player, globalBal);
                                    vaultOwnerSlots.put(uuid, 0);
                                    storage.saveGlobalVaultState(uuid, globalBal, 0);
                                }

                                clearPlayerInventoryAndEffects(player);
                                if (plugin.getMmoItemsHook() != null) {
                                    plugin.getMmoItemsHook().removeGamemodeModifiers(player);
                                }

                                dispatchNoProfileCommands(player);
                                ColorUtils.sendMessage(player, plugin.getMessagesConfig().getString("profile_fallen_locked",
                                        "<dark_red>All of your Hardcore profiles have fallen! Use <yellow>/profile <dark_red>to create a new character."));
                                Bukkit.getPluginManager().callEvent(new ProfileLoadEvent(player, null));
                                return;
                            }
                        }

                        if (isSeparateVaultEnabled()) {
                            if (activeProfile.isIronman()) {
                                if (vaultState.vaultOwnerSlot != activeProfile.getSlot()) {
                                    plugin.getVaultHook().setBalance(player, activeProfile.getIronmanBalance());
                                }
                                vaultOwnerSlots.put(uuid, activeProfile.getSlot());
                                storage.saveGlobalVaultState(uuid, globalBal, activeProfile.getSlot());
                            } else {
                                if (vaultState.vaultOwnerSlot != 0) {
                                    plugin.getVaultHook().setBalance(player, globalBal);
                                }
                                vaultOwnerSlots.put(uuid, 0);
                                storage.saveGlobalVaultState(uuid, globalBal, 0);
                            }
                        }

                        applyProfileToPlayer(player, activeProfile, false);
                        Bukkit.getPluginManager().callEvent(new ProfileLoadEvent(player, activeProfile));
                    });
                });
            });
            });
        });
    }

    private void clearPlayerInventoryAndEffects(Player player) {
        if (plugin.getConfig().getBoolean("first_join.clear_inventory", true)) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void dispatchNoProfileCommands(Player player) {
        List<String> triggerCmds = plugin.getConfig().getStringList("first_join.no_profile_commands");
        if (triggerCmds != null && !triggerCmds.isEmpty()) {
            for (String cmd : triggerCmds) {
                String parsed = ColorUtils.replaceBoth(cmd, "player", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
    }

    public void savePlayerData(Player player) {
        if (player == null) return;
        PlayerProfileHolder holder = holders.get(player.getUniqueId());
        if (holder == null) return;

        Profile active = holder.getActiveProfile();
        if (active != null) {
            savePlayerStateToProfile(player, active);
            saveVaultStateForProfile(player, active, false);
            storage.saveProfile(active);
        } else if (isSeparateVaultEnabled() && vaultOwnerSlots.getOrDefault(player.getUniqueId(), 0) == 0) {
            double liveBal = plugin.getVaultHook().getBalance(player);
            cachedGlobalBalances.put(player.getUniqueId(), liveBal);
            storage.saveGlobalVaultState(player.getUniqueId(), liveBal, 0);
        }
    }

    public void savePlayerDataSync(Player player) {
        if (player == null) return;
        saveAndRestoreVaultOnDisconnect(player, true);
    }

    private void saveAndRestoreVaultOnDisconnect(Player player, boolean sync) {
        PlayerProfileHolder holder = holders.get(player.getUniqueId());
        if (holder == null) return;

        boolean saveOnQuit = sync || plugin.getConfig().getBoolean("auto_save.save_on_quit", true);
        UUID uuid = player.getUniqueId();
        Profile active = holder.getActiveProfile();
        if (active != null) {
            if (saveOnQuit) {
                savePlayerStateToProfile(player, active);
            }
            if (isSeparateVaultEnabled()) {
                double liveBal = plugin.getVaultHook().getBalance(player);
                if (active.isIronman()) {
                    active.setIronmanBalance(liveBal);
                    if (saveOnQuit) {
                        if (sync) storage.saveProfileSync(active);
                        else storage.saveProfile(active);
                    }

                    // Kembalikan Vault ke saldo Normal/Global saat offline agar /pay offline masuk ke akun Normal
                    double globalBal = cachedGlobalBalances.getOrDefault(uuid, 0.0);
                    if (plugin.getVaultHook().setBalance(player, globalBal)) {
                        vaultOwnerSlots.put(uuid, 0);
                        if (sync) storage.saveGlobalVaultStateSync(uuid, globalBal, 0);
                        else storage.saveGlobalVaultState(uuid, globalBal, 0);
                    }
                    return;
                } else {
                    cachedGlobalBalances.put(uuid, liveBal);
                    if (sync) storage.saveGlobalVaultStateSync(uuid, liveBal, 0);
                    else storage.saveGlobalVaultState(uuid, liveBal, 0);
                }
            }
            if (saveOnQuit) {
                if (sync) storage.saveProfileSync(active);
                else storage.saveProfile(active);
            }
        } else if (isSeparateVaultEnabled() && vaultOwnerSlots.getOrDefault(uuid, 0) == 0) {
            double liveBal = plugin.getVaultHook().getBalance(player);
            cachedGlobalBalances.put(uuid, liveBal);
            if (sync) storage.saveGlobalVaultStateSync(uuid, liveBal, 0);
            else storage.saveGlobalVaultState(uuid, liveBal, 0);
        }
    }

    private void saveVaultStateForProfile(Player player, Profile active, boolean sync) {
        if (!isSeparateVaultEnabled() || active == null) return;
        UUID uuid = player.getUniqueId();
        double liveBal = plugin.getVaultHook().getBalance(player);
        if (active.isIronman()) {
            active.setIronmanBalance(liveBal);
        } else {
            cachedGlobalBalances.put(uuid, liveBal);
            if (sync) {
                storage.saveGlobalVaultStateSync(uuid, liveBal, 0);
            } else {
                storage.saveGlobalVaultState(uuid, liveBal, 0);
            }
        }
    }

    public void unloadPlayerData(Player player) {
        if (player == null) return;
        saveAndRestoreVaultOnDisconnect(player, false);
        if (plugin.getMmoItemsHook() != null) {
            plugin.getMmoItemsHook().removeGamemodeModifiers(player);
        }
        if (plugin.getGamemodeManager() != null) {
            plugin.getGamemodeManager().clearPlayerTransientState(player.getUniqueId());
        }
        cachedGlobalBalances.remove(player.getUniqueId());
        vaultOwnerSlots.remove(player.getUniqueId());
        holders.remove(player.getUniqueId());
    }

    public void saveAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            savePlayerData(player);
        }
    }

    public boolean checkSecurityBeforeSwitch(Player player, PlayerProfileHolder holder) {
        FileConfiguration config = plugin.getConfig();
        FileConfiguration messages = plugin.getMessagesConfig();

        // 1. Combat Tag Check
        if (config.getBoolean("security.combat_tag.enabled", true)) {
            int combatDur = config.getInt("security.combat_tag.duration_seconds", 10);
            if (holder.isInCombat(combatDur)) {
                long rem = holder.getRemainingCombatSeconds(combatDur);
                String msg = ColorUtils.replaceBoth(
                        messages.getString("switch_blocked_combat", "<red>Cannot switch profiles while in combat!"),
                        "seconds", String.valueOf(rem));
                ColorUtils.sendMessage(player, msg);
                return false;
            }
        }

        // 2. Blacklisted Worlds Check
        List<String> blacklistedWorlds = config.getStringList("security.blacklisted_worlds");
        if (blacklistedWorlds != null && !blacklistedWorlds.isEmpty()) {
            String curWorld = player.getWorld().getName();
            for (String bw : blacklistedWorlds) {
                if (bw.equalsIgnoreCase(curWorld)) {
                    ColorUtils.sendMessage(player, messages.getString("switch_blocked_world", "<red>Not allowed in this world!"));
                    return false;
                }
            }
        }

        // 3. Environmental & State Checks
        boolean blockedState = false;
        if (config.getBoolean("security.state_checks.block_while_dead", true) && (player.isDead() || player.getHealth() <= 0)) {
            blockedState = true;
        } else if (config.getBoolean("security.state_checks.block_while_in_vehicle", true) && player.isInsideVehicle()) {
            blockedState = true;
        } else if (config.getBoolean("security.state_checks.block_while_gliding", true) && player.isGliding()) {
            blockedState = true;
        } else if (config.getBoolean("security.state_checks.block_while_sleeping", true) && player.isSleeping()) {
            blockedState = true;
        } else if (config.getBoolean("security.state_checks.block_while_in_liquid", true) &&
                (player.isInWater() || player.getLocation().getBlock().getType() == Material.LAVA)) {
            blockedState = true;
        } else if (config.getBoolean("security.state_checks.block_while_in_air_falling", true) &&
                (!player.isOnGround() && player.getFallDistance() > 1.5f)) {
            blockedState = true;
        }

        if (blockedState) {
            ColorUtils.sendMessage(player, messages.getString("switch_blocked_state", "<red>Cannot switch profiles in your current state!"));
            return false;
        }

        return true;
    }

    public void switchProfile(Player player, int targetSlot) {
        switchProfile(player, targetSlot, false, true);
    }

    public void switchProfile(Player player, int targetSlot, boolean bypassCooldown, boolean teleport) {
        PlayerProfileHolder holder = getHolder(player);
        if (holder == null) return;

        FileConfiguration config = plugin.getConfig();
        FileConfiguration messages = plugin.getMessagesConfig();

        // 1. Permission check
        String switchPerm = config.getString("permissions.switch", "cyayoprofiles.switch");
        if (!player.hasPermission(switchPerm)) {
            ColorUtils.sendMessage(player, messages.getString("no_permission"));
            return;
        }

        // 2. Security & Anti-Exploit checks (Skip only if bypassCooldown for forced admin switch / initial creation)
        if (!bypassCooldown && !checkSecurityBeforeSwitch(player, holder)) {
            return;
        }

        // 3. Cooldown check
        int cooldown = config.getInt("profile_switch.cooldown_seconds", 5);
        if (!bypassCooldown && holder.isSwitchOnCooldown(cooldown)) {
            long remaining = holder.getRemainingCooldownSeconds(cooldown);
            String msg = ColorUtils.replaceBoth(
                    messages.getString("profile_switch_cooldown", "<red>Wait {seconds} seconds!"),
                    "seconds", String.valueOf(remaining));
            ColorUtils.sendMessage(player, msg);
            return;
        }

        // 4. Already active check
        if (holder.getActiveSlot() == targetSlot) {
            String msg = ColorUtils.replaceBoth(
                    messages.getString("profile_switch_already_active", "<yellow>Already active!"),
                    "slot", String.valueOf(targetSlot));
            ColorUtils.sendMessage(player, msg);
            return;
        }

        // 5. Target profile exists & not fallen check
        Profile newProfile = holder.getProfile(targetSlot);
        if (newProfile == null) return;

        if (newProfile.isDead()) {
            String fallenMsg = ColorUtils.replaceBoth(
                    messages.getString("profile_fallen_locked", "<dark_red>Profile Slot #{slot} has FALLEN and can no longer be played!"),
                    "slot", String.valueOf(targetSlot));
            ColorUtils.sendMessage(player, fallenMsg);
            return;
        }

        Profile oldProfile = holder.getActiveProfile();

        // 6. Fire ProfileSwitchEvent
        ProfileSwitchEvent switchEvent = new ProfileSwitchEvent(player, oldProfile, newProfile);
        Bukkit.getPluginManager().callEvent(switchEvent);
        if (switchEvent.isCancelled()) return;

        // 7. Anti-dupe Freeze & Cursor cleanup
        holder.setFrozen(true);

        // Kembalikan item yang mungkin sedang dipegang di kursor mouse ke tas sebelum simpan
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            player.setItemOnCursor(null);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(cursor);
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        player.closeInventory();

        // 8. Capture & swap Ironman separated Vault balance if enabled
        UUID uuid = player.getUniqueId();
        boolean swapVault = isSeparateVaultEnabled();
        if (swapVault) {
            double liveBal = plugin.getVaultHook().getBalance(player);
            if (oldProfile != null) {
                if (oldProfile.isIronman()) {
                    oldProfile.setIronmanBalance(liveBal);
                } else {
                    cachedGlobalBalances.put(uuid, liveBal);
                }
            } else if (vaultOwnerSlots.getOrDefault(uuid, 0) <= 0) {
                cachedGlobalBalances.put(uuid, liveBal);
            }
        }

        // 9. Save old profile
        if (oldProfile != null) {
            savePlayerStateToProfile(player, oldProfile);
            if (config.getBoolean("auto_save.save_on_switch", true)) {
                storage.saveProfile(oldProfile);
            }
        }

        // 10. Apply new profile Vault balance & load player state
        if (swapVault) {
            double globalBal = cachedGlobalBalances.getOrDefault(uuid, 0.0);
            if (newProfile.isIronman()) {
                plugin.getVaultHook().setBalance(player, newProfile.getIronmanBalance());
                vaultOwnerSlots.put(uuid, targetSlot);
                storage.saveGlobalVaultState(uuid, globalBal, targetSlot);
            } else {
                if ((oldProfile != null && oldProfile.isIronman()) || vaultOwnerSlots.getOrDefault(uuid, 0) > 0) {
                    plugin.getVaultHook().setBalance(player, globalBal);
                }
                vaultOwnerSlots.put(uuid, 0);
                storage.saveGlobalVaultState(uuid, globalBal, 0);
            }
        }

        holder.setActiveSlot(targetSlot);
        storage.setActiveSlot(uuid, targetSlot);
        applyProfileToPlayer(player, newProfile, teleport);

        // 10. Cooldown & unfreeze
        holder.setLastSwitchTime(System.currentTimeMillis());

        int freezeTicks = config.getInt("profile_switch.freeze_duration_ticks", 20);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            holder.setFrozen(false);
        }, Math.max(1L, freezeTicks));

        // 11. Notifications
        ClassData classData = plugin.getClassManager().getClass(newProfile.getClassId());
        String classDisplay = classData != null ? classData.getDisplayName() : newProfile.getClassId();
        String classPlain = classData != null ? classData.getPlainDisplayName() : newProfile.getClassId();

        String switchMsg = messages.getString("profile_switched", "<green>Profile switched!");
        switchMsg = ColorUtils.replaceBoth(switchMsg, "slot", String.valueOf(targetSlot));
        switchMsg = ColorUtils.replaceBoth(switchMsg, "class", classDisplay);
        switchMsg = ColorUtils.replaceBoth(switchMsg, "class_plain", classPlain);
        switchMsg = ColorUtils.replaceBoth(switchMsg, "level", String.valueOf(newProfile.getLevel()));
        ColorUtils.sendMessage(player, switchMsg);

        if (config.getBoolean("profile_switch.send_title_on_switch", true)) {
            String title = messages.getString("profile_switch_title.title", "<gold><bold>{class}</bold>");
            title = ColorUtils.replaceBoth(title, "class", classDisplay);
            title = ColorUtils.replaceBoth(title, "class_plain", classPlain);
            title = ColorUtils.replaceBoth(title, "slot", String.valueOf(targetSlot));
            title = ColorUtils.replaceBoth(title, "level", String.valueOf(newProfile.getLevel()));

            String subtitle = messages.getString("profile_switch_title.subtitle", "<gray>Profile Slot #{slot}");
            subtitle = ColorUtils.replaceBoth(subtitle, "class", classDisplay);
            subtitle = ColorUtils.replaceBoth(subtitle, "class_plain", classPlain);
            subtitle = ColorUtils.replaceBoth(subtitle, "slot", String.valueOf(targetSlot));
            subtitle = ColorUtils.replaceBoth(subtitle, "level", String.valueOf(newProfile.getLevel()));

            int fadeIn = config.getInt("profile_switch.title_fade_in", 5);
            int stay = config.getInt("profile_switch.title_stay", 30);
            int fadeOut = config.getInt("profile_switch.title_fade_out", 10);
            ColorUtils.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);

            String sound = messages.getString("profile_switch_title.sound", "ENTITY_PLAYER_LEVELUP:1.0:1.0");
            ColorUtils.playSound(player, sound);
        }
    }

    public void createProfile(Player player, int slot, String classId) {
        createProfile(player, slot, classId, Collections.emptySet(), 1);
    }

    public void createProfile(Player player, int slot, String classId, Set<String> selectedModes, int hardcoreLives) {
        PlayerProfileHolder holder = getHolder(player);
        if (holder == null) return;

        FileConfiguration config = plugin.getConfig();
        FileConfiguration messages = plugin.getMessagesConfig();

        String createPerm = config.getString("permissions.create", "cyayoprofiles.create");
        if (!player.hasPermission(createPerm)) {
            ColorUtils.sendMessage(player, messages.getString("no_permission"));
            return;
        }

        int maxSlots = getMaxSlots(player);
        if (slot > maxSlots) {
            ColorUtils.sendMessage(player, messages.getString("no_permission"));
            return;
        }

        if (holder.hasProfile(slot)) return;

        ClassData classDataCheck = plugin.getClassManager().getClass(classId);
        if (classDataCheck == null || !classDataCheck.hasPermission(player)) {
            if (plugin.getGamemodeManager() != null) {
                plugin.getGamemodeManager().clearCreationSession(player.getUniqueId());
            }
            if (classDataCheck == null) {
                ColorUtils.sendMessage(player, messages.getString("no_permission"));
            } else {
                sendClassLockedMessage(player, classDataCheck, true);
            }
            return;
        }

        // Security check if switching from an active profile
        if (holder.hasActiveProfile() && !checkSecurityBeforeSwitch(player, holder)) {
            return;
        }

        boolean wasFirstProfile = holder.getProfiles().isEmpty();

        Profile newProfile = createDefaultProfile(player, slot, classId);

        // Apply Challenge Gamemodes (permanently bound to this profile)
        GamemodeManager gm = plugin.getGamemodeManager();
        if (gm != null && selectedModes != null && !selectedModes.isEmpty()) {
            Set<String> sanitized = new LinkedHashSet<>();
            for (String mId : selectedModes) {
                var mode = gm.getMode(mId);
                if (mode != null && mode.isEnabled()) {
                    sanitized.add(mode.getId());
                    if (sanitized.size() >= gm.getMaxSelectableModes()) {
                        break;
                    }
                }
            }
            newProfile.setGamemodes(sanitized);
            if (newProfile.isHardcore()) {
                int lives = gm.clampLives(hardcoreLives);
                newProfile.setMaxLives(lives);
                newProfile.setCurrentLives(lives);
            }
        }

        if (gm != null) {
            gm.clearCreationSession(player.getUniqueId());
        }

        // Tentukan apakah profil baru harus teleport ke new_profile_spawn
        boolean shouldTeleportToSpawn = false;
        Location spawnLoc = getNewProfileSpawnLocation();
        if (config.getBoolean("new_profile_spawn.enabled", true) && spawnLoc != null) {
            boolean firstOnly = config.getBoolean("new_profile_spawn.teleport_on_first_profile_only", false);
            if (!firstOnly || wasFirstProfile) {
                newProfile.setLastLocation(spawnLoc);
                shouldTeleportToSpawn = true;
            }
        }

        holder.setProfile(slot, newProfile);
        storage.saveProfile(newProfile);

        Bukkit.getPluginManager().callEvent(new ProfileCreateEvent(player, newProfile));

        ClassData classData = plugin.getClassManager().getClass(classId);
        String classDisplay = classData != null ? classData.getDisplayName() : classId;
        String classPlain = classData != null ? classData.getPlainDisplayName() : classId;

        String createdMsg = messages.getString("profile_created", "<green>Profile created!");
        createdMsg = ColorUtils.replaceBoth(createdMsg, "slot", String.valueOf(slot));
        createdMsg = ColorUtils.replaceBoth(createdMsg, "class", classDisplay);
        createdMsg = ColorUtils.replaceBoth(createdMsg, "class_plain", classPlain);
        ColorUtils.sendMessage(player, createdMsg);

        String sound = messages.getString("profile_create_title.sound", "UI_TOAST_CHALLENGE_COMPLETE:1.0:1.0");
        ColorUtils.playSound(player, sound);

        // Switch ke profil baru (dan teleport ke spawn jika diaktifkan)
        switchProfile(player, slot, true, shouldTeleportToSpawn);
    }

    public void sendClassLockedMessage(Player player, ClassData classData, boolean playSound) {
        if (player == null || classData == null) return;
        FileConfiguration messages = plugin.getMessagesConfig();
        String lockedMsg = messages.getString(
                "class_locked",
                "<red>You do not have permission to select <white>{class_display}<red>! {locked_hint}");
        lockedMsg = ColorUtils.replaceBoth(lockedMsg, "class", classData.getId());
        lockedMsg = ColorUtils.replaceBoth(lockedMsg, "class_display", classData.getDisplayName());
        lockedMsg = ColorUtils.replaceBoth(lockedMsg, "class_plain", classData.getPlainDisplayName());
        lockedMsg = ColorUtils.replaceBoth(lockedMsg, "permission", classData.getPermission());
        lockedMsg = ColorUtils.replaceBoth(lockedMsg, "locked_hint", classData.getLockedHint()).trim();
        ColorUtils.sendMessage(player, lockedMsg);
        if (playSound) {
            ColorUtils.playSound(player, messages.getString("class_locked_sound", "ENTITY_VILLAGER_NO:1.0:1.0"));
        }
    }

    public void handleHardcoreFallenRespawn(Player player, int fallenSlot) {
        if (player == null || !player.isOnline()) return;
        PlayerProfileHolder holder = getHolder(player);
        if (holder == null) return;

        UUID uuid = player.getUniqueId();
        Profile active = holder.getActiveProfile();
        if (active != null && active.getSlot() == fallenSlot && !active.isDead()) {
            return;
        }

        if (isSeparateVaultEnabled() && vaultOwnerSlots.getOrDefault(uuid, 0) == fallenSlot) {
            Profile fallenProfile = holder.getProfile(fallenSlot);
            if (fallenProfile != null && fallenProfile.isIronman()) {
                fallenProfile.setIronmanBalance(plugin.getVaultHook().getBalance(player));
                storage.saveProfile(fallenProfile);
            }
            double globalBal = cachedGlobalBalances.getOrDefault(uuid, 0.0);
            plugin.getVaultHook().setBalance(player, globalBal);
            vaultOwnerSlots.put(uuid, 0);
            storage.saveGlobalVaultState(uuid, globalBal, 0);
        }

        holder.setActiveSlot(0);
        storage.setActiveSlot(uuid, 0);

        clearPlayerInventoryAndEffects(player);
        if (plugin.getMmoItemsHook() != null) {
            plugin.getMmoItemsHook().removeGamemodeModifiers(player);
        }

        if (MAX_HEALTH_ATTR != null) {
            AttributeInstance attr = player.getAttribute(MAX_HEALTH_ATTR);
            if (attr != null) {
                attr.setBaseValue(20.0);
                player.setHealth(Math.min(20.0, attr.getValue()));
            }
        }
        player.setWalkSpeed(0.2f);

        Location spawnLoc = getNewProfileSpawnLocation();
        if (plugin.getConfig().getBoolean("new_profile_spawn.enabled", true) && spawnLoc != null) {
            player.teleport(spawnLoc);
        }

        Bukkit.getPluginManager().callEvent(new ProfileLoadEvent(player, null));

        if (plugin.getGuiManager() != null) {
            plugin.getGuiManager().openProfilesMenu(player);
        }
    }

    public Location getNewProfileSpawnLocation() {
        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("new_profile_spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = config.getDouble("new_profile_spawn.x", 0.5);
        double y = config.getDouble("new_profile_spawn.y", 65.0);
        double z = config.getDouble("new_profile_spawn.z", 0.5);
        float yaw = (float) config.getDouble("new_profile_spawn.yaw", 0.0);
        float pitch = (float) config.getDouble("new_profile_spawn.pitch", 0.0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    public void setNewProfileSpawnLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        FileConfiguration config = plugin.getConfig();
        config.set("new_profile_spawn.world", loc.getWorld().getName());
        config.set("new_profile_spawn.x", Math.round(loc.getX() * 100.0) / 100.0);
        config.set("new_profile_spawn.y", Math.round(loc.getY() * 100.0) / 100.0);
        config.set("new_profile_spawn.z", Math.round(loc.getZ() * 100.0) / 100.0);
        config.set("new_profile_spawn.yaw", Math.round(loc.getYaw() * 10.0f) / 10.0f);
        config.set("new_profile_spawn.pitch", Math.round(loc.getPitch() * 10.0f) / 10.0f);
        plugin.saveConfig();
    }

    public void deleteProfile(Player player, int slot) {
        PlayerProfileHolder holder = getHolder(player);
        if (holder == null) return;

        FileConfiguration config = plugin.getConfig();
        FileConfiguration messages = plugin.getMessagesConfig();

        String deletePerm = config.getString("permissions.delete", "cyayoprofiles.delete");
        if (!player.hasPermission(deletePerm)) {
            ColorUtils.sendMessage(player, messages.getString("no_permission"));
            return;
        }

        if (holder.getActiveSlot() == slot) {
            ColorUtils.sendMessage(player, messages.getString("profile_delete_active_error", "<red>Cannot delete your active profile!"));
            return;
        }

        int deleteCooldown = config.getInt("security.delete_cooldown_seconds", 60);
        if (!player.isOp() && holder.isDeleteOnCooldown(deleteCooldown)) {
            long rem = holder.getRemainingDeleteSeconds(deleteCooldown);
            String msg = ColorUtils.replaceBoth(messages.getString("delete_cooldown", "<red>Wait {seconds} seconds!"),
                    "seconds", String.valueOf(rem));
            ColorUtils.sendMessage(player, msg);
            return;
        }

        Profile profile = holder.getProfile(slot);
        if (profile == null) return;

        holder.tagDelete();
        holder.removeProfile(slot);
        storage.deleteProfile(player.getUniqueId(), slot);

        Bukkit.getPluginManager().callEvent(new ProfileDeleteEvent(player, slot, profile));

        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
        String classDisplay = classData != null ? classData.getDisplayName() : profile.getClassId();
        String classPlain = classData != null ? classData.getPlainDisplayName() : profile.getClassId();

        String deletedMsg = messages.getString("profile_deleted", "<red>Profile deleted.");
        deletedMsg = ColorUtils.replaceBoth(deletedMsg, "slot", String.valueOf(slot));
        deletedMsg = ColorUtils.replaceBoth(deletedMsg, "class", classDisplay);
        deletedMsg = ColorUtils.replaceBoth(deletedMsg, "class_plain", classPlain);
        ColorUtils.sendMessage(player, deletedMsg);

        String sound = messages.getString("profile_delete_sound", "BLOCK_ANVIL_DESTROY:1.0:1.0");
        ColorUtils.playSound(player, sound);
    }

    public int getMaxSlots(Player player) {
        if (player == null) return 1;
        FileConfiguration config = plugin.getConfig();

        int maxSlots = Math.max(1, config.getInt("slots.default_slots", 1));
        List<Map<?, ?>> tiers = config.getMapList("slots.permission_tiers");
        if (tiers != null) {
            for (Map<?, ?> map : tiers) {
                Object permObj = map.get("permission");
                Object slotsObj = map.get("slots");
                if (permObj != null && slotsObj != null) {
                    String perm = permObj.toString().trim();
                    if (!perm.isEmpty() && player.hasPermission(perm)) {
                        try {
                            int tierSlots = Integer.parseInt(slotsObj.toString().trim());
                            if (tierSlots > maxSlots) {
                                maxSlots = tierSlots;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        return maxSlots;
    }

    public void applyProfileToPlayer(Player player, Profile profile, boolean teleport) {
        if (player == null || profile == null) return;

        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());

        // 1. Clear inventory & effects
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // 2. Load inventory contents
        ItemStack[] inv = profile.getInventoryContents();
        if (inv != null && inv.length > 0) {
            player.getInventory().setStorageContents(inv);
        }
        ItemStack[] armor = profile.getArmorContents();
        if (armor != null && armor.length > 0) {
            player.getInventory().setArmorContents(armor);
        }
        ItemStack offhand = profile.getOffhandContent();
        if (offhand != null) {
            player.getInventory().setItemInOffHand(offhand);
        }

        // 3. Load potion effects
        Collection<PotionEffect> effects = profile.getActivePotionEffects();
        if (effects != null) {
            for (PotionEffect effect : effects) {
                player.addPotionEffect(effect);
            }
        }

        // 4. Base Attributes & Manual RPG Stat Modifiers
        double baseHp = classData != null ? classData.getBaseHealth() : 20.0;
        if (MAX_HEALTH_ATTR != null) {
            AttributeInstance attr = player.getAttribute(MAX_HEALTH_ATTR);
            if (attr != null) {
                attr.setBaseValue(baseHp);
            }
        }

        if (plugin.getMmoItemsHook() != null) {
            plugin.getMmoItemsHook().applyGamemodeModifiers(player, profile);
        }

        double effectiveMaxHp = baseHp;
        if (MAX_HEALTH_ATTR != null) {
            AttributeInstance attr = player.getAttribute(MAX_HEALTH_ATTR);
            if (attr != null) {
                effectiveMaxHp = attr.getValue();
            }
        }

        player.setHealth(Math.min(profile.getHealth() > 0 ? profile.getHealth() : effectiveMaxHp, effectiveMaxHp));
        player.setFoodLevel(profile.getHunger() > 0 ? profile.getHunger() : 20);

        float walkSpeed = (float) (classData != null ? classData.getWalkSpeed() : 0.2);
        player.setWalkSpeed(walkSpeed);

        // 5. Teleport to last location if requested
        if (teleport) {
            Location lastLoc = profile.getLastLocation();
            if (lastLoc != null && lastLoc.getWorld() != null) {
                player.teleport(lastLoc);
            }
        }

        // 6. Recalculate MMOItems equipment stats
        if (plugin.getMmoItemsHook() != null) {
            plugin.getMmoItemsHook().updateInventory(player);
        }
    }

    public void reapplyProfileAttributes(Player player) {
        if (player == null || !player.isOnline()) return;
        PlayerProfileHolder holder = getHolder(player);
        if (holder == null || !holder.hasActiveProfile()) return;

        Profile profile = holder.getActiveProfile();
        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());

        double baseHp = classData != null ? classData.getBaseHealth() : 20.0;
        if (MAX_HEALTH_ATTR != null) {
            AttributeInstance attr = player.getAttribute(MAX_HEALTH_ATTR);
            if (attr != null) {
                attr.setBaseValue(baseHp);
            }
        }

        if (plugin.getMmoItemsHook() != null) {
            plugin.getMmoItemsHook().applyGamemodeModifiers(player, profile);
        }

        float walkSpeed = (float) (classData != null ? classData.getWalkSpeed() : 0.2);
        player.setWalkSpeed(walkSpeed);

        if (MAX_HEALTH_ATTR != null) {
            AttributeInstance attr = player.getAttribute(MAX_HEALTH_ATTR);
            if (attr != null && player.getHealth() > 0) {
                player.setHealth(attr.getValue());
            }
        }

        if (plugin.getMmoItemsHook() != null) {
            plugin.getMmoItemsHook().updateInventory(player);
        }
    }

    public void savePlayerStateToProfile(Player player, Profile profile) {
        if (player == null || profile == null || profile.isDead()) return;

        if (plugin.getGamemodeListener() != null) {
            plugin.getGamemodeListener().flushOpenIronmanEnderChest(player, false);
        }

        profile.setInventoryContents(player.getInventory().getStorageContents());
        profile.setArmorContents(player.getInventory().getArmorContents());
        profile.setOffhandContent(player.getInventory().getItemInOffHand());
        profile.setHealth(player.getHealth());
        profile.setHunger(player.getFoodLevel());
        profile.setLastLocation(player.getLocation());
        profile.setActivePotionEffects(player.getActivePotionEffects());
    }

    private Profile createDefaultProfile(Player player, int slot, String classId) {
        Profile profile = new Profile(player.getUniqueId(), slot, classId);
        ClassData classData = plugin.getClassManager().getClass(classId);

        profile.setLevel(1);
        profile.setXp(0.0);
        profile.setHealth(classData != null ? classData.getBaseHealth() : 20.0);
        profile.setHunger(classData != null ? classData.getBaseHunger() : 20);
        profile.setLastLocation(player.getLocation());

        // Starter kit
        ItemStack[] contents = new ItemStack[36];
        if (classData != null && classData.isStarterKitEnabled()) {
            List<ItemStack> kit = plugin.getClassManager().buildStarterKit(classData);
            for (int i = 0; i < kit.size() && i < contents.length; i++) {
                contents[i] = kit.get(i).clone();
            }
        }
        profile.setInventoryContents(contents);
        profile.setArmorContents(new ItemStack[4]);
        profile.setOffhandContent(null);
        profile.setActivePotionEffects(new ArrayList<>());

        return profile;
    }
}
