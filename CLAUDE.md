# BarterShops: AI Assistant Instructions

@import ../../.claude/rules/archon-workflow.md
@import ../../.claude/rules/java-plugin-build.md

---

## Project Overview

**BarterShops** is a player-to-player trading plugin for Bukkit/Spigot/Paper servers. It provides sign-based shop creation, chest-backed inventory management, and secure barter-style trading (item-for-item exchanges). The plugin uses modern Bukkit API patterns, supports database persistence (SQLite/MySQL), and integrates with RVNKCore's ServiceRegistry for cross-plugin access.

## Build Commands

```bash
# Build plugin JAR
mvn clean package

# Build without tests (faster)
mvn clean package -DskipTests

# Validate POM and dependencies
mvn validate

# Check dependency tree
mvn dependency:tree
```

**Output**: `target/BarterShops.jar` (versionless — `<finalName>` set in pom.xml)

## Remote Testing Workflow

Use `/rvnkdev-deploy` and `/rvnkdev-query` skills for remote server testing:

```bash
# Full deployment cycle (build locally first)
mvn clean package
/rvnkdev-deploy <server_id> full

# Query console for errors
/rvnkdev-query <server_id> errors

# Check plugin startup logs
/rvnkdev-query <server_id> plugin BarterShops

# Quick config iteration (no restart)
/rvnkdev-deploy <server_id> reload-only
```

Use `mcp__ravencast-mcp__find_servers` to look up current server IDs.

## Architecture

### Core Class Structure

