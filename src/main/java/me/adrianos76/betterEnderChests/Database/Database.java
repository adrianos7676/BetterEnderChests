package me.adrianos76.betterEnderChests.database;

import me.adrianos76.betterEnderChests.BetterEnderChests;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class Database {

    public  BetterEnderChests plugin;


    public Connection dbConnection;
    private String dbUrl;
    private String dbUser;
    private String dbPassword;

    public Database(BetterEnderChests plugin, String dbUrl, String dbUser, String dbPassword) {
        this.plugin = plugin;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        if (!checkDatabaseValues()) {
            plugin.getLogger().severe(plugin.languageConfigManager.getString("Config-MissingDatabase-Settings"));
            plugin.getServer().getPluginManager().disablePlugin(plugin);
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
