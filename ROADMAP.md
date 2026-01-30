# BarterShops Development Roadmap

**Last Updated**: January 25, 2026
**Archon Board**: `bd4e478b-772a-4b97-bd99-300552840815`

## Current Status (v0.1)

Early development with foundational framework in place:

- ✅ Sign creation and validation
- ✅ Shop ownership tracking (ShopManager)
- ✅ Multiple shop interaction modes (SETUP, BOARD, TYPE, etc.)
- ✅ Command system infrastructure
- ✅ Database connection framework (DatabaseFactory)
- ✅ Debug and logging utilities
- ✅ Sym-linked to Ravenkraft Dev documentation hub

## RVNKCore Integration (Q1 2026)

Aligning BarterShops with the unified RVNKCore pattern standard:

| Gap | Task | Status |
|-----|------|--------|
| Interface naming (I-prefix) + Repository pattern | impl-07 | todo |
| Java Records DTOs + service layer | impl-08 | todo |
| CommandManager framework | impl-09 | todo |
| Trade engine (core transaction logic) | impl-10 | todo |
| Unit tests | test-06 | todo |
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