```
org.fourz.BarterShops
├── BarterShops.java           # Main plugin class, lifecycle management
├── ManagerCore.java           # Core manager registration
├── command/
│   ├── CommandManager.java    # Command registration
│   ├── ShopCommand.java       # Main /shop command dispatcher (20 subcommands)
│   ├── SubCommand.java        # Abstract subcommand interface
│   ├── SeedSubCommand.java    # Test data seeding
│   └── sub/                   # All subcommand implementations
│       ├── ShopCreateSubCommand.java
│       ├── ShopListSubCommand.java
│       ├── ShopInfoSubCommand.java
│       ├── ShopRemoveSubCommand.java
│       ├── ShopNearbySubCommand.java
│       ├── ShopAdminSubCommand.java
│       ├── ShopAdminGUISubCommand.java
│       ├── ShopTemplateSubCommand.java   # feat-04
│       ├── ShopNotificationsSubCommand.java
│       ├── ShopRateSubCommand.java       # feat-06 (conditional: IRatingService)
│       ├── ShopReviewsSubCommand.java    # feat-06 (conditional: IRatingService)
│       ├── ShopStatsSubCommand.java      # feat-07 (conditional: IStatsService)
│       ├── ShopRegionSubCommand.java     # feat-05
│       ├── ShopFeeSubCommand.java        # feat-02 (conditional: EconomyManager)
│       ├── ShopTaxSubCommand.java        # feat-02 (conditional: EconomyManager)
│       ├── ShopInspectSubCommand.java
│       ├── ShopClearSubCommand.java
│       ├── ShopReloadSubCommand.java
│       ├── ShopDebugSubCommand.java      # feat-01
│       └── ShopTradeSubCommand.java      # Admin force-trade (console-capable)
├── config/
│   └── ConfigManager.java     # Configuration (config.yml, messages)
├── data/
│   ├── DatabaseFactory.java   # Database backend factory (SQLite/MySQL)
│   ├── DatabaseManager.java   # Database abstraction layer
│   ├── MySQLDatabaseManager.java
│   ├── SQLiteDatabaseManager.java
│   ├── IConnectionProvider.java
│   ├── FallbackTracker.java   # Database fallback detection
│   ├── RetentionManager.java  # 30-day trade archive scheduler (Bukkit async task)
│   ├── ShopsTestDataGenerator.java  # Test data generation
│   ├── dto/                   # Data Transfer Objects (Java records)
│   │   ├── ShopDataDTO.java
│   │   ├── TradeItemDTO.java
│   │   ├── TradeRecordDTO.java
│   │   ├── RatingDataDTO.java     # feat-06
│   │   └── StatsDataDTO.java      # feat-07
│   └── repository/            # Repository pattern for data access
│       ├── IShopRepository.java
│       ├── ITradeRepository.java
│       ├── IRatingRepository.java  # feat-06
│       └── impl/
│           ├── ConnectionProviderImpl.java
│           ├── ShopRepositoryImpl.java
│           ├── TradeRepositoryImpl.java
│           └── RatingRepositoryImpl.java  # feat-06
├── service/
│   ├── IShopService.java          # Service interface (RVNKCore integration)
│   ├── ITradeService.java         # Trade service interface
│   ├── IRatingService.java        # Rating service interface (feat-06)
│   ├── IStatsService.java         # Statistics service interface (feat-07)
│   ├── IShopDatabaseService.java  # Database service interface
│   └── impl/
│       ├── ShopServiceImpl.java   # Concrete service implementation
│       ├── RatingServiceImpl.java # feat-06
│       └── StatsServiceImpl.java  # feat-07
├── economy/                       # feat-02
│   ├── IEconomyService.java      # Economy service interface
│   ├── EconomyManager.java       # Vault integration, fees, taxes
│   └── ShopFeeCalculator.java    # Fee calculation with rarity multipliers
├── protection/                    # feat-05
│   ├── ProtectionManager.java    # Region protection orchestrator
│   ├── IProtectionProvider.java  # Provider interface
│   ├── WorldGuardProvider.java   # WorldGuard integration
│   ├── GriefPreventionProvider.java  # GriefPrevention integration
│   └── NoOpProtectionProvider.java   # No-op fallback
├── template/                      # feat-04
│   ├── TemplateManager.java      # Template lifecycle
│   └── ShopTemplate.java         # Template data model
├── notification/
│   ├── NotificationManager.java  # Multi-channel notifications
│   ├── NotificationType.java     # Notification event types
│   └── NotificationPreferencesDTO.java
├── gui/
│   └── admin/
│       ├── AdminShopGUI.java         # Admin shop management GUI
│       ├── AdminTradeHistoryGUI.java # Trade history viewer
│       └── AdminStatsGUI.java        # Statistics dashboard
├── api/                           # feat-03
│   ├── ApiServer.java            # Embedded Jetty server
│   ├── ShopApiServlet.java       # HTTP request routing
│   ├── ShopApiEndpoint.java      # Endpoint interface
│   └── ShopApiEndpointImpl.java  # Endpoint implementation
├── shop/
│   ├── ShopManager.java       # Shop lifecycle management
│   ├── ShopSession.java       # Active shop sessions
│   └── ShopMode.java          # Shop operational modes (see Dual-Mode Note)
├── sign/
│   ├── SignManager.java       # Sign creation and validation
│   ├── SignInteraction.java   # Player-sign interaction handling
│   ├── SignDisplay.java       # Sign text rendering (SignSide API)
│   ├── BarterSign.java        # Sign data model
│   ├── SignMode.java          # Sign modes (see Dual-Mode Note)
│   ├── SignType.java          # Sign type enumeration
│   └── SignUtil.java          # Sign utility methods
├── container/
│   ├── ContainerManager.java  # Chest container management
│   ├── BarterContainer.java   # Container data model
│   └── ContainerType.java     # Container type enumeration
├── trade/
│   ├── TradeEngine.java          # Trade execution logic
│   ├── TradeSession.java         # Active trade state
│   ├── TradeValidator.java       # Trade validation rules
│   └── TradeConfirmationGUI.java # GUI for trade confirmation
└── util/
    └── PlayerLookup.java         # Player name resolution with RVNKCore PlayerService integration
```

### Key Patterns

