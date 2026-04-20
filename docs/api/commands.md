# BarterShops Command Reference

**Version**: 1.1.27
**Last Updated**: 2026-04-11

---

## Overview

All BarterShops commands use the `/shop` base command.

**Aliases**: `/barter`, `/shops`

**Help**: `/shop help` or `/shop ?` — shows available subcommands based on sender permissions (no permission check).

**Console**: All commands support console execution except where noted.

---

## Quick Reference

### Always-Available Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/shop create <name>` | Create a new shop at a targeted sign | `bartershops.create` |
| `/shop list [player] [page]` | List all shops or a player's shops | `bartershops.use` |
| `/shop info <id\|username>` | Show shop details | `bartershops.use` |
| `/shop history <id> [page]` | Paginated trade history | `bartershops.use` |
| `/shop remove <id> [--confirm]` | Remove a shop | `bartershops.create` |
| `/shop nearby [radius]` | Find nearby shops | `bartershops.use` |
| `/shop template <action>` | Manage shop templates | `bartershops.create` |
| `/shop notifications [on\|off\|toggle <type>]` | Manage notification preferences | `bartershops.use` |
| `/shop fee [list]` | View listing fee schedule | `bartershops.economy.fee` |
| `/shop tax [info\|calculate]` | View/calculate trade taxes | `bartershops.economy.tax` |
| `/shop region [status\|info]` | View region protection status | `bartershops.region.*` |
| `/shop admin <action>` | Admin operations dispatcher | `bartershops.admin` |
| `/shop admingui` | Open admin GUI | `bartershops.admin.gui` |
| `/shop inspect <id>` | Full shop inspection (admin) | `bartershops.admin.inspect` |
| `/shop clear <id>` | Clear shop chest inventory | `bartershops.admin.clear` |
| `/shop reload` | Reload configuration | `bartershops.admin.reload` |
| `/shop debug` | Debug diagnostics | `bartershops.admin.debug` |
| `/shop trade <player> <id> [qty]` | Admin force-trade | `bartershops.admin` |

### Conditional Commands

These commands are only registered when their required service is available. They will not appear in `/shop help` if the service is not loaded.

| Command | Requires | Permission |
|---------|----------|------------|
| `/shop rate <id> <1-5> [review]` | `IRatingService` | `bartershops.use` |
| `/shop reviews <id> [page]` | `IRatingService` | `bartershops.use` |
| `/shop stats [player\|server]` | `IStatsService` | `bartershops.stats` |
| `/shop group <action>` | `IShopGroupService` | `bartershops.group.*` |
| `/shop share <id> <player>` | `IShopGroupService` | `bartershops.create` |
| `/shop unshare <id> <player>` | `IShopGroupService` | `bartershops.create` |
| `/shop shared [page]` | `IShopGroupService` | `bartershops.use` |

---

## Player Commands

### /shop create

Creates a new shop. The sender must be looking at a sign within 5 blocks. The sign must have an attached chest.

**Usage**: `/shop create <name>`

**Parameters**:
- `name` — Shop display name (required; multi-word allowed)

**Permission**: `bartershops.create`

**Player-only**: Yes

**Notes**:
- Attach a chest to the sign before running this command (wall sign → block behind; standing sign → block below)
- After creation the shop enters SETUP mode — see the [Sign UI Guide](../SIGN_UI_UX.md) for configuration steps
- Economy listing fee may be charged if Vault is configured

---

### /shop list

Lists shops in a paginated table. Without arguments shows all shops server-wide.

**Usage**: `/shop list [player] [page]`

**Parameters**:
- `player` — Filter to a specific player's shops (optional)
- `page` — Page number (optional; default 1; 10 shops per page)

**Permission**: `bartershops.use`

**Console**: Yes

---

### /shop info

Shows detailed information about a shop — owner, type, mode, location, offerings, payment options, stock levels.

**Usage**: `/shop info <id|username>`

**Parameters**:
- `id` — Numeric shop ID or shop ID string (required)
- `username` — Owner username (returns first shop found for that player)

**Permission**: `bartershops.use`

**Console**: Yes

**Notes**: Falls back to database lookup if the shop's chunk is not loaded.

---

### /shop history

Shows paginated trade history for a shop — buyer/seller names, items exchanged, timestamps.

**Usage**: `/shop history <shopId> [page]`

**Parameters**:
- `shopId` — Numeric shop ID (required)
- `page` — Page number (optional; default 1; 5 trades per page)

**Permission**: `bartershops.use`

**Console**: Yes

---

### /shop remove

Removes a shop. Requires `--confirm` to execute the deletion (prevents accidental removal).

**Usage**: `/shop remove <id> [--confirm]`

**Parameters**:
- `id` — Shop ID or coordinates `x,y,z` (required)
- `--confirm` — Required flag for final deletion

**Permission**: `bartershops.create`

**Console**: Yes

**Notes**: Only the shop owner can remove their shop unless the sender has `bartershops.admin`. Chest and items are preserved.

---

### /shop nearby

