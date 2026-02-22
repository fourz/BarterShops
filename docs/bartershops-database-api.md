# BarterShops Database API Specification

**Version**: 1.0.0
**Last Updated**: January 30, 2026
**Status**: Planned (pre-implementation)

## Overview

This document specifies the database API for BarterShops plugin, following RVNKCore patterns for repository interfaces and async operations.

## Database Configuration

### Connection Settings

```yaml
database:
  type: sqlite  # mysql | sqlite
  mysql:
    host: localhost
    port: 3306
    database: bartershops
    username: user
    password: pass
    pool-size: 5
  sqlite:
    file: plugins/BarterShops/data.db
```

### HikariCP Configuration

```java
HikariConfig config = new HikariConfig();
config.setMaximumPoolSize(5);
config.setMinimumIdle(1);
config.setConnectionTimeout(30000);
config.setIdleTimeout(600000);
config.setMaxLifetime(1800000);
```

---

## Schema Definition

### shops Table

```sql
CREATE TABLE bartershops_shops (
    shop_id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    shop_type VARCHAR(32) NOT NULL DEFAULT 'BARTER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    metadata_json TEXT,
    
    INDEX idx_owner (owner_id),
    INDEX idx_location (world, x, y, z),
    INDEX idx_status (status)
);
```

### trades Table

```sql
CREATE TABLE bartershops_trades (
    trade_id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(36) NOT NULL,
    buyer_id VARCHAR(36) NOT NULL,
    seller_id VARCHAR(36) NOT NULL,
    offered_items TEXT NOT NULL,
    requested_items TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    
    FOREIGN KEY (shop_id) REFERENCES bartershops_shops(shop_id) ON DELETE CASCADE,
    INDEX idx_shop (shop_id),
    INDEX idx_buyer (buyer_id),
    INDEX idx_status (status)
);
```

### trade_items Table (Normalized)

```sql
CREATE TABLE bartershops_trade_items (
    item_id VARCHAR(36) PRIMARY KEY,
    trade_id VARCHAR(36) NOT NULL,
    item_type VARCHAR(64) NOT NULL,
    amount INT NOT NULL,
    item_data TEXT,
    is_offered BOOLEAN NOT NULL,
    
    FOREIGN KEY (trade_id) REFERENCES bartershops_trades(trade_id) ON DELETE CASCADE,
    INDEX idx_trade (trade_id)
);
```

---

## Repository Interfaces

### IShopRepository

```java
public interface IShopRepository {
    /**
     * Find shop by unique identifier.
     * @param shopId Shop UUID
     * @return Optional containing shop if found
     */
    CompletableFuture<Optional<ShopDTO>> findById(UUID shopId);
    
    /**
     * Find all shops owned by a player.
     * @param ownerId Player UUID
     * @return List of shops (may be empty)
     */
    CompletableFuture<List<ShopDTO>> findByOwner(UUID ownerId);
    
    /**
     * Find shop at specific location.
     * @param world World name
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Optional containing shop if found
     */
    CompletableFuture<Optional<ShopDTO>> findByLocation(String world, int x, int y, int z);
    
    /**
     * Find all active shops.
     * @param limit Maximum results
     * @param offset Pagination offset
     * @return List of active shops
     */
    CompletableFuture<List<ShopDTO>> findActive(int limit, int offset);
    
    /**
     * Save or update a shop.
     * @param shop Shop data to save
     * @return Saved shop with generated ID if new
     */
    CompletableFuture<ShopDTO> save(ShopDTO shop);
    
    /**
     * Delete a shop by ID.
     * @param shopId Shop UUID
     * @return true if deleted, false if not found
     */
    CompletableFuture<Boolean> delete(UUID shopId);
    
    /**
     * Count shops owned by player.
     * @param ownerId Player UUID
     * @return Number of shops owned
     */
    CompletableFuture<Integer> countByOwner(UUID ownerId);
}
```

### ITradeRepository

```java
public interface ITradeRepository {
    CompletableFuture<Optional<TradeDTO>> findById(UUID tradeId);
    CompletableFuture<List<TradeDTO>> findByShop(UUID shopId);
    CompletableFuture<List<TradeDTO>> findByBuyer(UUID buyerId);
    CompletableFuture<List<TradeDTO>> findPending(UUID shopId);
    CompletableFuture<TradeDTO> save(TradeDTO trade);
    CompletableFuture<Boolean> updateStatus(UUID tradeId, TradeStatus status);
    CompletableFuture<Boolean> delete(UUID tradeId);
}
```

---

## DTO Definitions

### ShopDTO

```java
public record ShopDTO(
    UUID shopId,
    UUID ownerId,
    String world,
    int x,
    int y,
    int z,
    ShopType shopType,
    ShopStatus status,
    Instant createdAt,
    Instant updatedAt,
    Map<String, Object> metadata
) {
    public ShopDTO {
        Objects.requireNonNull(shopId, "shopId cannot be null");
        Objects.requireNonNull(ownerId, "ownerId cannot be null");
        Objects.requireNonNull(world, "world cannot be null");
        Objects.requireNonNull(shopType, "shopType cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
    
    public static ShopDTO create(UUID ownerId, String world, int x, int y, int z, ShopType type) {
        return new ShopDTO(
            UUID.randomUUID(),
            ownerId,
            world, x, y, z,
            type,
            ShopStatus.ACTIVE,
            Instant.now(),
            Instant.now(),
            Map.of()
        );
    }
}
```

