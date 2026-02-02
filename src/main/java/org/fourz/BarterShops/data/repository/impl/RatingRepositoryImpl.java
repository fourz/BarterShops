package org.fourz.BarterShops.data.repository.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.FallbackTracker;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.data.dto.RatingDataDTO;
import org.fourz.BarterShops.data.repository.IRatingRepository;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of IRatingRepository for MySQL and SQLite databases.
 * All operations are async using CompletableFuture.
 * Uses FallbackTracker for graceful degradation.
 */
public class RatingRepositoryImpl implements IRatingRepository {

    private final IConnectionProvider connectionProvider;
    private final FallbackTracker fallbackTracker;
    private final LogManager logger;
    private final ExecutorService executor;

    // Table name helper
    private String t(String baseName) {
        return connectionProvider.table(baseName);
    }

    /**
     * Creates a new RatingRepositoryImpl.
     *
     * @param plugin The BarterShops plugin instance
     * @param connectionProvider The connection provider
     * @param fallbackTracker The fallback tracker for error handling
     */
    public RatingRepositoryImpl(BarterShops plugin, IConnectionProvider connectionProvider,
                                FallbackTracker fallbackTracker) {
        this(connectionProvider, fallbackTracker, LogManager.getInstance(plugin, "RatingRepository"));
    }

    /**
     * Creates a new RatingRepositoryImpl with injected LogManager.
     * This constructor supports dependency injection for testing.
     *
     * @param connectionProvider The connection provider
     * @param fallbackTracker The fallback tracker for error handling
     * @param logger The LogManager instance for logging
     */
    public RatingRepositoryImpl(IConnectionProvider connectionProvider,
                                FallbackTracker fallbackTracker, LogManager logger) {
        this.connectionProvider = connectionProvider;
        this.fallbackTracker = fallbackTracker;
        this.logger = logger;
        this.executor = Executors.newFixedThreadPool(2);
    }

    // ========================================================
    // Rating CRUD Operations
    // ========================================================

    @Override
    public CompletableFuture<RatingDataDTO> save(RatingDataDTO rating) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(rating);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql;
            boolean isInsert = rating.ratingId() <= 0;

