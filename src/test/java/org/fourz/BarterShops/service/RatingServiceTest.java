package org.fourz.BarterShops.service;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.RatingDataDTO;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.repository.IRatingRepository;
import org.fourz.BarterShops.data.repository.IShopRepository;
import org.fourz.BarterShops.service.impl.RatingServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RatingServiceImpl.
 * Tests rating CRUD operations, validation, and aggregation logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RatingServiceImpl Tests")
class RatingServiceTest {

    @Mock
    private BarterShops plugin;

    @Mock
    private IRatingRepository ratingRepository;

    @Mock
    private IShopRepository shopRepository;

    private RatingServiceImpl ratingService;
    private UUID testRaterUuid;
    private UUID testOwnerUuid;
    private UUID testOtherPlayerUuid;
    private int testShopId;

    @BeforeEach
    void setUp() {
        testRaterUuid = UUID.randomUUID();
        testOwnerUuid = UUID.randomUUID();
        testOtherPlayerUuid = UUID.randomUUID();
        testShopId = 1;

        ratingService = new RatingServiceImpl(plugin, ratingRepository, shopRepository);
    }

    // ====== Rating Creation Tests ======

    @Nested
    @DisplayName("Rating Creation")
    class RatingCreationTests {

        @Test
        @DisplayName("rateShop creates new rating when none exists")
        void rateShopCreatesNewRating() throws ExecutionException, InterruptedException {
            RatingDataDTO expectedRating = RatingDataDTO.create(testShopId, testRaterUuid, 5, "Great shop!");
            when(ratingRepository.findByShopAndRater(testShopId, testRaterUuid))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(ratingRepository.save(any(RatingDataDTO.class)))
                .thenReturn(CompletableFuture.completedFuture(expectedRating));

            RatingDataDTO result = ratingService.rateShop(testShopId, testRaterUuid, 5, "Great shop!").get();

            assertNotNull(result);
            assertEquals(5, result.rating());
            assertEquals("Great shop!", result.review());
            verify(ratingRepository).save(any(RatingDataDTO.class));
        }

        @Test
        @DisplayName("rateShop updates existing rating")
        void rateShopUpdatesExistingRating() throws ExecutionException, InterruptedException {
            RatingDataDTO existingRating = RatingDataDTO.builder()
                .ratingId(1)
                .shopId(testShopId)
                .raterUuid(testRaterUuid)
                .rating(3)
                .review("OK shop")
                .createdAt(new Timestamp(System.currentTimeMillis()))
                .build();

            RatingDataDTO updatedRating = RatingDataDTO.builder()
                .ratingId(1)
                .shopId(testShopId)
                .raterUuid(testRaterUuid)
                .rating(5)
                .review("Great shop now!")
                .createdAt(existingRating.createdAt())
                .build();

            when(ratingRepository.findByShopAndRater(testShopId, testRaterUuid))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(existingRating)));
            when(ratingRepository.save(any(RatingDataDTO.class)))
                .thenReturn(CompletableFuture.completedFuture(updatedRating));

            RatingDataDTO result = ratingService.rateShop(testShopId, testRaterUuid, 5, "Great shop now!").get();

