package org.fourz.BarterShops.data.repository;

import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.ShopGroupDTO;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for shop group data access.
 * Follows the Repository pattern for data access abstraction.
 *
 * <p>All methods return CompletableFuture for async database operations.</p>
 * <p>Implementations should use FallbackTracker for graceful degradation.</p>
 */
public interface IShopGroupRepository {

    // ========================================================
    // Group CRUD Operations
    // ========================================================

    /**
     * Saves a new group or updates an existing one.
     *
     * @param group The group data to save
     * @return CompletableFuture containing the saved group with generated ID
     */
    CompletableFuture<ShopGroupDTO> save(ShopGroupDTO group);

    /**
     * Finds a group by its unique ID.
     *
     * @param groupId The group ID to find
     * @return CompletableFuture containing the group with co-owners, or empty if not found
     */
    CompletableFuture<Optional<ShopGroupDTO>> findById(int groupId);

    /**
     * Finds all groups owned by a player.
     *
     * @param ownerUuid The owner's UUID
     * @return CompletableFuture containing list of owned groups
     */
    CompletableFuture<List<ShopGroupDTO>> findByOwner(UUID ownerUuid);

    /**
     * Finds all groups where player is owner OR co-owner.
     *
     * @param memberUuid The player's UUID
     * @return CompletableFuture containing list of groups
     */
    CompletableFuture<List<ShopGroupDTO>> findByMember(UUID memberUuid);

    /**
     * Finds a group by owner and world.
     * Used for auto-grouping lookup.
     *
     * @param ownerUuid The owner's UUID
     * @param world The world name
     * @return CompletableFuture containing the group, or empty if not found
     */
    CompletableFuture<Optional<ShopGroupDTO>> findByOwnerAndWorld(UUID ownerUuid, String world);

    /**
     * Renames a group.
     *
     * @param groupId The group ID
     * @param newName The new group name
     * @return CompletableFuture with true if renamed
     */
    CompletableFuture<Boolean> rename(int groupId, String newName);

    /**
     * Deletes a group by ID (soft delete — sets is_active = false).
     *
     * @param groupId The group ID to delete
     * @return CompletableFuture with true if deleted
     */
    CompletableFuture<Boolean> deleteById(int groupId);

    // ========================================================
    // Co-Owner Operations
    // ========================================================

    /**
     * Adds a co-owner to a group.
     *
     * @param groupId The group ID
     * @param memberUuid The co-owner UUID
     * @return CompletableFuture with true if added
     */
    CompletableFuture<Boolean> addCoOwner(int groupId, UUID memberUuid);

    /**
     * Removes a co-owner from a group.
     *
     * @param groupId The group ID
     * @param memberUuid The co-owner UUID to remove
     * @return CompletableFuture with true if removed
     */
    CompletableFuture<Boolean> removeCoOwner(int groupId, UUID memberUuid);

    /**
     * Gets all co-owners of a group.
     *
     * @param groupId The group ID
     * @return CompletableFuture containing list of co-owner UUIDs
     */
    CompletableFuture<List<UUID>> getCoOwners(int groupId);

    // ========================================================
    // Shop-Group Assignment
    // ========================================================

    /**
     * Assigns a shop to a group.
     *
     * @param shopId The shop ID
     * @param groupId The group ID
     * @return CompletableFuture with true if assigned
     */
    CompletableFuture<Boolean> assignShopToGroup(int shopId, int groupId);

    /**
     * Removes a shop from its group (sets group_id = NULL).
     *
     * @param shopId The shop ID
     * @return CompletableFuture with true if removed
     */
    CompletableFuture<Boolean> removeShopFromGroup(int shopId);

    /**
     * Gets all shops in a group.
     *
     * @param groupId The group ID
     * @return CompletableFuture containing list of shops in the group
     */
    CompletableFuture<List<ShopDataDTO>> getShopsInGroup(int groupId);

    // ========================================================
    // Proximity Queries
    // ========================================================

    /**
     * Finds groups owned by a player that have shops near the given location.
     * Uses bounding box query on bs_shops joined with bs_shop_groups.
     *
     * @param ownerUuid The owner's UUID
     * @param world The world name
     * @param x Center X coordinate
     * @param z Center Z coordinate
     * @param radius The search radius in blocks
     * @return CompletableFuture containing list of nearby groups
     */
    CompletableFuture<List<ShopGroupDTO>> findGroupsNearby(
            UUID ownerUuid, String world, double x, double z, double radius);
}
