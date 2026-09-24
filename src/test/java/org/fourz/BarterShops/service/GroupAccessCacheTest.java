package org.fourz.BarterShops.service;

import org.fourz.BarterShops.data.dto.ShopGroupDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Co-owner cache read by sign clicks instead of the database (#2118). */
class GroupAccessCacheTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID coOwner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    private ShopGroupDTO group(int id, boolean active, UUID... coOwners) {
        return ShopGroupDTO.builder()
            .groupId(id).groupName("g" + id).ownerUuid(owner).world("world")
            .isActive(active).coOwners(List.of(coOwners)).build();
    }

    @Test
    void ownerAndCoOwnerCanManageStrangerCannot() {
        GroupAccessCache cache = new GroupAccessCache();
        cache.put(group(1, true, coOwner));

        assertTrue(cache.canManage(1, owner));
        assertTrue(cache.canManage(1, coOwner));
        assertFalse(cache.canManage(1, stranger));
        assertFalse(cache.canManage(2, owner), "unknown group");
        assertFalse(cache.canManage(0, owner), "ungrouped shop");
    }

    @Test
    void refreshingAGroupAfterCoOwnerRemovalRevokesAccess() {
        GroupAccessCache cache = new GroupAccessCache();
        cache.put(group(1, true, coOwner));
        cache.put(group(1, true));

        assertFalse(cache.canManage(1, coOwner));
        assertTrue(cache.canManage(1, owner));
    }

    @Test
    void removeAndInactiveGroupRevokeAccess() {
        GroupAccessCache cache = new GroupAccessCache();
        cache.put(group(1, true, coOwner));
        cache.put(group(2, true, coOwner));

        cache.remove(1);
        cache.put(group(2, false, coOwner));

        assertFalse(cache.canManage(1, coOwner));
        assertFalse(cache.canManage(2, coOwner));
        assertEquals(0, cache.size());
    }

    @Test
    void replaceAllSwapsTheWholeMap() {
        GroupAccessCache cache = new GroupAccessCache();
        cache.put(group(1, true, coOwner));

        long token = cache.beginReload();
        assertTrue(cache.replaceAll(List.of(group(2, true, coOwner), group(3, false, coOwner)), token));

        assertFalse(cache.canManage(1, coOwner), "group missing from reload must drop out");
        assertTrue(cache.canManage(2, coOwner));
        assertFalse(cache.canManage(3, coOwner), "inactive group must not load");
    }

    @Test
    void reloadThatStartedBeforeASingleGroupWriteIsDropped() {
        GroupAccessCache cache = new GroupAccessCache();
        long token = cache.beginReload();

        // A co-owner is added while the full snapshot (taken without them) is loading
        cache.put(group(1, true, coOwner));

        assertFalse(cache.replaceAll(List.of(group(1, true)), token));
        assertTrue(cache.canManage(1, coOwner), "stale snapshot must not undo the newer write");
    }
}
