package org.fourz.BarterShops.data.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TradeRecordDTO Java Record.
 * Tests immutability, validation, factory methods, and JSON serialization.
 */
@DisplayName("TradeRecordDTO Tests")
class TradeRecordDTOTest {

    private UUID testBuyer;
    private UUID testSeller;
    private Timestamp testTimestamp;
    private Gson gson;

    @BeforeEach
    void setUp() {
        testBuyer = UUID.randomUUID();
        testSeller = UUID.randomUUID();
        testTimestamp = new Timestamp(System.currentTimeMillis());
        gson = new GsonBuilder().create();
    }

    @Nested
    @DisplayName("Record Construction")
    class ConstructionTests {

        @Test
        @DisplayName("Should create DTO with all fields")
        void shouldCreateWithAllFields() {
            String txId = UUID.randomUUID().toString();

            TradeRecordDTO dto = new TradeRecordDTO(
                txId, 1, testBuyer, testSeller,
                "DIAMOND:64", 64, "EMERALD", 10,
                TradeRecordDTO.TradeStatus.COMPLETED, testTimestamp
            );

            assertEquals(txId, dto.transactionId());
            assertEquals(1, dto.shopId());
            assertEquals(testBuyer, dto.buyerUuid());
            assertEquals(testSeller, dto.sellerUuid());
            assertEquals("DIAMOND:64", dto.itemStackData());
            assertEquals(64, dto.quantity());
            assertEquals("EMERALD", dto.currencyMaterial());
            assertEquals(10, dto.pricePaid());
            assertEquals(TradeRecordDTO.TradeStatus.COMPLETED, dto.status());
        }

        @Test
        @DisplayName("Should throw on null transactionId")
        void shouldThrowOnNullTransactionId() {
            assertThrows(NullPointerException.class, () ->
                new TradeRecordDTO(
                    null, 1, testBuyer, testSeller,
                    "DIAMOND:1", 1, "EMERALD", 1,
                    TradeRecordDTO.TradeStatus.COMPLETED, testTimestamp
                )
            );
        }

        @Test
        @DisplayName("Should throw on null buyerUuid")
        void shouldThrowOnNullBuyer() {
            assertThrows(NullPointerException.class, () ->
                new TradeRecordDTO(
                    UUID.randomUUID().toString(), 1, null, testSeller,
                    "DIAMOND:1", 1, "EMERALD", 1,
                    TradeRecordDTO.TradeStatus.COMPLETED, testTimestamp
                )
            );
        }

        @Test
        @DisplayName("Should throw on null sellerUuid")
        void shouldThrowOnNullSeller() {
            assertThrows(NullPointerException.class, () ->
                new TradeRecordDTO(
                    UUID.randomUUID().toString(), 1, testBuyer, null,
                    "DIAMOND:1", 1, "EMERALD", 1,
                    TradeRecordDTO.TradeStatus.COMPLETED, testTimestamp
                )
            );
        }

        @Test
        @DisplayName("Should throw on zero quantity")
        void shouldThrowOnZeroQuantity() {
            assertThrows(IllegalArgumentException.class, () ->
                new TradeRecordDTO(
                    UUID.randomUUID().toString(), 1, testBuyer, testSeller,
                    "DIAMOND:0", 0, "EMERALD", 1,
                    TradeRecordDTO.TradeStatus.COMPLETED, testTimestamp
                )
            );
        }

        @Test
        @DisplayName("Should throw on negative quantity")
        void shouldThrowOnNegativeQuantity() {
            assertThrows(IllegalArgumentException.class, () ->
                new TradeRecordDTO(
                    UUID.randomUUID().toString(), 1, testBuyer, testSeller,
                    "DIAMOND:-1", -1, "EMERALD", 1,
                    TradeRecordDTO.TradeStatus.COMPLETED, testTimestamp
                )
            );
        }

