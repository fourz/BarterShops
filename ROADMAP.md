# BarterShops Development Roadmap

**Last Updated**: February 12, 2026
**Archon Board**: `bd4e478b-772a-4b97-bd99-300552840815`

## February 12, 2026 Status: Sign UI Phases 6-8 Complete

**Recent Development** (derek/dev):

- ✅ Phase 6: Multi-step sign configuration with multiple BARTER payments (impl-28)
- ✅ Phase 7: Shop configuration persistence to database (ShopConfigSerializer/Manager)
- ✅ Phase 8: Customer-facing UI with paginated payment browsing + owner preview
- ✅ Type-locking: Inventory type locked once first item placed (impl-27)
- ✅ Shop protection: Three-layer sign/chest protection (impl-29)
- ✅ Auto-revert scheduler: All modes now auto-revert to BOARD after 10s (bug-32/33)
- ✅ Orphaned shop fix: Sign deletion now clears cache + PDC properly (bug-35)
- ✅ TypeAvailabilityManager: Config-driven shop/sign type enable/disable
- ✅ Immutable metadata map regression fixed (bug-31)

**Prior Sprint** (Feb 7-9):

- ✅ `/shop list` and `/shop info` now query database directly (bug-10)
- ✅ PlayerLookup utility with RVNKCore integration (feat-11)
- ✅ Debug command with diagnostics, loglevel, seed (feat-01)
- ✅ Integration-01: Sign hydration verification + 3 bugs found/fixed
- ✅ Legacy DatabaseManager cleanup (258 lines removed, refactor-23)
- ✅ TradeRepository SQL text block fixes (bug-12)
- ✅ Init order race fix for conditional subcommands (bug-11)

**Archon Status**: EPICs 8-12 archived, active development on sign UI stabilization

**Current Focus**: Sign UI testing, trade execution validation, customer experience polish

---

## Completed Epics

### EPIC 9: RVNKCore Integration & Database Foundation (Jan 26 - Feb 2, 2026)

9 tasks archived. Established complete RVNKCore integration layer with database-backed persistence.

- impl-08: Repository Pattern & DTO Layer (ShopDataDTO, TradeItemDTO, TradeRecordDTO records)
- impl-09: CommandManager Framework (ShopCommand + 6 initial subcommands, tab completion, permissions)
- impl-10: Trade Engine core transaction logic (TradeEngine, TradeSession, TradeValidator, TradeConfirmationGUI)
- impl-11: ShopServiceImpl for RVNKCore ServiceRegistry (in-memory ConcurrentHashMap, async CompletableFuture)
- impl-11: Database repository integration (ConnectionProviderImpl, HikariCP, dual-mode repository/fallback)
- impl-12: Database Repository Implementations (ShopRepositoryImpl ~850 lines, TradeRepositoryImpl ~770 lines, ConnectionProviderImpl ~340 lines)
- bug-07: RVNKCore service registration NullPointerException (ShopManager→ShopServiceImpl factory fix)
- refactor-XX: Migrate to RVNKCore LogManager (13 files verified, no local Debug.java)

### EPIC 10: Feature Sprint v0.2 (Jan 31 - Feb 2, 2026)

13 tasks archived. Delivered 8 major features plus admin command infrastructure.

- feat-01: Admin GUI (AdminShopGUI, AdminTradeHistoryGUI, AdminStatsGUI)
- feat-02: Economy Integration (EconomyManager, ShopFeeCalculator, Vault API)
- feat-03: Web API (ApiServer, ShopApiServlet, 8 REST endpoints, OpenAPI spec)
- feat-04: Shop Templates (TemplateManager, ShopTemplate record, YAML persistence)
- feat-05: Shop Regions & Protection (WorldGuard, GriefPrevention, NoOp providers)
- feat-06: Trade Ratings & Reviews (IRatingService, RatingRepositoryImpl, 5-star system)
- feat-07: Shop Analytics & Statistics (IStatsService, StatsServiceImpl, 5-min cache TTL)
- feat-08: Notification System (NotificationManager, 8 types, multi-channel delivery)
- feat-30: ShopInspectSubCommand (admin shop inspection)
- feat-31: ShopClearSubCommand (admin inventory clear)
- feat-32: ShopReloadSubCommand (admin config reload)
- impl-18: Register admin subcommands in ShopCommand
- impl-16: SignInteraction trade processing (TradeEngine integration, async execution)

