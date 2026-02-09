# BarterShops Development Roadmap

**Last Updated**: February 9, 2026
**Archon Board**: `bd4e478b-772a-4b97-bd99-300552840815`

## February 9, 2026 Status: Database-First Commands + Bug Fixes

**Recent Development** (derek/dev):

- ✅ `/shop list` and `/shop info` now query database directly (was reading empty sign cache) — bug-10
- ✅ `/shop info` supports both numeric IDs and shop name lookup
- ✅ Tab completion for `/shop info` suggests shop IDs and names from DB
- ✅ SignManager sign hydration verified working (sync `.join()` fix from prior session)
- ✅ Debug logging cleanup deployed (removed temporary `plugin.getLogger()` bypass calls)
- ✅ Spark profiler workflows added to admin skills documentation (admin-01)

**Prior Sprint** (ccafdc2..e502c32):

- ✅ SignManager click handling improvements, sign hydration wiring, seed generator refactor
- ✅ Console command cleanup, PlayerLookup addition, sign-UI internals
- ✅ Unit tests wired, admin GUIs connected to real repositories
- ✅ Trade engine and service improvements
- ✅ Legacy DatabaseManager cleanup (258 lines removed)
- ✅ TradeRepository SQL text block fixes (10 methods)

**Archon Status**: EPIC 8 archived (~37 tasks), 5 open bugs (sign interaction), 1 feature (trade history)

**Current Focus**: Sign interaction bug fixes (type cycling, punch navigation, delete confirmation)

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

**Completed** (Jan-Feb 2026, per EPIC 8):
- ✅ impl-08: Java Records DTOs + service layer
- ✅ impl-10: Trade engine (core transaction logic)
- ✅ impl-11: Repository pattern implementation
- ✅ test-06: 100/100 tests passing
- ✅ Table prefix support deployed

**Remaining Tasks** (Q1 2026):

| Gap | Task | Status |
|-----|------|--------|
| Interface naming (I-prefix) standardization | impl-07 | todo |
| CommandManager framework | impl-09 | todo |
| Live server integration test | test-07 | todo |

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
