package org.fourz.BarterShops.data.repository.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.TradeItemDTO;
import org.fourz.BarterShops.data.repository.IShopRepository;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of IShopRepository for MySQL and SQLite databases.
 * All operations are async using CompletableFuture.
 * Uses FallbackTracker for graceful degradation.
 */
public class ShopRepositoryImpl implements IShopRepository {

    private final IConnectionProvider connectionProvider;
    private final FallbackTracker fallbackTracker;
    private final LogManager logger;
    private final ExecutorService executor;

    // Table name helper
    private String t(String baseName) {
        return connectionProvider.table(baseName);
    }

    /**
     * Creates a new ShopRepositoryImpl.
     *
     * @param plugin The BarterShops plugin instance
     * @param connectionProvider The connection provider
     * @param fallbackTracker The fallback tracker for error handling
     */
    public ShopRepositoryImpl(BarterShops plugin, IConnectionProvider connectionProvider,
                              FallbackTracker fallbackTracker) {
        this(connectionProvider, fallbackTracker, LogManager.getInstance(plugin, "ShopRepository"));
    }

    /**
     * Creates a new ShopRepositoryImpl with injected LogManager.
     * This constructor supports dependency injection for testing.
     *
     * @param connectionProvider The connection provider
     * @param fallbackTracker The fallback tracker for error handling
     * @param logger The LogManager instance for logging
     */
    public ShopRepositoryImpl(IConnectionProvider connectionProvider,
                              FallbackTracker fallbackTracker, LogManager logger) {
        this.connectionProvider = connectionProvider;
        this.fallbackTracker = fallbackTracker;
        this.logger = logger;
        this.executor = Executors.newFixedThreadPool(4);
    }

    // ========================================================
    // Shop CRUD Operations
    // ========================================================

    @Override
    public CompletableFuture<ShopDataDTO> save(ShopDataDTO shop) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(shop);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql;
            boolean isInsert = shop.shopId() <= 0;

