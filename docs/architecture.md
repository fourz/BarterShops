# BarterShops Architecture Specification

**Version**: 1.0.0
**Last Updated**: January 30, 2026
**Status**: Early Development (v0.1)
**Archon Board**: `bd4e478b-772a-4b97-bd99-300552840815`

## Overview

BarterShops is a Bukkit plugin for player-to-player item trading using chests and signs. This document describes the current architecture and planned RVNKCore integration patterns.

## Current Architecture (v0.1)

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     BarterShops Plugin                       │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ ConfigManager│  │CommandManager│  │  ShopManager │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│         │                │                   │              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ SignManager  │  │ContainerMgr  │  │ ShopSession  │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│         │                                   │               │
│  ┌──────────────┐                   ┌──────────────┐        │
│  │DatabaseFactory│─────────────────→│DatabaseManager│       │
│  └──────────────┘                   └──────────────┘        │
│                                            │                │
│                          ┌─────────────────┼────────────┐   │
│                          ▼                 ▼            │   │
│                   ┌──────────┐      ┌──────────┐        │   │
│                   │  MySQL   │      │  SQLite  │        │   │
│                   └──────────┘      └──────────┘        │   │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  RVNKCore    │
                    │ (LogManager) │
                    └──────────────┘
```

### Core Components

| Component | Package | Purpose |
|-----------|---------|---------|
| `BarterShops` | `org.fourz.BarterShops` | Main plugin class, lifecycle management |
| `ConfigManager` | `org.fourz.BarterShops.config` | Configuration loading, log level management |
| `CommandManager` | `org.fourz.BarterShops.command` | Command registration and routing |
| `ShopManager` | `org.fourz.BarterShops.shop` | Player session and shop state management |
| `SignManager` | `org.fourz.BarterShops.sign` | Sign creation, validation, interaction |
| `ContainerManager` | `org.fourz.BarterShops.container` | Chest/container management |
| `DatabaseFactory` | `org.fourz.BarterShops.data` | Database provider selection |
| `DatabaseManager` | `org.fourz.BarterShops.data` | Database operations interface |

### Current RVNKCore Usage

Currently uses:
- `org.fourz.rvnkcore.util.log.LogManager` - Centralized logging

**Not yet integrated:**
- ServiceRegistry (dependency injection)
- Repository pattern
- DTO layer with Java Records
- FallbackTracker (service resilience)

---

## Data Model

### Shop Entities

```java
// Current: POJO-style classes
// Target: Java Records with validation

// Shop entity (planned record)
public record Shop(
    UUID shopId,
    UUID ownerId,
    Location location,
    ShopType type,
    Instant createdAt,
    Map<String, Object> metadata
) {
    public Shop {
        Objects.requireNonNull(shopId);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(location);
    }
}

// Trade entity (planned record)
public record Trade(
    UUID tradeId,
    UUID shopId,
    UUID buyerId,
    List<ItemStack> offered,
    List<ItemStack> requested,
    TradeStatus status,
    Instant timestamp
) {}
```

### Database Schema (Planned)

```sql
-- shops table
CREATE TABLE bartershops_shops (
    shop_id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    shop_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata_json TEXT,
    INDEX idx_owner (owner_id),
    INDEX idx_location (world, x, y, z)
);

-- trades table
CREATE TABLE bartershops_trades (
    trade_id VARCHAR(36) PRIMARY KEY,
    shop_id VARCHAR(36) NOT NULL,
    buyer_id VARCHAR(36) NOT NULL,
    offered_items TEXT NOT NULL,
    requested_items TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (shop_id) REFERENCES bartershops_shops(shop_id)
);
```

---

## RVNKCore Integration Plan

### Phase 1: Interface Naming (impl-07)

Convert to I-prefix naming convention:

| Current | Target |
|---------|--------|
| `DatabaseManager` | `IDatabaseManager` |
| - | `IShopService` |
| - | `ITradeService` |
| - | `IShopRepository` |
| - | `ITradeRepository` |

### Phase 2: Service Layer (impl-08)

```java
// Service interface
public interface IShopService {
    CompletableFuture<Optional<Shop>> getShop(UUID shopId);
    CompletableFuture<List<Shop>> getPlayerShops(UUID ownerId);
    CompletableFuture<Shop> createShop(UUID ownerId, Location location, ShopType type);
    CompletableFuture<Void> deleteShop(UUID shopId);
    boolean isInFallbackMode();
}