        @Test
        @DisplayName("Should default status to COMPLETED when null")
        void shouldDefaultStatus() {
            TradeRecordDTO dto = new TradeRecordDTO(
                UUID.randomUUID().toString(), 1, testBuyer, testSeller,
                "DIAMOND:1", 1, "EMERALD", 1,
                null, testTimestamp
            );

            assertEquals(TradeRecordDTO.TradeStatus.COMPLETED, dto.status());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryTests {

        @Test
        @DisplayName("Should create completed trade via factory")
        void shouldCreateCompletedTrade() {
            TradeRecordDTO dto = TradeRecordDTO.completed(
                42, testBuyer, testSeller,
                "GOLD_INGOT:32", 32, "DIAMOND", 5
            );

            assertNotNull(dto.transactionId());
            assertEquals(42, dto.shopId());
            assertEquals(testBuyer, dto.buyerUuid());
            assertEquals(testSeller, dto.sellerUuid());
            assertEquals("GOLD_INGOT:32", dto.itemStackData());
            assertEquals(32, dto.quantity());
            assertEquals("DIAMOND", dto.currencyMaterial());
            assertEquals(5, dto.pricePaid());
            assertEquals(TradeRecordDTO.TradeStatus.COMPLETED, dto.status());
            assertNotNull(dto.completedAt());
        }

        @Test
        @DisplayName("Should generate unique transaction IDs")
        void shouldGenerateUniqueIds() {
            TradeRecordDTO dto1 = TradeRecordDTO.completed(
                1, testBuyer, testSeller, "ITEM:1", 1, "EMERALD", 1
            );
            TradeRecordDTO dto2 = TradeRecordDTO.completed(
                1, testBuyer, testSeller, "ITEM:1", 1, "EMERALD", 1
            );

            assertNotEquals(dto1.transactionId(), dto2.transactionId());
        }
    }

    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("Should build DTO using builder")
        void shouldBuildDTO() {
            TradeRecordDTO dto = TradeRecordDTO.builder()
                .transactionId("tx-12345")
                .shopId(100)
                .buyerUuid(testBuyer)
                .sellerUuid(testSeller)
                .itemStackData("IRON_INGOT:16")
                .quantity(16)
                .currencyMaterial("GOLD_NUGGET")
                .pricePaid(48)
                .status(TradeRecordDTO.TradeStatus.PENDING)
                .completedAt(testTimestamp)
                .build();

            assertEquals("tx-12345", dto.transactionId());
            assertEquals(100, dto.shopId());
            assertEquals(16, dto.quantity());
            assertEquals(TradeRecordDTO.TradeStatus.PENDING, dto.status());
        }

        @Test
        @DisplayName("Should generate transactionId if not set")
        void shouldGenerateTransactionId() {
            TradeRecordDTO dto = TradeRecordDTO.builder()
                .shopId(1)
                .buyerUuid(testBuyer)
                .sellerUuid(testSeller)
                .itemStackData("ITEM:1")
                .quantity(1)
                .currencyMaterial("EMERALD")
                .pricePaid(1)
                .build();

            assertNotNull(dto.transactionId());
            assertFalse(dto.transactionId().isEmpty());
        }
    }

    @Nested
    @DisplayName("JSON Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Should serialize to JSON")
        void shouldSerializeToJson() {
            TradeRecordDTO dto = TradeRecordDTO.builder()
                .transactionId("tx-json-test")
                .shopId(55)
                .buyerUuid(testBuyer)
                .sellerUuid(testSeller)
                .itemStackData("NETHERITE_INGOT:4")
                .quantity(4)
                .currencyMaterial("DIAMOND_BLOCK")
                .pricePaid(64)
                .status(TradeRecordDTO.TradeStatus.COMPLETED)
                .completedAt(testTimestamp)
                .build();

            String json = gson.toJson(dto);

            assertNotNull(json);
            assertTrue(json.contains("\"transactionId\":\"tx-json-test\""));
            assertTrue(json.contains("\"shopId\":55"));
            assertTrue(json.contains("\"quantity\":4"));
            assertTrue(json.contains("\"status\":\"COMPLETED\""));
        }

        @Test
        @DisplayName("Should deserialize from JSON")
        void shouldDeserializeFromJson() {
            TradeRecordDTO original = TradeRecordDTO.builder()
                .transactionId("tx-deserialize")
                .shopId(77)
                .buyerUuid(testBuyer)
                .sellerUuid(testSeller)
                .itemStackData("EMERALD:10")
                .quantity(10)
                .currencyMaterial("DIAMOND")
                .pricePaid(5)
                .status(TradeRecordDTO.TradeStatus.CANCELLED)
                .completedAt(testTimestamp)
                .build();

            String json = gson.toJson(original);
            TradeRecordDTO restored = gson.fromJson(json, TradeRecordDTO.class);

            assertEquals(original.transactionId(), restored.transactionId());
            assertEquals(original.shopId(), restored.shopId());
            assertEquals(original.buyerUuid(), restored.buyerUuid());
            assertEquals(original.sellerUuid(), restored.sellerUuid());
            assertEquals(original.quantity(), restored.quantity());
            assertEquals(original.status(), restored.status());
        }

