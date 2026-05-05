package org.fourz.BarterShops.data.repository.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.IConnectionProvider;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.ShopGroupDTO;
import org.fourz.BarterShops.data.repository.IShopGroupRepository;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of IShopGroupRepository for MySQL and SQLite databases.
 * All operations are async using CompletableFuture.
 * Uses FallbackTracker for graceful degradation.
 */
public class ShopGroupRepositoryImpl implements IShopGroupRepository {

    private final IConnectionProvider connectionProvider;
    private final FallbackTracker fallbackTracker;
    private final LogManager logger;
    private final ExecutorService executor;

    private String t(String baseName) {
        return connectionProvider.table(baseName);
    }

    public ShopGroupRepositoryImpl(BarterShops plugin, IConnectionProvider connectionProvider,
                                    FallbackTracker fallbackTracker) {
        this(connectionProvider, fallbackTracker, LogManager.getInstance(plugin, "ShopGroupRepository"));
    }

    public ShopGroupRepositoryImpl(IConnectionProvider connectionProvider,
                                    FallbackTracker fallbackTracker, LogManager logger) {
        this.connectionProvider = connectionProvider;
        this.fallbackTracker = fallbackTracker;
        this.logger = logger;
        this.executor = Executors.newFixedThreadPool(2);
    }

    // ========================================================
    // Group CRUD Operations
    // ========================================================

    @Override
    public CompletableFuture<ShopGroupDTO> save(ShopGroupDTO group) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(group);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql;
            boolean isInsert = group.groupId() <= 0;

