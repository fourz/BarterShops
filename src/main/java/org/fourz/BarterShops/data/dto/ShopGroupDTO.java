package org.fourz.BarterShops.data.dto;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Data Transfer Object for Shop Group data using Java Record.
 * Immutable and thread-safe for cross-plugin data transfer.
 *
 * <p>Represents a group of shops owned by the same player,
 * with optional co-owners for shared management.</p>
 */
public record ShopGroupDTO(
    int groupId,
    String groupName,
    UUID ownerUuid,
    String world,
    boolean isActive,
    Timestamp createdAt,
    Timestamp lastModified,
    List<UUID> coOwners
) {
    /**
     * Compact constructor with validation and defensive copies.
     */
    public ShopGroupDTO {
        Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");
        Objects.requireNonNull(groupName, "groupName cannot be null");

        // Defensive copy for mutable collection
        coOwners = coOwners == null ? List.of() : List.copyOf(coOwners);
    }

    /**
     * Checks if a player is a co-owner of this group.
     *
     * @param playerUuid The player UUID to check
     * @return true if the player is a co-owner
     */
    public boolean isCoOwner(UUID playerUuid) {
        return coOwners.contains(playerUuid);
    }

    /**
     * Checks if a player is the owner or a co-owner of this group.
     *
     * @param playerUuid The player UUID to check
     * @return true if the player is the owner or a co-owner
     */
    public boolean canManage(UUID playerUuid) {
        return ownerUuid.equals(playerUuid) || isCoOwner(playerUuid);
    }

    /**
     * Builder for constructing ShopGroupDTO with optional fields.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for ShopGroupDTO.
     */
    public static class Builder {
        private int groupId;
        private String groupName;
        private UUID ownerUuid;
        private String world;
        private boolean isActive = true;
        private Timestamp createdAt = new Timestamp(System.currentTimeMillis());
        private Timestamp lastModified = new Timestamp(System.currentTimeMillis());
        private List<UUID> coOwners = List.of();

        public Builder groupId(int groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder groupName(String groupName) {
            this.groupName = groupName;
            return this;
        }

        public Builder ownerUuid(UUID ownerUuid) {
            this.ownerUuid = ownerUuid;
            return this;
        }

        public Builder world(String world) {
            this.world = world;
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public Builder createdAt(Timestamp createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder lastModified(Timestamp lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder coOwners(List<UUID> coOwners) {
            this.coOwners = coOwners != null ? coOwners : List.of();
            return this;
        }

        public ShopGroupDTO build() {
            return new ShopGroupDTO(
                groupId, groupName, ownerUuid, world,
                isActive, createdAt, lastModified, coOwners
            );
        }
    }
}
