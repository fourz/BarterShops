# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

**Output**: `target/BarterShops-1.0-SNAPSHOT.jar`

## Remote Testing Workflow

Use `/rvnkdev-deploy` and `/rvnkdev-query` skills for remote server testing:

```bash
# Full deployment cycle (build locally first)
mvn clean package
/rvnkdev-deploy b2bc4d7e full

# Query console for errors
/rvnkdev-query b2bc4d7e errors

# Check plugin startup logs
/rvnkdev-query b2bc4d7e plugin BarterShops

# Quick config iteration (no restart)
/rvnkdev-deploy b2bc4d7e reload-only
```

**Server IDs**:
- `b2bc4d7e` - SparkedHost test server
- `1eb313b1-40f7-4209-aa9d-352128214206` - Local MCSS dev server

## Local MCSS Development

Configure `.vscode/project.json` for local MCSS deployment:
```json
{
    "OutputFile": "..\\target\\BarterShops-1.0-SNAPSHOT.jar",
    "DestinationPath": "D:\\Minecraft\\MCSS\\servers\\RVNK Dev\\plugins",
    "PluginFolder": "BarterShops",
    "API": { "serverid": "...", "key": "...", "hostname": "localhost", "port": 25560 }
}
```

## Architecture

### Core Class Structure

```
org.fourz.BarterShops
├── BarterShops.java           # Main plugin class, lifecycle management
├── ManagerCore.java           # Core manager registration
├── command/
│   ├── CommandManager.java    # Command registration
│   ├── ShopCommand.java       # Main /shop command dispatcher
│   ├── SubCommand.java        # Abstract subcommand interface
│   ├── BaseCommand.java       # Base command implementation
│   └── sub/
│       ├── ShopCreateSubCommand.java
│       ├── ShopListSubCommand.java
│       ├── ShopInfoSubCommand.java
│       ├── ShopRemoveSubCommand.java
│       ├── ShopNearbySubCommand.java
│       └── ShopAdminSubCommand.java
├── config/
│   └── ConfigManager.java     # Configuration (config.yml, messages)
├── data/
│   ├── DatabaseFactory.java   # Database backend factory (SQLite/MySQL)
│   ├── DatabaseManager.java   # Database abstraction layer
│   ├── MySQLDatabaseManager.java
│   ├── SQLiteDatabaseManager.java
│   ├── IConnectionProvider.java
│   ├── FallbackTracker.java   # Database fallback detection
│   ├── dto/                   # Data Transfer Objects (Java records)
│   │   ├── ShopDataDTO.java
│   │   ├── TradeItemDTO.java
│   │   └── TradeRecordDTO.java
│   └── repository/            # Repository pattern for data access
│       ├── IShopRepository.java
│       └── ITradeRepository.java
├── service/
│   ├── IShopService.java          # Service interface (RVNKCore integration)
│   ├── ITradeService.java         # Trade service interface
│   ├── IShopDatabaseService.java  # Database service interface
│   └── impl/
│       └── ShopServiceImpl.java   # Concrete service implementation
├── shop/
│   ├── ShopManager.java       # Shop lifecycle management
│   ├── ShopSession.java       # Active shop sessions
│   └── ShopMode.java          # Shop operational modes
├── sign/
│   ├── SignManager.java       # Sign creation and validation
│   ├── SignInteraction.java   # Player-sign interaction handling
│   ├── SignDisplay.java       # Sign text rendering (SignSide API)
│   ├── BarterSign.java        # Sign data model
│   ├── SignMode.java          # Sign modes (SETUP, MAIN, TYPE, HELP, DELETE)
│   ├── SignType.java          # Sign type enumeration
│   └── SignUtil.java          # Sign utility methods
├── container/
│   ├── ContainerManager.java  # Chest container management
│   ├── BarterContainer.java   # Container data model
│   └── ContainerType.java     # Container type enumeration
└── trade/
    ├── TradeEngine.java          # Trade execution logic
    ├── TradeSession.java         # Active trade state
    ├── TradeValidator.java       # Trade validation rules
    └── TradeConfirmationGUI.java # GUI for trade confirmation
```

### Key Patterns

**Manager Lifecycle**: All managers implement `cleanup()` pattern for shutdown
**Database-First**: SQLite/MySQL with automatic fallback detection (FallbackTracker)
**Subcommand Pattern**: ShopCommand dispatches to Shop*SubCommand implementations
**Service Registry**: IShopService registered with RVNKCore (reflection-based, optional)
**Repository Pattern**: IShopRepository, ITradeRepository for async data access
**DTO Layer**: Java records for data transfer (ShopDataDTO, TradeItemDTO, TradeRecordDTO)

