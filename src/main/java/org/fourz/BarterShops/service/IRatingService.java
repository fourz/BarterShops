package org.fourz.BarterShops.service;

import org.fourz.BarterShops.data.dto.RatingDataDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for shop rating operations.
 * Provides business logic for the rating and review system.
 *
 * <p>This interface is registered with RVNKCore ServiceRegistry for cross-plugin access.</p>
 */
public interface IRatingService {

    /**
     * Submits or updates a rating for a shop.
     * If the player has already rated this shop, updates the existing rating.
     *
     * @param shopId The shop ID to rate
     * @param raterUuid The UUID of the player rating
     * @param rating The star rating (1-5)
     * @param review Optional written review (can be null)
     * @return CompletableFuture containing the saved rating
     */
    CompletableFuture<RatingDataDTO> rateShop(int shopId, UUID raterUuid, int rating, String review);

    /**
     * Gets all ratings for a specific shop.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing list of ratings
     */
    CompletableFuture<List<RatingDataDTO>> getShopRatings(int shopId);

    /**
     * Gets all ratings with reviews for a specific shop.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing list of ratings with reviews
     */
    CompletableFuture<List<RatingDataDTO>> getShopReviews(int shopId);

    /**
     * Gets a player's rating for a specific shop.
     *
     * @param shopId The shop ID
     * @param raterUuid The rater's UUID
     * @return CompletableFuture containing the rating, or empty if not found
     */
    CompletableFuture<Optional<RatingDataDTO>> getPlayerRating(int shopId, UUID raterUuid);

    /**
     * Gets all ratings submitted by a player.
     *
     * @param raterUuid The rater's UUID
     * @return CompletableFuture containing list of ratings by the player
     */
    CompletableFuture<List<RatingDataDTO>> getPlayerRatings(UUID raterUuid);

    /**
     * Gets the average rating for a shop.
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
    CompletableFuture<Integer> getRatingCount(int shopId);

    /**
     * Gets the average rating for all shops owned by a player.
     *
     * @param ownerUuid The shop owner's UUID
     * @return CompletableFuture containing the average rating across all their shops
     */
    CompletableFuture<Double> getAverageRatingForOwner(UUID ownerUuid);

    /**
     * Gets the total rating count for all shops owned by a player.
     *
     * @param ownerUuid The shop owner's UUID
     * @return CompletableFuture containing the total rating count across all their shops
     */
    CompletableFuture<Integer> getRatingCountForOwner(UUID ownerUuid);

    /**
     * Gets a breakdown of ratings by star value for a shop.
     *
     * @param shopId The shop ID
     * @return CompletableFuture containing a map of star rating to count
     */
    CompletableFuture<java.util.Map<Integer, Integer>> getRatingBreakdown(int shopId);

    /**
     * Deletes a rating (admin/moderation function).
     *
     * @param ratingId The rating ID to delete
     * @return CompletableFuture with true if deleted
     */
    CompletableFuture<Boolean> deleteRating(int ratingId);

    /**
     * Checks if a player can rate a shop.
     * Validates that the player is not the shop owner.
     *
     * @param shopId The shop ID
     * @param playerUuid The player's UUID
     * @return CompletableFuture with true if the player can rate
     */
    CompletableFuture<Boolean> canPlayerRate(int shopId, UUID playerUuid);

    /**
     * Gets the top-rated shops.
     *
     * @param limit Maximum number of shops to return
     * @return CompletableFuture containing list of shop IDs sorted by rating
     */
    CompletableFuture<List<Integer>> getTopRatedShops(int limit);
}
