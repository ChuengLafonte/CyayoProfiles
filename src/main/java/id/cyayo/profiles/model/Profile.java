package id.cyayo.profiles.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public class Profile {

    private int id;
    private UUID profileUuid;
    private final UUID playerUuid;
    private final int slot;
    private String classId;
    private int level;
    private double xp;
    private double health;
    private int hunger;
    private Location lastLocation;
    private ItemStack[] inventoryContents;
    private ItemStack[] armorContents;
    private ItemStack offhandContent;
    private Collection<PotionEffect> activePotionEffects;
    private long createdAt;
    private long updatedAt;

    // Challenge Gamemodes
    private final Set<String> gamemodes = new CopyOnWriteArraySet<>();
    private int maxLives = 1;
    private int currentLives = 1;
    private boolean dead = false;
    private long fallenAt = 0L;
    private String fallenReason = "";
    private ItemStack[] ironmanEnderChest = new ItemStack[27];
    private double ironmanBalance = 0.0;

    public Profile(UUID playerUuid, int slot, String classId) {
        this.profileUuid = UUID.randomUUID();
        this.playerUuid = playerUuid;
        this.slot = slot;
        this.classId = classId;
        this.level = 1;
        this.xp = 0.0;
        this.health = 20.0;
        this.hunger = 20;
        this.inventoryContents = new ItemStack[36];
        this.armorContents = new ItemStack[4];
        this.offhandContent = null;
        this.activePotionEffects = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getProfileUuid() {
        if (profileUuid == null) {
            profileUuid = UUID.randomUUID();
        }
        return profileUuid;
    }

    public void setProfileUuid(UUID profileUuid) {
        this.profileUuid = profileUuid != null ? profileUuid : UUID.randomUUID();
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getSlot() {
        return slot;
    }

    public String getClassId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = xp;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public int getHunger() {
        return hunger;
    }

    public void setHunger(int hunger) {
        this.hunger = hunger;
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void setLastLocation(Location lastLocation) {
        this.lastLocation = lastLocation;
    }

    public ItemStack[] getInventoryContents() {
        return inventoryContents;
    }

    public void setInventoryContents(ItemStack[] inventoryContents) {
        this.inventoryContents = inventoryContents;
    }

    public ItemStack[] getArmorContents() {
        return armorContents;
    }

    public void setArmorContents(ItemStack[] armorContents) {
        this.armorContents = armorContents;
    }

    public ItemStack getOffhandContent() {
        return offhandContent;
    }

    public void setOffhandContent(ItemStack offhandContent) {
        this.offhandContent = offhandContent;
    }

    public Collection<PotionEffect> getActivePotionEffects() {
        return activePotionEffects != null ? activePotionEffects : new ArrayList<>();
    }

    public void setActivePotionEffects(Collection<PotionEffect> activePotionEffects) {
        this.activePotionEffects = activePotionEffects;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    // --- Challenge Gamemodes ---

    public Set<String> getGamemodes() {
        return gamemodes;
    }

    public void setGamemodes(Collection<String> modes) {
        this.gamemodes.clear();
        if (modes != null) {
            for (String mode : modes) {
                if (mode != null && !mode.trim().isEmpty()) {
                    this.gamemodes.add(mode.trim().toLowerCase());
                }
            }
        }
    }

    public boolean hasGamemode(String modeId) {
        return modeId != null && gamemodes.contains(modeId.toLowerCase());
    }

    public boolean isIronman() {
        return hasGamemode("ironman");
    }

    public boolean isHardcore() {
        return hasGamemode("hardcore");
    }

    public boolean isHunted() {
        return hasGamemode("hunted");
    }

    public boolean isCursed() {
        return hasGamemode("cursed");
    }

    public int getMaxLives() {
        return maxLives;
    }

    public void setMaxLives(int maxLives) {
        this.maxLives = Math.max(1, maxLives);
    }

    public int getCurrentLives() {
        return currentLives;
    }

    public void setCurrentLives(int currentLives) {
        this.currentLives = Math.max(0, currentLives);
    }

    public boolean isDead() {
        return dead;
    }

    public void setDead(boolean dead) {
        this.dead = dead;
    }

    public long getFallenAt() {
        return fallenAt;
    }

    public void setFallenAt(long fallenAt) {
        this.fallenAt = Math.max(0L, fallenAt);
    }

    public String getFallenReason() {
        return fallenReason != null ? fallenReason : "";
    }

    public void setFallenReason(String fallenReason) {
        this.fallenReason = fallenReason != null ? fallenReason : "";
    }

    public ItemStack[] getIronmanEnderChest() {
        if (ironmanEnderChest == null || ironmanEnderChest.length != 27) {
            ironmanEnderChest = new ItemStack[27];
        }
        return ironmanEnderChest;
    }

    public void setIronmanEnderChest(ItemStack[] ironmanEnderChest) {
        if (ironmanEnderChest != null && ironmanEnderChest.length == 27) {
            this.ironmanEnderChest = ironmanEnderChest;
        } else if (ironmanEnderChest != null) {
            this.ironmanEnderChest = Arrays.copyOf(ironmanEnderChest, 27);
        } else {
            this.ironmanEnderChest = new ItemStack[27];
        }
    }

    public double getIronmanBalance() {
        return ironmanBalance;
    }

    public void setIronmanBalance(double ironmanBalance) {
        this.ironmanBalance = Math.max(0.0, ironmanBalance);
    }
}
