package org.fourz.BarterShops.service.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.RatingDataDTO;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.repository.IRatingRepository;
import org.fourz.BarterShops.data.repository.IShopRepository;
import org.fourz.BarterShops.service.IRatingService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of IRatingService for shop rating operations.
 * Provides business logic for the rating and review system.
 */
public class RatingServiceImpl implements IRatingService {

    private final BarterShops plugin;
    private final IRatingRepository ratingRepository;
    private final IShopRepository shopRepository;
    private final LogManager logger;

    /**
     * Creates a new RatingServiceImpl.
     *
     * @param plugin The BarterShops plugin instance
     * @param ratingRepository The rating repository
     * @param shopRepository The shop repository
     */
    public RatingServiceImpl(BarterShops plugin, IRatingRepository ratingRepository,
                            IShopRepository shopRepository) {
        this.plugin = plugin;
        this.ratingRepository = ratingRepository;
        this.shopRepository = shopRepository;
        this.logger = LogManager.getInstance(plugin, "RatingService");
    }

    @Override
    public CompletableFuture<RatingDataDTO> rateShop(int shopId, UUID raterUuid, int rating, String review) {
        Objects.requireNonNull(raterUuid, "raterUuid cannot be null");

        if (rating < 1 || rating > 5) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Rating must be between 1 and 5")
            );
        }

        // Check if player has already rated this shop
        return ratingRepository.findByShopAndRater(shopId, raterUuid)
            .thenCompose(existingRating -> {
                if (existingRating.isPresent()) {
                    // Update existing rating
                    RatingDataDTO existing = existingRating.get();
                    RatingDataDTO updated = RatingDataDTO.builder()
                        .ratingId(existing.ratingId())
                        .shopId(shopId)
                        .raterUuid(raterUuid)
                        .rating(rating)
                        .review(review)
                        .createdAt(existing.createdAt())
                        .build();
                    return ratingRepository.save(updated);
                } else {
                    // Create new rating
                    RatingDataDTO newRating = RatingDataDTO.create(shopId, raterUuid, rating, review);
                    return ratingRepository.save(newRating);
                }
            })
            .whenComplete((result, error) -> {
                if (error != null) {
                    logger.error("Failed to save rating for shop " + shopId + ": " + error.getMessage());
                } else {
                    logger.debug("Rating saved for shop " + shopId + " by " + raterUuid + ": " + rating + " stars");
                }
            });
    }

    @Override
    public CompletableFuture<List<RatingDataDTO>> getShopRatings(int shopId) {
        return ratingRepository.findByShop(shopId);
    }

    @Override
    public CompletableFuture<List<RatingDataDTO>> getShopReviews(int shopId) {
        return ratingRepository.findReviewsByShop(shopId);
    }

    @Override
    public CompletableFuture<Optional<RatingDataDTO>> getPlayerRating(int shopId, UUID raterUuid) {
        return ratingRepository.findByShopAndRater(shopId, raterUuid);
    }

    @Override
    public CompletableFuture<List<RatingDataDTO>> getPlayerRatings(UUID raterUuid) {
        return ratingRepository.findByRater(raterUuid);
    }

    @Override
    public CompletableFuture<Double> getAverageRating(int shopId) {
        return ratingRepository.getAverageRating(shopId);
    }

    @Override
    public CompletableFuture<Integer> getRatingCount(int shopId) {
        return ratingRepository.countByShop(shopId);
    }

    @Override
    public CompletableFuture<Double> getAverageRatingForOwner(UUID ownerUuid) {
        // Get all shops owned by this player
        return shopRepository.findByOwner(ownerUuid)
            .thenCompose(shops -> {
                if (shops.isEmpty()) {
                    return CompletableFuture.completedFuture(0.0);
                }

                // Get average rating for each shop
                List<CompletableFuture<Double>> ratingFutures = shops.stream()
                    .map(shop -> ratingRepository.getAverageRating(shop.shopId()))
                    .toList();

                return CompletableFuture.allOf(ratingFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        // Calculate overall average
                        double sum = ratingFutures.stream()
                            .map(CompletableFuture::join)
                            .filter(rating -> rating > 0)
                            .mapToDouble(Double::doubleValue)
                            .sum();

                        long count = ratingFutures.stream()
                            .map(CompletableFuture::join)
                            .filter(rating -> rating > 0)
                            .count();

                        return count > 0 ? sum / count : 0.0;
                    });
            });
    }

    @Override
    public CompletableFuture<Integer> getRatingCountForOwner(UUID ownerUuid) {
        // Get all shops owned by this player
        return shopRepository.findByOwner(ownerUuid)
            .thenCompose(shops -> {
                if (shops.isEmpty()) {
                    return CompletableFuture.completedFuture(0);
                }

                // Get rating count for each shop
                List<CompletableFuture<Integer>> countFutures = shops.stream()
                    .map(shop -> ratingRepository.countByShop(shop.shopId()))
                    .toList();

                return CompletableFuture.allOf(countFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> countFutures.stream()
                        .map(CompletableFuture::join)
                        .mapToInt(Integer::intValue)
                        .sum());
            });
    }

    @Override
    public CompletableFuture<Double> getAverageRatingForShop(int shopId) {
        // Alias for getAverageRating
        return getAverageRating(shopId);
    }

    @Override
    public CompletableFuture<Integer> getRatingCountForShop(int shopId) {
        // Alias for getRatingCount
        return getRatingCount(shopId);
    }

    @Override
    public CompletableFuture<Map<Integer, Integer>> getRatingBreakdown(int shopId) {
        // Get counts for each star rating (1-5)
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int star = 1; star <= 5; star++) {
            futures.add(ratingRepository.countByShopAndRating(shopId, star));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<Integer, Integer> breakdown = new HashMap<>();
                for (int star = 1; star <= 5; star++) {
                    breakdown.put(star, futures.get(star - 1).join());
                }
                return breakdown;
            });
    }

    @Override
    public CompletableFuture<Boolean> deleteRating(int ratingId) {
        return ratingRepository.deleteById(ratingId)
            .whenComplete((result, error) -> {
                if (error != null) {
                    logger.error("Failed to delete rating " + ratingId + ": " + error.getMessage());
                } else if (result) {
                    logger.debug("Rating " + ratingId + " deleted");
                }
            });
    }

    @Override
    public CompletableFuture<Boolean> canPlayerRate(int shopId, UUID playerUuid) {
        // Check if the player is the shop owner
        return shopRepository.findById(shopId)
            .thenApply(shopOpt -> {
                if (shopOpt.isEmpty()) {
                    logger.debug("Shop " + shopId + " not found - cannot rate");
                    return false;
                }

                ShopDataDTO shop = shopOpt.get();
                if (shop.ownerUuid().equals(playerUuid)) {
                    logger.debug("Player " + playerUuid + " is owner of shop " + shopId + " - cannot rate own shop");
                    return false;
                }

                return true;
            });
    }

    @Override
    public CompletableFuture<List<Integer>> getTopRatedShops(int limit) {
        return ratingRepository.findTopRatedShops(limit);
    }
}
