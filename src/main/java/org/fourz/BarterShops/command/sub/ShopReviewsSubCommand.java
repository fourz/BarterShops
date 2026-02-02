package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.RatingDataDTO;
import org.fourz.BarterShops.service.IRatingService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand for viewing shop reviews and ratings.
 * Usage: /shop reviews <shopId>
 * Console-friendly: Yes
 */
public class ShopReviewsSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final IRatingService ratingService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");

    public ShopReviewsSubCommand(BarterShops plugin, IRatingService ratingService) {
        this.plugin = plugin;
        this.ratingService = ratingService;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "▶ " + getUsage());
            sender.sendMessage(ChatColor.GRAY + "   Example: /shop reviews 123");
            return true;
        }

        // Parse shop ID
        int shopId;
        try {
            shopId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "✖ Invalid shop ID: " + args[0]);
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "⚙ Loading reviews for shop #" + shopId + "...");

        // Get ratings and statistics
        ratingService.getAverageRating(shopId)
            .thenCombine(ratingService.getRatingCount(shopId), (avgRating, count) -> {
                sender.sendMessage(ChatColor.GOLD + "===== Shop #" + shopId + " Reviews =====");

                if (count == 0) {
                    sender.sendMessage(ChatColor.GRAY + "No ratings yet for this shop.");
                    return null;
                }

                // Display average rating
                sender.sendMessage(ChatColor.YELLOW + "Average Rating: " + ChatColor.WHITE +
                    String.format("%.1f", avgRating) + "/5.0 " + ChatColor.GRAY + "(" + count + " ratings)");
                sender.sendMessage(ChatColor.YELLOW + "Stars: " + ChatColor.WHITE + getStarDisplay(avgRating));

                return shopId;
            })
            .thenCompose(id -> {
                if (id == null) return null;
                return ratingService.getRatingBreakdown(shopId);
            })
            .thenAccept(breakdown -> {
                if (breakdown == null) return;

                // Display rating breakdown
                sender.sendMessage(ChatColor.GOLD + "----- Rating Breakdown -----");
                for (int star = 5; star >= 1; star--) {
                    int count = breakdown.getOrDefault(star, 0);
                    String bar = getProgressBar(count, breakdown.values().stream().mapToInt(Integer::intValue).sum());
                    sender.sendMessage(ChatColor.YELLOW + String.valueOf(star) + " ★ " + ChatColor.WHITE + bar +
                        ChatColor.GRAY + " (" + count + ")");
                }

                // Get and display reviews
                ratingService.getShopReviews(shopId).thenAccept(reviews -> {
                    if (reviews.isEmpty()) {
                        sender.sendMessage(ChatColor.GRAY + "No written reviews yet.");
                        return;
                    }

                    sender.sendMessage(ChatColor.GOLD + "----- Reviews -----");
                    int displayCount = Math.min(reviews.size(), 5);

                    for (int i = 0; i < displayCount; i++) {
                        RatingDataDTO review = reviews.get(i);
                        String playerName = Bukkit.getOfflinePlayer(review.raterUuid()).getName();
                        if (playerName == null) playerName = "Unknown";

                        sender.sendMessage(ChatColor.YELLOW + getStarDisplay(review.rating()) +
                            ChatColor.GRAY + " - " + playerName +
                            ChatColor.DARK_GRAY + " (" + dateFormat.format(review.createdAt()) + ")");

                        if (review.hasReview()) {
                            sender.sendMessage(ChatColor.WHITE + "  \"" + review.review() + "\"");
                        }
                    }

                    if (reviews.size() > 5) {
                        sender.sendMessage(ChatColor.GRAY + "... and " + (reviews.size() - 5) + " more reviews");
                    }
                });
            })
            .exceptionally(error -> {
                sender.sendMessage(ChatColor.RED + "✖ Failed to load reviews: " + error.getMessage());
                return null;
            });

        return true;
    }

    /**
     * Converts a numeric rating to a star display.
     *
     * @param rating The numeric rating (can be decimal)
     * @return Star display string
     */
    private String getStarDisplay(double rating) {
        StringBuilder stars = new StringBuilder();
        int fullStars = (int) rating;
        boolean hasHalfStar = (rating - fullStars) >= 0.5;

        for (int i = 0; i < 5; i++) {
            if (i < fullStars) {
                stars.append("★");
            } else if (i == fullStars && hasHalfStar) {
                stars.append("⯨");
            } else {
                stars.append("☆");
            }
        }

        return stars.toString();
    }

    /**
     * Converts an integer rating to a star display.
     *
     * @param rating The integer rating (1-5)
     * @return Star display string
     */
    private String getStarDisplay(int rating) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i < rating) {
                stars.append("★");
            } else {
                stars.append("☆");
            }
        }
        return stars.toString();
    }

    /**
     * Creates a progress bar visualization.
     *
     * @param value The current value
     * @param total The total value
     * @return Progress bar string
     */
    private String getProgressBar(int value, int total) {
        if (total == 0) return "░░░░░░░░░░";

        int barLength = 10;
        int filled = (int) Math.round((double) value / total * barLength);

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }

        return bar.toString();
    }

    @Override
    public String getDescription() {
        return "View ratings and reviews for a shop";
    }

    @Override
    public String getUsage() {
        return "/shop reviews <shopId>";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.command.reviews";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Suggest shop IDs (would need ShopService integration)
            completions.add("<shopId>");
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly
    }
}
