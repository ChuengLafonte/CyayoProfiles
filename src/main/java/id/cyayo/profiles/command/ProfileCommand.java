package id.cyayo.profiles.command;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.util.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ProfileCommand implements CommandExecutor {

    private final CyayoProfiles plugin;

    public ProfileCommand(CyayoProfiles plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ColorUtils.sendMessage(sender, plugin.getMessagesConfig().getString("player_only"));
            return true;
        }

        String usePerm = plugin.getConfig().getString("permissions.use", "cyayoprofiles.use");
        if (!player.hasPermission(usePerm)) {
            ColorUtils.sendMessage(player, plugin.getMessagesConfig().getString("no_permission"));
            return true;
        }

        if (args.length >= 1) {
            if (args[0].equalsIgnoreCase("switch") && args.length >= 2) {
                try {
                    int slot = Integer.parseInt(args[1]);
                    plugin.getProfileManager().switchProfile(player, slot);
                    return true;
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    int slot = Integer.parseInt(args[0]);
                    plugin.getProfileManager().switchProfile(player, slot);
                    return true;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        plugin.getGuiManager().openProfilesMenu(player);
        return true;
    }
}