            if (isInsert) {
                sql = "INSERT INTO " + t("shop_groups") + " (group_name, owner_uuid, world, is_active) " +
                    "VALUES (?, ?, ?, ?)";
            } else {
                sql = "UPDATE " + t("shop_groups") + " SET group_name = ?, owner_uuid = ?, world = ?, " +
                    "is_active = ?, last_modified = CURRENT_TIMESTAMP WHERE group_id = ?";
            }

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, group.groupName());
                stmt.setString(2, group.ownerUuid().toString());
                stmt.setString(3, group.world());
                stmt.setBoolean(4, group.isActive());

                if (!isInsert) {
                    stmt.setInt(5, group.groupId());
                }

                stmt.executeUpdate();
                fallbackTracker.recordSuccess();

                if (isInsert) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            int newId = keys.getInt(1);
                            return ShopGroupDTO.builder()
                                    .groupId(newId)
                                    .groupName(group.groupName())
                                    .ownerUuid(group.ownerUuid())
                                    .world(group.world())
                                    .isActive(group.isActive())
                                    .createdAt(group.createdAt())
                                    .lastModified(new Timestamp(System.currentTimeMillis()))
                                    .coOwners(group.coOwners())
                                    .build();
                        }
                    }
                }

                return group;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Save group failed: " + e.getMessage());
                logger.error("Failed to save group: " + e.getMessage());
                throw new RuntimeException("Failed to save group", e);
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<ShopGroupDTO>> findById(int groupId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shop_groups") + " WHERE group_id = ? AND is_active = 1";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, groupId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        List<UUID> coOwners = loadCoOwnersInternal(conn, groupId);
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapRowToGroup(rs, coOwners));
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find group by ID failed: " + e.getMessage());
                logger.error("Failed to find group by ID: " + e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<ShopGroupDTO>> findByOwner(UUID ownerUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shop_groups") +
                " WHERE owner_uuid = ? AND is_active = 1";
            List<ShopGroupDTO> groups = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, ownerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int gid = rs.getInt("group_id");
                        List<UUID> coOwners = loadCoOwnersInternal(conn, gid);
                        groups.add(mapRowToGroup(rs, coOwners));
                    }
                }

                fallbackTracker.recordSuccess();
                return groups;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find groups by owner failed: " + e.getMessage());
                logger.error("Failed to find groups by owner: " + e.getMessage());
                return groups;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<ShopGroupDTO>> findByMember(UUID memberUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            // Groups where player is owner OR co-owner
            String sql = "SELECT DISTINCT g.* FROM " + t("shop_groups") + " g " +
                "LEFT JOIN " + t("shop_group_members") + " m ON g.group_id = m.group_id " +
                "WHERE (g.owner_uuid = ? OR m.member_uuid = ?) AND g.is_active = 1";
            List<ShopGroupDTO> groups = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, memberUuid.toString());
                stmt.setString(2, memberUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int gid = rs.getInt("group_id");
                        List<UUID> coOwners = loadCoOwnersInternal(conn, gid);
                        groups.add(mapRowToGroup(rs, coOwners));
                    }
                }

                fallbackTracker.recordSuccess();
                return groups;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find groups by member failed: " + e.getMessage());
                logger.error("Failed to find groups by member: " + e.getMessage());
                return groups;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Optional<ShopGroupDTO>> findByOwnerAndWorld(UUID ownerUuid, String world) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shop_groups") +
                " WHERE owner_uuid = ? AND world = ? AND is_active = 1 LIMIT 1";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, ownerUuid.toString());
                stmt.setString(2, world);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int gid = rs.getInt("group_id");
                        List<UUID> coOwners = loadCoOwnersInternal(conn, gid);
                        fallbackTracker.recordSuccess();
                        return Optional.of(mapRowToGroup(rs, coOwners));
                    }
                }

                fallbackTracker.recordSuccess();
                return Optional.empty();

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find group by owner+world failed: " + e.getMessage());
                logger.error("Failed to find group by owner and world: " + e.getMessage());
                return Optional.empty();
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> rename(int groupId, String newName) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + t("shop_groups") +
                " SET group_name = ?, last_modified = CURRENT_TIMESTAMP WHERE group_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, newName);
                stmt.setInt(2, groupId);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Rename group failed: " + e.getMessage());
                logger.error("Failed to rename group: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> deleteById(int groupId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connectionProvider.getConnection()) {
                // Soft delete the group
                String sql = "UPDATE " + t("shop_groups") +
                    " SET is_active = 0, last_modified = CURRENT_TIMESTAMP WHERE group_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, groupId);
                    stmt.executeUpdate();
                }

                // Unassign all shops from this group
                String unassignSql = "UPDATE " + t("shops") +
                    " SET group_id = NULL WHERE group_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(unassignSql)) {
                    stmt.setInt(1, groupId);
                    stmt.executeUpdate();
                }

                fallbackTracker.recordSuccess();
                return true;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Delete group failed: " + e.getMessage());
                logger.error("Failed to delete group: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    // ========================================================
    // Co-Owner Operations
    // ========================================================

    @Override
    public CompletableFuture<Boolean> addCoOwner(int groupId, UUID memberUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = isMysql()
                ? "INSERT INTO " + t("shop_group_members") +
                    " (group_id, member_uuid, role, added_at) VALUES (?, ?, 'CO_OWNER', CURRENT_TIMESTAMP) " +
                    "ON DUPLICATE KEY UPDATE role = 'CO_OWNER'"
                : "INSERT OR REPLACE INTO " + t("shop_group_members") +
                    " (group_id, member_uuid, role, added_at) VALUES (?, ?, 'CO_OWNER', CURRENT_TIMESTAMP)";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, groupId);
                stmt.setString(2, memberUuid.toString());
                stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return true;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Add co-owner failed: " + e.getMessage());
                logger.error("Failed to add co-owner: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> removeCoOwner(int groupId, UUID memberUuid) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("shop_group_members") +
                " WHERE group_id = ? AND member_uuid = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, groupId);
                stmt.setString(2, memberUuid.toString());
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Remove co-owner failed: " + e.getMessage());
                logger.error("Failed to remove co-owner: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<UUID>> getCoOwners(int groupId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = connectionProvider.getConnection()) {
                fallbackTracker.recordSuccess();
                return loadCoOwnersInternal(conn, groupId);
            } catch (SQLException e) {
                fallbackTracker.recordFailure("Get co-owners failed: " + e.getMessage());
                logger.error("Failed to get co-owners: " + e.getMessage());
                return List.of();
            }
        }, executor);
    }

    // ========================================================
    // Shop-Group Assignment
    // ========================================================

    @Override
    public CompletableFuture<Boolean> assignShopToGroup(int shopId, int groupId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + t("shops") + " SET group_id = ? WHERE shop_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, groupId);
                stmt.setInt(2, shopId);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Assign shop to group failed: " + e.getMessage());
                logger.error("Failed to assign shop to group: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Boolean> removeShopFromGroup(int shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + t("shops") + " SET group_id = NULL WHERE shop_id = ?";

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, shopId);
                int affected = stmt.executeUpdate();
                fallbackTracker.recordSuccess();
                return affected > 0;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Remove shop from group failed: " + e.getMessage());
                logger.error("Failed to remove shop from group: " + e.getMessage());
                return false;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<ShopDataDTO>> getShopsInGroup(int groupId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("shops") + " WHERE group_id = ? AND is_active = 1";
            List<ShopDataDTO> shops = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, groupId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int shopId = rs.getInt("shop_id");
                        Map<String, String> metadata = loadShopMetadataInternal(conn, shopId);
                        shops.add(mapRowToShop(rs, metadata));
                    }
                }

                fallbackTracker.recordSuccess();
                return shops;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Get shops in group failed: " + e.getMessage());
                logger.error("Failed to get shops in group: " + e.getMessage());
                return shops;
            }
        }, executor);
    }

    // ========================================================
    // Proximity Queries
    // ========================================================

    @Override
    public CompletableFuture<List<ShopGroupDTO>> findGroupsNearby(
            UUID ownerUuid, String world, double x, double z, double radius) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            // Join groups with shops to find groups that have shops nearby
            String sql = "SELECT DISTINCT g.* FROM " + t("shop_groups") + " g " +
                "INNER JOIN " + t("shops") + " s ON s.group_id = g.group_id " +
                "WHERE g.owner_uuid = ? AND g.world = ? AND g.is_active = 1 " +
                "AND s.location_x BETWEEN ? AND ? " +
                "AND s.location_z BETWEEN ? AND ? " +
                "AND s.is_active = 1";
            List<ShopGroupDTO> groups = new ArrayList<>();

            try (Connection conn = connectionProvider.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, ownerUuid.toString());
                stmt.setString(2, world);
                stmt.setDouble(3, x - radius);
                stmt.setDouble(4, x + radius);
                stmt.setDouble(5, z - radius);
                stmt.setDouble(6, z + radius);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int gid = rs.getInt("group_id");
                        List<UUID> coOwners = loadCoOwnersInternal(conn, gid);
                        groups.add(mapRowToGroup(rs, coOwners));
                    }
                }

                fallbackTracker.recordSuccess();
                return groups;

            } catch (SQLException e) {
                fallbackTracker.recordFailure("Find groups nearby failed: " + e.getMessage());
                logger.error("Failed to find groups nearby: " + e.getMessage());
                return groups;
            }
        }, executor);
    }

    // ========================================================
    // Private Helper Methods
    // ========================================================

    private boolean isMysql() {
        String type = connectionProvider.getDatabaseType();
        return type != null && (type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb"));
    }

    private ShopGroupDTO mapRowToGroup(ResultSet rs, List<UUID> coOwners) throws SQLException {
        return ShopGroupDTO.builder()
                .groupId(rs.getInt("group_id"))
                .groupName(rs.getString("group_name"))
                .ownerUuid(UUID.fromString(rs.getString("owner_uuid")))
                .world(rs.getString("world"))
                .isActive(rs.getBoolean("is_active"))
                .createdAt(rs.getTimestamp("created_at"))
                .lastModified(rs.getTimestamp("last_modified"))
                .coOwners(coOwners)
                .build();
    }

    private List<UUID> loadCoOwnersInternal(Connection conn, int groupId) throws SQLException {
        String sql = "SELECT member_uuid FROM " + t("shop_group_members") + " WHERE group_id = ?";
        List<UUID> coOwners = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, groupId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    coOwners.add(UUID.fromString(rs.getString("member_uuid")));
                }
            }
        }

        return coOwners;
    }

    private ShopDataDTO mapRowToShop(ResultSet rs, Map<String, String> metadata) throws SQLException {
        return ShopRowMapper.mapRowToShop(rs, metadata);
    }

    private Map<String, String> loadShopMetadataInternal(Connection conn, int shopId) throws SQLException {
        String sql = "SELECT meta_key, meta_value FROM " + t("shop_metadata") + " WHERE shop_id = ?";
        Map<String, String> metadata = new HashMap<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, shopId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    metadata.put(rs.getString("meta_key"), rs.getString("meta_value"));
                }
            }
        }

        return metadata;
    }

    /**
     * Shuts down the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
