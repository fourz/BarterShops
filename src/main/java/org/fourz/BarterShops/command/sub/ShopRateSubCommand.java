package org.fourz.BarterShops.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.command.SubCommand;
import org.fourz.BarterShops.data.dto.RatingDataDTO;
import org.fourz.BarterShops.service.IRatingService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand for rating a shop.
 * Usage: /shop rate <shopId> <1-5> [review]
 * Console-friendly: Yes (requires player UUID for console)
 */
public class ShopRateSubCommand implements SubCommand {

    private final BarterShops plugin;
    private final IRatingService ratingService;

    public ShopRateSubCommand(BarterShops plugin, IRatingService ratingService) {
        this.plugin = plugin;
        this.ratingService = ratingService;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "▶ " + getUsage());
            sender.sendMessage(ChatColor.GRAY + "   Example: /shop rate 123 5 Great shop!");
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

        // Parse rating
        int rating;
        try {
            rating = Integer.parseInt(args[1]);
            if (rating < 1 || rating > 5) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "✖ Rating must be between 1 and 5");
            return true;
        }

        // Parse optional review
        String review = null;
        if (args.length > 2) {
            StringBuilder reviewBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) reviewBuilder.append(" ");
                reviewBuilder.append(args[i]);
            }
            review = reviewBuilder.toString();

            // Limit review length
            if (review.length() > 200) {
                sender.sendMessage(ChatColor.RED + "✖ Review is too long (max 200 characters)");
                return true;
            }
        }

        // Get player UUID
        UUID playerUuid;
        String playerName;
        if (sender instanceof Player player) {
            playerUuid = player.getUniqueId();
            playerName = player.getName();
        } else {
            // Console mode - require player name as first arg after review
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "✖ Console usage: /shop rate <shopId> <rating> <playerName> [review]");
                return true;
            }
            Player targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null) {
                sender.sendMessage(ChatColor.RED + "✖ Player not found: " + args[2]);
                return true;
            }
            playerUuid = targetPlayer.getUniqueId();
            playerName = targetPlayer.getName();

            // Adjust review to skip player name
            if (args.length > 3) {
                StringBuilder reviewBuilder = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    if (i > 3) reviewBuilder.append(" ");
                    reviewBuilder.append(args[i]);
                }
                review = reviewBuilder.toString();
            }
        }

        // Check if player can rate this shop
        String finalReview = review;
        ratingService.canPlayerRate(shopId, playerUuid)
            .thenCompose(canRate -> {
                if (!canRate) {
                    sender.sendMessage(ChatColor.RED + "✖ You cannot rate your own shop");
                    return CompletableFuture.<RatingDataDTO>failedFuture(
                        new IllegalStateException("validation:cannot_rate_own_shop"));
                }

                sender.sendMessage(ChatColor.YELLOW + "⚙ Submitting rating...");
                return ratingService.rateShop(shopId, playerUuid, rating, finalReview);
            })
            .thenAccept(ratingDTO -> {
                sender.sendMessage(ChatColor.GREEN + "✓ Rating submitted for shop #" + shopId);
                sender.sendMessage(ChatColor.YELLOW + "  Rating: " + ChatColor.WHITE + getStarDisplay(rating));

                if (finalReview != null && !finalReview.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "  Review: " + ChatColor.WHITE + finalReview);
                }

                ratingService.getAverageRating(shopId).thenAccept(avg -> {
                    if (avg > 0) {
                        sender.sendMessage(ChatColor.GRAY + "  New average: " + String.format("%.1f", avg) + "/5.0");
                    }
                });
            })
            .exceptionally(error -> {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                if (!(cause instanceof IllegalStateException)) {
                    sender.sendMessage(ChatColor.RED + "✖ Failed to submit rating: " + cause.getMessage());
                }
                return null;
            });

        return true;
    }

    /**
     * Converts a numeric rating to a star display.
     *
     * @param rating The numeric rating (1-5)
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
        return stars.toString() + " (" + rating + "/5)";
    }

    @Override
    public String getDescription() {
        return "Rate a shop with a 1-5 star rating";
    }

    @Override
    public String getUsage() {
        return "/shop rate <shopId> <1-5> [review]";
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    @Override
    public String getPermission() {
        return "bartershops.command.rate";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Suggest shop IDs (would need ShopService integration)
            completions.add("<shopId>");
        } else if (args.length == 2) {
            // Suggest ratings
            for (int i = 1; i <= 5; i++) {
                completions.add(String.valueOf(i));
            }
        } else if (args.length == 3 && !(sender instanceof Player)) {
            // Console mode - suggest player names
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length >= 3) {
            completions.add("[review]");
        }

        return completions;
    }

    @Override
    public boolean requiresPlayer() {
        return false; // Console-friendly with player name parameter
    }
}
