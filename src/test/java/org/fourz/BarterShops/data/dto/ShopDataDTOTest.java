package org.fourz.BarterShops.data.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShopDataDTO Java Record.
 * Tests immutability, validation, builder pattern, and JSON serialization.
 */
@DisplayName("ShopDataDTO Tests")
class ShopDataDTOTest {

    private UUID testOwner;
    private Timestamp testTimestamp;
    private Gson gson;

    @BeforeEach
    void setUp() {
        testOwner = UUID.randomUUID();
        testTimestamp = new Timestamp(System.currentTimeMillis());
        gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    @Nested
    @DisplayName("Record Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should create DTO with all fields")
        void shouldCreateWithAllFields() {
            ShopDataDTO dto = new ShopDataDTO(
                1, testOwner, "Test Shop", ShopDataDTO.ShopType.BARTER,
                "world", 100.0, 64.0, 200.0,
                "world", 101.0, 64.0, 200.0,
                true, testTimestamp, testTimestamp,
                Map.of("key", "value")
            );

            assertEquals(1, dto.shopId());
            assertEquals(testOwner, dto.ownerUuid());
            assertEquals("Test Shop", dto.shopName());
            assertEquals(ShopDataDTO.ShopType.BARTER, dto.shopType());
            assertEquals("world", dto.locationWorld());
            assertEquals(100.0, dto.locationX());
            assertEquals(64.0, dto.locationY());
            assertEquals(200.0, dto.locationZ());
            assertTrue(dto.isActive());
            assertEquals("value", dto.metadata().get("key"));
        }

        @Test
        @DisplayName("Should throw on null ownerUuid")
        void shouldThrowOnNullOwner() {
            assertThrows(NullPointerException.class, () -> 
                new ShopDataDTO(1, null, "Shop", ShopDataDTO.ShopType.BARTER,
                    "world", 0, 0, 0, "world", 0, 0, 0,
                    true, testTimestamp, testTimestamp, Map.of())
            );
        }

        @Test
        @DisplayName("Should default shopType to BARTER when null")
        void shouldDefaultShopType() {
            ShopDataDTO dto = new ShopDataDTO(
                1, testOwner, "Shop", null,
                "world", 0, 0, 0, "world", 0, 0, 0,
                true, testTimestamp, testTimestamp, Map.of()
            );

            assertEquals(ShopDataDTO.ShopType.BARTER, dto.shopType());
        }

        @Test
        @DisplayName("Should create defensive copy of metadata")
        void shouldDefensiveCopyMetadata() {
            Map<String, String> mutableMap = new java.util.HashMap<>();
            mutableMap.put("key", "value");

            ShopDataDTO dto = new ShopDataDTO(
                1, testOwner, "Shop", ShopDataDTO.ShopType.BARTER,
                "world", 0, 0, 0, "world", 0, 0, 0,
                true, testTimestamp, testTimestamp, mutableMap
            );

            // Modify original map
            mutableMap.put("newKey", "newValue");

            // DTO should not be affected
            assertFalse(dto.metadata().containsKey("newKey"));
            assertEquals(1, dto.metadata().size());
        }

        @Test
        @DisplayName("Should handle null metadata with empty map")
        void shouldHandleNullMetadata() {
            ShopDataDTO dto = new ShopDataDTO(
                1, testOwner, "Shop", ShopDataDTO.ShopType.BARTER,
                "world", 0, 0, 0, "world", 0, 0, 0,
                true, testTimestamp, testTimestamp, null
            );

            assertNotNull(dto.metadata());
            assertTrue(dto.metadata().isEmpty());
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("Should build DTO using builder")
        void shouldBuildDTO() {
            ShopDataDTO dto = ShopDataDTO.builder()
                .shopId(42)
                .ownerUuid(testOwner)
                .shopName("Builder Shop")
                .shopType(ShopDataDTO.ShopType.SELL)
                .signLocation("world", 10.0, 20.0, 30.0)
                .chestLocation("world", 11.0, 20.0, 30.0)
                .isActive(true)
                .createdAt(testTimestamp)
                .lastModified(testTimestamp)
                .metadata(Map.of("built", "true"))
                .build();

            assertEquals(42, dto.shopId());
            assertEquals("Builder Shop", dto.shopName());
            assertEquals(ShopDataDTO.ShopType.SELL, dto.shopType());
            assertEquals(10.0, dto.locationX());
            assertEquals(11.0, dto.chestLocationX());
        }

        @Test
        @DisplayName("Should use default shopType in builder")
        void shouldUseDefaultShopType() {
            ShopDataDTO dto = ShopDataDTO.builder()
                .shopId(1)
                .ownerUuid(testOwner)
                .shopName("Default Type Shop")
                .signLocation("world", 0, 0, 0)
                .chestLocation("world", 0, 0, 0)
                .build();

            assertEquals(ShopDataDTO.ShopType.BARTER, dto.shopType());
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Should serialize to JSON")
        void shouldSerializeToJson() {
            ShopDataDTO dto = ShopDataDTO.builder()
                .shopId(1)
                .ownerUuid(testOwner)
                .shopName("JSON Shop")
                .shopType(ShopDataDTO.ShopType.BUY)
                .signLocation("world", 100.5, 64.0, 200.5)
                .chestLocation("world", 101.5, 64.0, 200.5)
                .isActive(true)
                .createdAt(testTimestamp)
                .lastModified(testTimestamp)
                .metadata(Map.of("serialized", "true"))
                .build();

            String json = gson.toJson(dto);

            assertNotNull(json);
            assertTrue(json.contains("\"shopId\": 1"));
            assertTrue(json.contains("\"shopName\": \"JSON Shop\""));
            assertTrue(json.contains("\"shopType\": \"BUY\""));
            assertTrue(json.contains("\"serialized\": \"true\""));
        }

        @Test
        @DisplayName("Should deserialize from JSON")
        void shouldDeserializeFromJson() {
            ShopDataDTO original = ShopDataDTO.builder()
                .shopId(99)
                .ownerUuid(testOwner)
                .shopName("Deserialize Shop")
                .shopType(ShopDataDTO.ShopType.ADMIN)
                .signLocation("nether", 50.0, 32.0, 50.0)
                .chestLocation("nether", 51.0, 32.0, 50.0)
                .isActive(false)
                .createdAt(testTimestamp)
                .lastModified(testTimestamp)
                .metadata(Map.of())
                .build();

            String json = gson.toJson(original);
            ShopDataDTO restored = gson.fromJson(json, ShopDataDTO.class);

            assertEquals(original.shopId(), restored.shopId());
            assertEquals(original.ownerUuid(), restored.ownerUuid());
            assertEquals(original.shopName(), restored.shopName());
            assertEquals(original.shopType(), restored.shopType());
            assertEquals(original.locationWorld(), restored.locationWorld());
            assertEquals(original.locationX(), restored.locationX());
            assertEquals(original.isActive(), restored.isActive());
        }

        @Test
        @DisplayName("Should preserve metadata in serialization")
        void shouldPreserveMetadata() {
            Map<String, String> metadata = Map.of(
                "key1", "value1",
                "key2", "value2",
                "nested.key", "nested.value"
            );

            ShopDataDTO dto = ShopDataDTO.builder()
                .shopId(1)
                .ownerUuid(testOwner)
                .shopName("Metadata Shop")
                .signLocation("world", 0, 0, 0)
                .chestLocation("world", 0, 0, 0)
                .metadata(metadata)
                .build();

            String json = gson.toJson(dto);
            ShopDataDTO restored = gson.fromJson(json, ShopDataDTO.class);

            assertEquals(3, restored.metadata().size());
            assertEquals("value1", restored.metadata().get("key1"));
            assertEquals("nested.value", restored.metadata().get("nested.key"));
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityTests {

        @Test
        @DisplayName("Should return shopId as string")
        void shouldReturnShopIdString() {
            ShopDataDTO dto = ShopDataDTO.builder()
                .shopId(12345)
                .ownerUuid(testOwner)
                .shopName("Shop")
                .signLocation("world", 0, 0, 0)
                .chestLocation("world", 0, 0, 0)
                .build();

            assertEquals("12345", dto.getShopIdString());
        }

        @Test
        @DisplayName("Should return null location when world is null")
        void shouldReturnNullLocationWhenWorldNull() {
            ShopDataDTO dto = ShopDataDTO.builder()
                .shopId(1)
                .ownerUuid(testOwner)
                .shopName("No World Shop")
                .signLocation(null, 0, 0, 0)
                .chestLocation(null, 0, 0, 0)
                .build();

            // These will return null because we can't get Bukkit.getWorld in unit tests
            assertNull(dto.getSignLocation());
            assertNull(dto.getChestLocation());
        }
    }

    @Nested
    @DisplayName("ShopType Enum")
    class ShopTypeTests {

        @Test
        @DisplayName("Should have all expected shop types")
        void shouldHaveAllShopTypes() {
            ShopDataDTO.ShopType[] types = ShopDataDTO.ShopType.values();

            assertEquals(4, types.length);
            assertNotNull(ShopDataDTO.ShopType.valueOf("BARTER"));
            assertNotNull(ShopDataDTO.ShopType.valueOf("SELL"));
            assertNotNull(ShopDataDTO.ShopType.valueOf("BUY"));
            assertNotNull(ShopDataDTO.ShopType.valueOf("ADMIN"));
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            ShopDataDTO dto1 = new ShopDataDTO(
                1, testOwner, "Shop", ShopDataDTO.ShopType.BARTER,
                "world", 100.0, 64.0, 200.0,
                "world", 101.0, 64.0, 200.0,
                true, testTimestamp, testTimestamp, Map.of()
            );

            ShopDataDTO dto2 = new ShopDataDTO(
                1, testOwner, "Shop", ShopDataDTO.ShopType.BARTER,
                "world", 100.0, 64.0, 200.0,
                "world", 101.0, 64.0, 200.0,
                true, testTimestamp, testTimestamp, Map.of()
            );

            assertEquals(dto1, dto2);
            assertEquals(dto1.hashCode(), dto2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            ShopDataDTO dto1 = ShopDataDTO.builder()
                .shopId(1)
                .ownerUuid(testOwner)
                .shopName("Shop 1")
                .signLocation("world", 0, 0, 0)
                .chestLocation("world", 0, 0, 0)
                .build();

            ShopDataDTO dto2 = ShopDataDTO.builder()
                .shopId(2)
                .ownerUuid(testOwner)
                .shopName("Shop 2")
                .signLocation("world", 0, 0, 0)
                .chestLocation("world", 0, 0, 0)
                .build();

            assertNotEquals(dto1, dto2);
        }
    }
}