            assertNotNull(result);
            assertEquals(5, result.rating());
            assertEquals("Great shop now!", result.review());
            verify(ratingRepository).save(any(RatingDataDTO.class));
        }

        @Test
        @DisplayName("rateShop rejects invalid rating (< 1)")
        void rateShopRejectsInvalidRatingBelowRange() {
            assertThrows(ExecutionException.class, () -> {
                ratingService.rateShop(testShopId, testRaterUuid, 0, "Bad").get();
            });
        }

        @Test
        @DisplayName("rateShop rejects invalid rating (> 5)")
        void rateShopRejectsInvalidRatingAboveRange() {
            assertThrows(ExecutionException.class, () -> {
                ratingService.rateShop(testShopId, testRaterUuid, 6, "Good").get();
            });
        }

        @Test
        @DisplayName("rateShop rejects null raterUuid")
        void rateShopRejectsNullRater() {
            assertThrows(ExecutionException.class, () -> {
                ratingService.rateShop(testShopId, null, 5, "Good").get();
            });
        }

        @Test
        @DisplayName("rateShop accepts null review")
        void rateShopAcceptsNullReview() throws ExecutionException, InterruptedException {
            RatingDataDTO expectedRating = RatingDataDTO.create(testShopId, testRaterUuid, 4, null);
            when(ratingRepository.findByShopAndRater(testShopId, testRaterUuid))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(ratingRepository.save(any(RatingDataDTO.class)))
                .thenReturn(CompletableFuture.completedFuture(expectedRating));

            RatingDataDTO result = ratingService.rateShop(testShopId, testRaterUuid, 4, null).get();

            assertNotNull(result);
            assertNull(result.review());
        }
    }

    // ====== Rating Query Tests ======

    @Nested
    @DisplayName("Rating Queries")
    class RatingQueryTests {

        @Test
        @DisplayName("getShopRatings returns all ratings for shop")
        void getShopRatingsReturnsAllRatings() throws ExecutionException, InterruptedException {
            List<RatingDataDTO> ratings = Arrays.asList(
                RatingDataDTO.create(testShopId, testRaterUuid, 5, "Good"),
                RatingDataDTO.create(testShopId, testOtherPlayerUuid, 4, "OK")
            );

            when(ratingRepository.findByShop(testShopId))
                .thenReturn(CompletableFuture.completedFuture(ratings));

            List<RatingDataDTO> result = ratingService.getShopRatings(testShopId).get();

            assertEquals(2, result.size());
            verify(ratingRepository).findByShop(testShopId);
        }

        @Test
        @DisplayName("getShopReviews returns only ratings with reviews")
        void getShopReviewsReturnsOnlyWithReviews() throws ExecutionException, InterruptedException {
            List<RatingDataDTO> reviews = Arrays.asList(
                RatingDataDTO.create(testShopId, testRaterUuid, 5, "Great!"),
                RatingDataDTO.create(testShopId, testOtherPlayerUuid, 4, "Good")
            );

            when(ratingRepository.findReviewsByShop(testShopId))
                .thenReturn(CompletableFuture.completedFuture(reviews));

            List<RatingDataDTO> result = ratingService.getShopReviews(testShopId).get();

            assertEquals(2, result.size());
            verify(ratingRepository).findReviewsByShop(testShopId);
        }

        @Test
        @DisplayName("getPlayerRating returns player's rating for shop")
        void getPlayerRatingReturnsRating() throws ExecutionException, InterruptedException {
            RatingDataDTO rating = RatingDataDTO.create(testShopId, testRaterUuid, 5, "Great!");
            when(ratingRepository.findByShopAndRater(testShopId, testRaterUuid))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(rating)));

            Optional<RatingDataDTO> result = ratingService.getPlayerRating(testShopId, testRaterUuid).get();

            assertTrue(result.isPresent());
            assertEquals(5, result.get().rating());
        }

        @Test
        @DisplayName("getPlayerRatings returns all ratings by player")
        void getPlayerRatingsReturnsAllRatingsByPlayer() throws ExecutionException, InterruptedException {
            List<RatingDataDTO> ratings = Arrays.asList(
                RatingDataDTO.create(1, testRaterUuid, 5, "Great"),
                RatingDataDTO.create(2, testRaterUuid, 4, "Good"),
                RatingDataDTO.create(3, testRaterUuid, 3, "OK")
            );

            when(ratingRepository.findByRater(testRaterUuid))
                .thenReturn(CompletableFuture.completedFuture(ratings));

            List<RatingDataDTO> result = ratingService.getPlayerRatings(testRaterUuid).get();

            assertEquals(3, result.size());
            verify(ratingRepository).findByRater(testRaterUuid);
        }
    }

    // ====== Average Rating Tests ======

    @Nested
    @DisplayName("Average Ratings")
    class AverageRatingTests {

        @Test
        @DisplayName("getAverageRating returns average for shop")
        void getAverageRatingReturnsAverage() throws ExecutionException, InterruptedException {
            when(ratingRepository.getAverageRating(testShopId))
                .thenReturn(CompletableFuture.completedFuture(4.5));

            Double average = ratingService.getAverageRating(testShopId).get();

            assertEquals(4.5, average);
            verify(ratingRepository).getAverageRating(testShopId);
        }

        @Test
        @DisplayName("getAverageRatingForOwner calculates average across all owned shops")
        void getAverageRatingForOwnerCalculatesAcrossShops() throws ExecutionException, InterruptedException {
            List<ShopDataDTO> ownedShops = Arrays.asList(
                createMockShop(1, testOwnerUuid),
                createMockShop(2, testOwnerUuid),
                createMockShop(3, testOwnerUuid)
            );

            when(shopRepository.findByOwner(testOwnerUuid))
                .thenReturn(CompletableFuture.completedFuture(ownedShops));
            when(ratingRepository.getAverageRating(1))
                .thenReturn(CompletableFuture.completedFuture(5.0));
            when(ratingRepository.getAverageRating(2))
                .thenReturn(CompletableFuture.completedFuture(4.0));
            when(ratingRepository.getAverageRating(3))
                .thenReturn(CompletableFuture.completedFuture(3.0));

            Double average = ratingService.getAverageRatingForOwner(testOwnerUuid).get();

            assertEquals(4.0, average);
            verify(shopRepository).findByOwner(testOwnerUuid);
        }

        @Test
        @DisplayName("getAverageRatingForOwner returns 0 when player owns no shops")
        void getAverageRatingForOwnerReturnsZeroNoShops() throws ExecutionException, InterruptedException {
            when(shopRepository.findByOwner(testOwnerUuid))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

            Double average = ratingService.getAverageRatingForOwner(testOwnerUuid).get();

            assertEquals(0.0, average);
        }

        @Test
        @DisplayName("getAverageRatingForShop returns average for shop (alias)")
        void getAverageRatingForShopAliasReturnsAverage() throws ExecutionException, InterruptedException {
            when(ratingRepository.getAverageRating(testShopId))
                .thenReturn(CompletableFuture.completedFuture(4.5));

            Double average = ratingService.getAverageRatingForShop(testShopId).get();

            assertEquals(4.5, average);
        }
    }

    // ====== Rating Count Tests ======

    @Nested
    @DisplayName("Rating Counts")
    class RatingCountTests {

        @Test
        @DisplayName("getRatingCount returns count for shop")
        void getRatingCountReturnsCount() throws ExecutionException, InterruptedException {
            when(ratingRepository.countByShop(testShopId))
                .thenReturn(CompletableFuture.completedFuture(10));

            Integer count = ratingService.getRatingCount(testShopId).get();

            assertEquals(10, count);
            verify(ratingRepository).countByShop(testShopId);
        }

        @Test
        @DisplayName("getRatingCountForOwner sums ratings across all owned shops")
        void getRatingCountForOwnerSumsAcrossShops() throws ExecutionException, InterruptedException {
            List<ShopDataDTO> ownedShops = Arrays.asList(
                createMockShop(1, testOwnerUuid),
                createMockShop(2, testOwnerUuid),
                createMockShop(3, testOwnerUuid)
            );

            when(shopRepository.findByOwner(testOwnerUuid))
                .thenReturn(CompletableFuture.completedFuture(ownedShops));
            when(ratingRepository.countByShop(1))
                .thenReturn(CompletableFuture.completedFuture(5));
            when(ratingRepository.countByShop(2))
                .thenReturn(CompletableFuture.completedFuture(3));
            when(ratingRepository.countByShop(3))
                .thenReturn(CompletableFuture.completedFuture(2));

            Integer count = ratingService.getRatingCountForOwner(testOwnerUuid).get();

            assertEquals(10, count);
        }

        @Test
        @DisplayName("getRatingCountForOwner returns 0 when player owns no shops")
        void getRatingCountForOwnerReturnsZeroNoShops() throws ExecutionException, InterruptedException {
            when(shopRepository.findByOwner(testOwnerUuid))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

            Integer count = ratingService.getRatingCountForOwner(testOwnerUuid).get();

            assertEquals(0, count);
        }

        @Test
        @DisplayName("getRatingCountForShop returns count for shop (alias)")
        void getRatingCountForShopAliasReturnsCount() throws ExecutionException, InterruptedException {
            when(ratingRepository.countByShop(testShopId))
                .thenReturn(CompletableFuture.completedFuture(7));

            Integer count = ratingService.getRatingCountForShop(testShopId).get();

            assertEquals(7, count);
        }
    }

    // ====== Rating Breakdown Tests ======

    @Nested
    @DisplayName("Rating Breakdown")
    class RatingBreakdownTests {

        @Test
        @DisplayName("getRatingBreakdown returns count for each star rating")
        void getRatingBreakdownReturnsStarCounts() throws ExecutionException, InterruptedException {
            when(ratingRepository.countByShopAndRating(testShopId, 1))
                .thenReturn(CompletableFuture.completedFuture(1));
            when(ratingRepository.countByShopAndRating(testShopId, 2))
                .thenReturn(CompletableFuture.completedFuture(0));
            when(ratingRepository.countByShopAndRating(testShopId, 3))
                .thenReturn(CompletableFuture.completedFuture(2));
            when(ratingRepository.countByShopAndRating(testShopId, 4))
                .thenReturn(CompletableFuture.completedFuture(3));
            when(ratingRepository.countByShopAndRating(testShopId, 5))
                .thenReturn(CompletableFuture.completedFuture(4));

            Map<Integer, Integer> breakdown = ratingService.getRatingBreakdown(testShopId).get();

            assertEquals(1, breakdown.get(1));
            assertEquals(0, breakdown.get(2));
            assertEquals(2, breakdown.get(3));
            assertEquals(3, breakdown.get(4));
            assertEquals(4, breakdown.get(5));
        }
    }

    // ====== Deletion Tests ======

    @Nested
    @DisplayName("Rating Deletion")
    class DeletionTests {

        @Test
        @DisplayName("deleteRating removes rating by ID")
        void deleteRatingRemovesById() throws ExecutionException, InterruptedException {
            when(ratingRepository.deleteById(1))
                .thenReturn(CompletableFuture.completedFuture(true));

            Boolean result = ratingService.deleteRating(1).get();

            assertTrue(result);
            verify(ratingRepository).deleteById(1);
        }

        @Test
        @DisplayName("deleteRating returns false when rating not found")
        void deleteRatingReturnsFalseNotFound() throws ExecutionException, InterruptedException {
            when(ratingRepository.deleteById(999))
                .thenReturn(CompletableFuture.completedFuture(false));

            Boolean result = ratingService.deleteRating(999).get();

            assertFalse(result);
        }
    }

    // ====== Permission Tests ======

    @Nested
    @DisplayName("Player Permissions")
    class PermissionTests {

        @Test
        @DisplayName("canPlayerRate returns false when player is shop owner")
        void canPlayerRateReturnsFalseForOwner() throws ExecutionException, InterruptedException {
            ShopDataDTO shop = createMockShop(testShopId, testRaterUuid); // Player is owner
            when(shopRepository.findById(testShopId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(shop)));

            Boolean result = ratingService.canPlayerRate(testShopId, testRaterUuid).get();

            assertFalse(result);
        }

        @Test
        @DisplayName("canPlayerRate returns true when player is not owner")
        void canPlayerRateReturnsTrueNotOwner() throws ExecutionException, InterruptedException {
            ShopDataDTO shop = createMockShop(testShopId, testOwnerUuid); // Different owner
            when(shopRepository.findById(testShopId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(shop)));

            Boolean result = ratingService.canPlayerRate(testShopId, testRaterUuid).get();

            assertTrue(result);
        }

        @Test
        @DisplayName("canPlayerRate returns false when shop not found")
        void canPlayerRateReturnsFalseShopNotFound() throws ExecutionException, InterruptedException {
            when(shopRepository.findById(999))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

            Boolean result = ratingService.canPlayerRate(999, testRaterUuid).get();

            assertFalse(result);
        }
    }

    // ====== Top Rated Shops Tests ======

    @Nested
    @DisplayName("Top Rated Shops")
    class TopRatedShopsTests {

        @Test
        @DisplayName("getTopRatedShops returns top shops by rating")
        void getTopRatedShopsReturnsTop() throws ExecutionException, InterruptedException {
            List<Integer> topShops = Arrays.asList(5, 3, 1);
            when(ratingRepository.findTopRatedShops(3))
                .thenReturn(CompletableFuture.completedFuture(topShops));

            List<Integer> result = ratingService.getTopRatedShops(3).get();

            assertEquals(3, result.size());
            assertEquals(5, result.get(0));
            verify(ratingRepository).findTopRatedShops(3);
        }

        @Test
        @DisplayName("getTopRatedShops returns empty list when no shops rated")
        void getTopRatedShopsReturnsEmptyWhenNone() throws ExecutionException, InterruptedException {
            when(ratingRepository.findTopRatedShops(5))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

            List<Integer> result = ratingService.getTopRatedShops(5).get();

            assertTrue(result.isEmpty());
        }
    }

    // ====== Helper Methods ======

    private ShopDataDTO createMockShop(int shopId, UUID ownerUuid) {
        return ShopDataDTO.builder()
            .shopId(shopId)
            .ownerUuid(ownerUuid)
            .shopName("Test Shop " + shopId)
            .shopType(ShopDataDTO.ShopType.BARTER)
            .signLocation("world", 0, 64, 0)
            .chestLocation("world", 1, 64, 0)
            .isActive(true)
            .createdAt(new Timestamp(System.currentTimeMillis()))
            .lastModified(new Timestamp(System.currentTimeMillis()))
            .metadata(new HashMap<>())
            .build();
    }
}
