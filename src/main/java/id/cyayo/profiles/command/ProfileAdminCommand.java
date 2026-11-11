package id.cyayo.profiles.command;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.model.ClassData;
import id.cyayo.profiles.model.PlayerProfileHolder;
import id.cyayo.profiles.model.Profile;
import id.cyayo.profiles.util.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class ProfileAdminCommand implements CommandExecutor {

    private final CyayoProfiles plugin;

    public ProfileAdminCommand(CyayoProfiles plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();
        FileConfiguration messages = plugin.getMessagesConfig();

        String adminPerm = config.getString("permissions.admin", "cyayoprofiles.admin");
        if (!sender.hasPermission(adminPerm)) {
            ColorUtils.sendMessage(sender, messages.getString("no_permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                plugin.reloadAll();
                ColorUtils.sendMessage(sender, messages.getString("admin_reload_success", "<green>Reload successful!"));
                return true;

            case "open":
                if (args.length < 2) {
                    ColorUtils.sendMessage(sender, "<yellow>Usage: /" + label + " open <player>");
                    return true;
                }
                Player targetOpen = Bukkit.getPlayer(args[1]);
                if (targetOpen == null) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(messages.getString("player_not_found"), "player", args[1]));
                    return true;
                }
                plugin.getGuiManager().openProfilesMenu(targetOpen);
                String openMsg = ColorUtils.replaceBoth(messages.getString("admin_open_success", "<green>Menu opened!"),
                        "player", targetOpen.getName());
                ColorUtils.sendMessage(sender, openMsg);
                return true;

            case "setspawn":
                if (!(sender instanceof Player playerSender)) {
                    ColorUtils.sendMessage(sender, messages.getString("player_only"));
                    return true;
                }
                plugin.getProfileManager().setNewProfileSpawnLocation(playerSender.getLocation());
                String spawnMsg = messages.getString("admin_setspawn_success", "<green>New profile spawn saved!");
                spawnMsg = ColorUtils.replaceBoth(spawnMsg, "world", playerSender.getWorld().getName());
                spawnMsg = ColorUtils.replaceBoth(spawnMsg, "x", String.valueOf(playerSender.getLocation().getBlockX()));
                spawnMsg = ColorUtils.replaceBoth(spawnMsg, "y", String.valueOf(playerSender.getLocation().getBlockY()));
                spawnMsg = ColorUtils.replaceBoth(spawnMsg, "z", String.valueOf(playerSender.getLocation().getBlockZ()));
                ColorUtils.sendMessage(sender, spawnMsg);
                return true;

            case "switch":
                if (args.length < 3) {
                    ColorUtils.sendMessage(sender, "<yellow>Usage: /" + label + " switch <player> <slot>");
                    return true;
                }
                Player targetSwitch = Bukkit.getPlayer(args[1]);
                if (targetSwitch == null) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(messages.getString("player_not_found"), "player", args[1]));
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[2]);
                    plugin.getProfileManager().switchProfile(targetSwitch, slot, true, true);
                    ColorUtils.sendMessage(sender, "<green>Forced profile switch for " + targetSwitch.getName() + " to slot " + slot + ".");
                } catch (NumberFormatException e) {
                    ColorUtils.sendMessage(sender, "<red>Slot number must be a valid integer!");
                }
                return true;

            case "setlevel":
                if (args.length < 3) {
                    ColorUtils.sendMessage(sender, "<yellow>Usage: /" + label + " setlevel <player> [class|slot] <level>");
                    return true;
                }
                Player targetLvl = Bukkit.getPlayer(args[1]);
                if (targetLvl == null) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(messages.getString("player_not_found"), "player", args[1]));
                    return true;
                }
                PlayerProfileHolder lvlHolder = plugin.getProfileManager().getHolder(targetLvl);
                if (lvlHolder == null || lvlHolder.getProfiles().isEmpty()) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(
                            ColorUtils.replaceBoth(messages.getString("admin_profile_not_found_for_class", "<red>Player {player} does not have a profile yet!"),
                                    "player", targetLvl.getName()),
                            "target", args.length >= 4 ? args[2] : "active"));
                    return true;
                }

                String lvlSelector = args.length >= 4 ? args[2] : "active";
                String lvlRaw = args.length >= 4 ? args[3] : args[2];
                Profile lvlProfile = resolveTargetProfile(lvlHolder, lvlSelector);
                if (lvlProfile == null) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(
                            ColorUtils.replaceBoth(messages.getString("admin_profile_not_found_for_class", "<red>Profile/class '{target}' not found!"),
                                    "player", targetLvl.getName()),
                            "target", lvlSelector));
                    return true;
                }
                try {
                    int level = Integer.parseInt(lvlRaw);
                    plugin.getLevelManager().setLevel(targetLvl, lvlProfile, level);
                    plugin.getProfileStorage().saveProfile(lvlProfile);
                    if (lvlProfile == lvlHolder.getActiveProfile() && plugin.getMmoItemsHook() != null) {
                        plugin.getMmoItemsHook().updateInventory(targetLvl);
                    }

                    String templateKey = args.length >= 4 ? "admin_set_level_class_success" : "admin_set_level_success";
                    String msg = messages.getString(templateKey, "<green>Level updated!");
                    msg = applyProfileMsgPlaceholders(msg, targetLvl, lvlProfile);
                    msg = ColorUtils.replaceBoth(msg, "level", String.valueOf(lvlProfile.getLevel()));
                    ColorUtils.sendMessage(sender, msg);
                } catch (NumberFormatException e) {
                    ColorUtils.sendMessage(sender, "<red>Level must be a valid integer!");
                }
                return true;

            case "addxp":
                if (args.length < 3) {
                    ColorUtils.sendMessage(sender, "<yellow>Usage: /" + label + " addxp <player> [class|slot] <amount>");
                    return true;
                }
                Player targetXp = Bukkit.getPlayer(args[1]);
                if (targetXp == null) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(messages.getString("player_not_found"), "player", args[1]));
                    return true;
                }
                PlayerProfileHolder xpHolder = plugin.getProfileManager().getHolder(targetXp);
                if (xpHolder == null || xpHolder.getProfiles().isEmpty()) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(
                            ColorUtils.replaceBoth(messages.getString("admin_profile_not_found_for_class", "<red>Player {player} does not have a profile yet!"),
                                    "player", targetXp.getName()),
                            "target", args.length >= 4 ? args[2] : "active"));
                    return true;
                }

                String xpSelector = args.length >= 4 ? args[2] : "active";
                String xpRaw = args.length >= 4 ? args[3] : args[2];
                Profile xpProfile = resolveTargetProfile(xpHolder, xpSelector);
                if (xpProfile == null) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(
                            ColorUtils.replaceBoth(messages.getString("admin_profile_not_found_for_class", "<red>Profile/class '{target}' not found!"),
                                    "player", targetXp.getName()),
                            "target", xpSelector));
                    return true;
                }
                try {
                    double amount = Double.parseDouble(xpRaw);
                    plugin.getLevelManager().addXp(targetXp, xpProfile, amount);
                    plugin.getProfileStorage().saveProfile(xpProfile);
                    if (xpProfile == xpHolder.getActiveProfile() && plugin.getMmoItemsHook() != null) {
                        plugin.getMmoItemsHook().updateInventory(targetXp);
                    }
                    String msg = messages.getString("admin_add_xp_success", "<green>XP added successfully!");
                    msg = applyProfileMsgPlaceholders(msg, targetXp, xpProfile);
                    msg = ColorUtils.replaceBoth(msg, "xp", String.valueOf(amount));
                    ColorUtils.sendMessage(sender, msg);
                } catch (NumberFormatException e) {
                    ColorUtils.sendMessage(sender, "<red>XP amount must be a valid number!");
                }
                return true;

            case "delete":
                if (args.length < 3) {
                    ColorUtils.sendMessage(sender, "<yellow>Usage: /" + label + " delete <player> <slot>");
                    return true;
                }
                Player targetDel = Bukkit.getPlayer(args[1]);
                if (targetDel == null) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(messages.getString("player_not_found"), "player", args[1]));
                    return true;
                }
                try {
                    int slot = Integer.parseInt(args[2]);
                    plugin.getProfileManager().deleteProfile(targetDel, slot);
                    String msg = messages.getString("admin_delete_success", "<green>Profile deleted!");
                    msg = ColorUtils.replaceBoth(msg, "player", targetDel.getName());
                    msg = ColorUtils.replaceBoth(msg, "slot", String.valueOf(slot));
                    ColorUtils.sendMessage(sender, msg);
                } catch (NumberFormatException e) {
                    ColorUtils.sendMessage(sender, "<red>Slot number must be a valid integer!");
                }
                return true;

            case "info":
                if (args.length < 2) {
                    ColorUtils.sendMessage(sender, "<yellow>Usage: /" + label + " info <player>");
                    return true;
                }
                Player targetInfo = Bukkit.getPlayer(args[1]);
                if (targetInfo == null) {
                    ColorUtils.sendMessage(sender, ColorUtils.replaceBoth(messages.getString("player_not_found"), "player", args[1]));
                    return true;
                }
                PlayerProfileHolder infoHolder = plugin.getProfileManager().getHolder(targetInfo);
                if (infoHolder == null) {
                    ColorUtils.sendMessage(sender, "<red>Profile data for this player has not been loaded yet.");
                    return true;
                }
                ColorUtils.sendMessage(sender, "<gold><bold>=== Profile Information: " + targetInfo.getName() + " ===</bold>");
                ColorUtils.sendMessage(sender, "<gray>Active Slot: <yellow>#" + infoHolder.getActiveSlot());
                ColorUtils.sendMessage(sender, "<gray>Max Slot Limit: <yellow>" + plugin.getProfileManager().getMaxSlots(targetInfo));
                for (Profile p : infoHolder.getProfiles().values()) {
                    ClassData cData = plugin.getClassManager().getClass(p.getClassId());
                    String cName = cData != null ? cData.getDisplayName() : p.getClassId();
                    String badges = plugin.getGamemodeManager() != null ? plugin.getGamemodeManager().getBadgesRaw(p.getGamemodes()) : "";
                    boolean isAct = (p.getSlot() == infoHolder.getActiveSlot());
                    String stateTag = p.isDead() ? "<dark_red>[FALLEN]" : (isAct ? "<green>[ACTIVE]" : "");
                    ColorUtils.sendMessage(sender, "<gray>Slot #" + p.getSlot() + ": <white>" + cName + " <dark_gray>(" + p.getClassId() + ") " + badges
                            + " <gray>(Lv." + p.getLevel() + ") " + stateTag);
                }
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    public static Profile resolveTargetProfile(PlayerProfileHolder holder, String selector) {
        if (holder == null) return null;
        if (selector == null || selector.trim().isEmpty() || selector.equalsIgnoreCase("active")) {
            return holder.getActiveProfile();
        }
        String clean = selector.trim();

        // Format: class#slot (mis. warrior#2)
        if (clean.contains("#")) {
            String[] parts = clean.split("#", 2);
            try {
                int slot = Integer.parseInt(parts[1].trim());
                Profile bySlot = holder.getProfile(slot);
                if (bySlot != null && (parts[0].trim().isEmpty() || bySlot.getClassId().equalsIgnoreCase(parts[0].trim()))) {
                    return bySlot;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Format: slot:class (mis. 2:warrior)
        if (clean.contains(":")) {
            String[] parts = clean.split(":", 2);
            try {
                int slot = Integer.parseInt(parts[0].trim());
                Profile bySlot = holder.getProfile(slot);
                if (bySlot != null && (parts[1].trim().isEmpty() || bySlot.getClassId().equalsIgnoreCase(parts[1].trim()))) {
                    return bySlot;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        // Format angka slot langsung (mis. 1, 2, 3)
        try {
            int slot = Integer.parseInt(clean);
            Profile bySlot = holder.getProfile(slot);
            if (bySlot != null) {
                return bySlot;
            }
        } catch (NumberFormatException ignored) {
        }

        // Format nama class (prioritaskan profil aktif jika class-nya sama)
        Profile active = holder.getActiveProfile();
        if (active != null && active.getClassId().equalsIgnoreCase(clean)) {
            return active;
        }
        for (Profile p : holder.getProfiles().values()) {
            if (p.getClassId().equalsIgnoreCase(clean)) {
                return p;
            }
        }
        return null;
    }

    private String applyProfileMsgPlaceholders(String text, Player target, Profile profile) {
        ClassData cd = plugin.getClassManager().getClass(profile.getClassId());
        String classDisplay = cd != null ? cd.getDisplayName() : profile.getClassId();
        String classPlain = cd != null ? cd.getPlainDisplayName() : profile.getClassId();
        String res = ColorUtils.replaceBoth(text, "player", target.getName());
        res = ColorUtils.replaceBoth(res, "class", classDisplay);
        res = ColorUtils.replaceBoth(res, "class_plain", classPlain);
        res = ColorUtils.replaceBoth(res, "slot", String.valueOf(profile.getSlot()));
        return res;
    }

    private void sendHelp(CommandSender sender) {
        List<String> helpList = plugin.getMessagesConfig().getStringList("admin_help");
        if (helpList != null && !helpList.isEmpty()) {
            for (String line : helpList) {
                ColorUtils.sendMessage(sender, line);
            }
        } else {
            ColorUtils.sendMessage(sender, "<gold>/cyayoprofiles reload | open | setspawn | switch | setlevel | addxp | delete | info");
        }
    }
}
