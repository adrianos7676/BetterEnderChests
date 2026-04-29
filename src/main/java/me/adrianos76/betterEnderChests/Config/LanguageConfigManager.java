package me.adrianos76.betterEnderChests.Config;

import me.adrianos76.betterEnderChests.BetterEnderChests;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

    private void updateLanguageConfig(String configLang) {
        plugin.getLogger().info(configLang);
    }

    public void updateLanguageConfigs() {
        File jarFile;
        try {
            jarFile = new File(this.plugin.getClass()
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();

                if (entry.getName().startsWith("translations/") && entry.getName().endsWith(".yml")) {
                    updateLanguageConfig(entry.getName());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
