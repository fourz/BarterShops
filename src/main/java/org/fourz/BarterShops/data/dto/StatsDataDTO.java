package org.fourz.BarterShops.data.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for shop and player statistics using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer via RVNKCore.
 *
 * <p>Represents analytics data for shops and players in the BarterShops system.</p>
 */
public record StatsDataDTO(
    StatType statType,
    UUID targetUuid,
    String targetName,
    int totalShopsOwned,
    int totalTradesCompleted,
    int totalItemsTraded,
    double averageRating,
    int totalRatings,
    Map<String, Integer> mostTradedItems,
    List<TopShop> topShops,
    ServerStats serverStats
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public StatsDataDTO {
        Objects.requireNonNull(statType, "statType cannot be null");

        // Ensure no negative values
        if (totalShopsOwned < 0) totalShopsOwned = 0;
        if (totalTradesCompleted < 0) totalTradesCompleted = 0;
        if (totalItemsTraded < 0) totalItemsTraded = 0;
        if (totalRatings < 0) totalRatings = 0;

        // Validate rating range
        if (averageRating < 0.0) averageRating = 0.0;
        if (averageRating > 5.0) averageRating = 5.0;

        // Defensive copies for mutable collections
        mostTradedItems = mostTradedItems == null ? Map.of() : Map.copyOf(mostTradedItems);
        topShops = topShops == null ? List.of() : List.copyOf(topShops);
    }

    /**
     * Creates player statistics.
     *
     * @param playerUuid The player's UUID
     * @param playerName The player's name
     * @param shopsOwned Number of shops owned
     * @param tradesCompleted Number of trades completed
     * @param itemsTraded Total items traded
     * @param avgRating Average shop rating
     * @param ratingCount Number of ratings
     * @param tradedItems Map of item types to trade counts
     * @return A new player stats DTO
     */
    public static StatsDataDTO playerStats(UUID playerUuid, String playerName,
            int shopsOwned, int tradesCompleted, int itemsTraded,
            double avgRating, int ratingCount, Map<String, Integer> tradedItems) {
        return new StatsDataDTO(
            StatType.PLAYER,
            playerUuid,
            playerName,
            shopsOwned,
            tradesCompleted,
            itemsTraded,
            avgRating,
            ratingCount,
            tradedItems,
            List.of(),
            null
        );
    }

    /**
     * Creates server-wide statistics.
     *
     * @param serverStats The aggregated server statistics
     * @return A new server stats DTO
     */
    public static StatsDataDTO serverStats(ServerStats serverStats) {
        return new StatsDataDTO(
            StatType.SERVER,
            null,
            "Server",
            serverStats.totalShops(),
            serverStats.totalTrades(),
            serverStats.totalItemsTraded(),
            0.0,
            0,
            serverStats.mostTradedItems(),
            serverStats.topShops(),
            serverStats
        );
    }

    /**
     * Gets a formatted rating string.
     *
     * @return Rating formatted as stars (e.g., "4.5 ★★★★☆")
     */
    public String getFormattedRating() {
        if (totalRatings == 0) {
            return "No ratings";
        }

        int fullStars = (int) Math.floor(averageRating);
        boolean halfStar = (averageRating - fullStars) >= 0.5;

        StringBuilder stars = new StringBuilder();
        stars.append(String.format("%.1f ", averageRating));

        for (int i = 0; i < fullStars; i++) {
            stars.append("★");
        }
        if (halfStar) {
            stars.append("☆");
        }
        for (int i = fullStars + (halfStar ? 1 : 0); i < 5; i++) {
            stars.append("☆");
        }

        return stars.toString();
    }

    /**
     * Gets the top 5 most traded items.
     *
     * @return List of item names sorted by trade count
     */
    public List<String> getTopTradedItems() {
        return mostTradedItems.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5)
            .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
            .toList();
    }

    /**
     * Stat type enumeration.
     */
    public enum StatType {
        /** Player-specific statistics */
        PLAYER,
        /** Shop-specific statistics */
        SHOP,
        /** Server-wide statistics */
        SERVER
    }

    /**
     * Represents server-wide statistics.
     */
    public record ServerStats(
        int totalShops,
        int activeShops,
        int totalPlayers,
        int totalTrades,
        int totalItemsTraded,
        double averageTradesPerShop,
        Map<String, Integer> mostTradedItems,
        List<TopShop> topShops,
        long lastUpdated
    ) {
        /**
         * Compact constructor with validation.
         */
        public ServerStats {
            if (totalShops < 0) totalShops = 0;
            if (activeShops < 0) activeShops = 0;
            if (totalPlayers < 0) totalPlayers = 0;
            if (totalTrades < 0) totalTrades = 0;
            if (totalItemsTraded < 0) totalItemsTraded = 0;
            if (averageTradesPerShop < 0.0) averageTradesPerShop = 0.0;

            mostTradedItems = mostTradedItems == null ? Map.of() : Map.copyOf(mostTradedItems);
            topShops = topShops == null ? List.of() : List.copyOf(topShops);
        }

        /**
         * Creates server stats with current timestamp.
         */
        public static ServerStats create(int totalShops, int activeShops, int totalPlayers,
                int totalTrades, int totalItemsTraded, double avgTrades,
                Map<String, Integer> tradedItems, List<TopShop> topShops) {
            return new ServerStats(
                totalShops, activeShops, totalPlayers, totalTrades,
                totalItemsTraded, avgTrades, tradedItems, topShops,
                System.currentTimeMillis()
            );
        }
    }

    /**
     * Represents a top-performing shop.
     */
    public record TopShop(
        int shopId,
        String shopName,
        UUID ownerUuid,
        String ownerName,
        int tradeCount,
        double rating,
        int ratingCount
    ) {
        /**
         * Compact constructor with validation.
         */
        public TopShop {
            Objects.requireNonNull(shopName, "shopName cannot be null");
            Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");

            if (tradeCount < 0) tradeCount = 0;
            if (rating < 0.0) rating = 0.0;
            if (rating > 5.0) rating = 5.0;
            if (ratingCount < 0) ratingCount = 0;
        }

        /**
         * Gets a formatted rating string.
         */
        public String getFormattedRating() {
            if (ratingCount == 0) {
                return "No ratings";
            }
            return String.format("%.1f ★ (%d)", rating, ratingCount);
        }
    }

    /**
     * Builder for constructing StatsDataDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for StatsDataDTO.
     */
    public static class Builder {
        private StatType statType = StatType.PLAYER;
        private UUID targetUuid;
        private String targetName;
        private int totalShopsOwned = 0;
        private int totalTradesCompleted = 0;
        private int totalItemsTraded = 0;
        private double averageRating = 0.0;
        private int totalRatings = 0;
        private Map<String, Integer> mostTradedItems = Map.of();
        private List<TopShop> topShops = List.of();
        private ServerStats serverStats;

        public Builder statType(StatType statType) {
            this.statType = statType;
            return this;
        }

        public Builder targetUuid(UUID targetUuid) {
            this.targetUuid = targetUuid;
            return this;
        }

        public Builder targetName(String targetName) {
            this.targetName = targetName;
            return this;
        }

        public Builder totalShopsOwned(int totalShopsOwned) {
            this.totalShopsOwned = totalShopsOwned;
            return this;
        }

        public Builder totalTradesCompleted(int totalTradesCompleted) {
            this.totalTradesCompleted = totalTradesCompleted;
            return this;
        }

        public Builder totalItemsTraded(int totalItemsTraded) {
            this.totalItemsTraded = totalItemsTraded;
            return this;
        }

        public Builder averageRating(double averageRating) {
            this.averageRating = averageRating;
            return this;
        }

        public Builder totalRatings(int totalRatings) {
            this.totalRatings = totalRatings;
            return this;
        }

        public Builder mostTradedItems(Map<String, Integer> mostTradedItems) {
            this.mostTradedItems = mostTradedItems;
            return this;
        }

        public Builder topShops(List<TopShop> topShops) {
            this.topShops = topShops;
            return this;
        }

        public Builder serverStats(ServerStats serverStats) {
            this.serverStats = serverStats;
            return this;
        }

        public StatsDataDTO build() {
            return new StatsDataDTO(
                statType, targetUuid, targetName,
                totalShopsOwned, totalTradesCompleted, totalItemsTraded,
                averageRating, totalRatings, mostTradedItems,
                topShops, serverStats
            );
        }
    }
}
