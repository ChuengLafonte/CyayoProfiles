package id.cyayo.profiles.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerProfileHolder {

    private final UUID playerUuid;
    private int activeSlot;
    private final Map<Integer, Profile> profiles = new ConcurrentHashMap<>();
    private boolean isFrozen = false;
    private long lastSwitchTime = 0L;
    private long lastCombatTime = 0L;
    private long lastDeleteTime = 0L;
    private long lastActionbarTime = 0L;

    public PlayerProfileHolder(UUID playerUuid, int activeSlot) {
        this.playerUuid = playerUuid;
        this.activeSlot = activeSlot;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getActiveSlot() {
        return activeSlot;
    }

    public void setActiveSlot(int activeSlot) {
        this.activeSlot = activeSlot;
    }

    public Profile getActiveProfile() {
        if (activeSlot <= 0) return null;
        return profiles.get(activeSlot);
    }

    public boolean hasActiveProfile() {
        return getActiveProfile() != null;
    }

    public Profile getProfile(int slot) {
        return profiles.get(slot);
    }

    public void setProfile(int slot, Profile profile) {
        if (profile != null) {
            profiles.put(slot, profile);
        } else {
            profiles.remove(slot);
        }
    }

    public void removeProfile(int slot) {
        profiles.remove(slot);
    }

    public Map<Integer, Profile> getProfiles() {
        return profiles;
    }

    public boolean hasProfile(int slot) {
        return profiles.containsKey(slot);
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    public void setFrozen(boolean frozen) {
        isFrozen = frozen;
    }

    public long getLastSwitchTime() {
        return lastSwitchTime;
    }

    public void setLastSwitchTime(long lastSwitchTime) {
        this.lastSwitchTime = lastSwitchTime;
    }

    public boolean isSwitchOnCooldown(int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;
        long elapsed = System.currentTimeMillis() - lastSwitchTime;
        return elapsed < (cooldownSeconds * 1000L);
    }

    public long getRemainingCooldownSeconds(int cooldownSeconds) {
        long elapsed = System.currentTimeMillis() - lastSwitchTime;
        long remaining = (cooldownSeconds * 1000L) - elapsed;
        return Math.max(0, (remaining + 999) / 1000);
    }

    public void tagCombat() {
        this.lastCombatTime = System.currentTimeMillis();
    }

    public boolean isInCombat(int durationSeconds) {
        if (durationSeconds <= 0 || lastCombatTime <= 0) return false;
        long elapsed = System.currentTimeMillis() - lastCombatTime;
        return elapsed < (durationSeconds * 1000L);
    }

    public long getRemainingCombatSeconds(int durationSeconds) {
        long elapsed = System.currentTimeMillis() - lastCombatTime;
        long remaining = (durationSeconds * 1000L) - elapsed;
        return Math.max(0, (remaining + 999) / 1000);
    }

    public void tagDelete() {
        this.lastDeleteTime = System.currentTimeMillis();
    }

    public boolean isDeleteOnCooldown(int cooldownSeconds) {
        if (cooldownSeconds <= 0 || lastDeleteTime <= 0) return false;
        long elapsed = System.currentTimeMillis() - lastDeleteTime;
        return elapsed < (cooldownSeconds * 1000L);
    }

    public long getRemainingDeleteSeconds(int cooldownSeconds) {
        long elapsed = System.currentTimeMillis() - lastDeleteTime;
        long remaining = (cooldownSeconds * 1000L) - elapsed;
        return Math.max(0, (remaining + 999) / 1000);
    }

    public boolean canSendActionbarWarning(int cooldownSeconds) {
        long now = System.currentTimeMillis();
        if (now - lastActionbarTime > (cooldownSeconds * 1000L)) {
            lastActionbarTime = now;
            return true;
        }
        return false;
    }
}
