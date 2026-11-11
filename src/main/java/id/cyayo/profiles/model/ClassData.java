package id.cyayo.profiles.model;

import id.cyayo.profiles.util.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClassData {

    private final String id;
    private final String displayName;
    private final String plainDisplayName;
    private final List<String> description;
    private final double baseHealth;
    private final int baseHunger;
    private final double walkSpeed;
    private final List<String> allowedGear;
    private final boolean starterKitEnabled;
    private final List<ItemStack> starterKitItems;
    private final List<StarterKitEntry> starterKitEntries;
    private final Material iconMaterial;
    private final int customModelData;
    private final String iconName;
    private final List<String> iconLore;
    private final String permission;
    private final boolean hideWithoutPermission;
    private final String lockedHint;

    public ClassData(String id, String displayName, List<String> description, double baseHealth, int baseHunger,
                     double walkSpeed, List<String> allowedGear, boolean starterKitEnabled,
                     List<ItemStack> starterKitItems, Material iconMaterial, int customModelData,
                     String iconName, List<String> iconLore) {
        this(id, displayName, description, baseHealth, baseHunger, walkSpeed, allowedGear,
                starterKitEnabled, starterKitItems, null, iconMaterial, customModelData, iconName, iconLore,
                "", false, "");
    }

    public ClassData(String id, String displayName, List<String> description, double baseHealth, int baseHunger,
                     double walkSpeed, List<String> allowedGear, boolean starterKitEnabled,
                     List<ItemStack> starterKitItems, Material iconMaterial, int customModelData,
                     String iconName, List<String> iconLore,
                     String permission, boolean hideWithoutPermission, String lockedHint) {
        this(id, displayName, description, baseHealth, baseHunger, walkSpeed, allowedGear,
                starterKitEnabled, starterKitItems, null, iconMaterial, customModelData, iconName, iconLore,
                permission, hideWithoutPermission, lockedHint);
    }

    public ClassData(String id, String displayName, List<String> description, double baseHealth, int baseHunger,
                     double walkSpeed, List<String> allowedGear, boolean starterKitEnabled,
                     List<ItemStack> starterKitItems, List<StarterKitEntry> starterKitEntries,
                     Material iconMaterial, int customModelData, String iconName, List<String> iconLore,
                     String permission, boolean hideWithoutPermission, String lockedHint) {
        this.id = id;
        this.displayName = displayName;
        this.plainDisplayName = ColorUtils.stripColor(displayName);
        this.description = description != null ? description : Collections.emptyList();
        this.baseHealth = baseHealth > 0 ? baseHealth : 20.0;
        this.baseHunger = baseHunger > 0 ? baseHunger : 20;
        this.walkSpeed = walkSpeed > 0 ? walkSpeed : 0.2;
        
        List<String> upperGear = new ArrayList<>();
        if (allowedGear != null) {
            for (String g : allowedGear) {
                if (g != null && !g.trim().isEmpty()) {
                    upperGear.add(g.trim().toUpperCase());
                }
            }
        }
        this.allowedGear = Collections.unmodifiableList(upperGear);
        this.starterKitEnabled = starterKitEnabled;
        if (starterKitEntries != null) {
            this.starterKitEntries = Collections.unmodifiableList(new ArrayList<>(starterKitEntries));
        } else if (starterKitItems != null && !starterKitItems.isEmpty()) {
            List<StarterKitEntry> wrapped = new ArrayList<>();
            for (ItemStack item : starterKitItems) {
                if (item != null) wrapped.add(StarterKitEntry.vanilla(item));
            }
            this.starterKitEntries = Collections.unmodifiableList(wrapped);
        } else {
            this.starterKitEntries = Collections.emptyList();
        }
        this.starterKitItems = starterKitItems != null ? starterKitItems : Collections.emptyList();
        this.iconMaterial = iconMaterial != null ? iconMaterial : Material.IRON_SWORD;
        this.customModelData = customModelData;
        this.iconName = iconName != null ? iconName : displayName;
        this.iconLore = iconLore != null ? iconLore : Collections.emptyList();
        this.permission = (permission != null && !permission.trim().isEmpty() && !permission.trim().equalsIgnoreCase("none"))
                ? permission.trim()
                : "";
        this.hideWithoutPermission = hideWithoutPermission;
        this.lockedHint = lockedHint != null ? lockedHint : "";
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPlainDisplayName() {
        return plainDisplayName;
    }

    public List<String> getDescription() {
        return description;
    }

    public double getBaseHealth() {
        return baseHealth;
    }

    public int getBaseHunger() {
        return baseHunger;
    }

    public double getWalkSpeed() {
        return walkSpeed;
    }

    public List<String> getAllowedGear() {
        return allowedGear;
    }

    public boolean isGearAllowed(String gearType) {
        if (gearType == null || gearType.trim().isEmpty()) return true;
        String upper = gearType.trim().toUpperCase();
        return allowedGear.contains(upper);
    }

    public boolean isStarterKitEnabled() {
        return starterKitEnabled;
    }

    public List<ItemStack> getStarterKitItems() {
        return starterKitItems;
    }

    public List<StarterKitEntry> getStarterKitEntries() {
        return starterKitEntries;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getIconName() {
        return iconName;
    }

    public List<String> getIconLore() {
        return iconLore;
    }

    public String getPermission() {
        return permission;
    }

    public boolean isHideWithoutPermission() {
        return hideWithoutPermission;
    }

    public String getLockedHint() {
        return lockedHint;
    }

    public boolean requiresPermission() {
        return permission != null && !permission.isEmpty();
    }

    public boolean hasPermission(Player player) {
        if (!requiresPermission()) {
            return true;
        }
        return player != null && player.hasPermission(permission);
    }

    public static final class StarterKitEntry {
        private final ItemStack vanillaItem;
        private final String mmoType;
        private final String mmoId;
        private final int amount;
        private final String customName;
        private final List<String> customLore;
        private final int customModelData;

        private StarterKitEntry(ItemStack vanillaItem, String mmoType, String mmoId, int amount,
                                String customName, List<String> customLore, int customModelData) {
            this.vanillaItem = vanillaItem;
            this.mmoType = mmoType;
            this.mmoId = mmoId;
            this.amount = Math.max(1, amount);
            this.customName = customName;
            this.customLore = customLore != null ? Collections.unmodifiableList(new ArrayList<>(customLore)) : null;
            this.customModelData = customModelData;
        }

        public static StarterKitEntry vanilla(ItemStack item) {
            return new StarterKitEntry(item, null, null, item != null ? item.getAmount() : 1, null, null, 0);
        }

        public static StarterKitEntry mmoItem(String mmoType, String mmoId, int amount,
                                              String customName, List<String> customLore, int customModelData) {
            return new StarterKitEntry(null, mmoType, mmoId, amount, customName, customLore, customModelData);
        }

        public boolean isMmoItem() {
            return mmoType != null && mmoId != null;
        }

        public ItemStack getVanillaItem() {
            return vanillaItem;
        }

        public String getMmoType() {
            return mmoType;
        }

        public String getMmoId() {
            return mmoId;
        }

        public int getAmount() {
            return amount;
        }

        public String getCustomName() {
            return customName;
        }

        public List<String> getCustomLore() {
            return customLore;
        }

        public int getCustomModelData() {
            return customModelData;
        }
    }
}