### EPIC 11: Test Suite Foundation (Jan 26 - Feb 3, 2026)

5 tasks archived. Established comprehensive testing infrastructure with 242+ tests.

- test-06: Unit & Integration Tests (100/100 tests, JUnit 5.10.1, Mockito 5.8.0, H2 in-memory)
- test-07: Live Server Integration Tests (MCSS Dev, 6/6 commands verified, PlugManX compatible)
- test-09: Integration Test Suite Design (34 tests designed: ServiceRegistry, ShopRepository, TradeEngine)
- test-16: Comprehensive integration suite (142 tests, 37 classes, 95%+ coverage for feat-02/05/06/07)
- bug-06: Unit test compilation errors (StatsServiceTest, EconomyManagerTest, ProtectionManagerTest fixes)

### EPIC 12: TokenEconomy Bug Fixes (Feb 1-3, 2026)

3 tasks archived. Fixed display and usability issues in TokenEconomy companion plugin.

- bug-02: /economy help returns "Unknown command" (created HelpCommand.java, registered in EconomyCommand)
- bug-04: /economy top shows UUID instead of player name (SQLiteDataStore resolvePlayerName() added)
- bug-05: Currency symbol displays as "?" in console (ASCII-safe defaults: "Wizbuck"/"W")

---

## Current Status (v0.1)

Foundation complete with active refactoring:

- ✅ Sign creation and validation
- ✅ Shop ownership tracking (ShopManager)
- ✅ Multiple shop interaction modes (SETUP, BOARD, TYPE, etc.)
- ✅ Command system infrastructure (console command cleanup complete)
- ✅ Database connection framework (DatabaseFactory)
- ✅ Repository-backed admin GUIs
- ✅ Trade engine service layer active
- ✅ Unit testing framework in place
- ✅ Debug and logging utilities
- ✅ Database-first command queries (ShopRepository for list/info)
- ✅ Sym-linked to Ravenkraft Dev documentation hub

## RVNKCore Integration Status

**Completed** (Jan-Feb 2026, per EPICs 8-9):
- ✅ impl-08: Java Records DTOs + service layer
- ✅ impl-09: CommandManager framework (19 subcommands)
- ✅ impl-10: Trade engine (core transaction logic)
- ✅ impl-11: Repository pattern + database integration
- ✅ impl-12: Full repository implementations (Shop, Trade, Connection)
- ✅ test-06: 100/100 unit tests passing
- ✅ test-07: Live server integration verified
- ✅ Table prefix support deployed
- ✅ LogManager migration complete (13 files)

**Remaining Tasks** (Q1 2026):

| Gap | Task | Status |
|-----|------|--------|
| Interface naming (I-prefix) standardization | impl-07 | todo |

**Pattern Standard**: I-prefix interfaces (`IShopService`), Impl suffix (`ShopServiceImpl`), Java Records DTOs, async `CompletableFuture`, `FallbackTracker` for resilience.

See: `rvnkcore-integration.md` | `shared-patterns.md` | `dto-patterns.md`

## Version 0.2 (Q1-Q2 2026)

- **Core Trade Functionality**
  - Complete transaction processing system (impl-10)
  - Inventory management during trades
  - Item validation and verification
  - Trade confirmation dialog

- **Command System**
  - CommandManager framework integration (impl-09)
  - Tab completion with contextual suggestions
  - Permission-based command access

- **Data Persistence**
  - Repository pattern for all data access
  - Shop data storage and retrieval
  - Transaction logging

## Version 0.3 (Q2-Q3 2026)

- **Admin Tools**
  - Shop inspection for moderators
  - Remote shop management
  - Advanced listing with filtering

- **Enhanced Features**
  - Multi-item trades
  - Item bundle support
  - Shop categories

## Version 0.4+ (Future)

- **Shop Discovery**: Global directory, search, nearby indicators
- **Integration**: Economy plugin (VaultUnlocked), PlaceholderAPI, server notifications
- **GUI**: GUI-based shop management
- **Advanced**: Shop rentals, group shops, templates, trade contracts

## Technical Debt

- Implement unit testing (test-06)
- Establish code coverage targets
- Standardize exception handling via RVNKCore patterns
- Performance profiling for Ravenkraft player counts

## Contributing

Feature requests via GitHub issues with `feature-request` or `enhancement` tags.

This roadmap is subject to change based on community feedback and server priorities.
