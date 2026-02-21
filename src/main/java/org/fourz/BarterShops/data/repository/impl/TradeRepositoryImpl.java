package org.fourz.BarterShops.data.repository.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.data.dto.TradeRecordDTO;
import org.fourz.BarterShops.data.repository.ITradeRepository;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of ITradeRepository for MySQL and SQLite databases.
 * All operations are async using CompletableFuture.
 * Uses FallbackTracker for graceful degradation.
 */
public class TradeRepositoryImpl implements ITradeRepository {

    private final IConnectionProvider connectionProvider;
    private final FallbackTracker fallbackTracker;
    private final LogManager logger;
    private final ExecutorService executor;

    // Table name helper
    private String t(String baseName) {
        return connectionProvider.table(baseName);
    }

    /**
     * Creates a new TradeRepositoryImpl.
     *
     * @param plugin The BarterShops plugin instance
     * @param connectionProvider The connection provider
     * @param fallbackTracker The fallback tracker for error handling
     */
    public TradeRepositoryImpl(BarterShops plugin, IConnectionProvider connectionProvider,
                               FallbackTracker fallbackTracker) {
        this(connectionProvider, fallbackTracker, LogManager.getInstance(plugin, "TradeRepository"));
    }

    /**
     * Creates a new TradeRepositoryImpl with injected LogManager.
     * This constructor supports dependency injection for testing.
     *
     * @param connectionProvider The connection provider
     * @param fallbackTracker The fallback tracker for error handling
     * @param logger The LogManager instance for logging
     */
    public TradeRepositoryImpl(IConnectionProvider connectionProvider,
                               FallbackTracker fallbackTracker, LogManager logger) {
        this.connectionProvider = connectionProvider;
        this.fallbackTracker = fallbackTracker;
        this.logger = logger;
        this.executor = Executors.newFixedThreadPool(4);
    }

    // ========================================================
    // Trade Record CRUD
    // ========================================================

    @Override
    public CompletableFuture<TradeRecordDTO> save(TradeRecordDTO trade) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(trade);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + t("trade_records") + " (transaction_id, shop_id, buyer_uuid, seller_uuid, " +
                "item_stack_data, quantity, currency_material, price_paid, status, completed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, trade.transactionId());
                stmt.setInt(2, trade.shopId());
                stmt.setString(3, trade.buyerUuid().toString());
                stmt.setString(4, trade.sellerUuid().toString());
                stmt.setString(5, trade.itemStackData());
                stmt.setInt(6, trade.quantity());
                stmt.setString(7, trade.currencyMaterial());
                stmt.setInt(8, trade.pricePaid());
                stmt.setString(9, trade.status().name());
                stmt.setTimestamp(10, trade.completedAt());

                stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return trade;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Save trade record failed: " + e.getMessage());
                logger.error("Failed to save trade record: " + e.getMessage());
                throw new RuntimeException("Failed to save trade record", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<TradeRecordDTO>> findByTransactionId(String transactionId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("trade_records") + " WHERE transaction_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, transactionId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapRowToTrade(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find trade by ID failed: " + e.getMessage());
                logger.error("Failed to find trade by transaction ID: " + e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteByTransactionId(String transactionId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("trade_records") + " WHERE transaction_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, transactionId);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Delete trade record failed: " + e.getMessage());
                logger.error("Failed to delete trade record: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ========================================================
    // Trade History Queries
    // ========================================================

    @Override
    public CompletableFuture<List<TradeRecordDTO>> findByPlayer(UUID playerUuid, int limit) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("trade_records") +
                " WHERE buyer_uuid = ? OR seller_uuid = ? ORDER BY completed_at DESC LIMIT ?";
            List<TradeRecordDTO> trades = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                String uuidStr = playerUuid.toString();
                stmt.setString(1, uuidStr);
                stmt.setString(2, uuidStr);
                stmt.setInt(3, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        trades.add(mapRowToTrade(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return trades;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find trades by player failed: " + e.getMessage());
                logger.error("Failed to find trades by player: " + e.getMessage());
                return trades;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TradeRecordDTO>> findByBuyer(UUID buyerUuid, int limit) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("trade_records") +
                " WHERE buyer_uuid = ? ORDER BY completed_at DESC LIMIT ?";
            List<TradeRecordDTO> trades = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, buyerUuid.toString());
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        trades.add(mapRowToTrade(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return trades;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find trades by buyer failed: " + e.getMessage());
                logger.error("Failed to find trades by buyer: " + e.getMessage());
                return trades;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TradeRecordDTO>> findBySeller(UUID sellerUuid, int limit) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("trade_records") +
                " WHERE seller_uuid = ? ORDER BY completed_at DESC LIMIT ?";
            List<TradeRecordDTO> trades = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, sellerUuid.toString());
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        trades.add(mapRowToTrade(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return trades;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find trades by seller failed: " + e.getMessage());
                logger.error("Failed to find trades by seller: " + e.getMessage());
                return trades;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TradeRecordDTO>> findByShop(int shopId, int limit) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("trade_records") +
                " WHERE shop_id = ? ORDER BY completed_at DESC LIMIT ?";
            List<TradeRecordDTO> trades = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        trades.add(mapRowToTrade(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return trades;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find trades by shop failed: " + e.getMessage());
                logger.error("Failed to find trades by shop: " + e.getMessage());
                return trades;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TradeRecordDTO>> findByDateRange(
            Timestamp start, Timestamp end, int limit) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("trade_records") +
                " WHERE completed_at >= ? AND completed_at < ? ORDER BY completed_at DESC LIMIT ?";
            List<TradeRecordDTO> trades = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setTimestamp(1, start);
                stmt.setTimestamp(2, end);
                stmt.setInt(3, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        trades.add(mapRowToTrade(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return trades;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find trades by date range failed: " + e.getMessage());
                logger.error("Failed to find trades by date range: " + e.getMessage());
                return trades;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<TradeRecordDTO>> findRecent(int limit) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            List<TradeRecordDTO> trades = new ArrayList<>();
            String sql = "SELECT * FROM " + t("trade_records") +
                " ORDER BY completed_at DESC LIMIT ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        trades.add(mapRowToTrade(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return trades;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find recent trades failed: " + e.getMessage());
                logger.error("Failed to find recent trades: " + e.getMessage());
                return trades;
            }
        }, executor);
    }

    // ========================================================
    // Statistics
    // ========================================================

    @Override
    public CompletableFuture<Long> count() {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("trade_records") + "";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    fallbackTracker.recordSuccess();
                    return rs.getLong(1);
                }
                return 0L;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Count trades failed: " + e.getMessage());
                return 0L;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> countByPlayer(UUID playerUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("trade_records") +
                " WHERE buyer_uuid = ? OR seller_uuid = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                String uuidStr = playerUuid.toString();
                stmt.setString(1, uuidStr);
                stmt.setString(2, uuidStr);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getLong(1);
                    }
                }
                return 0L;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Count trades by player failed: " + e.getMessage());
                return 0L;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> countByShop(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("trade_records") + " WHERE shop_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getLong(1);
                    }
                }
                return 0L;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Count trades by shop failed: " + e.getMessage());
                return 0L;
            }
        }, executor);
    }

    /**
     * Gets count of trades with a specific status.
     * Note: This is a convenience method not in the interface.
     *
     * @param status The trade status
     * @return CompletableFuture containing the count
     */
    public CompletableFuture<Integer> countByStatus(TradeRecordDTO.TradeStatus status) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("trade_records") + " WHERE status = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, status.name());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getInt(1);
                    }
                }
                return 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Count trades by status failed: " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> getTotalVolumeByShop(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COALESCE(SUM(quantity), 0) FROM " + t("trade_records") +
                " WHERE shop_id = ? AND status = 'COMPLETED'";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getLong(1);
                    }
                }
                return 0L;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Get total volume by shop failed: " + e.getMessage());
                return 0L;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Long> countByDateRange(Timestamp start, Timestamp end) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + t("trade_records") + " WHERE completed_at >= ? AND completed_at < ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setTimestamp(1, start);
                stmt.setTimestamp(2, end);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getLong(1);
                    }
                }
                return 0L;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Count trades by date range failed: " + e.getMessage());
                return 0L;
            }
        }, executor);
    }

    /**
     * Gets total volume (sum of all quantities) for a player.
     * Note: This is a convenience method not in the interface.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture containing total volume
     */
    public CompletableFuture<Long> sumVolumeByPlayer(UUID playerUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0L);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT SUM(price_paid) FROM " + t("trade_records") +
                " WHERE (buyer_uuid = ? OR seller_uuid = ?) AND status = 'COMPLETED'";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                String uuidStr = playerUuid.toString();
                stmt.setString(1, uuidStr);
                stmt.setString(2, uuidStr);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getLong(1);
                    }
                }
                return 0L;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Sum volume by player failed: " + e.getMessage());
                return 0L;
            }
        }, executor);
    }

    // ========================================================
    // Bulk Operations
    // ========================================================

    /**
     * Deletes all trade records for a shop.
     * Note: This is a convenience method not in the interface.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing number of deleted records
     */
    public CompletableFuture<Integer> deleteByShop(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("trade_records") + " WHERE shop_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Delete trades by shop failed: " + e.getMessage());
                logger.error("Failed to delete trades by shop: " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> deleteOlderThan(Timestamp threshold) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("trade_records") + " WHERE completed_at < ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setTimestamp(1, threshold);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                logger.info("Purged " + affected + " old trade records");
                return affected;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Delete old trades failed: " + e.getMessage());
                logger.error("Failed to delete old trades: " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> archiveOlderThan(Timestamp before) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            // For now, archive is a soft operation - we move to archive table
            // If archive table doesn't exist, we just return 0 (no archiving capability)
            String archiveTable = t("trade_records_archive");

            try (Connection conn = connectionProvider.getConnection()) {
                // Use DatabaseMetaData — safe, no SQL concatenation, works for MySQL and SQLite
                try (ResultSet checkRs = conn.getMetaData().getTables(null, null, archiveTable, new String[]{"TABLE"})) {
                    if (!checkRs.next()) {
                        // Archive table doesn't exist, just return 0
                        logger.debug("Archive table does not exist, skipping archive operation");
                        return 0;
                    }
                }

                // Archive the records
                String archiveSql = "INSERT INTO " + archiveTable +
                    " SELECT * FROM " + t("trade_records") + " WHERE completed_at < ?";
                String deleteSql = "DELETE FROM " + t("trade_records") + " WHERE completed_at < ?";

                conn.setAutoCommit(false);
                try {
                    int archived;
                    try (PreparedStatement archiveStmt = conn.prepareStatement(archiveSql)) {
                        archiveStmt.setTimestamp(1, before);
                        archived = archiveStmt.executeUpdate();
                    }

                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                        deleteStmt.setTimestamp(1, before);
                        deleteStmt.executeUpdate();
                    }

                    conn.commit();
                    fallbackTracker.recordSuccess();
                    logger.info("Archived " + archived + " trade records");
                    return archived;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Archive old trades failed: " + e.getMessage());
                logger.error("Failed to archive old trades: " + e.getMessage());
                return 0;
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

    private TradeRecordDTO mapRowToTrade(ResultSet rs) throws SQLException {
        return TradeRecordDTO.builder()
                .transactionId(rs.getString("transaction_id"))
                .shopId(rs.getInt("shop_id"))
                .buyerUuid(UUID.fromString(rs.getString("buyer_uuid")))
                .sellerUuid(UUID.fromString(rs.getString("seller_uuid")))
                .itemStackData(rs.getString("item_stack_data"))
                .quantity(rs.getInt("quantity"))
                .currencyMaterial(rs.getString("currency_material"))
                .pricePaid(rs.getInt("price_paid"))
                .status(TradeRecordDTO.TradeStatus.valueOf(rs.getString("status")))
                .completedAt(rs.getTimestamp("completed_at"))
                .build();
    }

    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