            if (isInsert) {
                sql = """
                    INSERT INTO " + t("shop_ratings") + " (shop_id, rater_uuid, rating, review, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            } else {
                sql = """
                    UPDATE " + t("shop_ratings") + " SET shop_id = ?, rater_uuid = ?, rating = ?,
                        review = ?, created_at = ?
                    WHERE rating_id = ?
                    """;
            }

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setInt(1, rating.shopId());
                stmt.setString(2, rating.raterUuid().toString());
                stmt.setInt(3, rating.rating());
                stmt.setString(4, rating.review());
                stmt.setTimestamp(5, rating.createdAt());

                if (!isInsert) {
                    stmt.setInt(6, rating.ratingId());
                }

                stmt.executeUpdate();
                fallbackTracker.recordSuccess();

                if (isInsert) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            int newId = keys.getInt(1);
                            return RatingDataDTO.builder()
                                    .ratingId(newId)
                                    .shopId(rating.shopId())
                                    .raterUuid(rating.raterUuid())
                                    .rating(rating.rating())
                                    .review(rating.review())
                                    .createdAt(rating.createdAt())
                                    .build();
                        }
                    }
                }

                return rating;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Save rating failed: " + e.getMessage());
                logger.error("Failed to save rating: " + e.getMessage());
                throw new RuntimeException("Failed to save rating", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<RatingDataDTO>> findById(int ratingId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shop_ratings") + " WHERE rating_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, ratingId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapRowToRating(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find rating by ID failed: " + e.getMessage());
                logger.error("Failed to find rating by ID: " + e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteById(int ratingId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("shop_ratings") + " WHERE rating_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, ratingId);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Delete rating failed: " + e.getMessage());
                logger.error("Failed to delete rating: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> existsByShopAndRater(int shopId, UUID raterUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + t("shop_ratings") + " WHERE shop_id = ? AND rater_uuid = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                stmt.setString(2, raterUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    boolean exists = rs.next();
                    fallbackTracker.recordSuccess();
                    return exists;
                }

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Check rating exists failed: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ========================================================
    // Rating Queries
    // ========================================================

    @Override
    public CompletableFuture<List<RatingDataDTO>> findByShop(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shop_ratings") + " WHERE shop_id = ? ORDER BY created_at DESC";
            List<RatingDataDTO> ratings = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ratings.add(mapRowToRating(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return ratings;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find ratings by shop failed: " + e.getMessage());
                logger.error("Failed to find ratings by shop: " + e.getMessage());
                return ratings;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<RatingDataDTO>> findByRater(UUID raterUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shop_ratings") + " WHERE rater_uuid = ? ORDER BY created_at DESC";
            List<RatingDataDTO> ratings = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, raterUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ratings.add(mapRowToRating(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return ratings;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find ratings by rater failed: " + e.getMessage());
                logger.error("Failed to find ratings by rater: " + e.getMessage());
                return ratings;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<RatingDataDTO>> findByShopAndRater(int shopId, UUID raterUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shop_ratings") + " WHERE shop_id = ? AND rater_uuid = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                stmt.setString(2, raterUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapRowToRating(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find rating by shop and rater failed: " + e.getMessage());
                logger.error("Failed to find rating by shop and rater: " + e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<RatingDataDTO>> findReviewsByShop(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM " + t("shop_ratings") + "
                WHERE shop_id = ? AND review IS NOT NULL AND review != ''
                ORDER BY created_at DESC
                """;
            List<RatingDataDTO> ratings = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ratings.add(mapRowToRating(rs));
                    }
                }

                fallbackTracker.recordSuccess();
                return ratings;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find reviews by shop failed: " + e.getMessage());
                logger.error("Failed to find reviews by shop: " + e.getMessage());
                return ratings;
            }
        }, executor);
    }

    // ========================================================
    // Rating Statistics
    // ========================================================

    @Override
    public CompletableFuture<Double> getAverageRating(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0.0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT AVG(rating) as avg_rating FROM " + t("shop_ratings") + " WHERE shop_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getDouble("avg_rating");
                    }
                }

                fallbackTracker.recordSuccess();
                return 0.0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Get average rating failed: " + e.getMessage());
                logger.error("Failed to get average rating: " + e.getMessage());
                return 0.0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> countByShop(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) as count FROM " + t("shop_ratings") + " WHERE shop_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getInt("count");
                    }
                }

                return 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Count ratings by shop failed: " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Integer> countByShopAndRating(int shopId, int starRating) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) as count FROM " + t("shop_ratings") + " WHERE shop_id = ? AND rating = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                stmt.setInt(2, starRating);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fallbackTracker.recordSuccess();
                        return rs.getInt("count");
                    }
                }

                return 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Count ratings by shop and rating failed: " + e.getMessage());
                return 0;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<Integer>> findTopRatedShops(int limit) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT shop_id, AVG(rating) as avg_rating
                FROM " + t("shop_ratings") + "
                GROUP BY shop_id
                ORDER BY avg_rating DESC
                LIMIT ?
                """;
            List<Integer> shopIds = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        shopIds.add(rs.getInt("shop_id"));
                    }
                }

                fallbackTracker.recordSuccess();
                return shopIds;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find top rated shops failed: " + e.getMessage());
                logger.error("Failed to find top rated shops: " + e.getMessage());
                return shopIds;
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

    private RatingDataDTO mapRowToRating(ResultSet rs) throws SQLException {
        return RatingDataDTO.builder()
                .ratingId(rs.getInt("rating_id"))
                .shopId(rs.getInt("shop_id"))
                .raterUuid(UUID.fromString(rs.getString("rater_uuid")))
                .rating(rs.getInt("rating"))
                .review(rs.getString("review"))
                .createdAt(rs.getTimestamp("created_at"))
                .build();
    }

    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
