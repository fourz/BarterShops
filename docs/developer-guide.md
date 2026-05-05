# BarterShops Developer Guide

**Version**: 1.0-SNAPSHOT
**For**: Plugin Developers and Contributors
**Last Updated**: February 2026

This guide covers the internal architecture, API usage, and extension points of BarterShops for developers integrating with the plugin or contributing to its development.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [ServiceRegistry Integration](#serviceregistry-integration)
3. [Repository Pattern](#repository-pattern)
4. [DTO Layer](#dto-layer)
5. [Custom Shop Types](#custom-shop-types)
6. [Event System](#event-system)
7. [Database Layer](#database-layer)
8. [Development Setup](#development-setup)
9. [Contributing](#contributing)

---

## Architecture Overview

### Design Principles

BarterShops follows modern Java and Minecraft plugin development patterns:

- **Service-Oriented Architecture**: Core functionality exposed via service interfaces
- **Repository Pattern**: Data access abstracted behind repository interfaces
- **DTO Transfer Objects**: Immutable Java records for cross-plugin communication
- **Async Operations**: CompletableFuture for non-blocking database operations
- **Manager Lifecycle**: Consistent initialization and cleanup patterns

### Core Components

```
BarterShops Architecture
│
├── Main Plugin (BarterShops.java)
│   └── Lifecycle management, RVNKCore registration
│
├── Managers
│   ├── SignManager - Sign creation and interaction
│   ├── ShopManager - Shop lifecycle and sessions
│   ├── ContainerManager - Chest inventory management
│   ├── CommandManager - Command registration
│   └── ConfigManager - Configuration and messages
│
├── Services (RVNKCore Integration)
│   ├── IShopService - Shop operations API
│   ├── ITradeService - Trade operations API (future)
│   └── IShopDatabaseService - Database operations (future)
│
├── Repositories (Data Access)
│   ├── IShopRepository - Shop persistence
│   └── ITradeRepository - Trade history persistence
│
├── DTOs (Data Transfer Objects)
│   ├── ShopDataDTO - Shop data record
│   ├── TradeItemDTO - Trade item record
│   └── TradeRecordDTO - Trade history record
│
├── Trade Engine
│   ├── TradeEngine - Trade execution logic
│   ├── TradeValidator - Trade validation rules
│   ├── TradeSession - Active trade state
│   └── TradeConfirmationGUI - Player confirmation interface
│
└── Database Layer
    ├── DatabaseFactory - Database backend selection
    ├── DatabaseManager - Abstract database operations
    ├── MySQLDatabaseManager - MySQL implementation
    ├── SQLiteDatabaseManager - SQLite implementation
    └── FallbackTracker - Database failure detection
```

---

## ServiceRegistry Integration

BarterShops registers services with RVNKCore's ServiceRegistry for cross-plugin access.

### Accessing BarterShops from Your Plugin

#### 1. Add Dependency

**Maven (pom.xml)**:
```xml
<dependencies>
    <!-- BarterShops API -->
    <dependency>
        <groupId>org.fourz</groupId>
        <artifactId>bartershops</artifactId>
        <version>1.0-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>

    <!-- RVNKCore (for ServiceRegistry) -->
    <dependency>
        <groupId>org.fourz</groupId>
        <artifactId>rvnkcore</artifactId>
        <version>1.3.0-alpha</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**plugin.yml**:
```yaml
depend:
  - RVNKCore
  - BarterShops
```

#### 2. Retrieve IShopService

**Method 1: ServiceRegistry (Recommended)**
```java
import org.fourz.BarterShops.service.IShopService;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.service.ServiceRegistry;

public class MyPlugin extends JavaPlugin {
    private IShopService shopService;

    @Override
    public void onEnable() {
        RVNKCore core = RVNKCore.getInstance();
        ServiceRegistry registry = core.getServiceRegistry();

        shopService = registry.getService(IShopService.class)
            .orElseThrow(() -> new IllegalStateException("BarterShops service not available"));

        getLogger().info("Connected to BarterShops service");
    }
}
```

**Method 2: Direct Plugin Access (Fallback)**
```java
import org.bukkit.plugin.Plugin;
import org.fourz.BarterShops.BarterShops;

Plugin bartershopsPlugin = getServer().getPluginManager().getPlugin("BarterShops");
if (bartershopsPlugin instanceof BarterShops) {
    BarterShops bartershops = (BarterShops) bartershopsPlugin;
    // Access managers directly (less abstraction)
    ShopManager shopManager = bartershops.getShopManager();
}
```

### IShopService API

#### Shop Queries

```java
// Get shop by ID
CompletableFuture<Optional<ShopDataDTO>> getShopById(String shopId);

// Get shop at location
CompletableFuture<Optional<ShopDataDTO>> getShopAtLocation(Location location);

// Get shop by sign block
CompletableFuture<Optional<ShopDataDTO>> getShopBySign(Block signBlock);

// Get all shops owned by player
CompletableFuture<List<ShopDataDTO>> getShopsByOwner(UUID ownerUuid);

// Get all active shops
CompletableFuture<List<ShopDataDTO>> getAllShops();

// Get shops within radius
CompletableFuture<List<ShopDataDTO>> getShopsNearby(Location center, double radius);
```

#### Shop Management

```java
// Create shop
CompletableFuture<ShopDataDTO> createShop(
    UUID ownerUuid,
    Location location,
    String shopName
);

// Remove shop
CompletableFuture<Boolean> removeShop(String shopId);

// Update shop
CompletableFuture<Boolean> updateShop(
    String shopId,
    ShopUpdateRequest updates
);
```

#### Shop Statistics

```java
// Get total shop count
CompletableFuture<Integer> getShopCount();

// Get shop count by owner
CompletableFuture<Integer> getShopCountByOwner(UUID ownerUuid);

// Check if service is in fallback mode
boolean isInFallbackMode();
```

### Example: Finding Nearby Shops

```java
import org.bukkit.Location;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.data.dto.ShopDataDTO;

public void findNearbyShops(IShopService shopService, Location playerLoc) {
    shopService.getShopsNearby(playerLoc, 50.0)
        .thenAccept(shops -> {
            getLogger().info("Found " + shops.size() + " shops within 50 blocks");

            for (ShopDataDTO shop : shops) {
                getLogger().info("Shop #" + shop.shopId() + ": " + shop.shopName());
                getLogger().info("  Owner: " + shop.ownerUuid());
                getLogger().info("  Type: " + shop.shopType());
                getLogger().info("  Location: " + formatLocation(shop.getSignLocation()));
            }
        })
        .exceptionally(ex -> {
            getLogger().severe("Failed to query shops: " + ex.getMessage());
            return null;
        });
}

private String formatLocation(Location loc) {
    return String.format("%s (%d, %d, %d)",
        loc.getWorld().getName(),
        loc.getBlockX(),
        loc.getBlockY(),
        loc.getBlockZ());
}
```

### Example: Creating a Shop Programmatically

```java
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.data.dto.ShopDataDTO;

public void createShopForPlayer(IShopService shopService, Player player, Location signLoc) {
    shopService.createShop(player.getUniqueId(), signLoc, "Auto-Created Shop")
        .thenAccept(shop -> {
            player.sendMessage("Shop created! ID: " + shop.getShopIdString());
            getLogger().info("Created shop #" + shop.shopId() + " for " + player.getName());
        })
        .exceptionally(ex -> {
            player.sendMessage("Failed to create shop: " + ex.getMessage());
            getLogger().severe("Shop creation failed: " + ex.getMessage());
            return null;
        });
}
```

---

## Repository Pattern

BarterShops uses the Repository pattern for database abstraction.

### IShopRepository Interface

Located: `org.fourz.BarterShops.data.repository.IShopRepository`

**Purpose**: Abstract shop data persistence operations

**Key Methods**:
```java
public interface IShopRepository {
    // Create
    CompletableFuture<ShopDataDTO> createShop(ShopDataDTO shopData);

    // Read
    CompletableFuture<Optional<ShopDataDTO>> getShopById(int shopId);
    CompletableFuture<List<ShopDataDTO>> getShopsByOwner(UUID ownerUuid);
    CompletableFuture<List<ShopDataDTO>> getAllShops();

    // Update
    CompletableFuture<Boolean> updateShop(ShopDataDTO shopData);

    // Delete
    CompletableFuture<Boolean> deleteShop(int shopId);

    // Utility
    CompletableFuture<Integer> getShopCount();
    CompletableFuture<Integer> getShopCountByOwner(UUID ownerUuid);
}
```

### ITradeRepository Interface

Located: `org.fourz.BarterShops.data.repository.ITradeRepository`

**Purpose**: Trade history persistence

**Key Methods** (Future Implementation):
```java
public interface ITradeRepository {
    // Record trade
    CompletableFuture<TradeRecordDTO> recordTrade(TradeRecordDTO tradeRecord);

    // Query trades
    CompletableFuture<List<TradeRecordDTO>> getTradesByShop(int shopId);
    CompletableFuture<List<TradeRecordDTO>> getTradesByPlayer(UUID playerUuid);

    // Statistics
    CompletableFuture<Integer> getTotalTradeCount();
    CompletableFuture<Integer> getTradeCountByShop(int shopId);
}
```

### Implementation Notes

- **Async by Design**: All repository methods return `CompletableFuture<T>`
- **Thread Safety**: Repository implementations handle thread safety internally
- **Database Agnostic**: Repositories abstract MySQL vs SQLite differences

---

## DTO Layer

BarterShops uses Java Records for immutable data transfer objects.

### ShopDataDTO

Located: `org.fourz.BarterShops.data.dto.ShopDataDTO`

**Purpose**: Transfer shop data between services, plugins, and database

**Structure**:
```java
public record ShopDataDTO(
    int shopId,
    UUID ownerUuid,
    String shopName,
    ShopType shopType,
    String locationWorld,
    double locationX,
    double locationY,
    double locationZ,
    String chestLocationWorld,
    double chestLocationX,
    double chestLocationY,
    double chestLocationZ,
    boolean isActive,
    Timestamp createdAt,
    Timestamp lastModified,
    Map<String, String> metadata
) {
    // Enum: BARTER, SELL, BUY, ADMIN
    public enum ShopType { BARTER, SELL, BUY, ADMIN }

    // Helper methods
    public Location getSignLocation();
    public Location getChestLocation();
    public String getShopIdString();
}
```

**Builder Pattern**:
```java
ShopDataDTO shop = ShopDataDTO.builder()
    .ownerUuid(player.getUniqueId())
    .shopName("My Shop")
    .shopType(ShopDataDTO.ShopType.BARTER)
    .signLocation(signLoc)
    .chestLocation(chestLoc)
    .isActive(true)
    .metadata(Map.of("custom_field", "value"))
    .build();
```

### TradeItemDTO

Located: `org.fourz.BarterShops.data.dto.TradeItemDTO`

**Purpose**: Represent items in trade transactions

**Structure** (Future Implementation):
```java
public record TradeItemDTO(
    String itemType,           // Material name (e.g., "DIAMOND")
    int quantity,              // Stack size
    String displayName,        // Custom item name
    List<String> lore,         // Item lore lines
    Map<String, Object> enchantments, // Enchantment data
    Map<String, Object> nbt    // NBT data (serialized)
) {
    // Builder pattern
    public static Builder builder();

    // Serialization
    public ItemStack toItemStack();
    public static TradeItemDTO fromItemStack(ItemStack item);
}
```

### TradeRecordDTO

Located: `org.fourz.BarterShops.data.dto.TradeRecordDTO`

**Purpose**: Record completed trade transactions

**Structure** (Future Implementation):
```java
public record TradeRecordDTO(
    int tradeId,
    int shopId,
    UUID traderUuid,
    Timestamp tradeTimestamp,
    List<TradeItemDTO> inputItems,
    List<TradeItemDTO> outputItems,
    boolean success,
    String failureReason
) {
    public static Builder builder();
}
```

---

## Custom Shop Types

BarterShops supports extending shop types beyond the default BARTER type.

### Built-in Shop Types

| Type | Description | Status |
|------|-------------|--------|
| `BARTER` | Item-for-item exchange | Implemented |
| `SELL` | Player sells to shop for currency | Planned |
| `BUY` | Player buys from shop with currency | Planned |
| `ADMIN` | Server-managed unlimited stock | Planned |

### Creating Custom Shop Types

**Step 1: Extend ShopType Enum**

Modify `ShopDataDTO.ShopType`:
```java
public enum ShopType {
    BARTER,
    SELL,
    BUY,
    ADMIN,
    AUCTION,    // Custom type: Auction-based trades
    LOTTERY     // Custom type: Random trades
}
```

**Step 2: Implement Custom Shop Logic**

Create a custom shop handler:
```java
package org.fourz.BarterShops.shop.custom;

import org.fourz.BarterShops.shop.IShopHandler;
import org.fourz.BarterShops.data.dto.ShopDataDTO;

public class AuctionShopHandler implements IShopHandler {

    @Override
    public ShopDataDTO.ShopType getShopType() {
        return ShopDataDTO.ShopType.AUCTION;
    }

    @Override
    public boolean canPlayerTrade(Player player, ShopDataDTO shop) {
        // Custom validation logic
        return true;
    }

    @Override
    public void executeTrade(Player player, ShopDataDTO shop, TradeSession session) {
        // Custom trade logic
    }

    @Override
    public void updateShopSign(Block signBlock, ShopDataDTO shop) {
        // Custom sign display
    }
}
```

**Step 3: Register Custom Handler**

```java
ShopManager shopManager = bartershops.getShopManager();
shopManager.registerShopHandler(new AuctionShopHandler());
```

---

## Event System

BarterShops fires Bukkit events for shop lifecycle and trade operations.

### ShopCreatedEvent

**When**: A new shop is created

**Cancellable**: Yes

```java
import org.fourz.BarterShops.event.ShopCreatedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onShopCreated(ShopCreatedEvent event) {
        ShopDataDTO shop = event.getShop();
        Player owner = event.getOwner();
        Location location = event.getLocation();

        // Custom logic (e.g., log to database, notify admins)
        getLogger().info(owner.getName() + " created shop: " + shop.shopName());

        // Cancel shop creation if needed
        if (isBlacklistedLocation(location)) {
            event.setCancelled(true);
            owner.sendMessage("Cannot create shops in this area");
        }
    }
}
```

### ShopRemovedEvent

**When**: A shop is removed

**Cancellable**: No (post-event)

```java
import org.fourz.BarterShops.event.ShopRemovedEvent;

@EventHandler
public void onShopRemoved(ShopRemovedEvent event) {
    ShopDataDTO shop = event.getShop();
    Player remover = event.getRemover();

    getLogger().info("Shop #" + shop.shopId() + " removed by " + remover.getName());
}
```

### TradeExecutedEvent

**When**: A trade is completed

**Cancellable**: No (post-event)

```java
import org.fourz.BarterShops.event.TradeExecutedEvent;

@EventHandler
public void onTradeExecuted(TradeExecutedEvent event) {
    ShopDataDTO shop = event.getShop();
    Player trader = event.getTrader();
    List<TradeItemDTO> inputItems = event.getInputItems();
    List<TradeItemDTO> outputItems = event.getOutputItems();

    // Log trade to custom analytics
    getLogger().info(trader.getName() + " traded at shop #" + shop.shopId());
}
```

### TradeFailedEvent

**When**: A trade validation fails

**Cancellable**: No (informational)

```java
import org.fourz.BarterShops.event.TradeFailedEvent;

@EventHandler
public void onTradeFailed(TradeFailedEvent event) {
    Player trader = event.getTrader();
    String reason = event.getFailureReason();

    getLogger().info("Trade failed for " + trader.getName() + ": " + reason);
}
```

---

## Database Layer

### DatabaseFactory

**Purpose**: Select database backend (MySQL or SQLite)

```java
DatabaseManager dbManager = DatabaseFactory.createDatabase(
    config.getString("storage.type"),
    config
);
```

### DatabaseManager (Abstract)

**Purpose**: Define common database operations

**Key Methods**:
```java
public abstract class DatabaseManager {
    // Connection management
    public abstract void connect();
    public abstract void disconnect();
    public abstract boolean isConnected();

    // Shop operations
    public abstract CompletableFuture<Integer> createShop(ShopDataDTO shop);
    public abstract CompletableFuture<Optional<ShopDataDTO>> getShopById(int shopId);
    public abstract CompletableFuture<List<ShopDataDTO>> getAllShops();

    // Table management
    protected abstract void createTables();
    protected abstract void migrateSchema(int fromVersion, int toVersion);
}
```

### MySQLDatabaseManager

**Implementation**: HikariCP connection pooling

**Configuration**:
```yaml
storage:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    username: bartershops
    password: secure_password
    database: bartershops
    tablePrefix: bartershops_
```

### SQLiteDatabaseManager

**Implementation**: Single-file database

**Configuration**:
```yaml
storage:
  type: sqlite
  sqlite:
    database: data.db  # plugins/BarterShops/data.db
```

### FallbackTracker

**Purpose**: Detect persistent database failures and enable fallback mode

**Behavior**:
- Track consecutive database operation failures
- After threshold (e.g., 5 failures), enable fallback mode
- In fallback mode, use in-memory storage (data lost on restart)

---

## Development Setup

### Prerequisites

- Java 17+ JDK
- Maven 3.8+
- Git
- IDE (IntelliJ IDEA recommended)
- Test Minecraft server (Paper 1.20+)

### Clone Repository

```bash
git clone https://github.com/fourz/BarterShops.git
cd BarterShops
```

### Build Project

```bash
mvn clean package
```

**Output**: `target/BarterShops-1.0-SNAPSHOT.jar`

### IDE Setup (IntelliJ IDEA)

1. **Import Project**: File → Open → Select `BarterShops/pom.xml`
2. **Configure JDK**: Project Structure → Project SDK → Java 17+
3. **Enable Lombok**: Install Lombok plugin (if using Lombok annotations)
4. **Run Configurations**: Create run config for test server

### Testing

#### Unit Tests

```bash
mvn test
```

#### Integration Tests

```bash
mvn verify
```

#### Manual Testing

1. Build plugin: `mvn clean package`
2. Copy JAR to test server: `cp target/*.jar ~/testserver/plugins/`
3. Start server
4. Test commands in-game or console

---

## Contributing

### Code Style

Follow RVNK coding standards:
- Java 17+ features (records, pattern matching, etc.)
- 4-space indentation
- Javadoc for public APIs
- Descriptive variable names

### Pull Request Process

1. **Fork Repository**
2. **Create Feature Branch**: `git checkout -b feature/my-feature`
3. **Commit Changes**: `git commit -m "Add my feature"`
4. **Push Branch**: `git push origin feature/my-feature`
5. **Open Pull Request** on GitHub

### Commit Message Format

```
type(scope): subject

body (optional)

footer (optional)
```

**Types**: feat, fix, docs, refactor, test, chore

**Example**:
```
feat(trade): add auction shop type

Implement auction-based trading where players bid on items.

Closes #42
```

### Testing Requirements

- Unit tests for new features
- Integration tests for database operations
- Manual testing on Paper 1.20+

---

## API Versioning

BarterShops follows Semantic Versioning (SemVer):

- **Major** (X.0.0): Breaking API changes
- **Minor** (1.X.0): New features, backward compatible
- **Patch** (1.0.X): Bug fixes, backward compatible

**Current Version**: 1.0-SNAPSHOT (pre-release)

---

## Resources

- **Source Code**: [GitHub - fourz/BarterShops](https://github.com/fourz/BarterShops)
- **Issue Tracker**: GitHub Issues
- **RVNKCore Documentation**: [RVNKCore Wiki](https://github.com/fourz/RVNKCore/wiki)
- **Paper API Docs**: [PaperMC Javadocs](https://papermc.io/javadocs)

---

**For user documentation, see [USER_GUIDE.md](USER_GUIDE.md)**
**For administrator documentation, see [ADMIN_GUIDE.md](ADMIN_GUIDE.md)**
