package me.adrianos76.betterEnderChests;

import me.adrianos76.betterEnderChests.Config.ConfigManager;
import me.adrianos76.betterEnderChests.Config.LanguageConfigManager;
import me.adrianos76.betterEnderChests.database.ItemStackEncoder;
import me.adrianos76.betterEnderChests.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URL;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

//TODO: Divide the code into classes

public final class BetterEnderChests extends JavaPlugin implements Listener {
    Database database;
    ConfigManager configManager;
    public LanguageConfigManager  languageConfigManager;
    ItemStackEncoder itemStackEncoder;

    private final Map<UUID, Inventory> playersWithOpenInventories = new HashMap<>();

    private final Map<UUID, String> viewingAs = new HashMap<>();

    public int getUserFromDB(String userName) {
        if (database.ensureConnection()) {
            String selectQuery = "SELECT id FROM user WHERE name = ?";
            String insertQuery = "INSERT INTO user (name) VALUES (?)";

            try (PreparedStatement selectStmt = database.dbConnection.prepareStatement(selectQuery)) {
                selectStmt.setString(1, userName);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            } catch (SQLException e) {
                Map<String, String> variables = new HashMap<>();
                variables.put("%err%", e.getMessage());
                getLogger().severe(languageConfigManager.getString("SQL-Select-Error", variables));
            }

            try (PreparedStatement insertStmt = database.dbConnection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, userName);
                insertStmt.executeUpdate();

                try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }

            } catch (SQLException e) {
                Map<String, String> variables = new HashMap<>();
                variables.put("%err%", e.getMessage());
                getLogger().severe(languageConfigManager.getString("SQL-Insert-Error", variables));
            }
        }
        return 0;
    }

    public void saveEnderChestToDB(ItemStack[] items, String userName, int chestNum) {
        if (database.ensureConnection()) {
            String query = """
            INSERT INTO item (user_id, server_id, enderchest_number, itemdata)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE itemdata = VALUES(itemdata)
            """;

            int userID = getUserFromDB(userName);
            String base64 = itemStackEncoder.itemStackArrayToBase64(items);

            try (PreparedStatement stmt = database.dbConnection.prepareStatement(query)) {
                stmt.setInt(1, userID);
                stmt.setInt(2, database.serverID);
                stmt.setInt(3, chestNum);
                stmt.setString(4, base64);
                stmt.executeUpdate();
            } catch (SQLException e) {
                Map<String, String> variables = new HashMap<>();
                variables.put("%err%", e.getMessage());
                getLogger().severe(languageConfigManager.getString("SQL-Error", variables));
            }
        }
    }

    public ItemStack[] loadEnderChestFromDB(String userName, int chestNum) {
        if (database.ensureConnection()) {
            String query = "SELECT itemdata FROM item WHERE user_id = ? AND server_id = ? AND enderchest_number = ?";
            int userID = getUserFromDB(userName);

            try (PreparedStatement stmt = database.dbConnection.prepareStatement(query)) {
                stmt.setInt(1, userID);
                stmt.setInt(2, database.serverID);
                stmt.setInt(3, chestNum);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String base64 = rs.getString("itemdata");
                        return itemStackEncoder.itemStackArrayFromBase64(base64);
                    }
                }
            } catch (SQLException e) {
                Map<String, String> variables = new HashMap<>();
                variables.put("%err%", e.getMessage());
                getLogger().severe(languageConfigManager.getString("SQL-Error", variables));
            }
        }
        return new ItemStack[27];
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (event.getClickedBlock() != null) {
                if (event.getClickedBlock().getType() == Material.ENDER_CHEST) {
                    Player player = event.getPlayer();
                    event.setCancelled(true);
                    showEnderChestGUI(player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        NamespacedKey enderChestButtonKey = new NamespacedKey(this, "encderchestselect");
        NamespacedKey unpickableKey = new NamespacedKey(this, "unpickable");
        NamespacedKey enderChestOwnerKey = new NamespacedKey(this, "encderchestowner");

        Byte enderChestButtonValue = meta.getPersistentDataContainer().get(enderChestButtonKey, PersistentDataType.BYTE);

        if (enderChestButtonValue != null && enderChestButtonValue == (byte) 1) {
            NamespacedKey enderChestnumberKey = new NamespacedKey(this, "enderchestnumber");
            Integer enderChestNumberValue = meta.getPersistentDataContainer().get(enderChestnumberKey, PersistentDataType.INTEGER);

            Player player = (Player) event.getWhoClicked();
            event.setCancelled(true);

            String enderChestOwner = meta.getPersistentDataContainer().get(enderChestOwnerKey, PersistentDataType.STRING);

            if (enderChestOwner != null) {
                showEnderChest(player, enderChestOwner, enderChestNumberValue, false);
            } else {
                showEnderChest(player, player.getName(), enderChestNumberValue, true);
            }
        }

        Byte unpickableValue = meta.getPersistentDataContainer().get(unpickableKey, PersistentDataType.BYTE);
        if (unpickableValue != null && unpickableValue == (byte) 1) {
            event.setCancelled(true);
        }
    }

    public void showEnderChestGUI(Player player) {
        showEnderChestGUI(player, null);
    }

    public void showEnderChestGUI(Player player, String playerToShow) {
        Inventory gui = Bukkit.createInventory(null, 27, languageConfigManager.getString("EnderChest"));

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);

        NamespacedKey enderChestselectKey = new NamespacedKey(this, "encderchestselect");
        NamespacedKey enderChestnumberKey = new NamespacedKey(this, "enderchestnumber");
        NamespacedKey enderChestOwnerKey = new NamespacedKey(this, "encderchestowner");
        NamespacedKey unpickableKey = new NamespacedKey(this, "unpickable");

        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName(" ");
        meta.getPersistentDataContainer().set(unpickableKey, PersistentDataType.BYTE, (byte) 1);
        filler.setItemMeta(meta);

        for (int i = 1; i <= 7; i++) {
            if (player.hasPermission("betterenderchests.use" + i)) {
                ItemStack enderChest = new ItemStack(Material.ENDER_CHEST, i);
                ItemMeta enderChestMeta = enderChest.getItemMeta();

                enderChestMeta.getPersistentDataContainer().set(enderChestselectKey, PersistentDataType.BYTE, (byte) 1);
                enderChestMeta.getPersistentDataContainer().set(enderChestnumberKey, PersistentDataType.INTEGER, i);

                if (playerToShow != null) {
                    enderChestMeta.getPersistentDataContainer().set(enderChestOwnerKey, PersistentDataType.STRING, playerToShow);
                }

                enderChestMeta.setDisplayName(languageConfigManager.getString("EnderChest-Prefix") + i);
                enderChest.setItemMeta(enderChestMeta);
                gui.setItem(9 + i, enderChest);
            } else {
                ItemStack barrier = new ItemStack(Material.BARRIER, i);
                ItemMeta barrierMeta = barrier.getItemMeta();

                barrierMeta.getPersistentDataContainer().set(unpickableKey, PersistentDataType.BYTE, (byte) 1);
                barrierMeta.setDisplayName(languageConfigManager.getString("EnderChest-Prefix") + i);

                barrier.setItemMeta(barrierMeta);
                gui.setItem(9 + i, barrier);
            }
        }

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }

        player.openInventory(gui);
    }

    public void showEnderChest(Player player, String playerName, Integer num) {
        showEnderChest(player, playerName, num, true);
    }

    public void showEnderChest(Player player, String playerName, Integer num, boolean ownView) {
        num = Math.clamp(num, 1, 7);

        if (ownView) {
            if (!player.hasPermission("betterenderchests.use" + num)) {
                player.sendMessage(languageConfigManager.getString("No-Permissions-Error"));
                return;
            }
            viewingAs.remove(player.getUniqueId()); // własny chest — wyczyść wpis admina
        } else {
            if (!player.hasPermission("betterenderchests.endersee")) {
                player.sendMessage(languageConfigManager.getString("No-Permissions-Error"));
                return;
            }
            viewingAs.put(player.getUniqueId(), playerName); // zapamiętaj czyj chest ogląda
        }

        String title = ownView ? languageConfigManager.getString("EnderChest-Prefix") + num : languageConfigManager.getString("EnderChest-Short-Prefix") + num + " gracza " + playerName;

        Inventory gui = Bukkit.createInventory(null, 27, title);

        ItemStack[] contents = loadEnderChestFromDB(playerName, num);
        gui.setContents(contents);

        playersWithOpenInventories.put(player.getUniqueId(), gui);

        player.openInventory(gui);
    }

    public void clearEnderChest(String playerName) {
        for (int i=1; i <= 7; i++) {
            clearEnderChest(playerName, i);
        }
    }

    public void clearEnderChest(String playerName, int chestNum) {
        if (database.ensureConnection()) {
            String query = "DELETE FROM item WHERE user_id = ? AND server_id = ? AND enderchest_number = ?";
            int playerId = getUserFromDB(playerName);

            try (PreparedStatement stmt = database.dbConnection.prepareStatement(query)) {
                stmt.setInt(1, playerId);
                stmt.setInt(2, database.serverID);
                stmt.setInt(3, chestNum);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("betterenderchests.updatemessages")) {
            Bukkit.getServer().getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    String localVersion = getDescription().getVersion();
                    String remoteVersion;

                    URL url = new URL("https://api.github.com/repos/adrianos7676/BetterEnderChests/releases/latest");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    String json = response.toString();

                    remoteVersion = json.split("\"tag_name\":\"")[1].split("\"")[0];

                    if (!remoteVersion.equalsIgnoreCase(localVersion)) {
                        Map<String, String> params = new HashMap<>();
                        params.put("%ver%", remoteVersion);
                        player.sendMessage(languageConfigManager.getString("Plugin-Update-Available", params));
                    }

                } catch (Exception e) {
                    getLogger().warning(languageConfigManager.getString("Plugin-Update-Error"));
                }
            });
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            if (command.getName().equalsIgnoreCase("ec") || command.getName().equalsIgnoreCase("enderchest")) {
                if (playersWithOpenInventories.containsKey(player.getUniqueId())) {
                    player.closeInventory();
                    playersWithOpenInventories.remove(player.getUniqueId());
                }

                if (args.length == 0) {
                    showEnderChestGUI(player);
                } else if (args.length == 1) {
                    int num;
                    try {
                        num = Integer.parseInt(args[0]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(languageConfigManager.getString("Incorrect-Number-Error"));
                        return true;
                    }
                    showEnderChest(player, player.getName(), num);
                }
                return true;

            } else if (command.getName().equalsIgnoreCase("endersee")) {
                if (args.length == 1) {
                    showEnderChestGUI(player, args[0]);
                } else if (args.length == 2) {
                    int num;
                    try {
                        num = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(languageConfigManager.getString("Incorrect-Number-Error"));
                        return true;
                    }
                    showEnderChest(player, args[0], num, false);
                } else {
                    sender.sendMessage(languageConfigManager.getString("EnderSee-Usage-Message"));
                }
                return true;
            } else if (command.getName().equalsIgnoreCase("enderclear")) {
                if (sender.hasPermission("betterenderchests.enderclear")) {
                    if (args.length == 1) {
                        clearEnderChest(args[0]);
                        sender.sendMessage("Wyczyszczono enderchesty gracza " + args[0]);
                        return true;
                    } else if (args.length == 2) {
                        try {
                            clearEnderChest(args[0], Integer.parseInt(args[1]));
                            sender.sendMessage("Wyczyszczono enderchest #" + args[1] + " gracza " + args[0]);
                        } catch (NumberFormatException e) {
                            sender.sendMessage("§cTo nie jest poprawna liczba!");
                        }
                        return true;
                    }
                } else {
                    sender.sendMessage(languageConfigManager.getString("No-Permissions-Error"));
                }
            }
        }

        return false;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();

        String prefix;
        if (title.startsWith(languageConfigManager.getString("EnderChest-Prefix"))) {
            prefix = languageConfigManager.getString("EnderChest-Prefix");
        } else if (title.startsWith(languageConfigManager.getString("EnderChest-Short-Prefix"))) {
            prefix = languageConfigManager.getString("EnderChest-Short-Prefix");
        } else {
            return;
        }

        int chestNumber;
        try {
            chestNumber = Integer.parseInt(title.replace(prefix, "").split(" ")[0]);
        } catch (NumberFormatException e) {
            getLogger().warning(languageConfigManager.getString("Number-Phrasing-Error"));
            return;
        }

        String owner;
        if (viewingAs.containsKey(player.getUniqueId())) {
            owner = viewingAs.get(player.getUniqueId());
        } else {
            owner = player.getName();
        }

        viewingAs.remove(player.getUniqueId());
        playersWithOpenInventories.remove(player.getUniqueId());
        saveEnderChestToDB(event.getInventory().getContents(), owner, chestNumber);
    }

    @Override
    public void onEnable() {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        //Config Manager setup
        configManager = new ConfigManager(this);

        configManager.saveDefaultConfig();

        //Update the config if it's an old version
        String localVersion = getDescription().getVersion();
        String configVersion = configManager.getString("configVersion", "0.0");

        if (!localVersion.equals(configVersion)) {
            configManager.updateConfig();
        }
        
        //Language Config Manager setup
        String lang = configManager.getString("lang");

        languageConfigManager = new LanguageConfigManager(this, lang);
        languageConfigManager.updateLanguageConfigs();

        //ItemStackEncoder setup
        itemStackEncoder = new ItemStackEncoder();

        //database setup
        String serverName = getConfig().getString("serverName");
        String dbUrl = "jdbc:" + getConfig().getString("database.url");
        String dbUser = getConfig().getString("database.user");
        String dbPassword = getConfig().getString("database.password");

        database = new Database(this, serverName, dbUrl, dbUser, dbPassword);

        Bukkit.getPluginManager().registerEvents(this, this);
    }
}