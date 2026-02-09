# BarterShops Development Roadmap

**Last Updated**: February 9, 2026
**Archon Board**: `bd4e478b-772a-4b97-bd99-300552840815`

## February 9, 2026 Status: Active Refactoring

**Recent Development** (derek/dev, ccafdc2):

- ✅ SignManager click handling improvements (refactor: ccafdc2)
- ✅ Console command cleanup and PlayerLookup addition (fb156e7)
- ✅ Sign hydration wiring fixes, seed generator refactor, sign-UI internals (e502c32)
- ✅ Unit tests wired, admin GUIs connected to real repositories (9084333)
- ✅ Trade engine and service improvements (b952125)

**Archon Status**: EPIC 8 consolidated and archived (~37 tasks completed Jan-Feb 2026)

**Current Focus**: Sign interaction refinement, admin tooling integration, repository-backed GUI systems

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
