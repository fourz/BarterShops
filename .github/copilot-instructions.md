# BarterShops Copilot Instructions

**Parent Hub**: See Ravenkraft-Dev CLAUDE.md for complete ecosystem standards.

## Tool Discovery

**Server Management**: `mcp_rvnkdev-minec_*` tools (console, files, state, db)
**Live Testing**: `/rvnktest [health|services|db|plugins|run all]`
**Agents**: Browse `.claude/agents/` for specialized workflows
**Skills**: Browse `.claude/skills/` for domain capabilities
**Rules Import**: Use `@import ../../.claude/rules/<rule>.md` for shared directives

## Archon Integration

**Board**: `bd4e478b-1234-5678-9abc-def012345678` (BarterShops)
**Workflow**: `find_tasks()` → `manage_task("update", status="doing")` → implement → `status="done"`

## Plugin-Specific Standards

### General Directive
- **No data migration methods** unless explicitly requested. Assume empty database.

### Shop System
- `IShopService` registered via RVNKCore ServiceRegistry
- Sign-based shop creation and management
- Player-to-player barter transactions

### Database Architecture
- `DatabaseManager` as central connection hub
- Table-specific logic in dedicated classes
- DTOs for clean data flow between layers
- Async operations where possible

### Message Prefixes
- `&a[Success]` | `&c[Error]` | `&e[Warning]` | `&b[Info]` | `&6[Help]`

### Logging
Use `Debug` class for all logging:
`java
private final Debug debug;
debug = new Debug(plugin, "ClassName", plugin.getDebugger().getLogLevel()) {};
`

## References

- **Architecture Spec**: `docs/spec/bartershops-architecture.md`
- **Database API**: `docs/standard/bartershops-database-api.md`
- **Coding Standards**: `docs/standard/coding-standards.md`
