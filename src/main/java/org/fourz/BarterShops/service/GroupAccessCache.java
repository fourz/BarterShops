package org.fourz.BarterShops.service;

import org.fourz.BarterShops.data.dto.ShopGroupDTO;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory map of shop group id to the players who may manage it (group owner + co-owners).
 *
 * <p>Sign clicks read this instead of querying the database: the old check blocked the main
 * thread for up to 2 s on every non-owner click on a grouped shop (#2118). Each entry is an
 * immutable set, so a reader sees either the old or the new member list, never a half-built one.
 * A full reload builds a new map and swaps the reference in one write.</p>
 */
public class GroupAccessCache {

    private volatile Map<Integer, Set<UUID>> managers = new ConcurrentHashMap<>();
    // Bumped by every single-group write, so a full reload that started before one can tell
    private final AtomicLong writes = new AtomicLong();

    /** True if the player owns or co-owns the group. Never touches the database. */
    public boolean canManage(int groupId, UUID playerUuid) {
        if (groupId <= 0 || playerUuid == null) return false;
        Set<UUID> members = managers.get(groupId);
        return members != null && members.contains(playerUuid);
    }

    /** Token to pass to {@link #replaceAll} - take it before starting the database load. */
    public long beginReload() {
        return writes.get();
    }

    /**
     * Replaces the whole cache with the given active groups, unless a single-group write landed
     * after {@code token} was taken: that snapshot may predate the write, so it is dropped and
     * the next periodic reload catches up.
     *
     * @return true if the cache was replaced
     */
    public synchronized boolean replaceAll(Collection<ShopGroupDTO> groups, long token) {
        if (writes.get() != token) {
            return false;
        }
        Map<Integer, Set<UUID>> fresh = new ConcurrentHashMap<>();
        for (ShopGroupDTO group : groups) {
            if (group.isActive()) {
                fresh.put(group.groupId(), membersOf(group));
            }
        }
        managers = fresh;
        return true;
    }

    /** Stores or replaces one group; an inactive group is removed. */
    public synchronized void put(ShopGroupDTO group) {
        if (!group.isActive()) {
            remove(group.groupId());
            return;
        }
        writes.incrementAndGet();
        managers.put(group.groupId(), membersOf(group));
    }

    public synchronized void remove(int groupId) {
        writes.incrementAndGet();
        managers.remove(groupId);
    }

    public int size() {
        return managers.size();
    }

    public synchronized void clear() {
        writes.incrementAndGet();
        managers = new ConcurrentHashMap<>();
    }

    private static Set<UUID> membersOf(ShopGroupDTO group) {
        Set<UUID> members = new HashSet<>(group.coOwners());
        members.add(group.ownerUuid());
        return Set.copyOf(members);
    }
}
