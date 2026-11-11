package id.cyayo.profiles.command;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.model.PlayerProfileHolder;
import id.cyayo.profiles.model.Profile;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class ProfileTabCompleter implements TabCompleter {

    private final CyayoProfiles plugin;
    private static final List<String> ADMIN_SUBS = Arrays.asList(
            "reload", "open", "setspawn", "switch", "setlevel", "addxp",
            "delete", "info", "help"
    );
    private static final List<String> XP_SUGGESTIONS = Arrays.asList("100", "500", "1000", "5000", "10000");

    public ProfileTabCompleter(CyayoProfiles plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmdName = command.getName().toLowerCase();

        // /profile [switch] [slot]
        if (cmdName.equals("profile") || cmdName.equals("profiles") || cmdName.equals("character") || cmdName.equals("char")) {
            String usePerm = plugin.getConfig().getString("permissions.use", "cyayoprofiles.use");
            if (!sender.hasPermission(usePerm)) {
                return Collections.emptyList();
            }
            if (args.length == 1) {
                List<String> list = new ArrayList<>();
                list.add("switch");
                if (sender instanceof Player p) {
                    addPlayerSlots(p, list, true);
                }
                return filterStartingWith(args[0], list);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("switch")) {
                if (sender instanceof Player p) {
                    List<String> list = new ArrayList<>();
                    addPlayerSlots(p, list, true);
                    return filterStartingWith(args[1], list);
                }
            }
            return Collections.emptyList();
        }

        // /cyayoprofiles <subcommand> ...
        if (cmdName.equals("cyayoprofiles") || cmdName.equals("cprofiles") || cmdName.equals("cprofile") || cmdName.equals("cyayoprofile")) {
            String adminPerm = plugin.getConfig().getString("permissions.admin", "cyayoprofiles.admin");
            if (!sender.hasPermission(adminPerm)) {
                return Collections.emptyList();
            }

            if (args.length == 1) {
                return filterStartingWith(args[0], ADMIN_SUBS);
            }

            String sub = args[0].toLowerCase();
            if (sub.equals("reload") || sub.equals("setspawn") || sub.equals("help")) {
                return Collections.emptyList();
            }

            if (args.length == 2) {
                List<String> players = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    players.add(online.getName());
                }
                return filterStartingWith(args[1], players);
            }

            Player target = Bukkit.getPlayer(args[1]);

            if (args.length == 3) {
                if (sub.equals("switch") || sub.equals("delete")) {
                    if (target != null) {
                        List<String> slots = new ArrayList<>();
                        addPlayerSlots(target, slots, sub.equals("switch"));
                        return filterStartingWith(args[2], slots);
                    }
                    return Collections.emptyList();
                }

                if (sub.equals("setlevel")) {
                    List<String> options = new ArrayList<>();
                    if (target != null) {
                        options.addAll(getPlayerClassSelectors(target));
                    }
                    options.add("active");
                    if (isNumeric(args[2])) {
                        options.addAll(getLevelSuggestions());
                    }
                    return filterStartingWith(args[2], options);
                }

                if (sub.equals("addxp")) {
                    List<String> options = new ArrayList<>();
                    if (target != null) {
                        options.addAll(getPlayerClassSelectors(target));
                    }
                    options.addAll(XP_SUGGESTIONS);
                    return filterStartingWith(args[2], options);
                }
            }

            if (args.length == 4) {
                if (sub.equals("setlevel")) {
                    // Jika arg 3 bukan angka (admin memilih class/slot/active), sarankan target level
                    if (!isNumeric(args[2]) || (target != null && plugin.getProfileManager().getHolder(target) != null
                            && plugin.getProfileManager().getHolder(target).getProfile(parseIntSafe(args[2])) != null)) {
                        return filterStartingWith(args[3], getLevelSuggestions());
                    }
                    return Collections.emptyList();
                }

                if (sub.equals("addxp")) {
                    if (!isNumeric(args[2])) {
                        return filterStartingWith(args[3], XP_SUGGESTIONS);
                    }
                    return Collections.emptyList();
                }
            }
        }

        return Collections.emptyList();
    }

    private void addPlayerSlots(Player player, List<String> out, boolean excludeDead) {
        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
        if (holder == null) return;

        List<Integer> sortedSlots = new ArrayList<>(holder.getProfiles().keySet());
        Collections.sort(sortedSlots);
        for (Integer slot : sortedSlots) {
            Profile p = holder.getProfile(slot);
            if (p != null && (!excludeDead || !p.isDead())) {
                out.add(String.valueOf(slot));
            }
        }
    }

    private List<String> getPlayerClassSelectors(Player player) {
        PlayerProfileHolder holder = plugin.getProfileManager().getHolder(player);
        if (holder == null || holder.getProfiles().isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> classCounts = new HashMap<>();
        List<Profile> ordered = new ArrayList<>(holder.getProfiles().values());
        ordered.sort(Comparator.comparingInt(Profile::getSlot));

        for (Profile p : ordered) {
            String cid = p.getClassId().toLowerCase();
            classCounts.merge(cid, 1, Integer::sum);
        }

        List<String> selectors = new ArrayList<>();
        for (Profile p : ordered) {
            String cid = p.getClassId().toLowerCase();
            if (classCounts.getOrDefault(cid, 0) > 1) {
                selectors.add(cid + "#" + p.getSlot());
            } else {
                selectors.add(cid);
            }
        }
        return selectors;
    }

    private List<String> getLevelSuggestions() {
        int max = plugin.getLevelManager() != null ? plugin.getLevelManager().getMaxLevel() : 60;
        Set<String> levels = new LinkedHashSet<>();
        for (int candidate : new int[]{1, 5, 10, 20, 30, 50}) {
            if (candidate <= max) {
                levels.add(String.valueOf(candidate));
            }
        }
        levels.add(String.valueOf(max));
        return new ArrayList<>(levels);
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private List<String> filterStartingWith(String prefix, List<String> options) {
        if (prefix == null || prefix.isEmpty()) return options;
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String s : options) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }
}
