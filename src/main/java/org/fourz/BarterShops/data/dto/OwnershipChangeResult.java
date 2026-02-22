package org.fourz.BarterShops.data.dto;

import java.util.UUID;

/**
 * Result of a shop ownership transfer operation.
 * Java record for immutability and compact syntax (Java 17+).
 */
public record OwnershipChangeResult(
    boolean success,
    UUID oldOwner,
    UUID newOwner,
    int sessionsInvalidated,
    String message
) {
    /**
     * Creates a successful ownership change result.
     *
     * @param oldOwner The previous owner UUID
     * @param newOwner The new owner UUID
     * @param sessions Number of sessions invalidated
     * @return OwnershipChangeResult with success status
     */
    public static OwnershipChangeResult success(UUID oldOwner, UUID newOwner, int sessions) {
        return new OwnershipChangeResult(
            true,
            oldOwner,
            newOwner,
            sessions,
            "Ownership transferred successfully"
        );
    }

    /**
     * Creates a failed ownership change result.
     *
     * @param reason The failure reason
     * @return OwnershipChangeResult with failure status
     */
    public static OwnershipChangeResult failure(String reason) {
        return new OwnershipChangeResult(false, null, null, 0, reason);
    }
}
