package me.erpand.PoisonRefunds.database;

import me.erpand.PoisonRefunds.PoisonRefunds;
import me.erpand.PoisonRefunds.models.RefundPackage;
import me.erpand.PoisonRefunds.models.RefundRecord;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.io.*;
import java.util.Base64;

public class DatabaseManager {
    private final PoisonRefunds plugin;
    private Connection connection;

    public DatabaseManager(PoisonRefunds plugin) {
        this.plugin = plugin;
    }

    public void initializeDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/" + "refunds.db");

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS refunds (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "player_uuid TEXT NOT NULL," +
                        "player_name TEXT NOT NULL," +
                        "code TEXT NOT NULL UNIQUE," +
                        "death_reason TEXT NOT NULL," +
                        "death_date TEXT NOT NULL," +
                        "status TEXT NOT NULL," +
                        "items TEXT NOT NULL)");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    public void storeRefund(UUID playerUuid, String playerName, String code, String deathReason, List<ItemStack> items) throws SQLException {
        String serializedItems = serializeItems(items);
        String currentDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        String sql = "INSERT INTO refunds (player_uuid, player_name, code, death_reason, death_date, status, items) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, code);
            stmt.setString(4, deathReason);
            stmt.setString(5, currentDate);
            stmt.setString(6, "PENDING");
            stmt.setString(7, serializedItems);
            stmt.executeUpdate();
        }
    }

    public List<RefundRecord> getRefundRecords(UUID playerUuid) {
        List<RefundRecord> records = new ArrayList<>();
        try {
            String sql = "SELECT code, death_reason, death_date, status FROM refunds WHERE player_uuid = ? ORDER BY death_date DESC";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        records.add(new RefundRecord(
                                rs.getString("code"),
                                rs.getString("death_reason"),
                                rs.getString("death_date"),
                                rs.getString("status")
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch refund records", e);
        }
        return records;
    }

    public boolean approveRefund(String code) throws SQLException {
        String sql = "UPDATE refunds SET status = 'APPROVED' WHERE code = ? AND status = 'PENDING'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, code);
            return stmt.executeUpdate() > 0;
        }
    }

    public List<RefundPackage> getApprovedRefundPackages(UUID playerUuid) {
        List<RefundPackage> packages = new ArrayList<>();
        try {
            String sql = "SELECT code, death_reason, death_date, items FROM refunds WHERE player_uuid = ? AND status = 'APPROVED'";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        packages.add(new RefundPackage(
                                rs.getString("code"),
                                rs.getString("death_reason"),
                                rs.getString("death_date"),
                                deserializeItems(rs.getString("items"))
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch approved refund packages", e);
        }
        return packages;
    }

    public void storeManualRefund(UUID playerUuid, String playerName, String code,
                                  String reason, String date, List<ItemStack> items) throws SQLException {
        String serializedItems = serializeItems(items);

        String sql = "INSERT INTO refunds (player_uuid, player_name, code, death_reason, death_date, status, items) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, code);
            stmt.setString(4, reason);
            stmt.setString(5, date);
            stmt.setString(6, "APPROVED");
            stmt.setString(7, serializedItems);
            stmt.executeUpdate();
        }
    }

    public boolean claimRefundPackage(@NotNull UUID player, String code) throws SQLException {
        // 1. Get items first
        List<ItemStack> items = getRefundItems(code);
        if (items.isEmpty()) return false;

        // 2. Delete from database
        String sql = "DELETE FROM refunds WHERE code = ? AND player_uuid = ? AND status = 'APPROVED'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, code);
            stmt.setString(2, player.toString());
            return stmt.executeUpdate() > 0;
        }
    }

    public String getRefundOwner(String code) throws SQLException {
        String sql = "SELECT player_uuid FROM refunds WHERE code = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("player_uuid") : null;
            }
        }
    }

    public boolean hasApprovedRefunds(UUID playerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM refunds WHERE player_uuid = ? AND status = 'APPROVED'";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public int cleanupOldRefunds() {
        try {
            String sql = "DELETE FROM refunds WHERE date(death_date) < date('now', '-14 days')";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                return stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cleanup old refunds", e);
            return 0;
        }
    }

    public boolean isCodeExists(String code) throws SQLException {
        String sql = "SELECT COUNT(*) FROM refunds WHERE code = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public List<ItemStack> getRefundItems(String code) throws SQLException {
        String sql = "SELECT items FROM refunds WHERE code = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return deserializeItems(rs.getString("items"));
                }
            }
        }
        return Collections.emptyList();
    }

    private String serializeItems(List<ItemStack> items) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeInt(items.size());
            for (ItemStack item : items) {
                if (item == null || item.getType() == Material.AIR) {
                    dos.writeBoolean(false);
                } else {
                    dos.writeBoolean(true);
                    byte[] serialized = item.serializeAsBytes();
                    dos.writeInt(serialized.length);
                    dos.write(serialized);
                }
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to serialize items", e);
            return "";
        }
    }

    private List<ItemStack> deserializeItems(String serialized) {
        if (serialized == null || serialized.isEmpty()) return Collections.emptyList();
        byte[] data = Base64.getDecoder().decode(serialized);
        List<ItemStack> items = new ArrayList<>();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream dis = new DataInputStream(bais)) {
            int size = dis.readInt();
            for (int i = 0; i < size; i++) {
                if (dis.readBoolean()) {
                    int length = dis.readInt();
                    byte[] itemBytes = new byte[length];
                    dis.readFully(itemBytes);
                    items.add(ItemStack.deserializeBytes(itemBytes));
                } else {
                    items.add(null);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to deserialize items", e);
            return Collections.emptyList();
        }
        return items;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database connection", e);
        }
    }
}