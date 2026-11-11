package id.cyayo.profiles.manager;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.model.ClassData;
import id.cyayo.profiles.model.Profile;
import id.cyayo.profiles.util.ColorUtils;
import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassManager {

    private final CyayoProfiles plugin;
    private final Map<String, ClassData> classes = new LinkedHashMap<>();
    private File classesFile;
    private FileConfiguration classesConfig;

    private static final Pattern LORE_REQUIREMENT_PATTERN = Pattern.compile("(?i)(?:required[\\s_]class(?:es)?|class[\\s_]requirement|syarat[\\s_]class)\\s*:\\s*(.+)");
    private static final Pattern LORE_LEVEL_PATTERN = Pattern.compile("(?i)(?:required[\\s_]level|level[\\s_]requirement|syarat[\\s_]level)\\s*:\\s*(\\d+)");

    public ClassManager(CyayoProfiles plugin) {
        this.plugin = plugin;
        loadClasses();
    }

    public void loadClasses() {
        classes.clear();
        classesFile = new File(plugin.getDataFolder(), "classes.yml");
        if (!classesFile.exists()) {
            plugin.saveResource("classes.yml", false);
        }
        classesConfig = YamlConfiguration.loadConfiguration(classesFile);

        ConfigurationSection section = classesConfig.getConfigurationSection("classes");
        if (section == null) {
            plugin.getLogger().warning("No 'classes' section found in classes.yml!");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection classSec = section.getConfigurationSection(key);
            if (classSec == null) continue;

            String id = key.toLowerCase();
            String displayName = classSec.getString("display_name", key);
            List<String> description = classSec.getStringList("description");
            double baseHealth = classSec.getDouble("base_health", 20.0);
            int baseHunger = classSec.getInt("base_hunger", 20);
            double walkSpeed = classSec.getDouble("walk_speed", 0.2);
            List<String> allowedGear = classSec.getStringList("allowed_gear");

            boolean starterKitEnabled = classSec.getBoolean("starter_kit.enabled", false);
            List<ItemStack> starterKitItems = new ArrayList<>();
            List<ClassData.StarterKitEntry> starterKitEntries = new ArrayList<>();
            if (starterKitEnabled && classSec.isList("starter_kit.items")) {
                List<?> rawKitList = classSec.getList("starter_kit.items");
                if (rawKitList != null) {
                    for (Object rawEntry : rawKitList) {
                        ClassData.StarterKitEntry entry = parseStarterKitEntry(id, rawEntry);
                        if (entry != null) {
                            starterKitEntries.add(entry);
                            if (!entry.isMmoItem() && entry.getVanillaItem() != null) {
                                starterKitItems.add(entry.getVanillaItem());
                            }
                        }
                    }
                }
            }

            String matStr = classSec.getString("icon.material", "IRON_SWORD");
            Material iconMat = Material.matchMaterial(matStr);
            if (iconMat == null) iconMat = Material.IRON_SWORD;
            int cmd = classSec.getInt("icon.custom_model_data", 0);
            String iconName = classSec.getString("icon.name", displayName);
            List<String> iconLore = classSec.getStringList("icon.lore");

            String permission = classSec.getString("permission", "");
            boolean hideWithoutPermission = classSec.getBoolean("hide_without_permission", false);
            String lockedHint = classSec.getString("locked_hint", "");

            ClassData classData = new ClassData(id, displayName, description, baseHealth, baseHunger,
                    walkSpeed, allowedGear, starterKitEnabled, starterKitItems, starterKitEntries,
                    iconMat, cmd, iconName, iconLore,
                    permission, hideWithoutPermission, lockedHint);
            classes.put(id, classData);
        }

        plugin.getLogger().info("Loaded " + classes.size() + " classes from classes.yml: " + classes.keySet());
    }

    public List<ItemStack> buildStarterKit(ClassData classData) {
        if (classData == null || !classData.isStarterKitEnabled()) {
            return Collections.emptyList();
        }
        List<ItemStack> result = new ArrayList<>();
        for (ClassData.StarterKitEntry entry : classData.getStarterKitEntries()) {
            if (entry.isMmoItem()) {
                if (plugin.getMmoItemsHook() == null) {
                    plugin.getLogger().warning("Class " + classData.getId() + " has MMOItems starter kit item ("
                            + entry.getMmoType() + ":" + entry.getMmoId() + "), but MMOItems hook is unavailable!");
                    continue;
                }
                ItemStack mmo = plugin.getMmoItemsHook().buildMMOItem(entry.getMmoType(), entry.getMmoId(), entry.getAmount());
                if (mmo != null) {
                    applyMetaOverrides(mmo, entry.getCustomName(), entry.getCustomLore(), entry.getCustomModelData());
                    result.add(mmo);
                }
            } else if (entry.getVanillaItem() != null) {
                result.add(entry.getVanillaItem().clone());
            }
        }
        return result;
    }

    private ClassData.StarterKitEntry parseStarterKitEntry(String classId, Object rawEntry) {
        if (rawEntry instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) return null;

            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("MMOITEMS:")) {
                trimmed = trimmed.substring("MMOITEMS:".length());
            } else if (upper.startsWith("MMOITEM:")) {
                trimmed = trimmed.substring("MMOITEM:".length());
            }
            if (trimmed.contains(":") || trimmed.contains(".")) {
                return parseMmoSpec(classId, trimmed, 1, false, null, null, 0);
            }
            Material mat = Material.matchMaterial(trimmed);
            if (mat == null) {
                plugin.getLogger().warning("Unknown starter kit material '" + str + "' in class " + classId);
                return null;
            }
            return ClassData.StarterKitEntry.vanilla(new ItemStack(mat, 1));
        }

        if (!(rawEntry instanceof Map<?, ?> map)) {
            return null;
        }

        boolean explicitAmount = map.containsKey("amount") && map.get("amount") != null;
        int amount = 1;
        if (explicitAmount) {
            try {
                amount = Math.max(1, Integer.parseInt(map.get("amount").toString().trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        String customName = map.get("name") != null ? map.get("name").toString() : null;
        int customCmd = 0;
        if (map.get("custom_model_data") != null) {
            try {
                customCmd = Integer.parseInt(map.get("custom_model_data").toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        List<String> customLore = null;
        if (map.get("lore") instanceof List<?> rawLore) {
            customLore = new ArrayList<>();
            for (Object line : rawLore) {
                if (line != null) customLore.add(line.toString());
            }
        }

        Object mmoRaw = map.containsKey("mmoitems") ? map.get("mmoitems") : map.get("mmoitem");
        if (mmoRaw != null) {
            return parseMmoSpec(classId, mmoRaw.toString(), amount, explicitAmount, customName, customLore, customCmd);
        }

        if (map.containsKey("mmoitems_type") || map.containsKey("mmoitems_id")
                || (map.containsKey("type") && map.containsKey("id") && !map.containsKey("material"))) {
            Object typeObj = map.containsKey("mmoitems_type") ? map.get("mmoitems_type") : map.get("type");
            Object idObj = map.containsKey("mmoitems_id") ? map.get("mmoitems_id") : map.get("id");
            String typeStr = typeObj != null ? typeObj.toString().trim() : "";
            String idStr = idObj != null ? idObj.toString().trim() : "";
            if (typeStr.isEmpty() || idStr.isEmpty()) {
                plugin.getLogger().warning("Incomplete MMOItems starter kit entry in class " + classId);
                return null;
            }
            return ClassData.StarterKitEntry.mmoItem(typeStr, idStr, amount, customName, customLore, customCmd);
        }

        Object matObj = map.get("material");
        if (matObj == null || matObj.toString().trim().isEmpty()) {
            plugin.getLogger().warning("Starter kit entry in class " + classId + " is missing 'material' or 'mmoitems'!");
            return null;
        }
        Material mat = Material.matchMaterial(matObj.toString().trim());
        if (mat == null) {
            plugin.getLogger().warning("Unknown starter kit material '" + matObj + "' in class " + classId);
            return null;
        }
        ItemStack kitItem = new ItemStack(mat, amount);
        applyMetaOverrides(kitItem, customName, customLore, customCmd);
        return ClassData.StarterKitEntry.vanilla(kitItem);
    }

    private ClassData.StarterKitEntry parseMmoSpec(String classId, String rawSpec, int defaultAmount,
                                                   boolean explicitAmount, String customName,
                                                   List<String> customLore, int customCmd) {
        String spec = rawSpec.trim();
        String[] parts = spec.contains(":") ? spec.split(":", 3) : spec.split("\\.", 3);
        if (parts.length < 2 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty()) {
            plugin.getLogger().warning("Invalid MMOItems starter_kit format in class " + classId + ": '" + rawSpec + "' (expected TYPE:ID)");
            return null;
        }
        String typeStr = parts[0].trim();
        String idStr = parts[1].trim();
        int amount = defaultAmount;
        if (!explicitAmount && parts.length == 3) {
            try {
                amount = Math.max(1, Integer.parseInt(parts[2].trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ClassData.StarterKitEntry.mmoItem(typeStr, idStr, amount, customName, customLore, customCmd);
    }

    private void applyMetaOverrides(ItemStack item, String customName, List<String> customLore, int customCmd) {
        if (item == null || (customName == null && (customLore == null || customLore.isEmpty()) && customCmd <= 0)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (customName != null) {
            meta.setDisplayName(ColorUtils.color(customName));
        }
        if (customLore != null && !customLore.isEmpty()) {
            List<String> colored = new ArrayList<>();
            for (String line : customLore) {
                colored.add(ColorUtils.color(line));
            }
            meta.setLore(colored);
        }
        if (customCmd > 0) {
            meta.setCustomModelData(customCmd);
        }
        item.setItemMeta(meta);
    }

    public ClassData getClass(String id) {
        if (id == null) return null;
        return classes.get(id.toLowerCase());
    }

    public Collection<ClassData> getAllClasses() {
        return Collections.unmodifiableCollection(classes.values());
    }

    public boolean classExists(String id) {
        if (id == null) return false;
        return classes.containsKey(id.toLowerCase());
    }

    public List<String> getRequiredClasses(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return Collections.emptyList();

        List<String> reqs = new ArrayList<>();

        try {
            NBTItem nbt = NBTItem.get(item);
            if (nbt != null) {
                if (nbt.hasTag("REQUIRED_CLASS")) {
                    parseCommaSeparated(nbt.getString("REQUIRED_CLASS"), reqs);
                }
                if (nbt.hasTag("REQUIRED_CLASSES")) {
                    parseCommaSeparated(nbt.getString("REQUIRED_CLASSES"), reqs);
                }
                if (nbt.hasTag("MMOITEMS_REQUIRED_CLASS")) {
                    parseCommaSeparated(nbt.getString("MMOITEMS_REQUIRED_CLASS"), reqs);
                }
            }
        } catch (Throwable ignored) {
        }

        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();
            if (lore != null) {
                for (String line : lore) {
                    String stripped = ChatColor.stripColor(line).trim();
                    Matcher matcher = LORE_REQUIREMENT_PATTERN.matcher(stripped);
                    if (matcher.find()) {
                        String match = matcher.group(1);
                        parseCommaSeparated(match, reqs);
                    }
                }
            }
        }

        return reqs;
    }

    private void parseCommaSeparated(String text, List<String> target) {
        if (text == null || text.trim().isEmpty()) return;
        String[] split = text.split("[,/|]");
        for (String s : split) {
            String clean = s.trim().toUpperCase();
            if (!clean.isEmpty() && !target.contains(clean)) {
                target.add(clean);
            }
        }
    }

    public boolean canUseGear(ClassData classData, ItemStack item) {
        return canUseGear(classData, null, item);
    }

    public boolean canUseGear(ClassData classData, Profile profile, ItemStack item) {
        return checkRestriction(classData, profile, item).allowed;
    }

    public int getRequiredLevel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;

        int reqLevel = 0;
        try {
            NBTItem nbt = NBTItem.get(item);
            if (nbt != null) {
                if (nbt.hasTag("MMOITEMS_REQUIRED_LEVEL")) {
                    reqLevel = Math.max(reqLevel, (int) Math.round(nbt.getDouble("MMOITEMS_REQUIRED_LEVEL")));
                    if (reqLevel <= 0) reqLevel = Math.max(reqLevel, nbt.getInteger("MMOITEMS_REQUIRED_LEVEL"));
                }
                if (nbt.hasTag("REQUIRED_LEVEL")) {
                    reqLevel = Math.max(reqLevel, (int) Math.round(nbt.getDouble("REQUIRED_LEVEL")));
                    if (reqLevel <= 0) reqLevel = Math.max(reqLevel, nbt.getInteger("REQUIRED_LEVEL"));
                }
            }
        } catch (Throwable ignored) {
        }

        if (reqLevel <= 0 && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();
            if (lore != null) {
                for (String line : lore) {
                    String stripped = ChatColor.stripColor(line).trim();
                    Matcher matcher = LORE_LEVEL_PATTERN.matcher(stripped);
                    if (matcher.find()) {
                        try {
                            reqLevel = Math.max(reqLevel, Integer.parseInt(matcher.group(1)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }

        return reqLevel;
    }

    public RestrictionResult checkRestriction(ClassData classData, Profile profile, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return RestrictionResult.ALLOW;
        }
        if (classData == null) {
            return RestrictionResult.BLOCK_NO_PROFILE;
        }

        // 1. Class check
        List<String> requiredClasses = getRequiredClasses(item);
        if (!requiredClasses.isEmpty()) {
            String playerClassId = classData.getId().toUpperCase();
            boolean classMatched = false;
            for (String req : requiredClasses) {
                if (req.equalsIgnoreCase(playerClassId) || classData.isGearAllowed(req)) {
                    classMatched = true;
                    break;
                }
            }
            if (!classMatched) {
                return new RestrictionResult(false, RestrictionType.CLASS, String.join(", ", requiredClasses), "");
            }
        }

        // 2. Class Level check (MMOITEMS_REQUIRED_LEVEL / REQUIRED_LEVEL)
        if (profile != null) {
            int reqLevel = getRequiredLevel(item);
            if (reqLevel > 0 && profile.getLevel() < reqLevel) {
                return new RestrictionResult(false, RestrictionType.LEVEL, String.valueOf(reqLevel), "Level");
            }
        }

        return RestrictionResult.ALLOW;
    }

    public enum RestrictionType {
        NONE,
        NO_PROFILE,
        CLASS,
        LEVEL
    }

    public static class RestrictionResult {
        public static final RestrictionResult ALLOW = new RestrictionResult(true, RestrictionType.NONE, "", "");
        public static final RestrictionResult BLOCK_NO_PROFILE = new RestrictionResult(false, RestrictionType.NO_PROFILE, "", "");

        public final boolean allowed;
        public final RestrictionType type;
        public final String requiredValue;
        public final String attributeName;

        public RestrictionResult(boolean allowed, RestrictionType type, String requiredValue, String attributeName) {
            this.allowed = allowed;
            this.type = type;
            this.requiredValue = requiredValue;
            this.attributeName = attributeName;
        }
    }
}
