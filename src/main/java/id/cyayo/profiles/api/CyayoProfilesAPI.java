package id.cyayo.profiles.api;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.model.ClassData;
import id.cyayo.profiles.model.PlayerProfileHolder;
import id.cyayo.profiles.model.Profile;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CyayoProfilesAPI {

    public static boolean hasActiveProfile(UUID playerUuid) {
        return getActiveProfile(playerUuid) != null;
    }

    public static UUID getActiveProfileUuid(UUID playerUuid) {
        Profile profile = getActiveProfile(playerUuid);
        return profile != null ? profile.getProfileUuid() : null;
    }

    public static int getActiveSlot(UUID playerUuid) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        if (plugin == null) return 0;
        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(playerUuid);
        return holder != null ? holder.getActiveSlot() : 0;
    }

    public static Profile getActiveProfile(Player player) {
        if (player == null) return null;
        return getActiveProfile(player.getUniqueId());
    }

    public static Profile getActiveProfile(UUID playerUuid) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        if (plugin == null || playerUuid == null) return null;
        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(playerUuid);
        return holder != null ? holder.getActiveProfile() : null;
    }

    public static String getActiveClassId(Player player) {
        Profile profile = getActiveProfile(player);
        return profile != null ? profile.getClassId() : null;
    }

    public static ClassData getActiveClassData(Player player) {
        String classId = getActiveClassId(player);
        if (classId == null) return null;
        return CyayoProfiles.getInstance().getClassManager().getClass(classId);
    }

    public static int getProfileLevel(Player player) {
        Profile profile = getActiveProfile(player);
        return profile != null ? profile.getLevel() : 1;
    }

    public static double getProfileXp(Player player) {
        Profile profile = getActiveProfile(player);
        return profile != null ? profile.getXp() : 0.0;
    }

    public static void addProfileXp(Player player, double amount) {
        Profile profile = getActiveProfile(player);
        if (profile != null) {
            CyayoProfiles.getInstance().getLevelManager().addXp(player, profile, amount);
        }
    }

    public static String getProfileKey(UUID playerUuid) {
        UUID profileUuid = getActiveProfileUuid(playerUuid);
        return profileUuid != null ? profileUuid.toString() : playerUuid.toString();
    }

    // --- Challenge Gamemodes API ---

    public static boolean hasGamemode(UUID playerUuid, String modeId) {
        Profile profile = getActiveProfile(playerUuid);
        return profile != null && profile.hasGamemode(modeId);
    }

    public static String getGamemodesList(UUID playerUuid) {
        Profile profile = getActiveProfile(playerUuid);
        if (profile == null || profile.getGamemodes().isEmpty()) {
            return "normal";
        }
        return String.join(",", profile.getGamemodes());
    }

    public static String getGamemodeBadges(UUID playerUuid) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        Profile profile = getActiveProfile(playerUuid);
        if (plugin == null || profile == null || plugin.getGamemodeManager() == null) {
            return "";
        }
        return id.cyayo.profiles.util.ColorUtils.color(plugin.getGamemodeManager().getBadgesRaw(profile.getGamemodes()));
    }

    public static double getClassXpMultiplier(UUID playerUuid) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        Profile profile = getActiveProfile(playerUuid);
        if (plugin == null || profile == null || plugin.getGamemodeManager() == null) return 1.0;
        return plugin.getGamemodeManager().getClassXpMultiplier(profile);
    }

    public static double getGatheringXpMultiplier(UUID playerUuid) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        Profile profile = getActiveProfile(playerUuid);
        if (plugin == null || profile == null || plugin.getGamemodeManager() == null) return 1.0;
        return plugin.getGamemodeManager().getGatheringXpMultiplier(profile);
    }

    public static double getGatheringLuckBonus(UUID playerUuid) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        Profile profile = getActiveProfile(playerUuid);
        if (plugin == null || profile == null || plugin.getGamemodeManager() == null) return 0.0;
        return plugin.getGamemodeManager().getGatheringLuckBonus(profile);
    }

    public static double getGatheringSpeedBonus(UUID playerUuid) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        Profile profile = getActiveProfile(playerUuid);
        if (plugin == null || profile == null || plugin.getGamemodeManager() == null) return 0.0;
        return plugin.getGamemodeManager().getGatheringSpeedBonus(profile);
    }

    public static double getGatheringDoubleBonus(UUID playerUuid) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        Profile profile = getActiveProfile(playerUuid);
        if (plugin == null || profile == null || plugin.getGamemodeManager() == null) return 0.0;
        return plugin.getGamemodeManager().getGatheringDoubleBonus(profile);
    }

    public static double getRaceXpMultiplier(UUID playerUuid) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        Profile profile = getActiveProfile(playerUuid);
        if (plugin == null || profile == null || plugin.getGamemodeManager() == null) return 1.0;
        return plugin.getGamemodeManager().getRaceXpMultiplier(profile);
    }

    public static int getHardcoreLives(UUID playerUuid) {
        Profile profile = getActiveProfile(playerUuid);
        return profile != null ? profile.getCurrentLives() : 0;
    }

    public static int getHardcoreMaxLives(UUID playerUuid) {
        Profile profile = getActiveProfile(playerUuid);
        return profile != null ? profile.getMaxLives() : 0;
    }

    public static boolean isHuntedPvpDeath(Player victim) {
        CyayoProfiles plugin = CyayoProfiles.getInstance();
        Profile profile = getActiveProfile(victim);
        if (plugin == null || profile == null || plugin.getGamemodeManager() == null) return false;
        return plugin.getGamemodeManager().isHuntedPvpDeath(victim, profile);
    }
}
