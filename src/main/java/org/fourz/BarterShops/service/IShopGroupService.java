package org.fourz.BarterShops.service;

import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.ShopGroupDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for shop group management.
 * Handles auto-grouping, co-ownership, and group lifecycle.
 */
public interface IShopGroupService {

    // ========================================================
    // Auto-Grouping
    // ========================================================

    /**
     * Auto-assigns a shop to a group based on proximity.
     * Creates a new group if no nearby group exists for the owner.
     *
     * @param ownerUuid The shop owner's UUID
     * @param world The world name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return CompletableFuture containing the assigned group, or empty if grouping disabled
     */
    CompletableFuture<Optional<ShopGroupDTO>> autoAssignGroup(
            UUID ownerUuid, String world, double x, double y, double z);

    /**
     * Migrates existing ungrouped shops into groups.
     * Should be called once at startup.
     */
    CompletableFuture<Void> migrateExistingShops();

    // ========================================================
    // Group Lifecycle
    // ========================================================

    /**
     * Creates a new group.
     *
     * @param ownerUuid The owner UUID
     * @param name The group name
     * @param world The world name
     * @return CompletableFuture containing the created group
     */
    CompletableFuture<ShopGroupDTO> createGroup(UUID ownerUuid, String name, String world);

    /**
     * Gets a group by ID.
     *
     * @param groupId The group ID
     * @return CompletableFuture containing the group, or empty
     */
    CompletableFuture<Optional<ShopGroupDTO>> getGroup(int groupId);

    /**
     * Gets all groups where the player is owner or co-owner.
     *
     * @param playerUuid The player UUID
     * @return CompletableFuture containing list of groups
     */
    CompletableFuture<List<ShopGroupDTO>> getPlayerGroups(UUID playerUuid);

    /**
     * Renames a group (owner only).
     *
     * @param groupId The group ID
     * @param requester The requesting player UUID
     * @param newName The new name
     * @return CompletableFuture with true if renamed
     */
    CompletableFuture<Boolean> renameGroup(int groupId, UUID requester, String newName);

    /**
     * Deletes a group (owner only). Ungroups all shops.
     *
     * @param groupId The group ID
     * @param requester The requesting player UUID
     * @return CompletableFuture with true if deleted
     */
    CompletableFuture<Boolean> deleteGroup(int groupId, UUID requester);

    // ========================================================
    // Shop Assignment
    // ========================================================

    /**
     * Adds a shop to a group (shop owner only).
     *
     * @param shopId The shop ID
     * @param groupId The group ID
     * @param requester The requesting player UUID
     * @return CompletableFuture with true if added
     */
    CompletableFuture<Boolean> addShopToGroup(int shopId, int groupId, UUID requester);

    /**
     * Removes a shop from its group (shop owner only).
     *
     * @param shopId The shop ID
     * @param requester The requesting player UUID
     * @return CompletableFuture with true if removed
     */
    CompletableFuture<Boolean> removeShopFromGroup(int shopId, UUID requester);

    /**
     * Gets all shops in a group.
     *
     * @param groupId The group ID
     * @return CompletableFuture containing list of shops
     */
    CompletableFuture<List<ShopDataDTO>> getGroupShops(int groupId);

    // ========================================================
    // Co-Ownership
    // ========================================================

    /**
     * Adds a co-owner to a group (group owner only).
     *
     * @param groupId The group ID
     * @param requester The requesting player UUID
     * @param coOwner The co-owner UUID to add
     * @return CompletableFuture with true if added
     */
    CompletableFuture<Boolean> addCoOwner(int groupId, UUID requester, UUID coOwner);

    /**
     * Removes a co-owner from a group (group owner only).
     *
     * @param groupId The group ID
     * @param requester The requesting player UUID
     * @param coOwner The co-owner UUID to remove
     * @return CompletableFuture with true if removed
     */
    CompletableFuture<Boolean> removeCoOwner(int groupId, UUID requester, UUID coOwner);

    /**
     * Transfers group ownership (group owner only).
     *
     * @param groupId The group ID
     * @param requester The current owner UUID
     * @param newOwner The new owner UUID
     * @return CompletableFuture with true if transferred
     */
    CompletableFuture<Boolean> transferGroupOwnership(int groupId, UUID requester, UUID newOwner);

    // ========================================================
    // Permission Checks
    // ========================================================

    /**
     * Checks if a player can manage a shop (is owner OR co-owner of shop's group).
     *
     * @param shopId The shop ID
     * @param playerUuid The player UUID
     * @return CompletableFuture with true if player can manage
     */
    CompletableFuture<Boolean> canManageShop(int shopId, UUID playerUuid);
}