Finds shops within a radius of the player's location, sorted by distance.

**Usage**: `/shop nearby [radius]`

**Parameters**:
- `radius` — Search radius in blocks (optional; default 50; max 200)

**Permission**: `bartershops.use`

**Player-only**: Yes

---

### /shop template

Manages reusable shop templates — save a shop configuration and reload it to create identical shops quickly.

**Usage**: `/shop template <action> [args]`

**Permission**: `bartershops.create`

**Console**: Yes (except `load` which requires being near a sign)

**Actions**:

| Action | Usage | Description |
|--------|-------|-------------|
| `save` | `/shop template save <name> [desc]` | Save current shop as template |
| `load` | `/shop template load <name>` | Apply template to targeted sign |
| `list` | `/shop template list [filter]` | List templates (filter by category, tag, or player) |
| `info` | `/shop template info <name>` | Show template details |
| `delete` | `/shop template delete <name>` | Delete a template |

---

### /shop notifications

Manages per-player notification preferences for shop events.

**Usage**: `/shop notifications [on|off|toggle <type>]`

**Parameters**:
- No args — Show current preferences
- `on` / `off` — Enable or disable all notifications
- `toggle <type>` — Toggle a specific notification type

**Permission**: `bartershops.use`

**Player-only**: Yes

**Notification types**: `SHOP_STOCK_LOW`, `SHOP_SALE`, `REVIEW_RECEIVED`, `PRICE_CHANGE`, `SYSTEM`

**Channels**: `CHAT`, `ACTION_BAR`, `TITLE`, `SOUND`

---

### /shop fee

Shows the listing fee schedule. Requires Vault; gracefully shows "no economy configured" if Vault is absent.

**Usage**: `/shop fee [list]`

**Permission**: `bartershops.economy.fee`

**Console**: Yes

---

### /shop tax

Views or calculates percentage-based trade taxes.

**Usage**: `/shop tax [info|calculate]`

**Permission**: `bartershops.economy.tax`

**Console**: Yes

---

### /shop region

Shows region protection status for a shop location — whether WorldGuard or GriefPrevention is protecting the shop area.

**Usage**: `/shop region [status|info]`

**Permission**: `bartershops.region.*`

**Console**: Yes

---

### /shop rate

Rates a shop 1–5 stars with an optional text review. Cannot rate your own shop.

**Usage**: `/shop rate <shopId> <1-5> [review]`

**Parameters**:
- `shopId` — Numeric shop ID (required)
- `1-5` — Star rating (required)
- `review` — Review text (optional; max 200 characters)

**Permission**: `bartershops.use`

**Player-only**: Yes

**Conditional**: Only available when `IRatingService` is loaded.

---

### /shop reviews

Lists all reviews for a shop with ratings and reviewer names.

**Usage**: `/shop reviews <shopId> [page]`

**Parameters**:
- `shopId` — Numeric shop ID (required)
- `page` — Page number (optional; default 1)

**Permission**: `bartershops.use`

**Console**: Yes

**Conditional**: Only available when `IRatingService` is loaded.

---

### /shop stats

Shows server-wide or per-player statistics: shops owned, trades completed, items traded, leaderboards.

**Usage**: `/shop stats [player|server]`

**Parameters**:
- No args / `server` — Server-wide statistics
- `player` — Statistics for the sender (or named player from console)

**Permission**: `bartershops.stats`

**Console**: Yes

**Conditional**: Only available when `IStatsService` is loaded.

---

### /shop group

Manages shop groups for multi-shop organization and shared ownership.

**Usage**: `/shop group <action> [args]`

**Permission**: `bartershops.group.*`

**Console**: Yes

**Conditional**: Only available when `IShopGroupService` is loaded.

**Actions**:

| Action | Usage | Permission | Description |
|--------|-------|------------|-------------|
| `list` | `/shop group list` | `bartershops.group.list` | List all groups |
| `info` | `/shop group info <id>` | `bartershops.group.info` | Show group details |
| `rename` | `/shop group rename <id> <name>` | `bartershops.group.rename` | Rename a group |
| `add` | `/shop group add <shop> <group>` | `bartershops.group.manage` | Add shop to group |
| `remove` | `/shop group remove <shop> <group>` | `bartershops.group.manage` | Remove shop from group |
| `transfer` | `/shop group transfer <group> <player>` | `bartershops.group.transfer` | Transfer group ownership |
| `create` | `/shop group create <name>` | `bartershops.group.manage` | Create a new group |
| `delete` | `/shop group delete <id>` | `bartershops.group.manage` | Delete a group |

---

### /shop share

Shares a shop with another player (grants co-owner access via group).

**Usage**: `/shop share <shopId> <player>`

**Permission**: `bartershops.create`

**Player-only**: Yes

**Conditional**: Only available when `IShopGroupService` is loaded.

---

### /shop unshare

Removes shared access from another player.

**Usage**: `/shop unshare <shopId> <player>`

**Permission**: `bartershops.create`

**Player-only**: Yes

**Conditional**: Only available when `IShopGroupService` is loaded.

