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

        ensureConnection();

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