// Service implementation with FallbackTracker
public class ShopServiceImpl implements IShopService {
    private final IShopRepository repository;
    private final FallbackTracker fallbackTracker;
    
    public ShopServiceImpl(IShopRepository repository) {
        this.repository = repository;
        this.fallbackTracker = new FallbackTracker(3, Duration.ofMinutes(5));
    }
    
    @Override
    public CompletableFuture<Optional<Shop>> getShop(UUID shopId) {
        if (fallbackTracker.isInFallbackMode()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return repository.findById(shopId)
            .whenComplete((result, error) -> {
                if (error != null) {
                    fallbackTracker.recordFailure();
                } else {
                    fallbackTracker.recordSuccess();
                }
            });
    }
    
    @Override
    public boolean isInFallbackMode() {
        return fallbackTracker.isInFallbackMode();
    }
}
```

### Phase 3: Repository Pattern (impl-07)

```java
// Repository interface
public interface IShopRepository {
    CompletableFuture<Optional<Shop>> findById(UUID shopId);
    CompletableFuture<List<Shop>> findByOwner(UUID ownerId);
    CompletableFuture<Optional<Shop>> findByLocation(Location location);
    CompletableFuture<Void> save(Shop shop);
    CompletableFuture<Boolean> delete(UUID shopId);
}

// Repository implementation
public class ShopRepositoryImpl implements IShopRepository {
    private final HikariDataSource dataSource;
    
    @Override
    public CompletableFuture<Optional<Shop>> findById(UUID shopId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM bartershops_shops WHERE shop_id = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, shopId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapToShop(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to find shop", e);
            }
            return Optional.empty();
        });
    }
}
```

### Phase 4: CommandManager Framework (impl-09)

```java
// RVNKCore CommandManager integration
public class BarterShops extends JavaPlugin {
    private ServiceRegistry serviceRegistry;
    
    @Override
    public void onEnable() {
        // Initialize ServiceRegistry
        this.serviceRegistry = new ServiceRegistry();
        
        // Register services
        IShopRepository shopRepo = new ShopRepositoryImpl(dataSource);
        IShopService shopService = new ShopServiceImpl(shopRepo);
        serviceRegistry.register(IShopService.class, shopService);
        
        // CommandManager integration
        CommandManager cmdManager = new CommandManager(this);
        cmdManager.register("shop", new ShopCommand(serviceRegistry));
    }
}
```

---

## Sign System Architecture

### Sign Types

| Type | First Line | Purpose |
|------|------------|---------|
| `BARTER` | `[barter]` | Standard barter shop |
| `SELL` | `[sell]` | Sell-only shop |
| `BUY` | `[buy]` | Buy-only shop |

### Sign Modes (ShopMode)

```java
public enum ShopMode {
    NONE,       // No active interaction
    SETUP,      // Configuring shop
    BOARD,      // Viewing shop board
    TYPE,       // Selecting trade type
    ITEM,       // Selecting items
    DELETE      // Deleting shop
}
```

### Sign Interaction Flow

```
Player clicks sign
       │
       ▼
┌──────────────┐
│ SignManager  │ → Validate sign format
└──────────────┘
       │
       ▼
┌──────────────┐
│ ShopManager  │ → Get/create session
└──────────────┘
       │
       ▼
┌──────────────┐
│ ShopSession  │ → Track mode, temp data
└──────────────┘
       │
       ▼
┌──────────────┐
│Send mode info│ → Player feedback
└──────────────┘
```

---

## Configuration

### config.yml Structure

```yaml
# Database configuration
database:
  type: sqlite  # mysql | sqlite
  mysql:
    host: localhost
    port: 3306
    database: bartershops
    username: user
    password: pass
  sqlite:
    file: plugins/BarterShops/data.db

# Logging
general:
  logLevel: INFO  # DEBUG | INFO | WARN | ERROR

# Shop settings
shops:
  maxPerPlayer: 10
  defaultType: BARTER
  expirationDays: 30  # 0 = never expire
