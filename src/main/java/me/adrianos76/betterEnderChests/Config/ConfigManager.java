package me.adrianos76.betterEnderChests.Config;

import me.adrianos76.betterEnderChests.BetterEnderChests;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
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

        for(String key : newConfig.getKeys(true)) {
            String value = newConfig.getString(key);
            if (!savedConfig.contains(value)) {
                plugin.getConfig().set(key, value);
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

    public void reloadConfig() {}
    public void saveDefaultConfig() {
        plugin.saveDefaultConfig();
    }
}
