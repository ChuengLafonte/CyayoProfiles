package id.cyayo.profiles.hook;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.manager.GamemodeManager;
import id.cyayo.profiles.model.ClassData;
import id.cyayo.profiles.model.PlayerProfileHolder;
import id.cyayo.profiles.model.Profile;
import id.cyayo.profiles.util.ColorUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final CyayoProfiles plugin;
    private static final DecimalFormat NUM_FORMAT = new DecimalFormat("#,###.##");

    public PlaceholderAPIHook(CyayoProfiles plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cyayoprofiles";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Cyayo";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
        String key = params.toLowerCase();

        if (key.equals("has_profile")) {
            return String.valueOf(holder != null && holder.hasActiveProfile());
        }
        if (key.equals("max_slots")) {
            return String.valueOf(plugin.getProfileManager().getMaxSlots(player));
        }

        if (holder == null || !holder.hasActiveProfile()) return "";
        Profile profile = holder.getActiveProfile();

        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
        String classDisplay = classData != null ? classData.getDisplayName() : profile.getClassId();
        String classPlain = classData != null ? classData.getPlainDisplayName() : profile.getClassId();

        int level = profile.getLevel();
        int maxLevel = plugin.getLevelManager().getMaxLevel();
        double reqXp = plugin.getLevelManager().getRequiredXp(level);

        if (key.startsWith("has_mode_")) {
            String modeId = key.substring(9);
            return String.valueOf(profile.hasGamemode(modeId));
        }
        if (key.startsWith("mode_level_")) {
            String modeId = key.substring(11);
            return profile.hasGamemode(modeId) ? String.valueOf(level) : "0";
        }
        if (key.startsWith("mode_alive_level_")) {
            String modeId = key.substring(17);
            return (profile.hasGamemode(modeId) && !profile.isDead()) ? String.valueOf(level) : "0";
        }

        GamemodeManager gm = plugin.getGamemodeManager();

        switch (key) {
            case "class":
                return profile.getClassId();
            case "class_display":
                return classDisplay;
            case "class_plain":
                return classPlain;
            case "level":
                return String.valueOf(level);
            case "max_level":
                return String.valueOf(maxLevel);
            case "xp":
                return level >= maxLevel ? plugin.getLevelManager().getMaxXpText() : NUM_FORMAT.format(profile.getXp());
            case "req_xp":
                return level >= maxLevel ? plugin.getLevelManager().getMaxXpText() : NUM_FORMAT.format(reqXp);
            case "progress_percent":
                if (level >= maxLevel || reqXp <= 0) return "100.0";
                return String.format("%.1f", (profile.getXp() / reqXp) * 100.0);
            case "progress_bar":
                return plugin.getLevelManager().getProgressBar(profile.getXp(), reqXp, 10, "§a", "§7");
            case "slot":
                return String.valueOf(holder.getActiveSlot());
            case "health":
                return String.format("%.1f", profile.getHealth());
            case "profile_uuid":
                return profile.getProfileUuid().toString();
            case "gamemodes":
                return gm != null ? ColorUtils.color(gm.getNamesRaw(profile.getGamemodes())) : "Normal";
            case "gamemodes_raw":
                return gm != null ? gm.getNamesRaw(profile.getGamemodes()) : "Normal";
            case "gamemode_badges":
                return gm != null ? ColorUtils.color(gm.getBadgesRaw(profile.getGamemodes())) : "";
            case "gamemode_badges_raw":
                return gm != null ? gm.getBadgesRaw(profile.getGamemodes()) : "";
            case "lives":
                return String.valueOf(profile.getCurrentLives());
            case "max_lives":
                return String.valueOf(profile.getMaxLives());
            case "is_dead":
                return String.valueOf(profile.isDead());
            case "is_ironman":
                return String.valueOf(profile.isIronman());
            case "is_hardcore":
                return String.valueOf(profile.isHardcore());
            case "is_hunted":
                return String.valueOf(profile.isHunted());
            case "is_cursed":
                return String.valueOf(profile.isCursed());
            default:
                return null;
        }
    }
}