```

---

## Integration Points

### Cross-Plugin Communication

| Plugin | Integration | Status |
|--------|-------------|--------|
| RVNKCore | LogManager, ServiceRegistry | Partial |
| TokenEconomy | Token-based payments | Planned |
| Vault/VaultUnlocked | Economy bridge | Planned |
| PlaceholderAPI | Shop statistics | Planned |

### Standalone Mode Fallback

BarterShops supports standalone operation when RVNKCore is unavailable or returns null during service registration. This ensures the plugin remains functional even when the core dependency fails.

**Trigger Conditions**:
- RVNKCore plugin not found at startup
- ServiceRegistry returns null on registration attempt
- RVNKCore service initialization timeout (>5s)

**Fallback Behavior**:
```
Normal Mode: BarterShops → RVNKCore ServiceRegistry → Shared services
Standalone:  BarterShops → Local fallback services → Limited functionality
```

**Startup Log Indicators**:
```
# Normal startup
[BarterShops] RVNKCore integration enabled
[BarterShops] Services registered: IShopService, ITradeService

# Standalone fallback
[WARN] RVNKCore registration returned null - running standalone
[BarterShops] Standalone mode: using local database only
```

**Capabilities in Standalone Mode**:

| Feature | Normal Mode | Standalone Mode |
|---------|-------------|-----------------|
| Local shop operations | ✅ Full | ✅ Full |
| Database (MySQL/SQLite) | ✅ Full | ✅ Full |
| Cross-plugin services | ✅ Available | ❌ Unavailable |
| Shared player data | ✅ Via RVNKCore | ❌ Local cache only |
| TokenEconomy integration | ✅ Full | ❌ Disabled |
| Centralized logging | ✅ LogManager | ⚠️ Local logger |

**Implementation Pattern**:
```java
@Override
public void onEnable() {
    // Attempt RVNKCore integration
    Plugin rvnkCore = getServer().getPluginManager().getPlugin("RVNKCore");
    
    if (rvnkCore != null && rvnkCore.isEnabled()) {
        try {
            ServiceRegistry registry = ((RVNKCorePlugin) rvnkCore).getServiceRegistry();
            if (registry != null) {
                initializeWithRVNKCore(registry);
                return;
            }
        } catch (Exception e) {
            getLogger().warning("RVNKCore integration failed: " + e.getMessage());
        }
    }
    
    // Fallback to standalone mode
    getLogger().warning("RVNKCore registration returned null - running standalone");
    initializeStandalone();
}

private void initializeStandalone() {
    // Use local implementations without ServiceRegistry
    this.shopService = new LocalShopService(dataSource);
    this.tradeService = new LocalTradeService(dataSource);
    standaloneMode = true;
}
```

**Recovery**: BarterShops does not automatically recover from standalone mode. A server restart is required after RVNKCore becomes available.

### Events (Planned)

```java
// Custom events for cross-plugin integration
public class ShopCreateEvent extends Event {
    private final UUID ownerId;
    private final Shop shop;
}

public class TradeCompleteEvent extends Event {
    private final Trade trade;
    private final Player buyer;
    private final Player seller;
}
```

---

## Testing Strategy

### Unit Tests (test-06)

- Repository CRUD operations
- Service layer business logic
- DTO validation (Records compact constructors)
- Sign parsing and validation

### Integration Tests (test-07)

- Full trade flow (create shop → trade → complete)
- Database connectivity (MySQL + SQLite)
- Cross-plugin service discovery
- Session management lifecycle

---

## Migration Path

### From v0.1 to v0.2 (RVNKCore Integration)

1. **Add RVNKCore dependency** (pom.xml, plugin.yml)
2. **Implement I-prefix interfaces** (impl-07)
3. **Convert to Java Records DTOs** (impl-08)
4. **Integrate CommandManager** (impl-09)
5. **Implement trade engine** (impl-10)
6. **Add unit tests** (test-06)
7. **Live server validation** (test-07)

---

## References

- [RVNKCore Integration Guide](../standard/rvnkcore-integration.md)
- [Shared Architecture Patterns](../architecture/shared-patterns.md)
- [DTO Patterns](../standard/dto-patterns.md)
- [Database Patterns](../standard/database-patterns.md)
- [BarterShops ROADMAP](../../repos/BarterShops/ROADMAP.md)

---

**Maintainer**: Ravenkraft Development Team
**Archon Task**: core-03 (BarterShops RVNKCore Integration Master)
