package me.adrianos76.betterEnderChests.Config;

import me.adrianos76.betterEnderChests.BetterEnderChests;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.net.URI;
import java.util.Objects;

public class ConfigManager {

    BetterEnderChests plugin;

    public ConfigManager(BetterEnderChests plugin) {
        this.plugin = plugin;
    }

    public void updateConfig() {
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(Objects.requireNonNull(plugin.getResource("config.yml")))
        );

        FileConfiguration savedConfig = plugin.getConfig();

        String version = savedConfig.getString("configVersion", "1.0");

        if (version.equals("2.0")) {
            String url = savedConfig.getString("database.url");

            if (url != null) {
                try {
                    URI uri = new URI(url);

                    String host = uri.getHost();
                    int port = uri.getPort() == -1 ? 3306 : uri.getPort();
                    String path = uri.getPath();

                    String dbName = (path != null && path.startsWith("/"))
                            ? path.substring(1)
                            : path;

                    savedConfig.set("database.type", "mariadb");
                    savedConfig.set("database.host", host);
                    savedConfig.set("database.port", port);
                    savedConfig.set("database.database", dbName);

                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid database URL in config!");
                    e.printStackTrace();
                }
            }
        }

        for (String key : newConfig.getKeys(true)) {
            if (!savedConfig.contains(key)) {
                savedConfig.set(key, newConfig.get(key));
            }
        }

        plugin.saveConfig();
    }

    public String getString(String key) {
        return plugin.getConfig().getString(key);
    }

    public String getString(String key, String def) {
        return plugin.getConfig().getString(key, def);
    }

    public void saveDefaultConfig() {
        plugin.saveDefaultConfig();
    }
}
