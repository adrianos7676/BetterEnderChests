package me.adrianos76.betterEnderChests;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;


public final class BetterEnderChests extends JavaPlugin implements Listener {
    Connection dbConnection;
    int serverID;

    private final Map<UUID, Inventory> playersWithOpenInventories = new HashMap<>();
    // UUID admina → nazwa gracza którego chest ogląda
    private final Map<UUID, String> viewingAs = new HashMap<>();

    private String dbUrl;
    private String dbUser;
    private String dbPassword;

    private boolean ensureConnection() {
        try {
            if (dbConnection != null && !dbConnection.isClosed() && dbConnection.isValid(2)) {
                return true;
            }
        } catch (SQLException e) {
            getLogger().warning("Sprawdzenie połączenia nieudane: " + e.getMessage());
        }

        getLogger().warning("Połączenie z bazą utracone — próba reconnectu...");

        try {
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
            }
        } catch (SQLException ignored) {}

        try {
            dbConnection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            getLogger().info("Reconnect udany!");
            return true;
        } catch (SQLException e) {
            getLogger().severe("Reconnect nieudany: " + e.getMessage());
            return false;
        }
    }

    public String itemStackArrayToBase64(ItemStack[] items) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(items.length);

            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    public ItemStack[] itemStackArrayFromBase64(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int length = dataInput.readInt();
            ItemStack[] items = new ItemStack[length];

            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return items;

        } catch (Exception e) {
            throw new IllegalStateException("Unable to load item stacks.", e);
        }
    }

    // Checks if a user is in the db, if not then it adds the user
    public int getUserFromDB(String userName) {
        if (ensureConnection()) {
            String selectQuery = "SELECT id FROM user WHERE name = ?";
            String insertQuery = "INSERT INTO user (name) VALUES (?)";

            try (PreparedStatement selectStmt = dbConnection.prepareStatement(selectQuery)) {
                selectStmt.setString(1, userName);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            } catch (SQLException e) {
                Bukkit.getLogger().severe("Select error: " + e.getMessage());
            }

            try (PreparedStatement insertStmt = dbConnection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)) {
                insertStmt.setString(1, userName);
                insertStmt.executeUpdate();

                try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    }
                }

            } catch (SQLException e) {
                Bukkit.getLogger().severe("Insert error: " + e.getMessage());
            }
        }
        return 0;
    }

    public void saveEnderChestToDB(ItemStack[] items, String userName, int chestNum) {
        if (ensureConnection()) {
            String query = """
            INSERT INTO item (user_id, server_id, enderchest_number, itemdata)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE itemdata = VALUES(itemdata)
            """;

            int userID = getUserFromDB(userName);
            String base64 = itemStackArrayToBase64(items);

            try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
                stmt.setInt(1, userID);
                stmt.setInt(2, serverID);
                stmt.setInt(3, chestNum);
                stmt.setString(4, base64);
                stmt.executeUpdate();
            } catch (SQLException e) {
                Bukkit.getLogger().severe("Save Ender Chest error: " + e.getMessage());
            }
        }
    }

    public ItemStack[] loadEnderChestFromDB(String userName, int chestNum) {
        if (ensureConnection()) {
            String query = "SELECT itemdata FROM item WHERE user_id = ? AND server_id = ? AND enderchest_number = ?";
            int userID = getUserFromDB(userName);

            try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
                stmt.setInt(1, userID);
                stmt.setInt(2, serverID);
                stmt.setInt(3, chestNum);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String base64 = rs.getString("itemdata");
                        return itemStackArrayFromBase64(base64);
                    }
                }
            } catch (SQLException e) {
                Bukkit.getLogger().severe("Load Ender Chest error: " + e.getMessage());
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
                // Admin klika w GUI endersee — otwiera chest innego gracza
                showEnderChest(player, enderChestOwner, enderChestNumberValue, false);
            } else {
                // Normalny gracz klika własne GUI
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
        Inventory gui = Bukkit.createInventory(null, 27, "Ender chest");

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

                enderChestMeta.setDisplayName(ChatColor.DARK_PURPLE + "Ender Chest #" + i);
                enderChest.setItemMeta(enderChestMeta);
                gui.setItem(9 + i, enderChest);
            } else {
                ItemStack barrier = new ItemStack(Material.BARRIER, i);
                ItemMeta barrierMeta = barrier.getItemMeta();

                barrierMeta.getPersistentDataContainer().set(unpickableKey, PersistentDataType.BYTE, (byte) 1);
                barrierMeta.setDisplayName(ChatColor.DARK_PURPLE + "Ender Chest #" + i);

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

    /**
     * Opens an ender chest GUI for a player.
     *
     * @param player     The player who will see the GUI.
     * @param playerName The owner of the ender chest being displayed.
     * @param num        The ender chest number (1-7).
     * @param ownView    If true, checks if player has permission to open their own chest (betterenderchests.useN).
     *                   If false, checks if player has admin permission to view another player's chest (betterenderchests.endersee).
     */
    public void showEnderChest(Player player, String playerName, Integer num, boolean ownView) {
        num = Math.clamp(num, 1, 7);

        if (ownView) {
            if (!player.hasPermission("betterenderchests.use" + num)) {
                player.sendMessage("§cNie masz permisji!");
                return;
            }
            viewingAs.remove(player.getUniqueId()); // własny chest — wyczyść wpis admina
        } else {
            if (!player.hasPermission("betterenderchests.endersee")) {
                player.sendMessage("§cNie masz permisji!");
                return;
            }
            viewingAs.put(player.getUniqueId(), playerName); // zapamiętaj czyj chest ogląda
        }

        String title = ownView ? "Ender chest #" + num : "Ec #" + num + " gracza " + playerName;

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
        if (ensureConnection()) {
            String query = "DELETE FROM item WHERE user_id = ? AND server_id = ? AND enderchest_number = ?";
            int playerId = getUserFromDB(playerName);

            try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
                stmt.setInt(1, playerId);
                stmt.setInt(2, serverID);
                stmt.setInt(3, chestNum);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
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
                        player.sendMessage("§cTo nie jest poprawna liczba!");
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
                        player.sendMessage("§cTo nie jest poprawna liczba!");
                        return true;
                    }
                    showEnderChest(player, args[0], num, false);
                } else {
                    sender.sendMessage("§cUsage: /endersee <nick> [chestnum]");
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
                    sender.sendMessage("§xa[BetterEnderChests] Nie masz permisji!");
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
        if (title.startsWith("Ender chest #")) {
            prefix = "Ender chest #";
        } else if (title.startsWith("Ec #")) {
            prefix = "Ec #";
        } else {
            return;
        }

        int chestNumber;
        try {
            chestNumber = Integer.parseInt(title.replace(prefix, "").split(" ")[0]);
        } catch (NumberFormatException e) {
            Bukkit.getLogger().warning("Error parsing number.");
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

        saveDefaultConfig();

        String serverName = getConfig().getString("serverName");
        dbUrl = "jdbc:" + getConfig().getString("database.url");
        dbUser = getConfig().getString("database.user");
        dbPassword = getConfig().getString("database.password");

        if (serverName == null || dbUrl == null || dbUser == null || dbPassword == null) {
            getLogger().severe("Config is missing required database settings!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!ensureConnection()) {
            getLogger().severe("Nie można połączyć z bazą danych!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String query = "SELECT id FROM server WHERE name = ?";

        try (PreparedStatement stmt = dbConnection.prepareStatement(query)) {
            stmt.setString(1, serverName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    serverID = rs.getInt("id");
                } else {
                    getLogger().info("Adding server to database.");

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

        Bukkit.getPluginManager().registerEvents(this, this);
    }
}