### TradeDTO

```java
public record TradeDTO(
    UUID tradeId,
    UUID shopId,
    UUID buyerId,
    UUID sellerId,
    List<TradeItem> offeredItems,
    List<TradeItem> requestedItems,
    TradeStatus status,
    Instant createdAt,
    Instant completedAt
) {
    public TradeDTO {
        Objects.requireNonNull(tradeId, "tradeId cannot be null");
        Objects.requireNonNull(shopId, "shopId cannot be null");
        Objects.requireNonNull(buyerId, "buyerId cannot be null");
        Objects.requireNonNull(sellerId, "sellerId cannot be null");
        offeredItems = offeredItems == null ? List.of() : List.copyOf(offeredItems);
        requestedItems = requestedItems == null ? List.of() : List.copyOf(requestedItems);
    }
}

public record TradeItem(
    String itemType,
    int amount,
    String itemData
) {
    public TradeItem {
        Objects.requireNonNull(itemType, "itemType cannot be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
```

### Enums

```java
public enum ShopType {
    BARTER,  // Two-way trade
    SELL,    // Seller only
    BUY      // Buyer only
}

public enum ShopStatus {
    ACTIVE,
    INACTIVE,
    DELETED
}

public enum TradeStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    COMPLETED,
    CANCELLED,
    EXPIRED
}
```

---

## Query Examples

### Find Nearby Shops

```java
public CompletableFuture<List<ShopDTO>> findNearby(String world, int x, int z, int radius) {
    return CompletableFuture.supplyAsync(() -> {
        String sql = """
            SELECT * FROM bartershops_shops 
            WHERE world = ? 
            AND status = 'ACTIVE'
            AND x BETWEEN ? AND ?
            AND z BETWEEN ? AND ?
            ORDER BY ((x - ?) * (x - ?) + (z - ?) * (z - ?))
            LIMIT 50
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            stmt.setInt(2, x - radius);
            stmt.setInt(3, x + radius);
            stmt.setInt(4, z - radius);
            stmt.setInt(5, z + radius);
            stmt.setInt(6, x);
            stmt.setInt(7, x);
            stmt.setInt(8, z);
            stmt.setInt(9, z);
            
            List<ShopDTO> results = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapToShopDTO(rs));
                }
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find nearby shops", e);
        }
    });
}
```

### Complete Trade Transaction

```java
public CompletableFuture<Boolean> completeTrade(UUID tradeId) {
    return CompletableFuture.supplyAsync(() -> {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            
            // Update trade status
            String updateSql = """
                UPDATE bartershops_trades 
                SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP
                WHERE trade_id = ? AND status = 'ACCEPTED'
                """;
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, tradeId.toString());
                int updated = stmt.executeUpdate();
                if (updated == 0) {
                    conn.rollback();
                    return false;
                }
            }
            
            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            throw new RuntimeException("Failed to complete trade", e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    });
}
```

---

## Error Handling

### Database Exceptions

```java
public class ShopDatabaseException extends RuntimeException {
    private final ErrorCode code;
    
    public enum ErrorCode {
        CONNECTION_FAILED,
        QUERY_FAILED,
        CONSTRAINT_VIOLATION,
        NOT_FOUND,
        DUPLICATE_ENTRY
    }
    
    public ShopDatabaseException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
```

### FallbackTracker Integration

```java
public class ShopRepositoryImpl implements IShopRepository {
    private final FallbackTracker fallbackTracker;
    
    public ShopRepositoryImpl(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.fallbackTracker = new FallbackTracker(3, Duration.ofMinutes(5));
    }
    
    @Override
    public CompletableFuture<Optional<ShopDTO>> findById(UUID shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        
        return doFindById(shopId)
            .whenComplete((result, error) -> {
                if (error != null) {
                    fallbackTracker.recordFailure();
                } else {
                    fallbackTracker.recordSuccess();
                }
            });
    }
}
```

---

## Migration Scripts

### v0.1 → v0.2 (Schema Creation)

```sql
-- Create initial schema
CREATE TABLE IF NOT EXISTS bartershops_shops (
    shop_id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    shop_type VARCHAR(32) NOT NULL DEFAULT 'BARTER',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata_json TEXT
);

CREATE INDEX IF NOT EXISTS idx_shops_owner ON bartershops_shops(owner_id);
CREATE INDEX IF NOT EXISTS idx_shops_location ON bartershops_shops(world, x, y, z);
```

---

## References

- [BarterShops Architecture](bartershops-architecture.md)
- [Database Patterns](../standard/database-patterns.md)
- [DTO Patterns](../standard/dto-patterns.md)
- [RVNKCore Integration](../standard/rvnkcore-integration.md)