**Manager Lifecycle**: All managers implement `cleanup()` pattern for shutdown
**Database-First**: SQLite/MySQL with automatic fallback detection (FallbackTracker)
**Subcommand Pattern**: ShopCommand dispatches to Shop*SubCommand implementations
**Service Registry**: IShopService registered with RVNKCore (reflection-based, optional)
**Repository Pattern**: IShopRepository, ITradeRepository, IRatingRepository for async data access
**DTO Layer**: Java records for data transfer (ShopDataDTO, TradeItemDTO, TradeRecordDTO, RatingDataDTO, StatsDataDTO)

### Sign UI System (BarterSignsPlus Lineage)

The sign interaction model is derived from **BarterSignsPlus**, adapted for the modern subcommand architecture:

| Feature | BarterSignsPlus (Reference) | BarterShops (Current) |
|---------|---------------------------|----------------------|
| Trigger phrase | `[barter]` on line 1 | `[barter]` on line 1 |
| Container detection | Wall sign -> behind, standing -> below | Wall sign -> behind, standing -> below |
| Owner left-click | Configure mode | Configure mode (enter SETUP) |
| Owner right-click | Cycle menu (ActionMenu) | Cycle modes: SETUP->TYPE->BOARD->DELETE->SETUP |
| Customer right-click | Browse/purchase | Initiate trade (TradeEngine + GUI) |
| Customer left-click | Confirm transaction | N/A (GUI-based confirmation) |
| Persistence | Flatfile YAML | SQLite/MySQL via repository pattern |
| Commands | None (sign-only) | 19 subcommands + sign UI |

**Dual-Mode Enum Note**: Two overlapping mode enums exist:
- `sign.SignMode`: SETUP, BOARD, TYPE, HELP, DELETE (used by SignInteraction/SignDisplay)
- `shop.ShopMode`: SETUP_SELL, SETUP_STACK, TYPE, DELETE, BOARD_SETUP, BOARD_DISPLAY, HELP (used by ShopManager/SignManager)

`SignManager.calculateNextMode()` uses `ShopMode`, while `SignInteraction.handleOwnerRightClick()` uses `SignMode`. These are not mapped to each other - potential source of state confusion. Future refactoring should unify or create an explicit mapping between them.

### Trade Flow

```
1. Player right-clicks sign -> SignInteraction
2. Sign validates shop state -> SignManager
3. Trade initiated -> TradeEngine
4. Inventory validation -> TradeValidator
5. GUI confirmation -> TradeConfirmationGUI
6. Execute trade -> TradeEngine
7. Persist record -> ITradeRepository (async)
```

### Shop Creation Flow

```
1. Player places chest
2. Player attaches sign with [barter] tag
3. SignManager validates location/permissions
4. Shop created -> ShopManager
5. Shop persisted -> IShopRepository (async)
6. Sign updated with shop ID -> SignDisplay
```

## Command Formatting Standards

Use consistent message prefixes in command handlers:
- `&c>` - Usage instructions
- `&6*` - Operations in progress
- `&a+` - Success messages
- `&cx` - Error messages
- `&e!` - Warnings
- `&7   ` - Additional tips

**Console/Debug**: No emojis, no color codes. Use `LogManager` from RVNKCore for all logging.

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| spigot-api | 1.21-R0.1-SNAPSHOT | Bukkit API |
| RVNKCore | 1.5.2-alpha | Shared logging, ServiceRegistry (depend) |
| Vault | 1.7+ | Economy integration (softdepend) |
| snakeyaml | 2.0 | YAML configuration |
| jetty-servlet | 9.4.44.v20210927 | Web integration (REST API) |
| jetty-util | 9.4.44.v20210927 | Jetty utilities |
| PlaceholderAPI | 2.11.6 | Placeholder integration (optional) |
| LuckPerms | 5.4 | Permissions integration (optional) |
| WorldGuard | 7.0+ | Region protection (optional) |
| GriefPrevention | 16.18+ | Claim protection (optional) |

**Java Version**: 21+ (compile target)

## Shop Commands

