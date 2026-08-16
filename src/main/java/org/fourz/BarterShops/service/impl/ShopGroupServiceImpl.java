package org.fourz.BarterShops.service.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.ShopGroupDTO;
import org.fourz.BarterShops.data.repository.IShopGroupRepository;
import org.fourz.BarterShops.data.repository.IShopRepository;
import org.fourz.BarterShops.service.IShopGroupService;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of IShopGroupService.
 * Handles auto-grouping, co-ownership, and group lifecycle management.
 */
public class ShopGroupServiceImpl implements IShopGroupService {

    private final BarterShops plugin;
    private final IShopGroupRepository groupRepository;
    private final IShopRepository shopRepository;
    private final LogManager logger;

    // Per-owner lock to prevent race conditions during auto-grouping
    private final ConcurrentHashMap<UUID, Object> ownerLocks = new ConcurrentHashMap<>();

    public ShopGroupServiceImpl(BarterShops plugin, IShopGroupRepository groupRepository,
                                 IShopRepository shopRepository) {
        this.plugin = plugin;
        this.groupRepository = groupRepository;
        this.shopRepository = shopRepository;
        this.logger = LogManager.getInstance(plugin, "ShopGroupService");
    }

    // ========================================================
    // Auto-Grouping
    // ========================================================

    @Override
    public CompletableFuture<Optional<ShopGroupDTO>> autoAssignGroup(
            UUID ownerUuid, String world, double x, double y, double z) {

        if (!isGroupingEnabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        double radius = getAutoGroupRadius();

        // Per-owner lock to prevent race conditions (duplicate group creation)
        Object lock = ownerLocks.computeIfAbsent(ownerUuid, k -> new Object());

        return CompletableFuture.<Optional<ShopGroupDTO>>supplyAsync(() -> {
            synchronized (lock) {
                try {
                    // Step 1: Find existing groups nearby
                    List<ShopGroupDTO> nearbyGroups = groupRepository
                            .findGroupsNearby(ownerUuid, world, x, z, radius).join();

                    if (!nearbyGroups.isEmpty()) {
                        // Use the first (closest) group
                        return Optional.of(nearbyGroups.get(0));
                    }

                    // Step 2: No nearby group — check if owner has ANY group in this world
                    Optional<ShopGroupDTO> worldGroup = groupRepository
                            .findByOwnerAndWorld(ownerUuid, world).join();

                    if (worldGroup.isPresent() && radius == 0) {
                        // radius=0 means every shop gets own group — create new
                        return Optional.of(createNewGroup(ownerUuid, world));
                    }

                    if (worldGroup.isPresent()) {
                        // Has group in world but out of range — create new group
                        return Optional.of(createNewGroup(ownerUuid, world));
                    }

                    // Step 3: No group at all in this world — create first
                    return Optional.of(createNewGroup(ownerUuid, world));

                } catch (Exception e) {
                    logger.error("Auto-assign group failed for " + ownerUuid + ": " + e.getMessage());
                    return Optional.empty();
                } finally {
                    ownerLocks.remove(ownerUuid, lock);
                }
            }
        });
    }

    private ShopGroupDTO createNewGroup(UUID ownerUuid, String world) {
        String template = getDefaultNameTemplate();
        String playerName = plugin.getPlayerLookup().getPlayerName(ownerUuid);
        String groupName = template.replace("{player}", playerName);

        return groupRepository.save(ShopGroupDTO.builder()
                .groupName(groupName)
                .ownerUuid(ownerUuid)
                .world(world)
                .isActive(true)
                .build()
        ).join();
    }

    @Override
    public CompletableFuture<Void> migrateExistingShops() {
        if (!isMigrateExisting()) {
            logger.info("Shop group migration disabled in config");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                // Check if already migrated by looking for any existing groups
                List<ShopDataDTO> allShopsCheck = shopRepository.findAllActive().join();
                boolean anyGrouped = allShopsCheck.stream().anyMatch(s -> s.groupId() != null);
                if (anyGrouped) {
                    logger.debug("Shop groups already migrated - skipping");
                    return;
                }

                // Load all active shops without a group
                List<ShopDataDTO> allShops = shopRepository.findAllActive().join();
                List<ShopDataDTO> ungrouped = allShops.stream()
                        .filter(s -> s.groupId() == null)
                        .toList();

                if (ungrouped.isEmpty()) {
                    logger.info("No ungrouped shops to migrate");
                    return;
                }

                // Group by owner + world
                Map<String, List<ShopDataDTO>> clusters = new LinkedHashMap<>();
                for (ShopDataDTO shop : ungrouped) {
                    String key = shop.ownerUuid().toString() + ":" + shop.locationWorld();
                    clusters.computeIfAbsent(key, k -> new ArrayList<>()).add(shop);
                }

                int groupCount = 0;
                int shopCount = 0;
                double radius = getAutoGroupRadius();

                for (List<ShopDataDTO> clusterShops : clusters.values()) {
                    if (clusterShops.isEmpty()) continue;

                    ShopDataDTO first = clusterShops.get(0);
                    UUID ownerUuid = first.ownerUuid();
                    String world = first.locationWorld();

                    // Simple clustering: create one group per owner+world for migration
                    // More sophisticated proximity clustering can be added later
                    String playerName = plugin.getPlayerLookup().getPlayerName(ownerUuid);
                    String template = getDefaultNameTemplate();
                    String groupName = template.replace("{player}", playerName);

                    ShopGroupDTO group = groupRepository.save(ShopGroupDTO.builder()
                            .groupName(groupName)
                            .ownerUuid(ownerUuid)
                            .world(world)
                            .isActive(true)
                            .build()
                    ).join();

                    for (ShopDataDTO shop : clusterShops) {
                        groupRepository.assignShopToGroup(shop.shopId(), group.groupId()).join();
                        shopCount++;
                    }
                    groupCount++;
                }

                logger.info("Migrated " + shopCount + " shops into " + groupCount + " groups");

            } catch (Exception e) {
                logger.error("Shop group migration failed: " + e.getMessage());
            }
        });
    }

    // ========================================================
    // Group Lifecycle
    // ========================================================

    @Override
    public CompletableFuture<ShopGroupDTO> createGroup(UUID ownerUuid, String name, String world) {
        return groupRepository.save(ShopGroupDTO.builder()
                .groupName(name)
                .ownerUuid(ownerUuid)
                .world(world)
                .isActive(true)
                .build());
    }

    @Override
    public CompletableFuture<Optional<ShopGroupDTO>> getGroup(int groupId) {
        return groupRepository.findById(groupId);
    }

    @Override
    public CompletableFuture<List<ShopGroupDTO>> getPlayerGroups(UUID playerUuid) {
        return groupRepository.findByMember(playerUuid);
    }

    @Override
    public CompletableFuture<Boolean> renameGroup(int groupId, UUID requester, String newName) {
        return groupRepository.findById(groupId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            ShopGroupDTO group = opt.get();
            if (!group.ownerUuid().equals(requester)) return CompletableFuture.completedFuture(false);
            return groupRepository.rename(groupId, newName);
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteGroup(int groupId, UUID requester) {
        return groupRepository.findById(groupId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            ShopGroupDTO group = opt.get();
            if (!group.ownerUuid().equals(requester)) return CompletableFuture.completedFuture(false);
            return groupRepository.deleteById(groupId);
        });
    }

    // ========================================================
    // Shop Assignment
    // ========================================================

    @Override
    public CompletableFuture<Boolean> addShopToGroup(int shopId, int groupId, UUID requester) {
        return shopRepository.findById(shopId).thenCompose(shopOpt -> {
            if (shopOpt.isEmpty()) return CompletableFuture.completedFuture(false);
            ShopDataDTO shop = shopOpt.get();
            // Only the shop owner can assign their shop to a group
            if (!shop.ownerUuid().equals(requester)) return CompletableFuture.completedFuture(false);
            return groupRepository.assignShopToGroup(shopId, groupId);
        });
    }

    @Override
    public CompletableFuture<Boolean> removeShopFromGroup(int shopId, UUID requester) {
        return shopRepository.findById(shopId).thenCompose(shopOpt -> {
            if (shopOpt.isEmpty()) return CompletableFuture.completedFuture(false);
            ShopDataDTO shop = shopOpt.get();
            if (!shop.ownerUuid().equals(requester)) return CompletableFuture.completedFuture(false);
            return groupRepository.removeShopFromGroup(shopId);
        });
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> getGroupShops(int groupId) {
        return groupRepository.getShopsInGroup(groupId);
    }

    // ========================================================
    // Co-Ownership
    // ========================================================

    @Override
    public CompletableFuture<Boolean> addCoOwner(int groupId, UUID requester, UUID coOwner) {
        return groupRepository.findById(groupId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            ShopGroupDTO group = opt.get();
            if (!group.ownerUuid().equals(requester)) return CompletableFuture.completedFuture(false);

            // Check co-owner limit
            int maxCoOwners = getMaxCoOwners();
            if (group.coOwners().size() >= maxCoOwners) {
                return CompletableFuture.completedFuture(false);
            }

            // Cannot add self as co-owner
            if (requester.equals(coOwner)) return CompletableFuture.completedFuture(false);

            return groupRepository.addCoOwner(groupId, coOwner);
        });
    }

    @Override
    public CompletableFuture<Boolean> removeCoOwner(int groupId, UUID requester, UUID coOwner) {
        return groupRepository.findById(groupId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            ShopGroupDTO group = opt.get();
            if (!group.ownerUuid().equals(requester)) return CompletableFuture.completedFuture(false);
            return groupRepository.removeCoOwner(groupId, coOwner);
        });
    }

    @Override
    public CompletableFuture<Boolean> transferGroupOwnership(int groupId, UUID requester, UUID newOwner) {
        return groupRepository.findById(groupId).thenCompose(opt -> {
            if (opt.isEmpty()) return CompletableFuture.completedFuture(false);
            ShopGroupDTO group = opt.get();
            if (!group.ownerUuid().equals(requester)) return CompletableFuture.completedFuture(false);

            // Update group owner
            ShopGroupDTO updated = ShopGroupDTO.builder()
                    .groupId(groupId)
                    .groupName(group.groupName())
                    .ownerUuid(newOwner)
                    .world(group.world())
                    .isActive(group.isActive())
                    .createdAt(group.createdAt())
                    .coOwners(group.coOwners())
                    .build();

            return groupRepository.save(updated).thenApply(saved -> true);
        });
    }

    // ========================================================
    // Permission Checks
    // ========================================================

    @Override
    public CompletableFuture<Boolean> canManageShop(int shopId, UUID playerUuid) {
        return shopRepository.findById(shopId).thenCompose(shopOpt -> {
            if (shopOpt.isEmpty()) return CompletableFuture.completedFuture(false);
            ShopDataDTO shop = shopOpt.get();

            // Direct owner always can manage
            if (shop.ownerUuid().equals(playerUuid)) {
                return CompletableFuture.completedFuture(true);
            }

            // Check if shop is in a group where player is co-owner
            Integer groupId = shop.groupId();
            if (groupId == null) return CompletableFuture.completedFuture(false);

            return groupRepository.findById(groupId).thenApply(groupOpt -> {
                if (groupOpt.isEmpty()) return false;
                return groupOpt.get().canManage(playerUuid);
            });
        });
    }

    // ========================================================
    // Config Helpers
    // ========================================================

    private boolean isGroupingEnabled() {
        return plugin.getConfigManager().getBoolean("shop-groups.enabled", true);
    }

    private double getAutoGroupRadius() {
        return plugin.getConfigManager().getDouble("shop-groups.auto-group-radius", 150.0);
    }

    private String getDefaultNameTemplate() {
        return plugin.getConfigManager().getString("shop-groups.default-name-template", "{player}'s Shop");
    }

    private int getMaxCoOwners() {
        return plugin.getConfigManager().getInt("shop-groups.max-co-owners", 5);
    }

    private boolean isMigrateExisting() {
        return plugin.getConfigManager().getBoolean("shop-groups.migrate-existing", true);
    }
}