        @Test
        @DisplayName("Should round-trip all status types")
        void shouldRoundTripAllStatuses() {
            for (TradeRecordDTO.TradeStatus status : TradeRecordDTO.TradeStatus.values()) {
                TradeRecordDTO dto = TradeRecordDTO.builder()
                    .shopId(1)
                    .buyerUuid(testBuyer)
                    .sellerUuid(testSeller)
                    .itemStackData("ITEM:1")
                    .quantity(1)
                    .currencyMaterial("EMERALD")
                    .pricePaid(1)
                    .status(status)
                    .completedAt(testTimestamp)
                    .build();

                String json = gson.toJson(dto);
                TradeRecordDTO restored = gson.fromJson(json, TradeRecordDTO.class);

                assertEquals(status, restored.status(),
                    "Failed to round-trip status: " + status);
            }
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityTests {

        @Test
        @DisplayName("Should return timestamp as epoch millis")
        void shouldReturnTimestampAsEpoch() {
            long now = System.currentTimeMillis();
            Timestamp ts = new Timestamp(now);

            TradeRecordDTO dto = TradeRecordDTO.builder()
                .shopId(1)
                .buyerUuid(testBuyer)
                .sellerUuid(testSeller)
                .itemStackData("ITEM:1")
                .quantity(1)
                .currencyMaterial("EMERALD")
                .pricePaid(1)
                .completedAt(ts)
                .build();

            assertEquals(now, dto.getTimestamp());
        }

        @Test
        @DisplayName("Should return 0 for null completedAt")
        void shouldReturnZeroForNullTimestamp() {
            TradeRecordDTO dto = TradeRecordDTO.builder()
                .shopId(1)
                .buyerUuid(testBuyer)
                .sellerUuid(testSeller)
                .itemStackData("ITEM:1")
                .quantity(1)
                .currencyMaterial("EMERALD")
                .pricePaid(1)
                .completedAt(null)
                .build();

            assertEquals(0, dto.getTimestamp());
        }
    }

    @Nested
    @DisplayName("TradeStatus Enum")
    class TradeStatusTests {

        @Test
        @DisplayName("Should have all expected trade statuses")
        void shouldHaveAllStatuses() {
            TradeRecordDTO.TradeStatus[] statuses = TradeRecordDTO.TradeStatus.values();

            assertEquals(5, statuses.length);
            assertNotNull(TradeRecordDTO.TradeStatus.valueOf("COMPLETED"));
            assertNotNull(TradeRecordDTO.TradeStatus.valueOf("CANCELLED"));
            assertNotNull(TradeRecordDTO.TradeStatus.valueOf("FAILED"));
            assertNotNull(TradeRecordDTO.TradeStatus.valueOf("PENDING"));
            assertNotNull(TradeRecordDTO.TradeStatus.valueOf("REFUNDED"));
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class EqualityTests {

        @Test
        @DisplayName("Should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            String txId = "tx-equality-test";

            TradeRecordDTO dto1 = new TradeRecordDTO(
                txId, 1, testBuyer, testSeller,
                "DIAMOND:1", 1, "EMERALD", 1,
                TradeRecordDTO.TradeStatus.COMPLETED, testTimestamp
            );

            TradeRecordDTO dto2 = new TradeRecordDTO(
                txId, 1, testBuyer, testSeller,
                "DIAMOND:1", 1, "EMERALD", 1,
                TradeRecordDTO.TradeStatus.COMPLETED, testTimestamp
            );

            assertEquals(dto1, dto2);
            assertEquals(dto1.hashCode(), dto2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when transactionId differs")
        void shouldNotBeEqualWhenTxIdDiffers() {
            TradeRecordDTO dto1 = TradeRecordDTO.builder()
                .transactionId("tx-1")
                .shopId(1)
                .buyerUuid(testBuyer)
                .sellerUuid(testSeller)
                .itemStackData("ITEM:1")
                .quantity(1)
                .currencyMaterial("EMERALD")
                .pricePaid(1)
                .build();

            TradeRecordDTO dto2 = TradeRecordDTO.builder()
                .transactionId("tx-2")
                .shopId(1)
                .buyerUuid(testBuyer)
                .sellerUuid(testSeller)
                .itemStackData("ITEM:1")
                .quantity(1)
                .currencyMaterial("EMERALD")
                .pricePaid(1)
                .build();

            assertNotEquals(dto1, dto2);
        }
    }
}
