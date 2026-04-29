package me.adrianos76.betterEnderChests.Config;

import me.adrianos76.betterEnderChests.BetterEnderChests;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LanguageConfigManager {
    BetterEnderChests plugin;

    FileConfiguration config;

    String lang;

    public LanguageConfigManager(BetterEnderChests plugin, String lang) {
        this.plugin = plugin;
        this.lang = lang;

        File langFile = new File(this.plugin.getDataFolder(), "translations/" + this.lang + ".yml");

        if (!langFile.exists()) {
            plugin.saveResource("translations/" + lang + ".yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(langFile);
    }

    public void updateLanguageConfig() {
        //TODO: implement
    }

    public String getString(String key) {
        return getString(key, new HashMap<>());
    }

    public String getString(String key, Map<String, String> variables) {
        String str = this.config.getString(key);

        if (str == null) {
            plugin.getLogger().warning("Could not find message for key: " + key);
            return "There's no message for that key, check the config";
        }

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            str = str.replace(entry.getKey(), entry.getValue());
        }

        return str;
    }
}
