# BarterShops

BarterShops is a modern, feature-rich trading plugin for Minecraft servers. Create sign-based shops for item-for-item barter trading or currency-based transactions with comprehensive economy integration.

## Features

### Core Trading
- **Chest-Based Trading**: Customers interact with chests to initiate and complete trades
- **Sign Interfaces**: Shop owners use signs to configure and manage shop transactions
- **Flexible Trade Options**: Supports item stacks, partial stacks, and non-stacking items
- **Multiple Shop Types**: BARTER (item-for-item), SELL (currency), BUY (currency), ADMIN shops
- **Security & Transaction Integrity**: Ensures fair trading mechanics and prevents duplication exploits

### Economy Integration (feat-02)
- **Vault Integration**: Full support for economy plugins via Vault
- **Listing Fees**: Configurable fees for creating shops
- **Trade Taxes**: Percentage-based taxation on currency trades
- **Mixed Trades**: Combine items and currency in single transactions
- Fee and tax calculation commands for transparency

### Region Protection (feat-05)
- **WorldGuard Integration**: Automatic region creation around shops
- **GriefPrevention Support**: Claim-based shop protection
- **Auto-Protection**: Configurable radius protection (default: 3 blocks)
- **Shop Limits**: Per-player shop limits (bypass with admin permission)
- Protection status commands for players

### Trade Ratings & Reviews (feat-06)
- **Star Ratings**: 1-5 star rating system for shops
- **Written Reviews**: Optional text reviews (200 character limit)
- **Average Ratings**: Calculated averages displayed on shops
- **Review History**: View all reviews for any shop
- Cannot rate your own shops (prevents abuse)

### Shop Analytics (feat-07)
- **Player Statistics**: Track shops owned, trades completed, items traded
- **Server Statistics**: Server-wide trading metrics and trends
- **Top Shops**: Leaderboards by trade volume
- **Most Traded Items**: Track popular items across the server
- **Performance Metrics**: Average trades per shop

### Shop Templates (feat-04)
- **Save Templates**: Capture shop configurations for reuse
- **Load Templates**: Quickly create shops from saved templates
- **Categorization**: Organize templates by category and tags
- **Server Presets**: Admin-created templates for all players
- **Template Sharing**: Share templates with permission control

### Web API (feat-03)
- **REST API**: HTTP endpoints for web integrations
- **Shop Listings**: Query shops with filters (owner, type, world)
- **Trade History**: Access recent trade activity
- **Statistics**: Server-wide and shop-specific analytics
- **CORS Support**: Cross-origin requests for web clients
- **API Authentication**: Optional API key requirement
- **Health Monitoring**: Health check endpoint

### Notifications
- **Multi-Channel**: Chat, ActionBar, Title, Sound notifications
- **Event Types**: Trade requests, completions, stock alerts, reviews
- **Customizable**: Per-channel and per-type settings
- **Low Stock Alerts**: Notify owners when inventory runs low

### Performance & Compatibility
- **Async Operations**: Non-blocking database operations with CompletableFuture
- **Database Support**: MySQL (production) and SQLite (development/small servers)
- **Database Fallback**: Automatic fallback detection for resilience
- **Connection Pooling**: HikariCP for efficient database connections
- **Minecraft Compatibility**: Supports Minecraft 1.16 - 1.21+

## Installation

