package me.adrianos76.betterEnderChests.Config;

import me.adrianos76.betterEnderChests.BetterEnderChests;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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

    private void updateLanguageConfig(String resourcePath) {
        File langFile = new File(plugin.getDataFolder(), resourcePath);

        FileConfiguration existing = langFile.exists()
                ? YamlConfiguration.loadConfiguration(langFile)
                : new YamlConfiguration();

        InputStream jarStream = plugin.getResource(resourcePath);
        if (jarStream == null) return;

        FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(jarStream, StandardCharsets.UTF_8)
        );

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (!existing.contains(key)) {
                existing.set(key, defaults.get(key));
                changed = true;
                plugin.getLogger().info("Added missing key '" + key + "' to " + resourcePath);
            }
        }

        if (changed) {
            try {
                existing.save(langFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save " + resourcePath + ": " + e.getMessage());
            }
        }
    }

    public void updateLanguageConfigs() {
        File jarFile;
        try {
            jarFile = new File(plugin.getClass()
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            boolean foundAny = false;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.startsWith("translations/") && name.endsWith(".yml")) {
                    foundAny = true;

                    File file = new File(plugin.getDataFolder(), name);

                    file.getParentFile().mkdirs();

                    updateLanguageConfig(name);
                }
            }

            if (!foundAny) {
                plugin.getLogger().warning(plugin.languageConfigManager.getString("Config-No-Translations-In-Jar"));
            }

        } catch (IOException e) {
            Map<String, String> variables = new HashMap<>();

            variables.put("%err%", e.getMessage());

            plugin.getLogger().severe(plugin.languageConfigManager.getString("Config-IOException-Reading-JAR-Error", variables));
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
