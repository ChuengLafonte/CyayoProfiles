package id.cyayo.profiles.manager;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.api.event.ProfileLevelUpEvent;
import id.cyayo.profiles.model.ClassData;
import id.cyayo.profiles.model.Profile;
import id.cyayo.profiles.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class LevelManager {

    private final CyayoProfiles plugin;
    private File levelsFile;
    private FileConfiguration levelsConfig;

    private boolean enabled = true;
    private int maxLevel = 60;
    private String formulaType = "EXPONENTIAL";
    private double baseXp = 100.0;
    private double curveMultiplier = 1.5;
    private String maxXpText = "MAX";

    public LevelManager(CyayoProfiles plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        levelsFile = new File(plugin.getDataFolder(), "levels.yml");
        if (!levelsFile.exists()) {
            plugin.saveResource("levels.yml", false);
        }
        levelsConfig = YamlConfiguration.loadConfiguration(levelsFile);

        this.enabled = levelsConfig.getBoolean("enabled", true);
        this.maxLevel = levelsConfig.getInt("max_level", 60);
        this.formulaType = levelsConfig.getString("formula.type", "EXPONENTIAL").toUpperCase();
        this.baseXp = levelsConfig.getDouble("formula.base_xp", 100.0);
        this.curveMultiplier = levelsConfig.getDouble("formula.curve_multiplier", 1.5);
        this.maxXpText = levelsConfig.getString("max_xp_text", "MAX");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public String getMaxXpText() {
        return maxXpText;
    }

    public double getRequiredXp(int level) {
        if (level >= maxLevel) {
            return 0.0;
        }
        if ("FIXED".equalsIgnoreCase(formulaType)) {
            return levelsConfig.getDouble("levels." + level + ".required_xp", baseXp * level);
        }
        return Math.round(baseXp * Math.pow(level, curveMultiplier));
    }

    public void addXp(Player player, Profile profile, double amount) {
        addXp(player, profile, amount, true);
    }

    public void addXp(Player player, Profile profile, double amount, boolean applyGamemodeBonus) {
        if (!enabled || profile == null || amount <= 0) return;

        double finalAmount = amount;
        if (applyGamemodeBonus && plugin.getGamemodeManager() != null) {
            finalAmount = amount * plugin.getGamemodeManager().getClassXpMultiplier(profile);
        }

        if (profile.getLevel() >= maxLevel) {
            if (profile.getXp() > 0) {
                profile.setXp(0.0);
            }
            if (levelsConfig.getBoolean("action_bar.enabled", true) &&
                levelsConfig.getBoolean("action_bar.show_on_max_level", false)) {
                sendMaxLevelActionBar(player, profile);
            }
            return;
        }

        profile.setXp(profile.getXp() + finalAmount);

        while (profile.getLevel() < maxLevel) {
            double reqXp = getRequiredXp(profile.getLevel());
            if (reqXp > 0 && profile.getXp() >= reqXp) {
                profile.setXp(profile.getXp() - reqXp);
                int oldLevel = profile.getLevel();
                int newLevel = oldLevel + 1;
                profile.setLevel(newLevel);

                triggerLevelUp(player, profile, oldLevel, newLevel);
            } else {
                break;
            }
        }

        if (profile.getLevel() >= maxLevel) {
            profile.setXp(0.0);
        }

        // Send Action Bar selaras dengan CyayoGathering
        if (levelsConfig.getBoolean("action_bar.enabled", true)) {
            if (profile.getLevel() < maxLevel) {
                sendActionBarNotification(player, profile, finalAmount);
            } else if (levelsConfig.getBoolean("action_bar.show_on_max_level", false)) {
                sendMaxLevelActionBar(player, profile);
            }
        }
    }

    public void setLevel(Player player, Profile profile, int newLevel) {
        if (profile == null) return;
        int oldLevel = profile.getLevel();
        int clamped = Math.max(1, Math.min(newLevel, maxLevel));
        profile.setLevel(clamped);
        profile.setXp(0.0);

        if (clamped > oldLevel) {
            for (int lvl = oldLevel + 1; lvl <= clamped; lvl++) {
                triggerLevelUp(player, profile, lvl - 1, lvl);
            }
        }
    }

    private void sendMaxLevelActionBar(Player player, Profile profile) {
        String format = levelsConfig.getString("action_bar.max_level_format", "none");
        if (ColorUtils.isNoneOrEmpty(format)) return;

        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
        String classDisplay = classData != null ? classData.getDisplayName() : profile.getClassId();
        String classPlain = classData != null ? classData.getPlainDisplayName() : profile.getClassId();

        String msg = format;
        msg = ColorUtils.replaceBoth(msg, "class", classDisplay);
        msg = ColorUtils.replaceBoth(msg, "class_plain", classPlain);
        msg = ColorUtils.replaceBoth(msg, "max_level", String.valueOf(maxLevel));
        ColorUtils.sendActionBar(player, msg);
    }

    private void sendActionBarNotification(Player player, Profile profile, double gainedXp) {
        String format = levelsConfig.getString("action_bar.format", "<color:#55FF55>+{xp} {class} XP &8(&e{current_xp}&7/&a{req_xp}&8) &7[{progress_bar}&7]");
        if (ColorUtils.isNoneOrEmpty(format)) return;

        int currentLvl = profile.getLevel();
        double currentXp = profile.getXp();
        double req = getRequiredXp(currentLvl);
        double pct = req > 0 ? Math.min(100.0, (currentXp / req) * 100.0) : 100.0;

        int barLen = levelsConfig.getInt("action_bar.progress_bar.length", 10);
        String barSymbol = levelsConfig.getString("action_bar.progress_bar.symbol", "■");
        String compCol = levelsConfig.getString("action_bar.progress_bar.completed_color", "&a");
        String remCol = levelsConfig.getString("action_bar.progress_bar.remaining_color", "&7");

        String bar = getProgressBar(currentXp, req, barLen, barSymbol, compCol, remCol);

        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
        String classDisplay = classData != null ? classData.getDisplayName() : profile.getClassId();
        String classPlain = classData != null ? classData.getPlainDisplayName() : profile.getClassId();

        String msg = format;
        msg = ColorUtils.replaceBoth(msg, "xp", String.format(Locale.US, "%.0f", gainedXp));
        msg = ColorUtils.replaceBoth(msg, "class", classDisplay);
        msg = ColorUtils.replaceBoth(msg, "class_plain", classPlain);
        msg = ColorUtils.replaceBoth(msg, "current_xp", String.format(Locale.US, "%,.0f", currentXp));
        msg = ColorUtils.replaceBoth(msg, "req_xp", String.format(Locale.US, "%,.0f", req));
        msg = ColorUtils.replaceBoth(msg, "percent", String.format(Locale.US, "%.1f", pct));
        msg = ColorUtils.replaceBoth(msg, "progress_bar", bar);

        ColorUtils.sendActionBar(player, msg);
    }

    private void triggerLevelUp(Player player, Profile profile, int oldLevel, int newLevel) {
        Bukkit.getPluginManager().callEvent(new ProfileLevelUpEvent(player, profile, oldLevel, newLevel));

        ClassData classData = plugin.getClassManager().getClass(profile.getClassId());
        String classDisplay = classData != null ? classData.getDisplayName() : profile.getClassId();
        String classPlain = classData != null ? classData.getPlainDisplayName() : profile.getClassId();

        ConfigurationSection milestoneSec = levelsConfig.getConfigurationSection("rewards.milestones." + newLevel);
        ConfigurationSection perLevelSec = levelsConfig.getConfigurationSection("rewards.per_level");

        // Milestones menggantikan per_level jika level tersebut terdaftar di milestones
        ConfigurationSection activeSec = milestoneSec != null ? milestoneSec : perLevelSec;
        if (activeSec == null) return;

        // 1. Sound
        String sound = activeSec.getString("sound", perLevelSec != null ? perLevelSec.getString("sound", "none") : "none");
        ColorUtils.playSound(player, sound);

        // 2. Title & Subtitle
        String title = activeSec.getString("title", perLevelSec != null ? perLevelSec.getString("title", "none") : "none");
        String subtitle = activeSec.getString("subtitle", perLevelSec != null ? perLevelSec.getString("subtitle", "none") : "none");
        title = parseLevelPlaceholders(title, player, classDisplay, classPlain, newLevel);
        subtitle = parseLevelPlaceholders(subtitle, player, classDisplay, classPlain, newLevel);
        ColorUtils.sendTitle(player, title, subtitle, 5, 45, 10);

        // 3. Chat message (supports single string or list, including " " blank lines)
        ConfigurationSection chatSrc = activeSec.contains("chat_message") ? activeSec : (perLevelSec != null ? perLevelSec : activeSec);
        for (String line : ColorUtils.getMessageLines(chatSrc, "chat_message")) {
            ColorUtils.sendMessage(player, parseLevelPlaceholders(line, player, classDisplay, classPlain, newLevel));
        }

        // 4. Broadcast (supports single string or list, hanya untuk pemain lain & konsol)
        ConfigurationSection bcSrc = activeSec.contains("broadcast") ? activeSec : (perLevelSec != null ? perLevelSec : activeSec);
        List<String> broadcastLines = ColorUtils.getMessageLines(bcSrc, "broadcast");
        if (!broadcastLines.isEmpty()) {
            for (String line : broadcastLines) {
                String broadcastMsg = parseLevelPlaceholders(line, player, classDisplay, classPlain, newLevel);
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.getUniqueId().equals(player.getUniqueId())) {
                        ColorUtils.sendMessage(other, broadcastMsg);
                    }
                }
                ColorUtils.sendMessage(Bukkit.getConsoleSender(), broadcastMsg);
            }
        }

        // 5. Reward commands
        List<String> rewardCmds = activeSec.getStringList("commands");
        if (rewardCmds != null && !rewardCmds.isEmpty()) {
            for (String cmd : rewardCmds) {
                if (ColorUtils.isNoneOrEmpty(cmd)) continue;
                String parsed = ColorUtils.replaceBoth(cmd, "player", player.getName());
                parsed = ColorUtils.replaceBoth(parsed, "level", String.valueOf(newLevel));
                parsed = ColorUtils.replaceBoth(parsed, "class", profile.getClassId());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }
    }

    private String parseLevelPlaceholders(String text, Player player, String classDisplay, String classPlain, int newLevel) {
        if (text == null || ColorUtils.isNone(text)) return "none";
        String res = text;
        res = ColorUtils.replaceBoth(res, "player", player.getName());
        res = ColorUtils.replaceBoth(res, "class", classDisplay);
        res = ColorUtils.replaceBoth(res, "class_plain", classPlain);
        res = ColorUtils.replaceBoth(res, "level", String.valueOf(newLevel));
        return res;
    }

    public String getProgressBar(double current, double max, int totalBars, String completedColor, String remainingColor) {
        return getProgressBar(current, max, totalBars, "■", completedColor, remainingColor);
    }

    public String getProgressBar(double current, double max, int totalBars, String symbol, String completedColor, String remainingColor) {
        if (max <= 0) return completedColor + symbol.repeat(totalBars);
        double pct = Math.min(1.0, Math.max(0.0, current / max));
        int completed = (int) Math.round(totalBars * pct);
        if (completed > totalBars) completed = totalBars;
        int remaining = Math.max(0, totalBars - completed);
        return completedColor + symbol.repeat(completed) + remainingColor + symbol.repeat(remaining);
    }
}
