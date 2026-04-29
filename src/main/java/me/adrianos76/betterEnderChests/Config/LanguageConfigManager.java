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

        plugin.getLogger().info("JAR path: " + jarFile.getAbsolutePath());
        plugin.getLogger().info("JAR exists: " + jarFile.exists());

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            boolean foundAny = false;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.startsWith("translations/") && name.endsWith(".yml")) {
                    foundAny = true;
                    plugin.getLogger().info("Found translation in JAR: " + name);

                    File file = new File(plugin.getDataFolder(), name);
                    plugin.getLogger().info("Target file: " + file.getAbsolutePath());
                    plugin.getLogger().info("Target file exists: " + file.exists());

                    file.getParentFile().mkdirs();
                    plugin.getLogger().info("mkdirs result: " + file.getParentFile().exists());

                    updateLanguageConfig(name);
                }
            }

            if (!foundAny) {
                plugin.getLogger().warning("No translation files found in JAR under translations/");
            }

        } catch (IOException e) {
            plugin.getLogger().severe("IOException while reading JAR: " + e.getMessage());
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
