package id.cyayo.profiles.manager;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.model.GamemodeData;
import id.cyayo.profiles.model.Profile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GamemodeManager {

    public enum ToggleResult {
        ADDED,
        REMOVED,
        MAX_REACHED,
        INVALID
    }

    public static class CreationSession {
        private final int targetSlot;
        private final String classId;
        private final Set<String> selectedModes = new LinkedHashSet<>();
        private int hardcoreLives;

        public CreationSession(int targetSlot, String classId, int defaultLives) {
            this.targetSlot = targetSlot;
            this.classId = classId;
            this.hardcoreLives = defaultLives;
        }

        public int getTargetSlot() {
            return targetSlot;
        }

        public String getClassId() {
            return classId;
        }

        public Set<String> getSelectedModes() {
            return selectedModes;
        }

        public int getHardcoreLives() {
            return hardcoreLives;
        }

        public void setHardcoreLives(int hardcoreLives) {
            this.hardcoreLives = hardcoreLives;
        }
    }

    private final CyayoProfiles plugin;
    private File gamemodesFile;
    private FileConfiguration gamemodesConfig;

    public static class PvpAttribution {
        public final UUID attackerUuid;
        public final String attackerName;
        public final long expiryMillis;

        public PvpAttribution(UUID attackerUuid, String attackerName, long expiryMillis) {
            this.attackerUuid = attackerUuid;
            this.attackerName = attackerName;
            this.expiryMillis = expiryMillis;
        }
    }

    private final Map<String, GamemodeData> modes = new LinkedHashMap<>();
    private final Map<UUID, CreationSession> creationSessions = new ConcurrentHashMap<>();
    // huntedVictimUuid -> (attackerUuid -> expirationTimeMillis)
    private final Map<UUID, Map<UUID, Long>> huntedRetaliation = new ConcurrentHashMap<>();
    private final Map<UUID, PvpAttribution> recentPvpAttackers = new ConcurrentHashMap<>();

    // Global settings
    private int maxSelectableModes = 4;
    private String normalDisplayName = "<white>Normal";
    private String normalSymbol = "";

    // Ironman settings
    private boolean ironmanSeparateEnderChest = true;
    private boolean ironmanSeparateVaultEconomy = true;
    private boolean ironmanBlockDropPickupFromOthers = true;
    private boolean ironmanPreventOthersPickupDrops = true;
    private boolean ironmanBlockPlayerInteractEntities = true;
    private boolean ironmanBlockExternalContainers = true;
    private boolean ironmanAllowWorldgenLootChests = true;
    private boolean ironmanProtectContainers = true;
    private final Set<String> ironmanBlockedCommands = new HashSet<>();

    // Hardcore settings
    private boolean hardcorePlayerSelectableLives = true;
    private int hardcoreMinLives = 1;
    private int hardcoreMaxLives = 3;
    private int hardcoreDefaultLives = 1;
    private boolean hardcoreBroadcastOnFallen = true;

    // Hunted settings
    private int huntedSelfDefenseDurationSeconds = 30;
    private int huntedPvpKillAttributionSeconds = 15;
    private final Set<String> huntedSafeWorlds = new HashSet<>();
    private final Set<String> huntedSafeRegions = new HashSet<>();
    private boolean huntedRespectWorldGuardPvpDeny = true;
    private boolean huntedDropOnlyOnPlayerKill = true;
    private double huntedDropChanceInventory = 0.50;
    private double huntedDropChanceArmor = 0.15;
    private double huntedDropChanceMainHand = 0.10;
    private double huntedDropChanceOffhand = 0.15;
    private boolean huntedDestroyDropsWhenIronmanDies = true;

    // Cursed settings
    private double cursedIncomingDamageMultiplier = 1.5;
    private double cursedOutgoingDamageMultiplier = 1.0;
    private boolean cursedDisableNaturalRegen = true;

    // Lazy WorldGuard reflection cache
    private boolean wgChecked = false;
    private boolean wgAvailable = false;
    private Method wgGetInstanceMethod;
    private Method wgGetPlatformMethod;
    private Method wgGetRegionContainerMethod;
    private Method wgCreateQueryMethod;
    private Method wgAdaptLocationMethod;
    private Method wgTestStateMethod;
    private Method wgGetApplicableRegionsMethod;
    private Method wgRegionGetIdMethod;
    private Object wgPvpFlagArray;

    public GamemodeManager(CyayoProfiles plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        modes.clear();
        ironmanBlockedCommands.clear();
        huntedSafeWorlds.clear();
        huntedSafeRegions.clear();

        gamemodesFile = new File(plugin.getDataFolder(), "gamemodes.yml");
        if (!gamemodesFile.exists()) {
            plugin.saveResource("gamemodes.yml", false);
        }
        gamemodesConfig = YamlConfiguration.loadConfiguration(gamemodesFile);

        this.maxSelectableModes = Math.max(1, gamemodesConfig.getInt("max_selectable_modes", 4));
        this.normalDisplayName = gamemodesConfig.getString("normal_mode.display_name", "<white>Normal");
        this.normalSymbol = gamemodesConfig.getString("normal_mode.symbol", "");

        // Ironman settings
        this.ironmanSeparateEnderChest = gamemodesConfig.getBoolean("settings.ironman.separate_ender_chest", true);
        this.ironmanSeparateVaultEconomy = gamemodesConfig.getBoolean("settings.ironman.separate_vault_economy", true);
        this.ironmanBlockDropPickupFromOthers = gamemodesConfig.getBoolean("settings.ironman.block_drop_pickup_from_others", true);
        this.ironmanPreventOthersPickupDrops = gamemodesConfig.getBoolean("settings.ironman.prevent_others_pickup_ironman_drops", true);
        this.ironmanBlockPlayerInteractEntities = gamemodesConfig.getBoolean("settings.ironman.block_player_interact_entities", true);
        this.ironmanBlockExternalContainers = gamemodesConfig.getBoolean("settings.ironman.block_external_containers", true);
        this.ironmanAllowWorldgenLootChests = gamemodesConfig.getBoolean("settings.ironman.allow_worldgen_loot_chests", true);
        this.ironmanProtectContainers = gamemodesConfig.getBoolean("settings.ironman.protect_ironman_containers", true);
        for (String cmd : gamemodesConfig.getStringList("settings.ironman.blocked_commands")) {
            if (cmd != null && !cmd.trim().isEmpty()) {
                String clean = cmd.trim().toLowerCase();
                if (clean.startsWith("/")) clean = clean.substring(1);
                ironmanBlockedCommands.add(clean);
            }
        }

        // Hardcore settings
        this.hardcorePlayerSelectableLives = gamemodesConfig.getBoolean("settings.hardcore.player_selectable_lives", true);
        this.hardcoreMinLives = Math.max(1, gamemodesConfig.getInt("settings.hardcore.min_lives", 1));
        this.hardcoreMaxLives = Math.max(hardcoreMinLives, gamemodesConfig.getInt("settings.hardcore.max_lives", 3));
        this.hardcoreDefaultLives = clampLives(gamemodesConfig.getInt("settings.hardcore.default_lives", 1));
        this.hardcoreBroadcastOnFallen = gamemodesConfig.getBoolean("settings.hardcore.broadcast_on_fallen", true);

        // Hunted settings
        this.huntedSelfDefenseDurationSeconds = Math.max(1, gamemodesConfig.getInt("settings.hunted.self_defense_duration_seconds", 30));
        this.huntedPvpKillAttributionSeconds = Math.max(1, gamemodesConfig.getInt("settings.hunted.pvp_kill_attribution_seconds", 15));
        for (String w : gamemodesConfig.getStringList("settings.hunted.safe_worlds")) {
            if (w != null && !w.trim().isEmpty()) {
                huntedSafeWorlds.add(w.trim().toLowerCase());
            }
        }
        for (String r : gamemodesConfig.getStringList("settings.hunted.safe_regions")) {
            if (r != null && !r.trim().isEmpty()) {
                huntedSafeRegions.add(r.trim().toLowerCase());
            }
        }
        this.huntedRespectWorldGuardPvpDeny = gamemodesConfig.getBoolean("settings.hunted.respect_worldguard_pvp_deny", true);
        this.huntedDropOnlyOnPlayerKill = gamemodesConfig.getBoolean("settings.hunted.drop_only_on_player_kill", true);
        this.huntedDropChanceInventory = clampChance(gamemodesConfig.getDouble("settings.hunted.drop_chances.inventory", 0.50));
        this.huntedDropChanceArmor = clampChance(gamemodesConfig.getDouble("settings.hunted.drop_chances.armor", 0.15));
        this.huntedDropChanceMainHand = clampChance(gamemodesConfig.getDouble("settings.hunted.drop_chances.main_hand", 0.10));
        this.huntedDropChanceOffhand = clampChance(gamemodesConfig.getDouble("settings.hunted.drop_chances.offhand", 0.15));
        this.huntedDestroyDropsWhenIronmanDies = gamemodesConfig.getBoolean("settings.hunted.destroy_drops_when_ironman_dies", true);

        // Cursed settings
        this.cursedIncomingDamageMultiplier = Math.max(0.0, gamemodesConfig.getDouble("settings.cursed.incoming_damage_multiplier", 1.5));
        this.cursedOutgoingDamageMultiplier = Math.max(0.0, gamemodesConfig.getDouble("settings.cursed.outgoing_damage_multiplier", 1.0));
        this.cursedDisableNaturalRegen = gamemodesConfig.getBoolean("settings.cursed.disable_natural_regen", true);

        // Load mode entries & bonuses
        ConfigurationSection modesSec = gamemodesConfig.getConfigurationSection("modes");
        if (modesSec != null) {
            for (String key : modesSec.getKeys(false)) {
                ConfigurationSection sec = modesSec.getConfigurationSection(key);
                if (sec == null) continue;

                boolean enabled = sec.getBoolean("enabled", true);
                String display = sec.getString("display_name", key);
                String symbol = sec.getString("symbol", "");

                double classXp = sec.getDouble("bonuses.class_xp_bonus", 0.0);
                double gatheringXp = sec.getDouble("bonuses.gathering_xp_bonus", 0.0);
                double raceXp = sec.getDouble("bonuses.race_xp_bonus", 0.0);
                double gatheringLuck = sec.getDouble("bonuses.gathering_luck_bonus", 0.0);
                double gatheringSpeed = sec.getDouble("bonuses.gathering_speed_bonus", 0.0);
                double gatheringDouble = sec.getDouble("bonuses.gathering_double_bonus", 0.0);

                Map<String, Double> mythicStats = new LinkedHashMap<>();
                ConfigurationSection statsSec = sec.getConfigurationSection("bonuses.mythic_stats");
                if (statsSec != null) {
                    for (String statKey : statsSec.getKeys(false)) {
                        double val = statsSec.getDouble(statKey, 0.0);
                        if (val != 0.0) {
                            mythicStats.put(statKey.toUpperCase(), val);
                        }
                    }
                }

                modes.put(key.toLowerCase(), new GamemodeData(
                        key.toLowerCase(), enabled, display, symbol,
                        classXp, gatheringXp, raceXp,
                        gatheringLuck, gatheringSpeed, gatheringDouble,
                        mythicStats
                ));
            }
        }
        plugin.getLogger().info("Loaded " + modes.size() + " Challenge Gamemodes from gamemodes.yml!");
    }

    private double clampChance(double val) {
        return Math.max(0.0, Math.min(1.0, val));
    }

    public int clampLives(int val) {
        return Math.max(hardcoreMinLives, Math.min(hardcoreMaxLives, val));
    }

    public GamemodeData getMode(String id) {
        if (id == null) return null;
        return modes.get(id.toLowerCase());
    }

    public Collection<GamemodeData> getAllModes() {
        return modes.values();
    }

    // --- Creation Sessions ---

    public CreationSession startCreationSession(UUID playerUuid, int slot, String classId) {
        CreationSession session = new CreationSession(slot, classId, hardcoreDefaultLives);
        creationSessions.put(playerUuid, session);
        return session;
    }

    public CreationSession getCreationSession(UUID playerUuid) {
        return creationSessions.get(playerUuid);
    }

    public void clearCreationSession(UUID playerUuid) {
        creationSessions.remove(playerUuid);
    }

    public ToggleResult toggleModeInSession(CreationSession session, String modeId) {
        if (session == null || modeId == null) return ToggleResult.INVALID;
        GamemodeData mode = getMode(modeId);
        if (mode == null || !mode.isEnabled()) return ToggleResult.INVALID;

        String key = mode.getId();
        if (session.getSelectedModes().contains(key)) {
            session.getSelectedModes().remove(key);
            return ToggleResult.REMOVED;
        }
        if (session.getSelectedModes().size() >= maxSelectableModes) {
            return ToggleResult.MAX_REACHED;
        }
        session.getSelectedModes().add(key);
        return ToggleResult.ADDED;
    }

    // --- Display Helpers ---

    public String getBadgesRaw(Collection<String> modeIds) {
        if (modeIds == null || modeIds.isEmpty()) {
            return normalSymbol;
        }
        List<String> parts = new ArrayList<>();
        for (String id : modeIds) {
            GamemodeData data = getMode(id);
            if (data != null && !data.getSymbol().isEmpty()) {
                parts.add(data.getSymbol());
            }
        }
        return parts.isEmpty() ? normalSymbol : String.join(" ", parts);
    }

    public String getNamesRaw(Collection<String> modeIds) {
        if (modeIds == null || modeIds.isEmpty()) {
            return normalDisplayName;
        }
        List<String> parts = new ArrayList<>();
        for (String id : modeIds) {
            GamemodeData data = getMode(id);
            if (data != null) {
                parts.add(data.getDisplayName());
            } else {
                parts.add(id);
            }
        }
        return parts.isEmpty() ? normalDisplayName : String.join("<gray>, ", parts);
    }

    // --- Additive Bonuses ---

    public double getClassXpBonusFraction(Collection<String> modeIds) {
        double sum = 0.0;
        if (modeIds == null) return 0.0;
        for (String id : modeIds) {
            GamemodeData data = getMode(id);
            if (data != null && data.isEnabled()) {
                sum += data.getClassXpBonus();
            }
        }
        return Math.max(0.0, sum);
    }

    public double getClassXpMultiplier(Profile profile) {
        if (profile == null) return 1.0;
        return 1.0 + getClassXpBonusFraction(profile.getGamemodes());
    }

    public double getGatheringXpBonusFraction(Collection<String> modeIds) {
        double sum = 0.0;
        if (modeIds == null) return 0.0;
        for (String id : modeIds) {
            GamemodeData data = getMode(id);
            if (data != null && data.isEnabled()) {
                sum += data.getGatheringXpBonus();
            }
        }
        return Math.max(0.0, sum);
    }

    public double getGatheringXpMultiplier(Profile profile) {
        if (profile == null) return 1.0;
        return 1.0 + getGatheringXpBonusFraction(profile.getGamemodes());
    }

    public double getRaceXpMultiplier(Profile profile) {
        if (profile == null) return 1.0;
        double sum = 0.0;
        for (String id : profile.getGamemodes()) {
            GamemodeData data = getMode(id);
            if (data != null && data.isEnabled()) {
                sum += data.getRaceXpBonus();
            }
        }
        return 1.0 + Math.max(0.0, sum);
    }

    public double getGatheringLuckBonus(Collection<String> modeIds) {
        double sum = 0.0;
        if (modeIds == null) return 0.0;
        for (String id : modeIds) {
            GamemodeData data = getMode(id);
            if (data != null && data.isEnabled()) {
                sum += data.getGatheringLuckBonus();
            }
        }
        return sum;
    }

    public double getGatheringLuckBonus(Profile profile) {
        return profile != null ? getGatheringLuckBonus(profile.getGamemodes()) : 0.0;
    }

    public double getGatheringSpeedBonus(Profile profile) {
        if (profile == null) return 0.0;
        double sum = 0.0;
        for (String id : profile.getGamemodes()) {
            GamemodeData data = getMode(id);
            if (data != null && data.isEnabled()) {
                sum += data.getGatheringSpeedBonus();
            }
        }
        return sum;
    }

    public double getGatheringDoubleBonus(Profile profile) {
        if (profile == null) return 0.0;
        double sum = 0.0;
        for (String id : profile.getGamemodes()) {
            GamemodeData data = getMode(id);
            if (data != null && data.isEnabled()) {
                sum += data.getGatheringDoubleBonus();
            }
        }
        return sum;
    }

    public Map<String, Double> getMythicStatBonuses(Profile profile) {
        if (profile == null) return Collections.emptyMap();
        Map<String, Double> total = new LinkedHashMap<>();
        for (String id : profile.getGamemodes()) {
            GamemodeData data = getMode(id);
            if (data != null && data.isEnabled()) {
                for (Map.Entry<String, Double> entry : data.getMythicStats().entrySet()) {
                    total.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
        }
        return total;
    }

    // --- Hunted Retaliation & Safezone Checks ---

    public void recordHuntedAttack(UUID huntedVictim, UUID attacker) {
        if (huntedVictim == null || attacker == null) return;
        long expiry = System.currentTimeMillis() + (huntedSelfDefenseDurationSeconds * 1000L);
        huntedRetaliation
                .computeIfAbsent(huntedVictim, k -> new ConcurrentHashMap<>())
                .put(attacker, expiry);
    }

    public boolean canHuntedRetaliate(UUID huntedPlayer, UUID target) {
        if (huntedPlayer == null || target == null) return false;
        Map<UUID, Long> map = huntedRetaliation.get(huntedPlayer);
        if (map == null) return false;
        Long expiry = map.get(target);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            map.remove(target);
            return false;
        }
        return true;
    }

    public void recordPvpAttribution(UUID victimUuid, Player attacker) {
        if (victimUuid == null || attacker == null || victimUuid.equals(attacker.getUniqueId())) return;
        long expiry = System.currentTimeMillis() + (huntedPvpKillAttributionSeconds * 1000L);
        recentPvpAttackers.put(victimUuid, new PvpAttribution(attacker.getUniqueId(), attacker.getName(), expiry));
    }

    public PvpAttribution getRecentPvpAttacker(UUID victimUuid) {
        if (victimUuid == null) return null;
        PvpAttribution attr = recentPvpAttackers.get(victimUuid);
        if (attr == null) return null;
        if (System.currentTimeMillis() > attr.expiryMillis) {
            recentPvpAttackers.remove(victimUuid);
            return null;
        }
        return attr;
    }

    public void clearPvpAttribution(UUID victimUuid) {
        if (victimUuid != null) {
            recentPvpAttackers.remove(victimUuid);
        }
    }

    public boolean isHuntedPvpDeath(Player victim, Profile profile) {
        if (victim == null || profile == null || !profile.isHunted()) return false;
        if (!huntedDropOnlyOnPlayerKill) return true;
        Player directKiller = victim.getKiller();
        if (directKiller != null && !directKiller.getUniqueId().equals(victim.getUniqueId())) {
            return true;
        }
        return getRecentPvpAttacker(victim.getUniqueId()) != null;
    }

    public void clearPlayerTransientState(UUID playerUuid) {
        creationSessions.remove(playerUuid);
        huntedRetaliation.remove(playerUuid);
        recentPvpAttackers.remove(playerUuid);
        for (Map<UUID, Long> map : huntedRetaliation.values()) {
            map.remove(playerUuid);
        }
        recentPvpAttackers.entrySet().removeIf(e -> e.getValue().attackerUuid.equals(playerUuid));
    }

    public boolean isSafeZone(Location location) {
        if (location == null || location.getWorld() == null) return true;
        if (huntedSafeWorlds.contains(location.getWorld().getName().toLowerCase())) {
            return true;
        }
        if ((!huntedSafeRegions.isEmpty() || huntedRespectWorldGuardPvpDeny) && checkWorldGuardSafeZone(location)) {
            return true;
        }
        return false;
    }

    private boolean checkWorldGuardSafeZone(Location location) {
        initWorldGuardReflection();
        if (!wgAvailable) return false;
        try {
            Object wgInstance = wgGetInstanceMethod.invoke(null);
            Object platform = wgGetPlatformMethod.invoke(wgInstance);
            Object regionContainer = wgGetRegionContainerMethod.invoke(platform);
            if (regionContainer == null) return false;
            Object query = wgCreateQueryMethod.invoke(regionContainer);
            Object weLoc = wgAdaptLocationMethod.invoke(null, location);

            if (!huntedSafeRegions.isEmpty() && wgGetApplicableRegionsMethod != null && wgRegionGetIdMethod != null) {
                Object applicable = wgGetApplicableRegionsMethod.invoke(query, weLoc);
                if (applicable instanceof Iterable<?> iterable) {
                    for (Object region : iterable) {
                        Object idObj = wgRegionGetIdMethod.invoke(region);
                        if (idObj instanceof String regionId && huntedSafeRegions.contains(regionId.toLowerCase())) {
                            return true;
                        }
                    }
                }
            }

            if (huntedRespectWorldGuardPvpDeny && wgTestStateMethod != null) {
                Object res = wgTestStateMethod.invoke(query, weLoc, null, wgPvpFlagArray);
                if (Boolean.FALSE.equals(res)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void initWorldGuardReflection() {
        if (wgChecked) return;
        wgChecked = true;
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return;
        }
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Class<?> platformClass = Class.forName("com.sk89q.worldguard.internal.platform.WorldGuardPlatform");
            Class<?> rcClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            Class<?> queryClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
            Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Class<?> weLocClass = Class.forName("com.sk89q.worldedit.util.Location");
            Class<?> associableClass = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
            Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
            Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Class<?> stateFlagArrayClass = Array.newInstance(stateFlagClass, 0).getClass();

            this.wgGetInstanceMethod = wgClass.getMethod("getInstance");
            this.wgGetPlatformMethod = wgClass.getMethod("getPlatform");
            this.wgGetRegionContainerMethod = platformClass.getMethod("getRegionContainer");
            this.wgCreateQueryMethod = rcClass.getMethod("createQuery");
            this.wgAdaptLocationMethod = adapterClass.getMethod("adapt", Location.class);
            this.wgTestStateMethod = queryClass.getMethod("testState", weLocClass, associableClass, stateFlagArrayClass);
            this.wgGetApplicableRegionsMethod = queryClass.getMethod("getApplicableRegions", weLocClass);
            this.wgRegionGetIdMethod = protectedRegionClass.getMethod("getId");

            Object pvpFlag = flagsClass.getField("PVP").get(null);
            Object arr = Array.newInstance(stateFlagClass, 1);
            Array.set(arr, 0, pvpFlag);
            this.wgPvpFlagArray = arr;
            this.wgAvailable = true;
        } catch (Throwable t) {
            plugin.getLogger().warning("WorldGuard detected, but region/PvP reflection failed: " + t.getMessage());
            this.wgAvailable = false;
        }
    }

    // --- Config Getters ---

    public int getMaxSelectableModes() {
        return maxSelectableModes;
    }

    public String getNormalDisplayName() {
        return normalDisplayName;
    }

    public String getNormalSymbol() {
        return normalSymbol;
    }

    public boolean isIronmanSeparateEnderChest() {
        return ironmanSeparateEnderChest;
    }

    public boolean isIronmanSeparateVaultEconomy() {
        return ironmanSeparateVaultEconomy;
    }

    public boolean isIronmanBlockDropPickupFromOthers() {
        return ironmanBlockDropPickupFromOthers;
    }

    public boolean isIronmanPreventOthersPickupDrops() {
        return ironmanPreventOthersPickupDrops;
    }

    public boolean isIronmanBlockPlayerInteractEntities() {
        return ironmanBlockPlayerInteractEntities;
    }

    public boolean isIronmanBlockExternalContainers() {
        return ironmanBlockExternalContainers;
    }

    public boolean isIronmanAllowWorldgenLootChests() {
        return ironmanAllowWorldgenLootChests;
    }

    public boolean isIronmanProtectContainers() {
        return ironmanProtectContainers;
    }

    public Set<String> getIronmanBlockedCommands() {
        return ironmanBlockedCommands;
    }

    public boolean isHardcorePlayerSelectableLives() {
        return hardcorePlayerSelectableLives;
    }

    public int getHardcoreMinLives() {
        return hardcoreMinLives;
    }

    public int getHardcoreMaxLives() {
        return hardcoreMaxLives;
    }

    public int getHardcoreDefaultLives() {
        return hardcoreDefaultLives;
    }

    public boolean isHardcoreBroadcastOnFallen() {
        return hardcoreBroadcastOnFallen;
    }

    public int getHuntedSelfDefenseDurationSeconds() {
        return huntedSelfDefenseDurationSeconds;
    }

    public boolean isHuntedDropOnlyOnPlayerKill() {
        return huntedDropOnlyOnPlayerKill;
    }

    public double getHuntedDropChanceInventory() {
        return huntedDropChanceInventory;
    }

    public double getHuntedDropChanceArmor() {
        return huntedDropChanceArmor;
    }

    public double getHuntedDropChanceMainHand() {
        return huntedDropChanceMainHand;
    }

    public double getHuntedDropChanceOffhand() {
        return huntedDropChanceOffhand;
    }

    public boolean isHuntedDestroyDropsWhenIronmanDies() {
        return huntedDestroyDropsWhenIronmanDies;
    }

    public double getCursedIncomingDamageMultiplier() {
        return cursedIncomingDamageMultiplier;
    }

    public double getCursedOutgoingDamageMultiplier() {
        return cursedOutgoingDamageMultiplier;
    }

    public boolean isCursedDisableNaturalRegen() {
        return cursedDisableNaturalRegen;
    }
}

