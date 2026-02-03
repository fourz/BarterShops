package org.fourz.BarterShops.data;

import org.fourz.rvnkcore.testing.TestDataGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Test data generator for BarterShops plugin.
 *
 * <p>Seeds 4 core tables with deterministic test data:
 * <ul>
 *   <li>shops - N shops with locations</li>
 *   <li>trade_items - 3 items per shop</li>
 *   <li>trade_records - Transaction records</li>
 *   <li>shop_metadata - Key-value pairs</li>
 * </ul>
 * </p>
 */
public class ShopsTestDataGenerator extends TestDataGenerator {

    // Item materials for trading
    private static final String[] MATERIALS = {
        "DIAMOND", "EMERALD", "IRON_INGOT", "GOLD_INGOT", "NETHERITE_INGOT",
        "COAL", "REDSTONE", "LAPIS_LAZULI", "COPPER_INGOT", "AMETHYST_SHARD"
    };

    // Shop types
    private static final String[] SHOP_TYPES = {"BARTER", "SELL", "BUY", "ADMIN"};

    // Transaction statuses
    private static final String[] TRANSACTION_STATUSES = {
        "COMPLETED", "CANCELLED", "FAILED", "PENDING", "REFUNDED"
    };

    // Metadata keys for shops
    private static final String[] METADATA_KEYS = {
        "description", "tax_rate", "min_trade", "max_trade", "display_mode"
    };

    private final IConnectionProvider connectionProvider;
    private final ExecutorService executor;
    private final String tablePrefix;
    private final boolean isMySQL;

    /**
     * Create a new ShopsTestDataGenerator.
     *
     * @param connectionProvider the connection provider instance
     */
    public ShopsTestDataGenerator(IConnectionProvider connectionProvider) {
        super(
            Logger.getLogger("BarterShops"),
            () -> "mysql".equalsIgnoreCase(connectionProvider.getDatabaseType()),
            () -> {
                try {
                    return connectionProvider.getConnection();
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to get connection", e);
                }
            }
        );
        this.connectionProvider = connectionProvider;
        this.executor = Executors.newSingleThreadExecutor();
        this.isMySQL = "mysql".equalsIgnoreCase(connectionProvider.getDatabaseType());
        this.tablePrefix = connectionProvider.getTablePrefix();
    }

    /**
     * Get prefixed table name.
     */
    private String table(String baseName) {
        if (tablePrefix == null || tablePrefix.isEmpty()) {
            return baseName;
        }
        return tablePrefix + baseName;
    }

    @Override
    public String getGeneratorName() {
        return "ShopsTestDataGenerator";
    }

    @Override
    public CompletableFuture<Integer> seed(DataCategory category) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Seeding " + category.name() + " data...");
            int totalRecords = 0;

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // 1. Seed shops
                    int shopCount = seedShops(conn, category.getBaseCount());
                    totalRecords += shopCount;

                    // 2. Get actual generated shop IDs (MySQL auto-increment may not start at 1)
                    int[] shopIds = getGeneratedShopIds(conn, category.getBaseCount());

                    // 3. Seed trade items (3 per shop)
                    totalRecords += seedTradeItems(conn, shopIds);

                    // 4. Seed trade records
                    totalRecords += seedTradeRecords(conn, shopIds);

                    // 5. Seed shop metadata (2-3 entries per shop)
                    totalRecords += seedShopMetadata(conn, shopIds);