            if (isInsert) {
                sql = "INSERT INTO " + t("shops") + " (owner_uuid, shop_name, shop_type, location_world, " +
                    "location_x, location_y, location_z, chest_location_world, " +
                    "chest_location_x, chest_location_y, chest_location_z, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            } else {
                sql = "UPDATE " + t("shops") + " SET owner_uuid = ?, shop_name = ?, shop_type = ?, " +
                    "location_world = ?, location_x = ?, location_y = ?, location_z = ?, " +
                    "chest_location_world = ?, chest_location_x = ?, chest_location_y = ?, " +
                    "chest_location_z = ?, is_active = ?, last_modified = CURRENT_TIMESTAMP " +
                    "WHERE shop_id = ?";
            }

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, shop.ownerUuid().toString());
                stmt.setString(2, shop.shopName());
                stmt.setString(3, shop.shopType().name());
                stmt.setString(4, shop.locationWorld());
                stmt.setDouble(5, shop.locationX());
                stmt.setDouble(6, shop.locationY());
                stmt.setDouble(7, shop.locationZ());
                stmt.setString(8, shop.chestLocationWorld());
                stmt.setDouble(9, shop.chestLocationX());
                stmt.setDouble(10, shop.chestLocationY());
                stmt.setDouble(11, shop.chestLocationZ());
                stmt.setBoolean(12, shop.isActive());

                if (!isInsert) {
                    stmt.setInt(13, shop.shopId());
                }

                stmt.executeUpdate();
                fallbackTracker.recordSuccess();

                if (isInsert) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            int newId = keys.getInt(1);
                            // Save metadata
                            saveMetadataInternal(conn, newId, shop.metadata());
                            return ShopDataDTO.builder()
                                    .shopId(newId)
                                    .ownerUuid(shop.ownerUuid())
                                    .shopName(shop.shopName())
                                    .shopType(shop.shopType())
                                    .signLocation(shop.getSignLocation())
                                    .chestLocation(shop.getChestLocation())
                                    .isActive(shop.isActive())
                                    .createdAt(shop.createdAt())
                                    .lastModified(new Timestamp(System.currentTimeMillis()))
                                    .metadata(shop.metadata())
                                    .build();
                        }
                    }
                } else {
                    saveMetadataInternal(conn, shop.shopId(), shop.metadata());
                }

                return shop;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Save shop failed: " + e.getMessage());
                logger.error("Failed to save shop: " + e.getMessage());
                throw new RuntimeException("Failed to save shop", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<ShopDataDTO>> findById(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shops") + " WHERE shop_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        Map<String, String> metadata = loadMetadataInternal(conn, shopId);
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapRowToShop(rs, metadata));
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find shop by ID failed: " + e.getMessage());
                logger.error("Failed to find shop by ID: " + e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteById(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("shops") + " WHERE shop_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Delete shop failed: " + e.getMessage());
                logger.error("Failed to delete shop: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> existsById(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + t("shops") + " WHERE shop_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                try (ResultSet rs = stmt.executeQuery()) {
                    boolean exists = rs.next();
                    fallbackTracker.recordSuccess();
                    return exists;
                }

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Check shop exists failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ========================================================
    // Shop Queries
    // ========================================================

    @Override
    public CompletableFuture<List<ShopDataDTO>> findByOwner(UUID ownerUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shops") + " WHERE owner_uuid = ?";
            List<ShopDataDTO> shops = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, ownerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int shopId = rs.getInt("shop_id");
                        Map<String, String> metadata = loadMetadataInternal(conn, shopId);
                        shops.add(mapRowToShop(rs, metadata));
                    }
                }

                fallbackTracker.recordSuccess();
                return shops;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find shops by owner failed: " + e.getMessage());
                logger.error("Failed to find shops by owner: " + e.getMessage());
                return shops;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<ShopDataDTO>> findBySignLocation(
            String world, double x, double y, double z) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shops") + " " +
                "WHERE location_world = ? AND location_x = ? AND location_y = ? AND location_z = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, world);
                stmt.setDouble(2, x);
                stmt.setDouble(3, y);
                stmt.setDouble(4, z);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int shopId = rs.getInt("shop_id");
                        Map<String, String> metadata = loadMetadataInternal(conn, shopId);
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapRowToShop(rs, metadata));
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find shop by location failed: " + e.getMessage());
                logger.error("Failed to find shop by location: " + e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> findNearby(
            String world, double x, double y, double z, double radius) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            // Calculate bounding box for efficient query
            String sql = "SELECT * FROM " + t("shops") + " " +
                "WHERE location_world = ? " +
                "AND location_x BETWEEN ? AND ? " +
                "AND location_y BETWEEN ? AND ? " +
                "AND location_z BETWEEN ? AND ? " +
                "AND is_active = TRUE";

            List<ShopDataDTO> shops = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, world);
                stmt.setDouble(2, x - radius);
                stmt.setDouble(3, x + radius);
                stmt.setDouble(4, y - radius);
                stmt.setDouble(5, y + radius);
                stmt.setDouble(6, z - radius);
                stmt.setDouble(7, z + radius);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        // Double-check with actual distance
                        double dx = rs.getDouble("location_x") - x;
                        double dy = rs.getDouble("location_y") - y;
                        double dz = rs.getDouble("location_z") - z;
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                        if (distance <= radius) {
                            int shopId = rs.getInt("shop_id");
                            Map<String, String> metadata = loadMetadataInternal(conn, shopId);
                            shops.add(mapRowToShop(rs, metadata));
                        }
                    }
                }

                fallbackTracker.recordSuccess();
                return shops;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find nearby shops failed: " + e.getMessage());
                logger.error("Failed to find nearby shops: " + e.getMessage());
                return shops;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> findAllActive() {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shops") + " WHERE is_active = TRUE";
            List<ShopDataDTO> shops = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    int shopId = rs.getInt("shop_id");
                    Map<String, String> metadata = loadMetadataInternal(conn, shopId);
                    shops.add(mapRowToShop(rs, metadata));
                }

                fallbackTracker.recordSuccess();
                return shops;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find all active shops failed: " + e.getMessage());
                logger.error("Failed to find all active shops: " + e.getMessage());
                return shops;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> findAll() {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shops") + "";
            List<ShopDataDTO> shops = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    int shopId = rs.getInt("shop_id");
                    Map<String, String> metadata = loadMetadataInternal(conn, shopId);
                    shops.add(mapRowToShop(rs, metadata));
                }

                fallbackTracker.recordSuccess();
                return shops;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find all shops failed: " + e.getMessage());
                logger.error("Failed to find all shops: " + e.getMessage());
                return shops;
            }
        }, executor);
    }

    // ========================================================
    // Trade Items (Shop Inventory)
    // ========================================================

    @Override
    public CompletableFuture<List<TradeItemDTO>> findTradeItems(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("trade_items") + " WHERE shop_id = ?";
            List<TradeItemDTO> items = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapRowToTradeItem(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return items;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find trade items failed: " + e.getMessage());
                logger.error("Failed to find trade items: " + e.getMessage());
                return items;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<TradeItemDTO> saveTradeItem(TradeItemDTO item) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(item);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql;
            boolean isInsert = item.tradeItemId() <= 0;

            if (isInsert) {
                sql = "INSERT INTO " + t("trade_items") + " (shop_id, item_stack_data, currency_material, " +
                    "price_amount, stock_quantity, is_offering) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            } else {
                sql = "UPDATE " + t("trade_items") + " SET shop_id = ?, item_stack_data = ?, currency_material = ?, " +
                    "price_amount = ?, stock_quantity = ?, is_offering = ? " +
                    "WHERE trade_item_id = ?";
            }

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setInt(1, item.shopId());
                stmt.setString(2, item.itemStackData());
                stmt.setString(3, item.currencyMaterial());
                stmt.setInt(4, item.priceAmount());
                stmt.setInt(5, item.stockQuantity());
                stmt.setBoolean(6, item.isOffering());

                if (!isInsert) {
                    stmt.setInt(7, item.tradeItemId());
                }

                stmt.executeUpdate();
                fallbackTracker.recordSuccess();

                if (isInsert) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            return TradeItemDTO.builder()
                                    .tradeItemId(keys.getInt(1))
                                    .shopId(item.shopId())
                                    .itemStackData(item.itemStackData())
                                    .currencyMaterial(item.currencyMaterial())
                                    .priceAmount(item.priceAmount())
                                    .stockQuantity(item.stockQuantity())
                                    .isOffering(item.isOffering())
                                    .createdAt(item.createdAt())
                                    .build();
                        }
                    }
                }

                return item;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Save trade item failed: " + e.getMessage());
                logger.error("Failed to save trade item: " + e.getMessage());
                throw new RuntimeException("Failed to save trade item", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteTradeItem(int tradeItemId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("trade_items") + " WHERE trade_item_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, tradeItemId);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Delete trade item failed: " + e.getMessage());
                logger.error("Failed to delete trade item: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> updateStock(int tradeItemId, int newQuantity) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + t("trade_items") + " SET stock_quantity = ? WHERE trade_item_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, newQuantity);
                stmt.setInt(2, tradeItemId);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Update stock failed: " + e.getMessage());
                logger.error("Failed to update stock: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ========================================================
    // Statistics
    // ========================================================

    @Override
    public CompletableFuture<Integer> count() {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("shops") + "";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    fallbackTracker.recordSuccess();
                    return rs.getInt(1);
                }
                return 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Count shops failed: " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> countByOwner(UUID ownerUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("shops") + " WHERE owner_uuid = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, ownerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getInt(1);
                    }
                }
                return 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Count shops by owner failed: " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    // ========================================================
    // Metadata Operations
    // ========================================================

    @Override
    public CompletableFuture<Optional<String>> getMetadata(int shopId, String key) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT meta_value FROM " + t("shop_metadata") + " WHERE shop_id = ? AND meta_key = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                stmt.setString(2, key);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return Optional.ofNullable(rs.getString("meta_value"));
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Get metadata failed: " + e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> setMetadata(int shopId, String key, String value) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = isMysql()
                    ? "INSERT INTO " + t("shop_metadata") + " (shop_id, meta_key, meta_value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE meta_value = ?"
                    : "INSERT OR REPLACE INTO " + t("shop_metadata") + " (shop_id, meta_key, meta_value) VALUES (?, ?, ?)";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                stmt.setString(2, key);
                stmt.setString(3, value);
                if (isMysql()) {
                    stmt.setString(4, value);
                }

                stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return true;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Set metadata failed: " + e.getMessage());
                logger.error("Failed to set metadata: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> removeMetadata(int shopId, String key) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("shop_metadata") + " WHERE shop_id = ? AND meta_key = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                stmt.setString(2, key);

                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Remove metadata failed: " + e.getMessage());
                logger.error("Failed to remove metadata: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ========================================================
    // Fallback Mode
    // ========================================================

    /**
     * Checks if the repository is in fallback mode.
     * Not part of interface - provided for convenience.
     *
     * @return true if in fallback mode
     */
    public boolean isInFallbackMode() {
        return fallbackTracker.isInFallbackMode();
    }

    // ========================================================
    // Private Helper Methods
    // ========================================================

    private boolean isMysql() {
        String type = connectionProvider.getDatabaseType();
        return type != null && (type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb"));
    }

    private ShopDataDTO mapRowToShop(ResultSet rs, Map<String, String> metadata) throws SQLException {
        return ShopDataDTO.builder()
                .shopId(rs.getInt("shop_id"))
                .ownerUuid(UUID.fromString(rs.getString("owner_uuid")))
                .shopName(rs.getString("shop_name"))
                .shopType(ShopDataDTO.ShopType.valueOf(rs.getString("shop_type")))
                .signLocation(
                        rs.getString("location_world"),
                        rs.getDouble("location_x"),
                        rs.getDouble("location_y"),
                        rs.getDouble("location_z"))
                .chestLocation(
                        rs.getString("chest_location_world"),
                        rs.getDouble("chest_location_x"),
                        rs.getDouble("chest_location_y"),
                        rs.getDouble("chest_location_z"))
                .isActive(rs.getBoolean("is_active"))
                .createdAt(rs.getTimestamp("created_at"))
                .lastModified(rs.getTimestamp("last_modified"))
                .metadata(metadata)
                .build();
    }

    private TradeItemDTO mapRowToTradeItem(ResultSet rs) throws SQLException {
        return TradeItemDTO.builder()
                .tradeItemId(rs.getInt("trade_item_id"))
                .shopId(rs.getInt("shop_id"))
                .itemStackData(rs.getString("item_stack_data"))
                .currencyMaterial(rs.getString("currency_material"))
                .priceAmount(rs.getInt("price_amount"))
                .stockQuantity(rs.getInt("stock_quantity"))
                .isOffering(rs.getBoolean("is_offering"))
                .createdAt(rs.getTimestamp("created_at"))
                .build();
    }

    private Map<String, String> loadMetadataInternal(Connection conn, int shopId) throws SQLException {
        String sql = "SELECT meta_key, meta_value FROM " + t("shop_metadata") + " WHERE shop_id = ?";
        Map<String, String> metadata = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, shopId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    metadata.put(rs.getString("meta_key"), rs.getString("meta_value"));
                }
            }
        }

        return metadata;
    }

    private void saveMetadataInternal(Connection conn, int shopId, Map<String, String> metadata)
            throws SQLException {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        String sql = isMysql()
                ? "INSERT INTO " + t("shop_metadata") + " (shop_id, meta_key, meta_value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE meta_value = ?"
                : "INSERT OR REPLACE INTO " + t("shop_metadata") + " (shop_id, meta_key, meta_value) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                stmt.setInt(1, shopId);
                stmt.setString(2, entry.getKey());
                stmt.setString(3, entry.getValue());
                if (isMysql()) {
                    stmt.setString(4, entry.getValue());
                }
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
