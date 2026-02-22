package org.fourz.BarterShops.data.repository;

import org.fourz.BarterShops.data.dto.RatingDataDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for shop rating data access.
 * Follows the Repository pattern for data access abstraction.
 *
 * <p>All methods return CompletableFuture for async database operations.</p>
 * <p>Implementations should use FallbackTracker for graceful degradation.</p>
 */
public interface IRatingRepository {

    // ========================================================
    // Rating CRUD Operations
    // ========================================================

    /**
     * Saves a new rating or updates an existing one.
     *
     * @param rating The rating data to save
     * @return CompletableFuture containing the saved rating with generated ID
     */
    CompletableFuture<RatingDataDTO> save(RatingDataDTO rating);

    /**
     * Finds a rating by its unique ID.
     *
     * @param ratingId The rating ID to find
     * @return CompletableFuture containing the rating, or empty if not found
     */
    CompletableFuture<Optional<RatingDataDTO>> findById(int ratingId);

    /**
     * Deletes a rating by its ID.
     *
     * @param ratingId The rating ID to delete
     * @return CompletableFuture with true if deleted, false if not found
     */
    CompletableFuture<Boolean> deleteById(int ratingId);

    /**
     * Checks if a rating exists for a specific player and shop.
     *
     * @param shopId The shop ID
     * @param raterUuid The rater's UUID
     * @return CompletableFuture with true if exists
     */
    CompletableFuture<Boolean> existsByShopAndRater(int shopId, UUID raterUuid);

    // ========================================================
    // Rating Queries
    // ========================================================

    /**
     * Finds all ratings for a specific shop.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing list of ratings for the shop
     */
    CompletableFuture<List<RatingDataDTO>> findByShop(int shopId);

    /**
     * Finds all ratings given by a specific player.
     *
     * @param raterUuid The rater's UUID
     * @return CompletableFuture containing list of ratings by the player
     */
    CompletableFuture<List<RatingDataDTO>> findByRater(UUID raterUuid);

    /**
     * Finds a specific rating by shop and rater.
     *
     * @param shopId The shop ID
     * @param raterUuid The rater's UUID
     * @return CompletableFuture containing the rating, or empty if not found
     */
    CompletableFuture<Optional<RatingDataDTO>> findByShopAndRater(int shopId, UUID raterUuid);

    /**
     * Finds all ratings with reviews (non-null, non-empty review text).
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing list of ratings with reviews
     */
    CompletableFuture<List<RatingDataDTO>> findReviewsByShop(int shopId);

    // ========================================================
    // Rating Statistics
    // ========================================================

    /**
     * Calculates the average rating for a shop.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing the average rating (0.0 if no ratings)
     */
    CompletableFuture<Double> getAverageRating(int shopId);

    /**
     * Gets the total count of ratings for a shop.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing the rating count
     */
    CompletableFuture<Integer> countByShop(int shopId);

    /**
     * Gets the count of ratings by star value for a shop.
     *
     * @param shopId The shop ID
     * @param starRating The star rating (1-5)
     * @return CompletableFuture containing the count of ratings with that star value
     */
    CompletableFuture<Integer> countByShopAndRating(int shopId, int starRating);

    /**
     * Gets all shop IDs sorted by average rating (highest first).
     *
     * @param limit Maximum number of shops to return
     * @return CompletableFuture containing list of shop IDs sorted by rating
     */
    CompletableFuture<List<Integer>> findTopRatedShops(int limit);
}
