package org.fourz.BarterShops.service.impl;

import java.util.List;

/**
 * Utility class for rating calculation operations.
 */
class RatingCalculator {

    private RatingCalculator() {}

    /**
     * Computes a weighted average rating from per-shop [avgRating, count] pairs.
     * Shops with zero ratings are excluded from the average.
     *
     * @param ratingCountPairs list of double[] where [0]=avgRating, [1]=ratingCount
     * @return weighted average, or 0.0 if no ratings exist
     */
    static double weightedAverage(List<double[]> ratingCountPairs) {
        double total = 0.0;
        int count = 0;
        for (double[] pair : ratingCountPairs) {
            int shopCount = (int) pair[1];
            if (shopCount > 0) {
                total += pair[0] * shopCount;
                count += shopCount;
            }
        }
        return count > 0 ? total / count : 0.0;
    }
}