                    conn.commit();
                    logInfo("Seed complete: " + totalRecords + " total records");

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Seed failed, rolling back: " + e.getMessage());
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to seed data: " + e.getMessage());
                return 0;
            }

            return totalRecords;
        }, executor);
    }

    private int seedShops(Connection conn, int count) throws SQLException {
        // Schema columns: shop_id (auto), owner_uuid, shop_name, shop_type,
        // location_world, location_x, location_y, location_z,
        // chest_location_world, chest_location_x, chest_location_y, chest_location_z,
        // is_active, created_at, last_modified
        String sql = "INSERT INTO " + table("shops") +
            " (owner_uuid, shop_name, shop_type, location_world, location_x, location_y, location_z, " +
            "chest_location_world, chest_location_x, chest_location_y, chest_location_z, is_active) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                UUID ownerUuid = testUUID(i);
                String shopName = "TestShop" + (i + 1);
                String shopType = SHOP_TYPES[i % SHOP_TYPES.length];
                String world = testWorldName(i % 3);
                double x = 100.0 + (i * 10);
                double y = 64.0;
                double z = 100.0 + (i * 10);

                stmt.setString(1, ownerUuid.toString());
                stmt.setString(2, shopName);
                stmt.setString(3, shopType);
                stmt.setString(4, world);
                stmt.setDouble(5, x);
                stmt.setDouble(6, y);
                stmt.setDouble(7, z);
                stmt.setString(8, world);       // chest_location_world
                stmt.setDouble(9, x + 1);       // chest_location_x
                stmt.setDouble(10, y);           // chest_location_y
                stmt.setDouble(11, z);           // chest_location_z
                stmt.setInt(12, i % 5 == 0 ? 0 : 1);  // 20% inactive
                stmt.addBatch();
                inserted++;

                if (inserted % 100 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
        }
        logSeeded("shops", inserted);
        return inserted;
    }

    /**
     * Query actual shop IDs generated by auto-increment after seedShops().
     */
    private int[] getGeneratedShopIds(Connection conn, int count) throws SQLException {
        String sql = "SELECT shop_id FROM " + table("shops") +
            " WHERE shop_name LIKE 'TestShop%' ORDER BY shop_id ASC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, count);
            try (ResultSet rs = stmt.executeQuery()) {
                java.util.List<Integer> ids = new java.util.ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getInt("shop_id"));
                }
                return ids.stream().mapToInt(Integer::intValue).toArray();
            }
        }
    }

    private int seedTradeItems(Connection conn, int[] shopIds) throws SQLException {
        // Schema columns: trade_item_id (auto), shop_id, item_stack_data,
        // currency_material, price_amount, stock_quantity, is_offering, created_at
        String sql = "INSERT INTO " + table("trade_items") +
            " (shop_id, item_stack_data, currency_material, price_amount, stock_quantity, is_offering) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int idx = 0; idx < shopIds.length; idx++) {
                int shopId = shopIds[idx];
                // 3 items per shop
                for (int itemNum = 0; itemNum < 3; itemNum++) {
                    String material = MATERIALS[(idx + itemNum) % MATERIALS.length];
                    String itemData = "{\"type\":\"" + material + "\",\"amount\":" + (itemNum + 1) + "}";
                    String currencyMaterial = MATERIALS[(idx + itemNum + 1) % MATERIALS.length];
                    int price = (itemNum + 1) * 10;
                    int stock = randomInt(1, 64);
                    boolean isOffering = itemNum % 2 == 0;

                    stmt.setInt(1, shopId);
                    stmt.setString(2, itemData);
                    stmt.setString(3, currencyMaterial);
                    stmt.setInt(4, price);
                    stmt.setInt(5, stock);
                    stmt.setInt(6, isOffering ? 1 : 0);
                    stmt.addBatch();
                    inserted++;

                    if (inserted % 100 == 0) {
                        stmt.executeBatch();
                    }
                }
            }
            stmt.executeBatch();
        }
        logSeeded("trade_items", inserted);
        return inserted;
    }

    private int seedTradeRecords(Connection conn, int[] shopIds) throws SQLException {
        // Schema columns: transaction_id (PK), shop_id, buyer_uuid, seller_uuid,
        // item_stack_data, quantity, currency_material, price_paid, status, completed_at
        String sql = "INSERT INTO " + table("trade_records") +
            " (transaction_id, shop_id, buyer_uuid, seller_uuid, item_stack_data, " +
            "quantity, currency_material, price_paid, status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 2 transactions per shop (for half the shops)
            int count = shopIds.length;
            for (int i = 0; i < count / 2; i++) {
                for (int txNum = 0; txNum < 2; txNum++) {
                    int shopId = shopIds[i % count];
                    UUID transactionId = testUUID(10000 + i * 2 + txNum);
                    UUID buyerUuid = testUUID(i);
                    UUID sellerUuid = testUUID(i + 1);
                    String material = MATERIALS[i % MATERIALS.length];
                    String itemData = "{\"type\":\"" + material + "\",\"amount\":1}";
                    int quantity = randomInt(1, 10);
                    String currencyMaterial = MATERIALS[(i + 1) % MATERIALS.length];
                    int pricePaid = quantity * 10;
                    String status = TRANSACTION_STATUSES[i % TRANSACTION_STATUSES.length];

                    stmt.setString(1, transactionId.toString());
                    stmt.setInt(2, shopId);
                    stmt.setString(3, buyerUuid.toString());
                    stmt.setString(4, sellerUuid.toString());
                    stmt.setString(5, itemData);
                    stmt.setInt(6, quantity);
                    stmt.setString(7, currencyMaterial);
                    stmt.setInt(8, pricePaid);
                    stmt.setString(9, status);
                    stmt.addBatch();
                    inserted++;

                    if (inserted % 100 == 0) {
                        stmt.executeBatch();
                    }
                }
            }
            stmt.executeBatch();
        }
        logSeeded("trade_records", inserted);
        return inserted;
    }

    private int seedShopMetadata(Connection conn, int[] shopIds) throws SQLException {
        String sql;
        if (isMySQL) {
            sql = "INSERT INTO " + table("shop_metadata") +
                " (shop_id, meta_key, meta_value) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE meta_value = VALUES(meta_value)";
        } else {
            sql = "INSERT OR REPLACE INTO " + table("shop_metadata") +
                " (shop_id, meta_key, meta_value) VALUES (?, ?, ?)";
        }

        int inserted = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int idx = 0; idx < shopIds.length; idx++) {
                int shopId = shopIds[idx];
                // 2-3 metadata entries per shop
                int numMeta = 2 + (idx % 2);
                for (int m = 0; m < numMeta; m++) {
                    String key = METADATA_KEYS[m];
                    String value = switch (key) {
                        case "description" -> "Test shop " + (idx + 1) + " description";
                        case "tax_rate" -> String.valueOf(5 + (idx % 10));
                        case "min_trade" -> "1";
                        case "max_trade" -> "64";
                        case "display_mode" -> idx % 2 == 0 ? "compact" : "detailed";
                        default -> "test_value";
                    };

                    stmt.setInt(1, shopId);
                    stmt.setString(2, key);
                    stmt.setString(3, value);
                    stmt.addBatch();
                    inserted++;
                }
            }
            stmt.executeBatch();
        }
        logSeeded("shop_metadata", inserted);
        return inserted;
    }

    @Override
    public CompletableFuture<Boolean> cleanup() {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up all test data...");

            try (Connection conn = getConnection()) {
                // Disable FK checks before starting transaction
                // (SQLite PRAGMA can't execute inside a transaction)
                if (!isMySQL) {
                    try (PreparedStatement stmt = conn.prepareStatement("PRAGMA foreign_keys=OFF")) {
                        stmt.execute();
                    }
                }

                conn.setAutoCommit(false);

                try {
                    if (isMySQL) {
                        try (PreparedStatement stmt = conn.prepareStatement("SET FOREIGN_KEY_CHECKS=0")) {
                            stmt.execute();
                        }
                    }

                    // Delete in reverse FK order
                    String[] tables = {
                        "trade_records",
                        "shop_metadata",
                        "trade_items",
                        "shops"
                    };

                    for (String tableName : tables) {
                        String sql = "DELETE FROM " + table(tableName) +
                            " WHERE " + getTestDataCondition(tableName);
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            int deleted = stmt.executeUpdate();
                            logInfo("Deleted " + deleted + " records from " + table(tableName));
                        }
                    }

                    if (isMySQL) {
                        try (PreparedStatement stmt = conn.prepareStatement("SET FOREIGN_KEY_CHECKS=1")) {
                            stmt.execute();
                        }
                    }

                    conn.commit();
                    logInfo("Cleanup complete");
                    return true;

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Cleanup failed: " + e.getMessage());
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                    // Re-enable FK checks for SQLite
                    if (!isMySQL) {
                        try (PreparedStatement stmt = conn.prepareStatement("PRAGMA foreign_keys=ON")) {
                            stmt.execute();
                        } catch (SQLException ignored) {}
                    }
                }

            } catch (SQLException e) {
                logSevere("Failed to cleanup: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    /**
     * Get WHERE condition to identify test data in each table.
     */
    private String getTestDataCondition(String tableName) {
        return switch (tableName) {
            case "shops" -> "shop_name LIKE 'TestShop%'";
            case "trade_items" -> "shop_id IN (SELECT shop_id FROM " +
                table("shops") + " WHERE shop_name LIKE 'TestShop%')";
            case "trade_records" -> "shop_id IN (SELECT shop_id FROM " +
                table("shops") + " WHERE shop_name LIKE 'TestShop%')";
            case "shop_metadata" -> "shop_id IN (SELECT shop_id FROM " + table("shops") +
                " WHERE shop_name LIKE 'TestShop%')";
            default -> "1=0";  // Safe fallback
        };
    }

    @Override
    public CompletableFuture<Integer> cleanupByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            logInfo("Cleaning up data for player: " + playerUuid);
            int totalDeleted = 0;

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try {
                    // Delete shop metadata for player's shops
                    String metaSql = "DELETE FROM " + table("shop_metadata") +
                        " WHERE shop_id IN (SELECT shop_id FROM " + table("shops") +
                        " WHERE owner_uuid = ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(metaSql)) {
                        stmt.setString(1, playerUuid.toString());
                        totalDeleted += stmt.executeUpdate();
                    }

                    // Delete trade items for player's shops
                    String itemsSql = "DELETE FROM " + table("trade_items") +
                        " WHERE shop_id IN (SELECT shop_id FROM " + table("shops") +
                        " WHERE owner_uuid = ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(itemsSql)) {
                        stmt.setString(1, playerUuid.toString());
                        totalDeleted += stmt.executeUpdate();
                    }

                    // Delete trade records where player is buyer or seller
                    String recordsSql = "DELETE FROM " + table("trade_records") +
                        " WHERE buyer_uuid = ? OR seller_uuid = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(recordsSql)) {
                        stmt.setString(1, playerUuid.toString());
                        stmt.setString(2, playerUuid.toString());
                        totalDeleted += stmt.executeUpdate();
                    }

                    // Delete player's shops
                    String shopsSql = "DELETE FROM " + table("shops") + " WHERE owner_uuid = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(shopsSql)) {
                        stmt.setString(1, playerUuid.toString());
                        totalDeleted += stmt.executeUpdate();
                    }

                    conn.commit();
                    logInfo("Player cleanup complete: " + totalDeleted + " records");

                } catch (SQLException e) {
                    conn.rollback();
                    logSevere("Player cleanup failed: " + e.getMessage());
                    return 0;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                logSevere("Failed to cleanup player data: " + e.getMessage());
                return 0;
            }

            return totalDeleted;
        }, executor);
    }

    /**
     * Cleanup the executor when done.
     */
    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}