---

### /shop shared

Lists shops that have been shared with the sender (not owned, but accessible).

**Usage**: `/shop shared [page]`

**Permission**: `bartershops.use`

**Player-only**: Yes

**Conditional**: Only available when `IShopGroupService` is loaded.

---

## Admin Commands

### /shop admin

Dispatcher for admin operations.

**Usage**: `/shop admin <action>`

**Permission**: `bartershops.admin`

**Console**: Yes

**Actions**:

| Action | Usage | Description |
|--------|-------|-------------|
| `reload` | `/shop admin reload` | Reload config and re-hydrate signs from database |
| `debug [on\|off]` | `/shop admin debug on` | Toggle debug logging |
| `stats` | `/shop admin stats` | Show plugin stats (shop count, active sessions, memory) |
| `cleanup` | `/shop admin cleanup` | List orphaned shops (DB record, no sign in loaded chunk) |
| `cleanup confirm` | `/shop admin cleanup confirm` | Deactivate all orphaned shops |
| `seed <action>` | `/shop admin seed standard` | Seed test data (see actions below) |

**Seed actions**: `minimal`, `standard`, `stress`, `cleanup`, `status`

---

### /shop admingui

Opens the admin shop management GUI — an inventory-based interface for inspecting and modifying shops.

**Usage**: `/shop admingui`

**Permission**: `bartershops.admin.gui`

**Player-only**: Yes

---

### /shop inspect

Admin-only full inspection of any shop — mode, type, offerings, payment options, stock, permissions.

**Usage**: `/shop inspect <id>`

**Permission**: `bartershops.admin.inspect`

**Console**: Yes

---

### /shop clear

Empties a shop's chest inventory without deleting the shop.

**Usage**: `/shop clear <id>`

**Permission**: `bartershops.admin.clear`

**Console**: Yes

---

### /shop reload

Reloads the configuration file and re-hydrates sign state from the database.

**Usage**: `/shop reload`

**Permission**: `bartershops.admin.reload`

**Console**: Yes

---

### /shop debug

Shows debug diagnostics: shop count, active sessions, memory usage, plugin version.

**Usage**: `/shop debug`

**Permission**: `bartershops.admin.debug`

**Console**: Yes

---

### /shop trade

Admin force-trade — executes a trade for a player without charging payment. Uses `ADMIN_OVERRIDE` as the trade source. Used for emergency situations, testing, and support resolution.

**Usage**: `/shop trade <player> <shopId> [qty]`

**Parameters**:
- `player` — Target online player (required)
- `shopId` — Shop ID to trade at (required)
- `qty` — Quantity (optional; must be a multiple of the shop's base offering quantity)

**Permission**: `bartershops.admin`

**Console**: Yes — this is a primary console-use command.

**Notes**: Bypasses all payment validation. Trade is logged with `ADMIN_OVERRIDE` source for audit purposes.

---

## Permission Nodes

### Player Permissions

| Permission | Purpose |
|-----------|---------|
| `bartershops.use` | Basic commands: `list`, `info`, `history`, `nearby`, `rate`, `reviews`, `notifications`, `shared` |
| `bartershops.create` | Shop creation, removal, templates, share/unshare |
| `bartershops.economy.fee` | View listing fees |
| `bartershops.economy.tax` | View trade taxes |
| `bartershops.stats` | View statistics (when IStatsService loaded) |

### Region Permissions

| Permission | Purpose |
|-----------|---------|
| `bartershops.region.*` | All region commands |
| `bartershops.region.status` | `/shop region status` |
| `bartershops.region.info` | `/shop region info` |

### Group Permissions

| Permission | Purpose |
|-----------|---------|
| `bartershops.group.*` | All group commands |
| `bartershops.group.list` | List groups |
| `bartershops.group.info` | View group details |
| `bartershops.group.rename` | Rename groups |
| `bartershops.group.manage` | Add/remove/create/delete groups |
| `bartershops.group.coowner` | Co-owner access |
| `bartershops.group.transfer` | Transfer group ownership |

### Admin Permissions

| Permission | Purpose |
|-----------|---------|
| `bartershops.admin` | All admin operations: `admin`, `trade` |
| `bartershops.admin.gui` | Admin GUI |
| `bartershops.admin.reload` | Reload configuration |
| `bartershops.admin.debug` | Debug information |
| `bartershops.admin.inspect` | Inspect any shop |
| `bartershops.admin.clear` | Clear any shop's chest |
| `bartershops.admin.trade` | Force-trade without payment |

---

## Console Support

Commands usable directly from the server console (no player context needed):

```
shop list
shop list Steve
shop info 42
shop history 42
shop remove 42 --confirm
shop inspect 42
shop clear 42
shop reload
shop debug
shop admin cleanup confirm
shop trade Steve 42 64
shop stats server
shop reviews 42
```

**Player-only** (no console support): `/shop create`, `/shop nearby`, `/shop admingui`, `/shop notifications`, `/shop rate`, `/shop share`, `/shop unshare`, `/shop shared`
