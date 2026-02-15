package org.fourz.BarterShops.service;

import org.bukkit.command.CommandSender;
import org.fourz.BarterShops.data.dto.OwnershipChangeResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for shop ownership management.
 * Handles ownership transfers with permission checks, cache synchronization,
 * session invalidation, and multi-player broadcast.
 * <p>
 * Follows RVNKCore integration pattern for optional ServiceRegistry registration.
 */
public interface IShopOwnershipService {

    /**
     * Transfer shop ownership to a new owner.
     * Orchestrates: permission check → database update → cache reload →
     * session invalidation → sign broadcast.
     *
     * @param shopId The shop ID to transfer
     * @param newOwner The new owner's UUID
     * @param initiator The command sender initiating the transfer (for permission checks)
     * @return CompletableFuture with transfer result
     */
    CompletableFuture<OwnershipChangeResult> transferOwnership(
        int shopId,
        UUID newOwner,
        CommandSender initiator
    );

    /**
     * Check if a sender has permission to change shop ownership.
     *
     * @param sender The command sender
     * @param shopId The shop ID (for owner-based permission logic)
     * @return CompletableFuture<Boolean> - true if permitted
     */
    CompletableFuture<Boolean> canChangeOwner(CommandSender sender, int shopId);

    /**
     * Get ownership history for a shop (optional feature).
     *
     * @param shopId The shop ID
     * @return CompletableFuture with list of previous owner UUIDs
     */
    CompletableFuture<List<UUID>> getOwnershipHistory(int shopId);
}