### Always Registered (17 commands + help)

| Command | Description | Permission |
|---------|-------------|------------|
| `/shop create <name>` | Create a new barter shop | `bartershops.create` |
| `/shop list` | List all your shops | `bartershops.list` |
| `/shop info <id>` | View shop details | `bartershops.info` |
| `/shop history <id> [page]` | View paginated trade history for a shop | `bartershops.use` |
| `/shop remove <id>` | Remove a shop | `bartershops.remove` |
| `/shop nearby [radius]` | Find nearby shops | `bartershops.nearby` |
| `/shop template <action>` | Manage shop templates | `bartershops.template` |
| `/shop notifications <channel> <on\|off>` | Manage notifications | `bartershops.notifications` |
| `/shop fee list` | View listing fees (Vault optional) | `bartershops.economy.fee` |
| `/shop tax info\|calculate` | View/calculate taxes (Vault optional) | `bartershops.economy.tax` |
| `/shop region status\|info` | View region protection | `bartershops.region.*` |
| `/shop admin <...>` | Admin commands | `bartershops.admin` |
| `/shop admingui` | Open admin GUI | `bartershops.admin.gui` |
| `/shop inspect <id>` | Inspect any shop (admin) | `bartershops.admin.inspect` |
| `/shop clear <id>` | Clear shop inventory (admin) | `bartershops.admin.clear` |
| `/shop reload` | Reload configuration | `bartershops.admin.reload` |
| `/shop debug` | Debug information | `bartershops.admin.debug` |
| `/shop trade <player> <shopId> [qty]` | Admin force-trade (console-capable) | `bartershops.admin.trade` |
| `/shop help` | Show help (special case, not registered) | None |

### Conditional Commands (require service availability)

| Command | Requires | Permission |
|---------|----------|------------|
| `/shop rate <id> <1-5> [review]` | IRatingService | Default |
| `/shop reviews <id>` | IRatingService | Default |
| `/shop stats [player\|server]` | IStatsService | `bartershops.stats` |

**Note**: fee/tax commands always register (EconomyManager is always created with graceful Vault fallback). rate/reviews register when RatingService is initialized. stats registers when StatsService is initialized. The plugin runs fully without Vault - it is barter-based by nature.

**Aliases**: `/barter`, `/shops`

## Documentation References

### Local Documentation
- [README.md](README.md) - Features, usage, configuration
- [User Guide](docs/user-guide.md) - Complete player guide
- [Admin Guide](docs/admin-guide.md) - Installation, configuration, permissions
- [Developer Guide](docs/developer-guide.md) - API usage, integration
- [API Reference](docs/api-reference.md) - REST API endpoints
- **Graph Memory** — For BarterShops status and history: `search_nodes("BarterShops")`

### Parent Repo Standards

- [Coding Standards](../../docs/standard/coding-standards.md) - Java 17+ conventions
- [RVNKCore Integration](../../docs/standard/rvnkcore-integration.md) - ServiceRegistry usage patterns
- [Database Patterns](../../docs/standard/database-patterns.md) - Repository pattern, HikariCP

## Development Status

**Current Version**: 1.1.16 (Mar 3, 2026)

For plugin status and history, search Graph Memory: `search_nodes("BarterShops")`

**Latest Completions** (Feb 21):
- v1.0.27: `trade_source` persisted across all TradeEngine paths — ALTER TABLE migration for existing installs; `TradeRecordDTO.tradeSource` field; TradeEngine.logTrade() wires source; `TradeServiceImpl.serializeItem()` marked `@Deprecated` (dead code)
- v1.0.28: 30-day trade archive scheduler — `RetentionManager` Bukkit async repeating task; `retention:` config section; calls `ITradeRepository.archiveOlderThan()`
- v1.0.28: `/shop trade <player> <shopId> [qty]` admin force-trade — console-capable, bypasses payment, `ADMIN_OVERRIDE` source, 20th subcommand
- v1.0.29: Sign debounce fix — `PURCHASE_DEBOUNCE_MS = STATUS_DISPLAY_TICKS * 50L`; debounce check moved before null/air item check in `handleCustomerLeftClick()` to prevent "Hold payment item" overwriting "Purchased" feedback