### Trade Flow

```
1. Player right-clicks sign → SignInteraction
2. Sign validates shop state → SignManager
3. Trade initiated → TradeEngine
4. Inventory validation → TradeValidator
5. GUI confirmation → TradeConfirmationGUI
6. Execute trade → TradeEngine
7. Persist record → ITradeRepository (async)
```

### Shop Creation Flow

```
1. Player places chest
2. Player attaches sign with [barter] tag
3. SignManager validates location/permissions
4. Shop created → ShopManager
5. Shop persisted → IShopRepository (async)
6. Sign updated with shop ID → SignDisplay
```

## Command Formatting Standards

Use consistent message prefixes in command handlers:
- `&c▶` - Usage instructions
- `&6⚙` - Operations in progress
- `&a✓` - Success messages
- `&c✖` - Error messages
- `&e⚠` - Warnings
- `&7   ` - Additional tips

**Console/Debug**: No emojis, no color codes. Use `LogManager` from RVNKCore for all logging.

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| spigot-api | 1.21-R0.1-SNAPSHOT | Bukkit API |
| RVNKCore | 1.3.0-alpha | Shared logging, ServiceRegistry (softdepend) |
| snakeyaml | 2.0 | YAML configuration |
| jetty-servlet | 9.4.44.v20210927 | Web integration (future REST API) |
| jetty-util | 9.4.44.v20210927 | Jetty utilities |
| PlaceholderAPI | 2.11.6 | Placeholder integration (optional) |
| LuckPerms | 5.4 | Permissions integration (optional) |

**Java Version**: 21+ (compile target)

## Shop Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/shop create` | Create a new barter shop | `bartershops.create` |
| `/shop list` | List all your shops | `bartershops.list` |
| `/shop info <id>` | View shop details | `bartershops.info` |
| `/shop remove <id>` | Remove a shop | `bartershops.remove` |
| `/shop nearby [radius]` | Find nearby shops | `bartershops.nearby` |
| `/shop admin <...>` | Admin commands | `bartershops.admin` |

**Aliases**: `/barter`, `/shops`

## Documentation References

- [README.md](README.md) - Features, usage, configuration
- [ROADMAP.md](ROADMAP.md) - Planned features, technical roadmap
- [docs/](docs/) - Component documentation (pending)

## Archon MCP Integration

**BarterShops Board**: `bd4e478b-772a-4b97-bd99-300552840815`
**Parent Project (Ravenkraft Dev)**: `4787f505-e92e-474d-ba54-f5ac7993ccfe`
**RVNKCore Board**: `7785e125-4468-44e2-a86c-2fef668fce48`

Use parent project for shared RVNK standards, coding patterns, and documentation. Reference RVNKCore board for integration patterns (ServiceRegistry, Repository, DTO) and shared service interfaces.

Use Archon task management for development workflow:

```python
# Check for existing tasks on this board
find_tasks(project_id="bd4e478b-772a-4b97-bd99-300552840815")

# Start work
manage_task("update", task_id="...", status="doing")

# Search knowledge base for Paper/Bukkit patterns
rag_search_knowledge_base(query="Bukkit sign interaction")

# Complete task
manage_task("update", task_id="...", status="done")
```

### Current Development Status (Jan 2026)

**Completed (v1.0):**

- Core plugin structure with manager lifecycle
- Sign-based shop creation and validation
- Chest container management
- Command framework (ShopCommand + subcommands)
- Database abstraction (SQLite/MySQL with factory)
- RVNKCore integration (LogManager, ServiceRegistry)
- DTO layer (ShopDataDTO, TradeItemDTO, TradeRecordDTO)

**In Progress:**

- impl-11: ShopServiceImpl implementation (RVNKCore registration)
- Trade engine and validation logic
- Repository pattern implementation (async CompletableFuture)
- GUI confirmation system for trades

**Pending:**

- REST API endpoints (RVNKCore RestAPIService)
- Shop statistics and analytics
- Advanced trade types (currency, partial stacks)
- Web dashboard integration
- PlaceholderAPI expansion support

## Development Checklist

Before committing changes:
1. `mvn clean package` - Build succeeds
2. Test on local MCSS server or deploy to test server
3. Verify console output for errors: `/rvnkdev-query <id> errors`
4. Check plugin loads correctly: `/rvnkdev-query <id> plugin BarterShops`
5. Test shop creation/removal workflow
6. Validate trade execution if changes affect trade logic
7. Check RVNKCore integration if services modified
