# BarterShops Administrator Guide

**Version**: 1.0-SNAPSHOT
**For**: Server Administrators and Staff
**Last Updated**: February 2026

This guide covers installation, configuration, permissions, and administrative features of BarterShops.

---

## Table of Contents

1. [Installation](#installation)
2. [Configuration](#configuration)
3. [Permissions Reference](#permissions-reference)
4. [Commands Reference](#commands-reference)
5. [Database Management](#database-management)
6. [Administrative Tools](#administrative-tools)
7. [Troubleshooting](#troubleshooting)
8. [Integration](#integration)

---

## Installation

### Requirements

- **Server**: Spigot, Paper, or compatible fork (1.16+)
- **Java**: Java 17 or higher
- **Dependencies** (optional):
  - RVNKCore 1.3.0+ (softdepend - enhanced logging and service integration)
  - Vault (for economy integration with SELL/BUY shop types)
  - LuckPerms or PermissionsEx (for advanced permission management)

### Installation Steps

1. **Download the Plugin**
   - Get the latest `BarterShops-X.X.X.jar` from releases
   - Verify checksum if provided

2. **Install to Server**
   ```
   server/
   └── plugins/
       └── BarterShops-X.X.X.jar
   ```

3. **Start Server**
   - Run your server to generate default configuration files
   - Check console for successful loading:
     ```
     [INFO] [BarterShops] BarterShops has been loaded
     ```

4. **Configure Settings**
   - Edit `plugins/BarterShops/config.yml`
   - Configure database backend (MySQL or SQLite)
   - Set permission defaults
   - Customize messages

5. **Reload Configuration**
   ```
   /shop admin reload
   ```

### First-Time Setup Checklist

- [ ] Plugin installed and loaded successfully
- [ ] Database configured (SQLite default, MySQL for production)
- [ ] Permissions configured for player groups
- [ ] Messages customized for your server
- [ ] Test shop creation by a player
- [ ] Verify shop persistence after server restart
- [ ] Check log level (WARNING for production, INFO for testing)

---

## Configuration

### config.yml Structure

```yaml
general:
  # Log level: DEBUG, INFO, WARNING, SEVERE
  # DEBUG: All debug messages (development only)
  # INFO: Operational logs (testing/staging)
  # WARNING: Important warnings only (production recommended)
  # SEVERE: Critical errors only
  logLevel: WARNING

storage:
  # Database backend: sqlite or mysql
  type: sqlite

  mysql:
    host: localhost
    port: 3306
    username: minecraft
    password: 'secure_password_here'
    useSSL: false
    database: bartershops
    tablePrefix: bartershops_

  sqlite:
    # Relative to plugin data folder
    database: data.db

retention:
  # Automatically move old trade records out of the active table
  enabled: true
  # Records older than this many days are moved to the archive table
  active-days: 30
  # "archive" = move to barter_trade_records_archive before removing
  action: archive

messages:
  generic:
    error:
    - '[Error]'
    success:
    - '[Success]'
    info:
    - '[Info]'
    help:
    - '[Help]'
    no_held_item:
    - 'You must be holding an item!'

  sign:
    create:
      denied:
      - 'You do not have permission to create this sign!'
    use:
      denied:
      - 'You do not have permission to use this sign!'
    setup:
    - 'Hold the item you wish to sell and left-click the sign.'

sign:
  setup:
  - '&5%1'
  - 'L-Click with'
  - 'item you want'
  - 'to sell'

  main_menu:
  - '&2Barter Shop'
  - '(right-click)'
  - '&9%1'
  - '&9%2'
```

### Configuration Options Explained

#### General Settings

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `logLevel` | DEBUG, INFO, WARNING, SEVERE | WARNING | Plugin logging verbosity |

**Recommended Log Levels:**
- **Production**: WARNING (minimal console noise)
- **Staging**: INFO (operational visibility)
- **Development**: DEBUG (full diagnostic output)

#### Storage Settings

##### SQLite (Default)

Best for:
- Small to medium servers (under 100 players)
- Simple setup requirements
- Single-server deployments

```yaml
storage:
  type: sqlite
  sqlite:
    database: data.db
```

**Database Location**: `plugins/BarterShops/data.db`

##### MySQL (Recommended for Production)

Best for:
- Large servers (100+ concurrent players)
- Multi-server networks
- Advanced backup/replication needs

```yaml
storage:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    username: bartershops_user
    password: 'strong_password_here'
    useSSL: false
    database: bartershops
    tablePrefix: bartershops_
```

**Security Best Practices:**
- Create dedicated MySQL user with minimal permissions
- Use strong, unique password
- Enable SSL for remote connections
- Restrict database access to localhost if possible

**MySQL Setup Commands:**
```sql
CREATE DATABASE bartershops CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'bartershops_user'@'localhost' IDENTIFIED BY 'strong_password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER ON bartershops.* TO 'bartershops_user'@'localhost';
FLUSH PRIVILEGES;
```

#### Retention Settings

Controls automatic archiving of old trade history records. Runs once every 24 hours (5-minute startup delay to avoid server load spikes).

| Option | Values | Default | Description |
|--------|--------|---------|-------------|
| `retention.enabled` | true/false | true | Enable automatic archiving |
| `retention.active-days` | integer | 30 | Records older than N days are archived |
| `retention.action` | archive | archive | Move records to archive table before removing from active |

**Archive behavior**: Records are INSERT-SELECTed from `barter_trade_records` into `barter_trade_records_archive`, then deleted from the active table. The archive table is retained indefinitely (no pruning currently). Set `active-days: 0` to archive all records immediately on next cycle.

**When to adjust**:
- High-traffic servers: reduce `active-days` (e.g., 14) to keep queries fast
- Low-traffic or audit-heavy servers: increase (e.g., 90) or disable archiving

#### Message Customization

Messages support Minecraft color codes (`&a`, `&c`, etc.) and placeholders:

| Placeholder | Replacement | Example Usage |
|-------------|-------------|---------------|
| `%1` | First dynamic value | Shop name, player name |
| `%2` | Second dynamic value | Item count, location |
| `%3` | Third dynamic value | Additional context |

**Example**:
```yaml
messages:
  shop:
    created:
    - '&a✓ Shop "&f%1&a" created at %2'
```

---

## Permissions Reference

### Player Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `bartershops.create` | true | Allow creating shops |
| `bartershops.list` | true | Allow listing own shops |
| `bartershops.info` | true | Allow viewing shop details |
| `bartershops.remove` | true | Allow removing own shops |
| `bartershops.nearby` | true | Allow finding nearby shops |
| `bartershops.use` | true | Allow using/trading at shops |

### Administrative Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `bartershops.admin` | op | Access to `/shop admin` commands |
| `bartershops.admin.inspect` | op | Inspect any shop (bypass ownership) |
| `bartershops.admin.clear` | op | Clear shop inventories |
| `bartershops.admin.reload` | op | Reload plugin configuration |
| `bartershops.admin.remove` | op | Remove any shop (bypass ownership) |
| `bartershops.admin.trade` | op | Force-execute a trade via `/shop trade` (console-capable) |
| `bartershops.reload` | op | Alternative reload permission |

### Permission Examples

#### LuckPerms Configuration

```bash
# Grant basic shop permissions to all players
lp group default permission set bartershops.create true
lp group default permission set bartershops.list true
lp group default permission set bartershops.nearby true

# Grant admin permissions to moderators
lp group moderator permission set bartershops.admin true
lp group moderator permission set bartershops.admin.inspect true

# Grant full permissions to admins
lp group admin permission set bartershops.* true
```

#### PermissionsEx Configuration

```yaml
groups:
  default:
    permissions:
      - bartershops.create
      - bartershops.list
      - bartershops.nearby
      - bartershops.use

  moderator:
    permissions:
      - bartershops.admin
      - bartershops.admin.inspect

  admin:
    permissions:
      - bartershops.*
```

---

## Commands Reference

### Player Commands

| Command | Permission | Description | Example |
|---------|-----------|-------------|---------|
| `/shop create <name>` | `bartershops.create` | Create a shop | `/shop create Diamond Shop` |
| `/shop list` | `bartershops.list` | List your shops | `/shop list` |
| `/shop info <id>` | `bartershops.info` | View shop details | `/shop info 5` |
| `/shop remove <id>` | `bartershops.remove` | Remove your shop | `/shop remove 3` |
| `/shop nearby [radius]` | `bartershops.nearby` | Find nearby shops | `/shop nearby 100` |

**Aliases**: `/barter`, `/shops`

### Administrative Commands

| Command | Permission | Description | Example |
|---------|-----------|-------------|---------|
| `/shop admin` | `bartershops.admin` | Display admin help | `/shop admin` |
| `/shop admin inspect <id>` | `bartershops.admin.inspect` | Inspect any shop | `/shop admin inspect 10` |
| `/shop admin clear <id>` | `bartershops.admin.clear` | Clear shop inventory | `/shop admin clear 10` |
| `/shop admin reload` | `bartershops.admin.reload` | Reload configuration | `/shop admin reload` |
| `/shop trade <player> <shopId> [qty]` | `bartershops.admin.trade` | Force a trade from a shop to a player, bypassing payment | `/shop trade Alice 5 2` |
| `/shop reload` | `bartershops.reload` | Reload configuration | `/shop reload` |

---

## Database Management

### Database Tables

BarterShops creates the following tables:

#### `bartershops_shops`

Stores shop configuration and metadata.

| Column | Type | Description |
|--------|------|-------------|
| `shop_id` | INT AUTO_INCREMENT | Primary key |
| `owner_uuid` | VARCHAR(36) | Shop owner UUID |
| `shop_name` | VARCHAR(255) | Custom shop name |
| `shop_type` | ENUM | BARTER, SELL, BUY, ADMIN |
| `location_world` | VARCHAR(255) | Sign world name |
| `location_x`, `_y`, `_z` | DOUBLE | Sign coordinates |
| `chest_location_world` | VARCHAR(255) | Chest world name |
| `chest_location_x`, `_y`, `_z` | DOUBLE | Chest coordinates |
| `is_active` | BOOLEAN | Shop enabled status |
| `created_at` | TIMESTAMP | Creation timestamp |
| `last_modified` | TIMESTAMP | Last update timestamp |
| `metadata` | TEXT (JSON) | Custom metadata |

#### `bartershops_trades` (Future)

Stores trade transaction history.

| Column | Type | Description |
|--------|------|-------------|
| `trade_id` | INT AUTO_INCREMENT | Primary key |
| `shop_id` | INT | Foreign key to shops |
| `trader_uuid` | VARCHAR(36) | Trader UUID |
| `trade_timestamp` | TIMESTAMP | When trade occurred |
| `input_items` | TEXT (JSON) | Items given |
| `output_items` | TEXT (JSON) | Items received |
| `success` | BOOLEAN | Trade completion status |

### Backup Procedures

#### SQLite Backup

```bash
# Stop server
# Copy database file
cp plugins/BarterShops/data.db plugins/BarterShops/data.db.backup-$(date +%Y%m%d)
# Restart server
```

#### MySQL Backup

```bash
# Dump database
mysqldump -u bartershops_user -p bartershops > bartershops_backup_$(date +%Y%m%d).sql

# Restore from backup
mysql -u bartershops_user -p bartershops < bartershops_backup_20260201.sql
```

### Migration Between Databases

#### SQLite to MySQL

1. **Export SQLite data** (manual extraction or custom script)
2. **Configure MySQL** in config.yml
3. **Restart server** - tables auto-created
4. **Import data** to MySQL tables
5. **Verify** shop data integrity

#### MySQL to SQLite

1. **Export MySQL data**
   ```sql
   SELECT * FROM bartershops_shops INTO OUTFILE '/tmp/shops.csv';
   ```
2. **Configure SQLite** in config.yml
3. **Restart server** - database auto-created
4. **Import data** using SQLite import tools

---

## Administrative Tools

### Shop Inspection

View detailed information about any shop, including non-owned shops:

```
/shop admin inspect <shop_id>
```

**Output**:
```
Shop #10: Alice's Emerald Exchange
Type: BARTER
Owner: Alice (UUID: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)
Status: Active
Sign Location: world (123, 64, -456)
Chest Location: world (123, 63, -456)
Created: 2026-01-20 10:00:00
Last Modified: 2026-01-25 14:30:00

Trade Configuration:
Offering: 32x Emerald
Requesting: 1x Diamond

Metadata:
{
  "custom_field": "example_value"
}
```

### Clearing Shop Inventories

Remove all items from a shop's chest (for cleanup or policy enforcement):

```
/shop admin clear <shop_id>
```

**Warning**: This permanently removes items. Use with caution.

**Use Cases**:
- Removing abandoned shops
- Enforcing item ban policies
- Cleaning up exploited shops

### Configuration Reloading

Reload config.yml without server restart:

```
/shop admin reload
```
or
```
/shop reload
```

**What reloads**:
- Log level settings
- Message templates
- Sign display formats

**What does NOT reload**:
- Database connection settings (requires restart)
- Permission configurations (managed by permission plugin)
- Retention scheduler (requires restart to apply new `active-days` or `enabled` changes)

### Force-Executing a Trade (`/shop trade`)

Execute a trade from a specific shop to a player without requiring the player to interact with the sign. Payment is bypassed entirely. Recorded as `ADMIN_OVERRIDE` in trade history.

**Usage** (in-game or console):
```
/shop trade <player> <shopId> [qty]
```

| Argument | Required | Description |
|----------|----------|-------------|
| `<player>` | Yes | Online player name. Player must be online. |
| `<shopId>` | Yes | Numeric shop ID (from `/shop info` or `/shop list`). |
| `[qty]` | No | Item quantity. Must be a positive multiple of the shop's base offering amount. Defaults to base amount. |

**Examples**:
```
/shop trade Alice 5
/shop trade Bob 12 4
```

**Console example**:
```
> shop trade Alice 5 2
[OK] Traded 2x EMERALD from shop #5 to Alice (txId: abc123)
```

**Failure conditions** (clear error returned to sender):
- Player is offline
- Shop ID does not exist or shop is inactive
- Qty is not a positive multiple of base offering amount
- Shop is out of stock (non-admin shops only)

**Use cases**:
- Reimbursing players after a failed trade
- Automated test flows from console
- Event rewards distribution

### Trade Archival & Retention

BarterShops automatically archives trade records older than `retention.active-days` (default: 30 days). This keeps the active `barter_trade_records` table small and queries fast.

**How it works**:
1. On server startup, RetentionManager starts (5-minute delay to avoid load spikes)
2. Every 24 hours, records with `completed_at < NOW() - active-days` are archived
3. Records are moved (INSERT-SELECT + DELETE) from `barter_trade_records` to `barter_trade_records_archive`
4. The archive table is retained indefinitely

**Trade history fields** (both active and archive tables):

| Column | Description |
|--------|-------------|
| `transaction_id` | UUID of the transaction |
| `shop_id` | Shop that executed the trade |
| `buyer_uuid` / `seller_uuid` | Player UUIDs involved |
| `item_stack_data` | Serialized item (TYPE:amount format) |
| `quantity` | Item count traded |
| `currency_material` / `price_paid` | Currency info (SELL/BUY shops) |
| `status` | COMPLETED, CANCELLED, FAILED, PENDING, REFUNDED |
| `trade_source` | How trade was initiated: `INSTANT_PURCHASE`, `GUI_CONFIRMATION`, `DEPOSIT_EXCHANGE`, `WITHDRAWAL_EXCHANGE`, `ADMIN_OVERRIDE` |
| `completed_at` | Timestamp of transaction |

**Archive backfill note**: Records created before v1.0.27 have `trade_source = 'UNKNOWN'` (migration default).

---

## Troubleshooting

### Plugin Won't Load

**Symptoms**:
- "BarterShops has been loaded" not in console
- Commands don't work

**Solutions**:
1. Check Java version: `java -version` (must be 17+)
2. Verify plugin.yml is intact (corrupted JAR)
3. Check for dependency conflicts
4. Review startup logs for errors

---

### Database Connection Errors

**MySQL Connection Failed**:
```
[ERROR] [BarterShops] Failed to connect to MySQL database
```

**Solutions**:
1. Verify MySQL server is running
2. Check credentials in config.yml
3. Test connection manually:
   ```bash
   mysql -h localhost -u bartershops_user -p bartershops
   ```
4. Check firewall rules
5. Verify user permissions in MySQL

---

### Shops Not Persisting

**Symptoms**:
- Shops disappear after server restart
- "/shop list" shows no shops after restart

**Solutions**:
1. Check database write permissions
2. Verify database file location (SQLite)
3. Check for errors during shutdown in console
4. Ensure plugin has time to save data before forced stop

---

### Permission Issues

**Players Can't Create Shops**:
- Verify `bartershops.create` permission
- Check permission plugin (LuckPerms, PEX) configuration
- Test with OP player to rule out permission system

**Commands Not Working**:
- Ensure aliases aren't conflicting with other plugins
- Check command registration in console logs
- Verify plugin loaded before attempting commands

---

### Performance Issues

**High CPU Usage**:
- Check log level (set to WARNING in production)
- Monitor database query performance
- Consider MySQL for large servers (100+ shops)

**Memory Leaks**:
- Update to latest version
- Report to developers with heap dump

---

## Integration

### RVNKCore Integration

BarterShops can integrate with RVNKCore for enhanced features:

**Services Registered**:
- `IShopService` - Shop management API for other plugins

**Benefits**:
- Centralized logging via LogManager
- ServiceRegistry access for cross-plugin features
- Shared database connection pooling (future)

**Configuration**: Add RVNKCore.jar to plugins/ (soft dependency - optional)

---

### Vault Integration (Future)

For economy-based shop types (SELL/BUY):

1. Install Vault plugin
2. Install economy plugin (EssentialsX, etc.)
3. Enable SELL/BUY shop types in BarterShops

---

### PlaceholderAPI Integration (Future)

Planned placeholders:
- `%bartershops_shop_count%` - Total shops
- `%bartershops_my_shops%` - Player's shop count
- `%bartershops_nearby_count%` - Shops within radius

---

## API for Developers

See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for:
- IShopService API usage
- Custom shop type development
- Event listener reference
- Database repository patterns

---

## Support and Resources

- **Plugin Documentation**: `plugins/BarterShops/docs/`
- **GitHub Repository**: [fourz/BarterShops](https://github.com/fourz/BarterShops)
- **Issue Tracker**: Report bugs and feature requests on GitHub
- **Community Discord**: [Ravenkraft Network](https://discord.gg/ravenkraft) (if available)

---

**For player documentation, see [USER_GUIDE.md](USER_GUIDE.md)**
**For developer documentation, see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)**