**Sign Display Optimization Phases (v1.0.1+)**:
- **Phase 8** (Feb 12): Customer pagination for multi-payment BARTER shops — `currentPaymentPage` session field on `BarterSign`; right-click cycles pages; chat feedback "Payment option N/M". Owner preview mode — sneak+right-click in BOARD toggles `ownerPreviewMode` flag, sign switches to customer view.
- **Phase 8.5** (Feb 12): 1 payment per page rendering — `renderPaginatedPayment()` in `BoardModeRenderer`; summary page (index 0) + N payment pages; page indicator `§6page N of M` on line 3.
- **Phase 9** (Feb 13): Offering name wrapping — `displayOfferingWithWrapping(side, offering, startLine)` in `SignRenderUtil`; names >15 chars word-split across two lines; `computeNameSplit()` helper for word-boundary detection.
- **Phases 10–13** (Feb 13): Dual-wrap mode for single-payment BARTER shops — `displayDualWrapMode()` in `SignRenderUtil`; when both offering AND payment exceed 15 chars, the [Barter] header is removed and all 4 sign lines are used for content. Payment wrapping added via `displayPaymentWithWrapping()`.

**Sign Display Architecture (post-Phase 13)**:
- `SignDisplay.java`: Thin dispatcher routing to `ISignModeRenderer` implementations via `EnumMap`
- `BoardModeRenderer.java`: All BOARD mode rendering logic (customer/owner paths, pagination, wrapping)
- `SignRenderUtil.java`: Shared rendering helpers (`getTypeHeader`, `formatItemName`, `displayOfferingWithWrapping`, `displayPaymentWithWrapping`, `displayDualWrapMode`, `computeNameSplit`, `applyLayoutToSign`)
- `SignLayoutFactory.java`: Legacy layout builders (`MAX_LINE_LENGTH = 15`, `truncateForSign`, type/setup/board/delete layouts)

**Known Issues**:
- **bug-32/33**: Auto-revert scheduler — fixed in commit 89f2e23, regression testing pending

**Resolved Issues** (closed 2026-03-04):
- ~~**bug-30**: Chest break prevention deletes shop from database~~ — resolved; use DELETE mode is now enforced
- ~~**bug-34**: Owner preview mode (sneak+right-click) shows owner summary instead of customer pagination view~~ — resolved in v1.1.x sign display refactor

**Latest Completions** (Mar 3):
- v1.1.16: SignDisplay refactor (#193) — all 5 ISignModeRenderer renderers delegate to SignLayoutFactory; TypeModeRenderer and BoardModeRenderer.renderNotConfigured() updated; factory createTypeLayout/createNotConfiguredLayout aligned to current output
- v1.1.16: `/shop history <id> [page]` — paginated trade history command; async via ITradeRepository.findByShop(); buyer/seller/item/currency display; console-friendly; REST endpoint already existed at GET /api/trades/recent?shop={id}

**In Development**:
- Review follow-ups: debounce on trade-failure path; config caching consistency in RetentionManager; redundant null check in TradeRepositoryImpl.save()

**Planned Next**:
- feat: economic history aggregate tables (daily/monthly summaries, archive pruning, item_type column)
- feat-23: Shop config persistence across server restarts
- refactor-01: Mode enum unification (SignMode vs ShopMode)

## Development Checklist

Before committing changes:
1. `mvn clean package` - Build succeeds
2. Test on local MCSS server or deploy to test server
3. Verify console output for errors: `/rvnkdev-query <id> errors`
4. Check plugin loads correctly: `/rvnkdev-query <id> plugin BarterShops`
5. Test shop creation/removal workflow
6. Validate trade execution if changes affect trade logic
7. Check RVNKCore integration if services modified