1. Download the latest release from the [Releases](https://github.com/fourz/BarterShops/releases) page
2. Place `BarterShops.jar` into your server's `plugins/` folder
3. **Optional Dependencies** (place in `plugins/` before starting):
   - **RVNKCore** - Enhanced logging and cross-plugin integration
   - **Vault** - Economy integration for currency shops
   - **WorldGuard** or **GriefPrevention** - Shop region protection
4. Start the server to generate configuration files
5. Configure `plugins/BarterShops/config.yml` as needed
6. Reload with `/shop admin reload` or restart server

### Requirements

- **Server**: Spigot, Paper, or compatible fork (1.16+)
- **Java**: Java 17 or higher
- **Optional**: Vault, RVNKCore, WorldGuard, GriefPrevention

## Quick Start

### For Players

**Create a Shop:**
1. Place a chest where items will be stored
2. Attach a sign to or near the chest
3. Write `[barter]` on the first line of the sign
4. Follow on-screen instructions to configure your shop

**Find Shops:**
```
/shop nearby [radius]    # Find shops near you
/shop list               # List your shops
```

**Rate a Shop:**
```
/shop rate <shopId> <1-5> [review]
```

**View Your Stats:**
```
/shop stats
/shop stats server      # Server-wide statistics
```

### For Administrators

**Installation:**
```bash
# 1. Download plugin
# 2. Place in plugins/ folder
# 3. Start server
# 4. Configure config.yml
```

**Key Configuration:**
```yaml
economy:
  fees:
    enabled: true
    base: 100.0           # Barter shop fee
    currency_shop: 500.0  # Currency shop fee
  taxes:
    enabled: true
    rate: 0.05           # 5% tax rate

protection:
  enabled: true
  provider: auto         # auto-detect WorldGuard/GriefPrevention
  auto-protect-radius: 3
  max-shops-per-player: 5

api:
  enabled: false         # Enable REST API
  port: 8080
  authentication:
    required: false      # Require API keys
```

**Admin Commands:**
```
/shop admin reload               # Reload configuration
/shop admin inspect <shopId>     # Inspect any shop
/shop admin clear <shopId>       # Clear shop inventory
/shop admin gui                  # Open admin GUI
```

## Commands Reference

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/shop create <name>` | Create a shop | `bartershops.create` |
| `/shop list` | List your shops | `bartershops.list` |
| `/shop info <id>` | View shop details | Default |
| `/shop remove <id>` | Delete a shop | Default |
| `/shop nearby [radius]` | Find nearby shops | `bartershops.nearby` |
| `/shop rate <id> <1-5> [review]` | Rate a shop | Default |
| `/shop reviews <id>` | View shop reviews | Default |
| `/shop stats [player\|server]` | View statistics | `bartershops.stats` |
| `/shop template <action> [args]` | Manage templates | `bartershops.template` |
| `/shop notifications <channel> <on\|off>` | Manage notifications | `bartershops.notifications` |
| `/shop fee list` | View listing fees | `bartershops.economy.fee` |
| `/shop tax info\|calculate` | View/calculate taxes | `bartershops.economy.tax` |
| `/shop region status\|info` | View region protection | `bartershops.region.*` |

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/shop admin reload` | Reload configuration | `bartershops.admin.reload` |
| `/shop admin inspect <id>` | Inspect any shop | `bartershops.admin.inspect` |
| `/shop admin clear <id>` | Clear shop inventory | `bartershops.admin.clear` |
| `/shop admin gui` | Open admin GUI | `bartershops.admin.gui` |

**Aliases**: `/barter`, `/shops`

## Permissions

See [ADMIN_GUIDE.md](ADMIN_GUIDE.md#permissions-reference) for complete permissions reference.

## Documentation

- **[USER_GUIDE.md](USER_GUIDE.md)** - Complete player guide with examples
- **[ADMIN_GUIDE.md](ADMIN_GUIDE.md)** - Installation, configuration, permissions
- **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** - API usage, integration, architecture
- **[API_REFERENCE.md](API_REFERENCE.md)** - REST API endpoint documentation

## Development

### Building from Source

```bash
git clone https://github.com/fourz/BarterShops.git
cd BarterShops
mvn clean package
```

Output: `target/BarterShops-1.0-SNAPSHOT.jar`

### Project Structure

```
src/main/java/org/fourz/BarterShops/
├── command/              # Command system
│   ├── ShopCommand.java  # Main command dispatcher
│   └── sub/              # Subcommand implementations
├── service/              # Service layer (RVNKCore integration)
│   ├── IShopService.java
│   ├── ITradeService.java
│   ├── IRatingService.java
│   └── IStatsService.java
├── data/
│   ├── repository/       # Repository pattern (async data access)
│   └── dto/              # Data Transfer Objects (Java records)
├── shop/                 # Shop lifecycle management
├── sign/                 # Sign creation and interaction
├── trade/                # Trade execution engine
├── economy/              # Vault integration
├── protection/           # Region protection
├── template/             # Shop templates
├── api/                  # REST API (Jetty-based)
└── notification/         # Notification system
```

### Key Technologies

- **Java 17+**: Modern Java features (records, pattern matching)
- **Paper API**: Minecraft server API
- **HikariCP**: Database connection pooling
- **Jetty**: Embedded HTTP server for REST API
- **CompletableFuture**: Async operations
- **Maven**: Build system

### Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

**Development Guidelines:**
- Follow RVNK coding standards (see `docs/standard/coding-standards.md`)
- Use async patterns for database/I/O operations
- Add unit tests for new features
- Update documentation for user-facing changes
- Support console execution for commands

## API Integration

BarterShops registers services with RVNKCore's ServiceRegistry for cross-plugin access:

```java
// Get shop service
IShopService shopService = ServiceRegistry.getService(IShopService.class);

// Query shops asynchronously
shopService.getShopsByOwner(playerUuid)
    .thenAccept(shops -> {
        // Process shop data
    });
```

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for complete API documentation.

## REST API

Enable web integration with the built-in REST API:

```yaml
api:
  enabled: true
  port: 8080
  cors:
    enabled: true
    allowedOrigins:
      - "http://localhost:3000"
      - "https://your-domain.com"
  authentication:
    required: true
    keys:
      - "your-api-key-here"
```

**Available Endpoints:**
- `GET /api/shops` - List all shops
- `GET /api/shops/{id}` - Get shop details
- `GET /api/shops/nearby` - Find nearby shops
- `GET /api/trades/recent` - Recent trade activity
- `GET /api/stats` - Server statistics
- `GET /api/health` - Health check

See [API_REFERENCE.md](API_REFERENCE.md) for complete endpoint documentation.

## Compatibility

- **Minecraft Versions**: 1.16 - 1.21+
- **Server Software**: Spigot, Paper, Purpur, Pufferfish
- **Dependencies**:
  - Vault (optional, for economy)
  - RVNKCore (optional, for enhanced features)
  - WorldGuard / GriefPrevention (optional, for protection)
  - LuckPerms (optional, for advanced permissions)

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Credits

**Developed by**: Fourz (Derek Schrishuhn)
**Contributors**: See [CONTRIBUTORS.md](CONTRIBUTORS.md)
**Inspired by**: BarterSigns plugin

## Support

- **Issues**: [GitHub Issues](https://github.com/fourz/BarterShops/issues)
- **Wiki**: [GitHub Wiki](https://github.com/fourz/BarterShops/wiki)
- **Discord**: Join the Ravenkraft Network Discord for support

---

**Version**: 1.0-SNAPSHOT
**Last Updated**: February 1, 2026
