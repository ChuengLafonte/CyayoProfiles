package id.cyayo.profiles.storage;

import id.cyayo.profiles.CyayoProfiles;
import id.cyayo.profiles.model.Profile;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ProfileStorage {

    private final CyayoProfiles plugin;
    private final DatabaseManager dbManager;
    private final Map<UUID, CompletableFuture<Void>> pendingWrites = new ConcurrentHashMap<>();

    public ProfileStorage(CyayoProfiles plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
    }

    private CompletableFuture<Void> chainWrite(UUID playerUuid, Runnable task) {
        CompletableFuture<Void> next = pendingWrites.compute(playerUuid, (uuid, existing) -> {
            CompletableFuture<Void> base = existing != null ? existing : CompletableFuture.completedFuture(null);
            return base.handle((v, ex) -> null).thenRunAsync(task);
        });
        next.whenComplete((v, ex) -> pendingWrites.remove(playerUuid, next));
        return next;
    }

    public CompletableFuture<Void> awaitPendingWrites(UUID playerUuid) {
        CompletableFuture<Void> pending = pendingWrites.get(playerUuid);
        if (pending == null) {
            return CompletableFuture.completedFuture(null);
        }
        return pending.handle((v, ex) -> null);
    }

    public void flushPendingWritesSync(long timeoutMs) {
        if (pendingWrites.isEmpty()) return;
        try {
            CompletableFuture<?>[] all = pendingWrites.values().toArray(new CompletableFuture<?>[0]);
            CompletableFuture.allOf(all).get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
    }

    public CompletableFuture<Integer> getActiveSlot(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT active_slot FROM cyayo_player_active WHERE player_uuid = ?";
            try (Connection conn = dbManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("active_slot");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get active slot for " + playerUuid + ": " + e.getMessage());
            }
            return 0;
        });
    }

    public CompletableFuture<Void> setActiveSlot(UUID playerUuid, int slot) {
        return chainWrite(playerUuid, () -> {
            String updateSql = "UPDATE cyayo_player_active SET active_slot = ? WHERE player_uuid = ?";
            String insertSql = "INSERT INTO cyayo_player_active (player_uuid, active_slot, global_balance, vault_owner_slot) VALUES (?, ?, 0.0, 0)";
            try (Connection conn = dbManager.getConnection()) {
                int updated;
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setInt(1, slot);
                    stmt.setString(2, playerUuid.toString());
                    updated = stmt.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                        stmt.setString(1, playerUuid.toString());
                        stmt.setInt(2, slot);
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to set active slot for " + playerUuid + ": " + e.getMessage());
            }
        });
    }

    public static class VaultState {
        public final double globalBalance;
        public final int vaultOwnerSlot;

        public VaultState(double globalBalance, int vaultOwnerSlot) {
            this.globalBalance = globalBalance;
            this.vaultOwnerSlot = vaultOwnerSlot;
        }
    }

    public VaultState getGlobalVaultStateSync(UUID playerUuid) {
        String sql = "SELECT global_balance, vault_owner_slot FROM cyayo_player_active WHERE player_uuid = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new VaultState(rs.getDouble("global_balance"), rs.getInt("vault_owner_slot"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get global vault state for " + playerUuid + ": " + e.getMessage());
        }
        return new VaultState(0.0, 0);
    }

    public CompletableFuture<VaultState> getGlobalVaultState(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> getGlobalVaultStateSync(playerUuid));
    }

    public void saveGlobalVaultStateSync(UUID playerUuid, double globalBalance, int vaultOwnerSlot) {
        String updateSql = "UPDATE cyayo_player_active SET global_balance = ?, vault_owner_slot = ? WHERE player_uuid = ?";
        String insertSql = "INSERT INTO cyayo_player_active (player_uuid, active_slot, global_balance, vault_owner_slot) VALUES (?, 0, ?, ?)";
        try (Connection conn = dbManager.getConnection()) {
            int updated;
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setDouble(1, globalBalance);
                stmt.setInt(2, vaultOwnerSlot);
                stmt.setString(3, playerUuid.toString());
                updated = stmt.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setDouble(2, globalBalance);
                    stmt.setInt(3, vaultOwnerSlot);
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save global vault state for " + playerUuid + ": " + e.getMessage());
        }
    }

    public CompletableFuture<Void> saveGlobalVaultState(UUID playerUuid, double globalBalance, int vaultOwnerSlot) {
        return chainWrite(playerUuid, () -> saveGlobalVaultStateSync(playerUuid, globalBalance, vaultOwnerSlot));
    }

    public CompletableFuture<Map<Integer, Profile>> loadAllProfiles(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Map<Integer, Profile> map = new HashMap<>();
            String sql = "SELECT * FROM cyayo_profiles WHERE player_uuid = ?";
            try (Connection conn = dbManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Profile profile = parseProfile(rs);
                        if (profile != null) {
                            map.put(profile.getSlot(), profile);
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load profiles for " + playerUuid + ": " + e.getMessage());
            }
            return map;
        });
    }

    public CompletableFuture<Profile> loadProfile(UUID playerUuid, int slot) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM cyayo_profiles WHERE player_uuid = ? AND slot_index = ?";
            try (Connection conn = dbManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setInt(2, slot);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return parseProfile(rs);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load profile slot " + slot + " for " + playerUuid + ": " + e.getMessage());
            }
            return null;
        });
    }

    private static class ProfileSaveSnapshot {
        final UUID profileUuid;
        final UUID playerUuid;
        final int slot;
        final String classId;
        final int level;
        final double xp;
        final double health;
        final int hunger;
        final Location lastLocation;
        final ItemStack[] inventory;
        final ItemStack[] armor;
        final ItemStack offhand;
        final Collection<PotionEffect> potionEffects;
        final long createdAt;
        final long updatedAt;
        final String gamemodesStr;
        final int maxLives;
        final int currentLives;
        final boolean dead;
        final long fallenAt;
        final String fallenReason;
        final ItemStack[] enderChest;
        final double ironmanBalance;

        ProfileSaveSnapshot(Profile p) {
            long now = System.currentTimeMillis();
            p.setUpdatedAt(now);
            this.profileUuid = p.getProfileUuid();
            this.playerUuid = p.getPlayerUuid();
            this.slot = p.getSlot();
            this.classId = p.getClassId();
            this.level = p.getLevel();
            this.xp = p.getXp();
            this.health = p.getHealth();
            this.hunger = p.getHunger();
            Location loc = p.getLastLocation();
            this.lastLocation = (loc != null && loc.getWorld() != null) ? loc.clone() : null;
            this.inventory = cloneItemArray(p.getInventoryContents());
            this.armor = cloneItemArray(p.getArmorContents());
            ItemStack oh = p.getOffhandContent();
            this.offhand = oh != null ? oh.clone() : null;
            this.potionEffects = new ArrayList<>(p.getActivePotionEffects());
            this.createdAt = p.getCreatedAt();
            this.updatedAt = now;
            this.gamemodesStr = String.join(",", p.getGamemodes());
            this.maxLives = p.getMaxLives();
            this.currentLives = p.getCurrentLives();
            this.dead = p.isDead();
            this.fallenAt = p.getFallenAt();
            this.fallenReason = p.getFallenReason();
            this.enderChest = cloneItemArray(p.getIronmanEnderChest());
            this.ironmanBalance = p.getIronmanBalance();
        }

        private static ItemStack[] cloneItemArray(ItemStack[] source) {
            if (source == null) return null;
            ItemStack[] copy = new ItemStack[source.length];
            for (int i = 0; i < source.length; i++) {
                copy[i] = source[i] != null ? source[i].clone() : null;
            }
            return copy;
        }
    }

    public CompletableFuture<Void> saveProfile(Profile profile) {
        if (profile == null) return CompletableFuture.completedFuture(null);
        ProfileSaveSnapshot snapshot = new ProfileSaveSnapshot(profile);
        return chainWrite(profile.getPlayerUuid(), () -> saveSnapshotSync(snapshot));
    }

    public void saveProfileSync(Profile profile) {
        if (profile == null) return;
        saveSnapshotSync(new ProfileSaveSnapshot(profile));
    }

    private void saveSnapshotSync(ProfileSaveSnapshot snap) {
        String checkSql = "SELECT id FROM cyayo_profiles WHERE player_uuid = ? AND slot_index = ?";
        String insertSql = "INSERT INTO cyayo_profiles (profile_uuid, player_uuid, slot_index, class_id, level, xp, health, hunger, location, inventory_data, armor_data, offhand_data, potion_effects, created_at, updated_at, gamemodes, max_lives, current_lives, is_dead, fallen_at, fallen_reason, enderchest_data, ironman_balance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSql = "UPDATE cyayo_profiles SET profile_uuid = ?, class_id = ?, level = ?, xp = ?, health = ?, hunger = ?, location = ?, inventory_data = ?, armor_data = ?, offhand_data = ?, potion_effects = ?, updated_at = ?, gamemodes = ?, max_lives = ?, current_lives = ?, is_dead = ?, fallen_at = ?, fallen_reason = ?, enderchest_data = ?, ironman_balance = ? WHERE player_uuid = ? AND slot_index = ?";

        String enderchestData = ItemSerializer.itemStackArrayToBase64(snap.enderChest);
        String locStr = ItemSerializer.locationToString(snap.lastLocation);
        String invData = ItemSerializer.itemStackArrayToBase64(snap.inventory);
        String armorData = ItemSerializer.itemStackArrayToBase64(snap.armor);
        String offhandData = ItemSerializer.itemStackToBase64(snap.offhand);
        String potionStr = ItemSerializer.potionEffectsToString(snap.potionEffects);

        try (Connection conn = dbManager.getConnection()) {
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, snap.playerUuid.toString());
                checkStmt.setInt(2, snap.slot);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                    updateStmt.setString(1, snap.profileUuid.toString());
                    updateStmt.setString(2, snap.classId);
                    updateStmt.setInt(3, snap.level);
                    updateStmt.setDouble(4, snap.xp);
                    updateStmt.setDouble(5, snap.health);
                    updateStmt.setInt(6, snap.hunger);
                    updateStmt.setString(7, locStr);
                    updateStmt.setString(8, invData);
                    updateStmt.setString(9, armorData);
                    updateStmt.setString(10, offhandData);
                    updateStmt.setString(11, potionStr);
                    updateStmt.setLong(12, snap.updatedAt);
                    updateStmt.setString(13, snap.gamemodesStr);
                    updateStmt.setInt(14, snap.maxLives);
                    updateStmt.setInt(15, snap.currentLives);
                    updateStmt.setBoolean(16, snap.dead);
                    updateStmt.setLong(17, snap.fallenAt);
                    updateStmt.setString(18, snap.fallenReason);
                    updateStmt.setString(19, enderchestData);
                    updateStmt.setDouble(20, snap.ironmanBalance);
                    updateStmt.setString(21, snap.playerUuid.toString());
                    updateStmt.setInt(22, snap.slot);
                    updateStmt.executeUpdate();
                }
            } else {
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, snap.profileUuid.toString());
                    insertStmt.setString(2, snap.playerUuid.toString());
                    insertStmt.setInt(3, snap.slot);
                    insertStmt.setString(4, snap.classId);
                    insertStmt.setInt(5, snap.level);
                    insertStmt.setDouble(6, snap.xp);
                    insertStmt.setDouble(7, snap.health);
                    insertStmt.setInt(8, snap.hunger);
                    insertStmt.setString(9, locStr);
                    insertStmt.setString(10, invData);
                    insertStmt.setString(11, armorData);
                    insertStmt.setString(12, offhandData);
                    insertStmt.setString(13, potionStr);
                    insertStmt.setLong(14, snap.createdAt);
                    insertStmt.setLong(15, snap.updatedAt);
                    insertStmt.setString(16, snap.gamemodesStr);
                    insertStmt.setInt(17, snap.maxLives);
                    insertStmt.setInt(18, snap.currentLives);
                    insertStmt.setBoolean(19, snap.dead);
                    insertStmt.setLong(20, snap.fallenAt);
                    insertStmt.setString(21, snap.fallenReason);
                    insertStmt.setString(22, enderchestData);
                    insertStmt.setDouble(23, snap.ironmanBalance);
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save profile slot " + snap.slot + " for " + snap.playerUuid + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> deleteProfile(UUID playerUuid, int slot) {
        return chainWrite(playerUuid, () -> {
            String sql = "DELETE FROM cyayo_profiles WHERE player_uuid = ? AND slot_index = ?";
            try (Connection conn = dbManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setInt(2, slot);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete profile slot " + slot + " for " + playerUuid + ": " + e.getMessage());
            }
        });
    }

    private Profile parseProfile(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
        int slot = rs.getInt("slot_index");
        String classId = rs.getString("class_id");

        Profile profile = new Profile(uuid, slot, classId);
        profile.setId(rs.getInt("id"));

        String pUuidStr = rs.getString("profile_uuid");
        if (pUuidStr != null && !pUuidStr.isEmpty()) {
            try {
                profile.setProfileUuid(UUID.fromString(pUuidStr));
            } catch (IllegalArgumentException ignored) {
            }
        }

        profile.setLevel(rs.getInt("level"));
        profile.setXp(rs.getDouble("xp"));
        profile.setHealth(rs.getDouble("health"));
        profile.setHunger(rs.getInt("hunger"));

        profile.setLastLocation(ItemSerializer.locationFromString(rs.getString("location")));
        profile.setInventoryContents(ItemSerializer.itemStackArrayFromBase64(rs.getString("inventory_data")));
        profile.setArmorContents(ItemSerializer.itemStackArrayFromBase64(rs.getString("armor_data")));
        profile.setOffhandContent(ItemSerializer.itemStackFromBase64(rs.getString("offhand_data")));
        profile.setActivePotionEffects(ItemSerializer.potionEffectsFromString(rs.getString("potion_effects")));
        profile.setCreatedAt(rs.getLong("created_at"));
        profile.setUpdatedAt(rs.getLong("updated_at"));

        String modesStr = rs.getString("gamemodes");
        if (modesStr != null && !modesStr.trim().isEmpty()) {
            profile.setGamemodes(Arrays.asList(modesStr.split(",")));
        }
        int maxLives = rs.getInt("max_lives");
        profile.setMaxLives(maxLives > 0 ? maxLives : 1);
        int curLives = rs.getInt("current_lives");
        profile.setCurrentLives(rs.getBoolean("is_dead") ? 0 : Math.max(0, curLives));
        profile.setDead(rs.getBoolean("is_dead"));
        profile.setFallenAt(rs.getLong("fallen_at"));
        profile.setFallenReason(rs.getString("fallen_reason"));
        profile.setIronmanEnderChest(ItemSerializer.itemStackArrayFromBase64(rs.getString("enderchest_data")));
        profile.setIronmanBalance(rs.getDouble("ironman_balance"));
        return profile;
    }
}
