package org.fourz.BarterShops.data.repository.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.data.connection.PoolDelegate;
import org.fourz.BarterShops.data.dto.ShopDataDTO;
import org.fourz.BarterShops.data.dto.ShopGroupDTO;
import org.fourz.rvnkcore.config.dto.DatabaseSettingsDTO;
import org.fourz.rvnkcore.data.FallbackTracker;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shop configuration against a real SQLite schema (#2118): a config save must delete the config
 * keys the sign no longer carries, keep other metadata, and persist the shop type; the group
 * repository must load every active group with its co-owners for the co-owner cache.
 */
class ShopConfigPersistenceSqliteTest {

    @TempDir
    Path dir;

    private String url;
    private ShopRepositoryImpl shops;
    private ShopGroupRepositoryImpl groups;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        BarterShops plugin = mock(BarterShops.class);
        ConfigManager config = mock(ConfigManager.class);
        DatabaseSettingsDTO settings = mock(DatabaseSettingsDTO.class);
        when(plugin.getConfigManager()).thenReturn(config);
        when(config.getDatabaseSettings()).thenReturn(settings);
        when(settings.isMySQL()).thenReturn(false);
        when(settings.getTablePrefix()).thenReturn("bs_");

        url = "jdbc:sqlite:" + dir.resolve("shops.db");
        ConnectionProviderImpl provider = new ConnectionProviderImpl(plugin, mock(LogManager.class));
        provider.initializeWith(sqlitePool(url));

        FallbackTracker tracker = mock(FallbackTracker.class);
        shops = new ShopRepositoryImpl(provider, tracker, mock(LogManager.class));
        groups = new ShopGroupRepositoryImpl(provider, tracker, mock(LogManager.class));
    }

    @AfterEach
    void tearDown() {
        shops.shutdown();
        groups.shutdown();
    }

    @Test
    void configSaveDeletesAbsentConfigKeysAndKeepsOtherMetadata() throws Exception {
        int shopId = insertShop();
        Map<String, String> full = new HashMap<>();
        full.put("ownerName", "wizardofire");
        full.put("shop_config_offering", "{\"type\":\"DIAMOND\"}");
        full.put("shop_config_price_item", "{\"type\":\"EMERALD\"}");
        full.put("shop_config_price_amount", "4");
        full.put("shop_config_accepted_payments", "[{\"type\":\"EMERALD\"}]");
        full.put("shop_config_is_stackable", "true");
        shops.saveConfiguration(dto(shopId, ShopDataDTO.ShopType.BARTER, full)).get(5, TimeUnit.SECONDS);

        // Owner removed the last payment and cleared the price; only the offering remains
        Map<String, String> trimmed = new HashMap<>();
        trimmed.put("shop_config_offering", "{\"type\":\"DIAMOND\"}");
        trimmed.put("shop_config_is_stackable", "true");
        shops.saveConfiguration(dto(shopId, ShopDataDTO.ShopType.SELL, trimmed)).get(5, TimeUnit.SECONDS);

        ShopDataDTO reloaded = shops.findById(shopId).get(5, TimeUnit.SECONDS).orElseThrow();
        Map<String, String> meta = reloaded.metadata();
        assertFalse(meta.containsKey("shop_config_accepted_payments"), "removed payment came back");
        assertFalse(meta.containsKey("shop_config_price_item"), "cleared price came back");
        assertFalse(meta.containsKey("shop_config_price_amount"), "cleared price amount came back");
        assertEquals("{\"type\":\"DIAMOND\"}", meta.get("shop_config_offering"));
        assertEquals("wizardofire", meta.get("ownerName"), "non-config metadata must survive");
        assertEquals(ShopDataDTO.ShopType.SELL, reloaded.shopType(), "shop type not persisted");
    }

    @Test
    void configSaveLeavesGroupIdAlone() throws Exception {
        int shopId = insertShop();
        exec("UPDATE bs_shops SET group_id = 7 WHERE shop_id = " + shopId);

        shops.saveConfiguration(dto(shopId, ShopDataDTO.ShopType.BARTER, Map.of())).get(5, TimeUnit.SECONDS);

        ShopDataDTO reloaded = shops.findById(shopId).get(5, TimeUnit.SECONDS).orElseThrow();
        assertEquals(7, reloaded.groupId());
    }

    @Test
    void findAllActiveLoadsGroupsWithCoOwnersAndSkipsInactive() throws Exception {
        UUID coOwner = UUID.randomUUID();
        exec("INSERT INTO bs_shop_groups (group_id, group_name, owner_uuid, world, is_active) "
            + "VALUES (1, 'Market', '" + owner + "', 'world', 1)");
        exec("INSERT INTO bs_shop_groups (group_id, group_name, owner_uuid, world, is_active) "
            + "VALUES (2, 'Old', '" + owner + "', 'world', 0)");
        exec("INSERT INTO bs_shop_group_members (group_id, member_uuid) VALUES (1, '" + coOwner + "')");
        exec("INSERT INTO bs_shop_group_members (group_id, member_uuid) VALUES (2, '" + UUID.randomUUID() + "')");

        List<ShopGroupDTO> active = groups.findAllActive().get(5, TimeUnit.SECONDS);

        assertEquals(1, active.size());
        assertEquals(1, active.get(0).groupId());
        assertEquals(List.of(coOwner), active.get(0).coOwners());
    }

    private ShopDataDTO dto(int shopId, ShopDataDTO.ShopType type, Map<String, String> metadata) {
        return ShopDataDTO.builder()
            .shopId(shopId)
            .ownerUuid(owner)
            .shopName("Barter Shop")
            .shopType(type)
            .signLocation("world", 1, 64, 1)
            .chestLocation("world", 1, 63, 1)
            .isActive(true)
            .metadata(metadata)
            .build();
    }

    private int insertShop() throws SQLException {
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO bs_shops (owner_uuid, shop_name, shop_type, location_world, location_x, location_y, location_z) "
                     + "VALUES (?, 'Barter Shop', 'BARTER', 'world', 1, 64, 1)")) {
            stmt.setString(1, owner.toString());
            stmt.executeUpdate();
        }
        try (Connection conn = DriverManager.getConnection(url);
             ResultSet rs = conn.createStatement().executeQuery("SELECT max(shop_id) FROM bs_shops")) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private void exec(String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url)) {
            conn.createStatement().execute(sql);
        }
    }

    private static PoolDelegate sqlitePool(String url) {
        return new PoolDelegate() {
            @Override public void initialize() { }
            @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url); }
            @Override public void shutdown() { }
            @Override public String getDatabaseType() { return "sqlite"; }
        };
    }
}
