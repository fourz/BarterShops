package org.fourz.BarterShops.data.repository.impl;

import org.fourz.BarterShops.data.dto.ShopDataDTO;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/**
 * Shared ResultSet → ShopDataDTO mapper used by ShopRepositoryImpl and ShopGroupRepositoryImpl.
 */
class ShopRowMapper {

    private ShopRowMapper() {}

    static ShopDataDTO mapRowToShop(ResultSet rs, Map<String, String> metadata) throws SQLException {
        int groupIdRaw = rs.getInt("group_id");
        Integer groupId = rs.wasNull() ? null : groupIdRaw;

        return ShopDataDTO.builder()
                .shopId(rs.getInt("shop_id"))
                .ownerUuid(UUID.fromString(rs.getString("owner_uuid")))
                .shopName(rs.getString("shop_name"))
                .shopType(ShopDataDTO.ShopType.valueOf(rs.getString("shop_type")))
                .signLocation(
                        rs.getString("location_world"),
                        rs.getDouble("location_x"),
                        rs.getDouble("location_y"),
                        rs.getDouble("location_z"))
                .chestLocation(
                        rs.getString("chest_location_world"),
                        rs.getDouble("chest_location_x"),
                        rs.getDouble("chest_location_y"),
                        rs.getDouble("chest_location_z"))
                .isActive(rs.getBoolean("is_active"))
                .createdAt(rs.getTimestamp("created_at"))
                .lastModified(rs.getTimestamp("last_modified"))
                .metadata(metadata)
                .groupId(groupId)
                .build();
    }
}
