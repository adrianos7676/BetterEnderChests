package me.adrianos76.betterEnderChests.database;

import me.adrianos76.betterEnderChests.BetterEnderChests;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class Database {

    public  BetterEnderChests plugin;


    public Connection dbConnection;
    private String dbUrl;
    private String dbUser;
    private String dbPassword;
    public int serverID;

    public Database(BetterEnderChests plugin, String serverName, String dbUrl, String dbUser, String dbPassword) {
        this.plugin = plugin;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        if (!checkDatabaseValues()) {
            plugin.getLogger().severe(plugin.languageConfigManager.getString("Config-MissingDatabase-Settings"));
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }

        if (!ensureConnection()) {
            plugin.getLogger().severe(plugin.languageConfigManager.getString("Database-Connection-Error"));
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        String query = "SELECT id FROM server WHERE name = ?";

        try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
            stmt.setString(1, serverName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    serverID = rs.getInt("id");
                } else {
                    plugin.getLogger().info("Adding server to database."); //TODO: ADD TO LANGCONFIG

                    String insert = "INSERT INTO server (name) VALUES (?)";

                    try (PreparedStatement insertStmt = dbConnection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                        insertStmt.setString(1, serverName);
                        insertStmt.executeUpdate();

                        try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                serverID = generatedKeys.getInt(1);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String setupQuery = """
                CREATE TABLE IF NOT EXISTS `user` (
                    `id` int(11) NOT NULL AUTO_INCREMENT,
                    `name` varchar(16) NOT NULL,
                    PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                
                CREATE TABLE IF NOT EXISTS `server` (
                    `id` int(11) NOT NULL AUTO_INCREMENT,
                    `name` varchar(100) NOT NULL,
                    PRIMARY KEY (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                
                CREATE TABLE IF NOT EXISTS `item` (
                    `id` int(11) NOT NULL AUTO_INCREMENT,
                    `user_id` int(11) NOT NULL,
                    `server_id` int(11) NOT NULL,
                    `itemdata` text DEFAULT NULL,
                    `enderchest_number` int(11) NOT NULL,
                
                    PRIMARY KEY (`id`),
                    UNIQUE KEY `unique_chest` (`user_id`,`server_id`,`enderchest_number`),
                    KEY `server_id` (`server_id`),
                
                    CONSTRAINT `item_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
                    CONSTRAINT `item_ibfk_2` FOREIGN KEY (`server_id`) REFERENCES `server` (`id`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
                """;

        StringBuilder currentQuery = new StringBuilder();

        for (String line : setupQuery.split("\n")) {

            line = line.trim();

            if (line.startsWith("--") || line.isEmpty()) continue;

            currentQuery.append(line);

            if (line.endsWith(";")) {
                try (Statement stmt = dbConnection.createStatement()) {
                    stmt.execute(currentQuery.toString());
                } catch (SQLException e) {
                    plugin.getLogger().severe("SQL error: " + e.getMessage());
                }
                currentQuery.setLength(0);
            }
        }

    }

    private boolean checkDatabaseValues() {
        if (dbUrl == null || dbUser == null || dbPassword == null) {
            return false;
        }
        return true;
    }

    public boolean ensureConnection() {
        try {
            if (dbConnection != null && !dbConnection.isClosed() && dbConnection.isValid(2)) {
                return true;
            }
        } catch (SQLException e) {
            Map<String, String> params = new HashMap<>();
            params.put("%err%", e.getMessage());
            plugin.getLogger().warning(plugin.languageConfigManager.getString("Database-Reconnect-Fail-Error", params));
        }

        plugin.getLogger().warning(plugin.languageConfigManager.getString("Database-Connection-Retry-Warning"));

        try {
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
            }
        } catch (SQLException ignored) {}

        try {
            dbConnection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            plugin.getLogger().info(plugin.languageConfigManager.getString("Database-Reconnect-Success-Message"));
            return true;
        } catch (SQLException e) {
            Map<String, String> variables = new HashMap<>();
            variables.put("%err%", e.getMessage());
            plugin.getLogger().severe(plugin.languageConfigManager.getString("Database-Reconnect-Fail-Error",  variables));
            return false;
        }
    }
}
