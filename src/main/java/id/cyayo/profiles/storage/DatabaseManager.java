package id.cyayo.profiles.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import id.cyayo.profiles.CyayoProfiles;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final CyayoProfiles plugin;
    private HikariDataSource dataSource;
    private boolean isMySQL = false;

    public DatabaseManager(CyayoProfiles plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("database.type", "SQLITE").trim().toUpperCase();
        this.isMySQL = "MYSQL".equals(type);

        HikariConfig hikariConfig = new HikariConfig();

        if (isMySQL) {
            String host = config.getString("database.mysql.host", "localhost");
            int port = config.getInt("database.mysql.port", 3306);
            String database = config.getString("database.mysql.database", "cyayoprofiles");
            String username = config.getString("database.mysql.username", "root");
            String password = config.getString("database.mysql.password", "");

            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);

            hikariConfig.setMaximumPoolSize(config.getInt("database.mysql.pool.maximum-pool-size", 10));
            hikariConfig.setMinimumIdle(config.getInt("database.mysql.pool.minimum-idle", 2));
            hikariConfig.setConnectionTimeout(config.getLong("database.mysql.pool.connection-timeout", 30000));
            hikariConfig.setIdleTimeout(config.getLong("database.mysql.pool.idle-timeout", 600000));
            hikariConfig.setMaxLifetime(config.getLong("database.mysql.pool.max-lifetime", 1800000));
            hikariConfig.setPoolName("CyayoProfiles-MySQL");
        } else {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            String fileName = config.getString("database.sqlite.file", "data.db");
            File dbFile = new File(dataFolder, fileName);

            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setPoolName("CyayoProfiles-SQLite");
            hikariConfig.setConnectionTestQuery("SELECT 1");
        }

        this.dataSource = new HikariDataSource(hikariConfig);
        createTables();
    }

    private void createTables() {
        String activeTableSql = "CREATE TABLE IF NOT EXISTS cyayo_player_active (" +
                "player_uuid VARCHAR(36) PRIMARY KEY, " +
                "active_slot INT NOT NULL, " +
                "global_balance DOUBLE DEFAULT 0.0, " +
                "vault_owner_slot INT DEFAULT 0" +
                ");";

        String profilesTableSql;
        if (isMySQL) {
            profilesTableSql = "CREATE TABLE IF NOT EXISTS cyayo_profiles (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "profile_uuid VARCHAR(36), " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "slot_index INT NOT NULL, " +
                    "class_id VARCHAR(32) NOT NULL, " +
                    "level INT DEFAULT 1, " +
                    "xp DOUBLE DEFAULT 0, " +
                    "health DOUBLE DEFAULT 20.0, " +
                    "hunger INT DEFAULT 20, " +
                    "location TEXT, " +
                    "inventory_data MEDIUMTEXT, " +
                    "armor_data MEDIUMTEXT, " +
                    "offhand_data MEDIUMTEXT, " +
                    "potion_effects TEXT, " +
                    "created_at BIGINT, " +
                    "updated_at BIGINT, " +
                    "gamemodes TEXT, " +
                    "max_lives INT DEFAULT 1, " +
                    "current_lives INT DEFAULT 1, " +
                    "is_dead BOOLEAN DEFAULT FALSE, " +
                    "fallen_at BIGINT DEFAULT 0, " +
                    "fallen_reason TEXT, " +
                    "enderchest_data MEDIUMTEXT, " +
                    "ironman_balance DOUBLE DEFAULT 0.0, " +
                    "UNIQUE(player_uuid, slot_index)" +
                    ");";
        } else {
            profilesTableSql = "CREATE TABLE IF NOT EXISTS cyayo_profiles (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "profile_uuid VARCHAR(36), " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "slot_index INT NOT NULL, " +
                    "class_id VARCHAR(32) NOT NULL, " +
                    "level INT DEFAULT 1, " +
                    "xp DOUBLE DEFAULT 0, " +
                    "health DOUBLE DEFAULT 20.0, " +
                    "hunger INT DEFAULT 20, " +
                    "location TEXT, " +
                    "inventory_data TEXT, " +
                    "armor_data TEXT, " +
                    "offhand_data TEXT, " +
                    "potion_effects TEXT, " +
                    "created_at BIGINT, " +
                    "updated_at BIGINT, " +
                    "gamemodes TEXT, " +
                    "max_lives INT DEFAULT 1, " +
                    "current_lives INT DEFAULT 1, " +
                    "is_dead BOOLEAN DEFAULT FALSE, " +
                    "fallen_at BIGINT DEFAULT 0, " +
                    "fallen_reason TEXT, " +
                    "enderchest_data TEXT, " +
                    "ironman_balance DOUBLE DEFAULT 0.0, " +
                    "UNIQUE(player_uuid, slot_index)" +
                    ");";
        }

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            if (!isMySQL) {
                // Optimasi SQLite WAL agar pembacaan & penulisan async jauh lebih cepat dan minim lock
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA busy_timeout=5000;");
            }
            stmt.execute(activeTableSql);
            stmt.execute(profilesTableSql);

            // Auto-migrate older tables if any columns are missing
            ensureColumnExists(conn, "cyayo_player_active", "global_balance", "DOUBLE DEFAULT 0.0");
            ensureColumnExists(conn, "cyayo_player_active", "vault_owner_slot", "INT DEFAULT 0");

            ensureColumnExists(conn, "cyayo_profiles", "profile_uuid", "VARCHAR(36)");
            ensureColumnExists(conn, "cyayo_profiles", "gamemodes", "TEXT");
            ensureColumnExists(conn, "cyayo_profiles", "max_lives", "INT DEFAULT 1");
            ensureColumnExists(conn, "cyayo_profiles", "current_lives", "INT DEFAULT 1");
            ensureColumnExists(conn, "cyayo_profiles", "is_dead", "BOOLEAN DEFAULT FALSE");
            ensureColumnExists(conn, "cyayo_profiles", "fallen_at", "BIGINT DEFAULT 0");
            ensureColumnExists(conn, "cyayo_profiles", "fallen_reason", "TEXT");
            ensureColumnExists(conn, "cyayo_profiles", "enderchest_data", isMySQL ? "MEDIUMTEXT" : "TEXT");
            ensureColumnExists(conn, "cyayo_profiles", "ironman_balance", "DOUBLE DEFAULT 0.0");

            plugin.getLogger().info("Database tables initialized successfully (" + (isMySQL ? "MySQL" : "SQLite") + ")!");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void ensureColumnExists(Connection conn, String tableName, String columnName, String columnType) {
        try {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
                if (!rs.next()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
                    }
                }
            }
        } catch (SQLException ignored) {
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("HikariDataSource is closed or not initialized!");
        }
        return dataSource.getConnection();
    }

    public boolean isMySQL() {
        return isMySQL;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
