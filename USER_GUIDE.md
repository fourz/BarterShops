# BarterShops User Guide

**Version**: 1.0-SNAPSHOT
**For**: Players and Shop Owners
**Last Updated**: February 1, 2026

Welcome to BarterShops, a modern item-for-item trading system for Minecraft. This guide will help you create and manage your own barter shops with the latest features including ratings, templates, statistics, and economy integration.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Creating Your First Shop](#creating-your-first-shop)
3. [Shop Modes and Types](#shop-modes-and-types)
4. [Trading at Shops](#trading-at-shops)
5. [Rating and Reviewing Shops](#rating-and-reviewing-shops)
6. [Shop Templates](#shop-templates)
7. [Viewing Statistics](#viewing-statistics)
8. [Economy Features](#economy-features)
9. [Region Protection](#region-protection)
10. [Managing Your Shops](#managing-your-shops)
11. [Notifications](#notifications)
12. [Commands Reference](#commands-reference)
13. [Troubleshooting](#troubleshooting)

---

## Getting Started

### What is BarterShops?

BarterShops enables player-to-player trading without currency (barter mode) or with economy integration for buy/sell shops. You set up a shop by placing a chest and sign, then configure what items you want to trade. Other players can interact with your shop to exchange items.

**Key Features:**
- Item-for-item barter trading (no money required)
- Economy integration with Vault (buy/sell shops)
- Sign-based shop interfaces
- Chest-backed inventory storage
- Trade ratings and reviews
- Shop templates for quick setup
- Comprehensive statistics tracking
- Region protection (WorldGuard/GriefPrevention)
- Web API for external integrations

### Requirements

To create a barter shop, you need:
- **Permission**: `bartershops.create` (default: all players)
- **Materials**:
  - 1 chest (to store traded items)
  - 1 sign (any sign type: oak, birch, spruce, etc.)
  - Items to trade
- **Optional**: Economy balance for listing fees (if enabled)

---

## Creating Your First Shop

### Step 1: Place Your Chest

1. Choose a location for your shop
2. Place a chest on the ground
3. This chest will store items from trades

**Tip**: Build in a protected region (WorldGuard/GriefPrevention) to prevent griefing.

### Step 2: Create the Shop Sign

**Method 1 - Command**:
1. Look at a sign within 5 blocks
2. Run: `/shop create <shop_name>`
3. Example: `/shop create Derek's Trading Post`

**Method 2 - Manual**:
1. Place a sign near your chest
2. Write `[barter]` on the first line
3. The plugin will automatically convert it

**Listing Fees**: If economy is enabled, you'll be charged a creation fee:
- Barter shops: 100 coins (default)
- Currency shops: 500 coins (default)
- Check fees with `/shop fee list`

### Step 3: Configure Your First Trade

After creating the shop, the sign will display:
```
[Shop Name]
L-Click with
item you want
to sell
```

1. **Hold the item** you want to trade away (what you're offering)
2. **Left-click the sign** while holding the item
3. The sign will update to show your item

### Step 4: Set the Trade Request

1. **Hold the item** you want to receive in exchange
2. **Left-click the sign** again
3. Your trade is now configured

### Step 5: Activate Your Shop

1. Right-click the sign to switch to Main Menu mode
2. Your shop is now open for business!

---

## Shop Modes and Types

### Shop Types

| Type | Description | Economy Required |
|------|-------------|------------------|
| **BARTER** | Item-for-item exchange | No |
| **SELL** | Players buy items with currency | Yes (Vault) |
| **BUY** | You buy items from players | Yes (Vault) |
| **ADMIN** | Special admin shops with unlimited stock | Admin only |

### Shop Modes

| Mode | Display | Purpose |
|------|---------|---------|
| **SETUP** | "L-Click with item you want to sell" | Configure trade items |
| **MAIN** | "Barter Shop (right-click)" | Customer trading interface |
| **TYPE** | "Shop Type: BARTER" | Change shop type |
| **HELP** | "BarterShops Help & Info" | Show usage instructions |
| **DELETE** | "Delete Shop? L-click confirm" | Confirm shop removal |

---

## Trading at Shops

### How to Trade

1. **Find a shop** using `/shop nearby [radius]`
2. **Right-click the shop sign** to open the trade interface
3. **Review the trade**:
   - What the shop owner wants (input)
   - What you'll receive (output)
   - Shop ratings and reviews
4. **Confirm the trade** in the GUI interface
5. Items are automatically exchanged

### Trade Requirements

To complete a trade, you must:
- Have the required items in your inventory
- Have enough inventory space to receive items
- Be within interaction range of the shop
- Meet economy requirements (if applicable)

### Trade Taxes

If economy features are enabled:
- Default tax rate: 5% of trade value
- View tax info: `/shop tax info`
- Calculate tax: `/shop tax calculate <amount>`
- Tax revenue can be configured by server admins

---

## Rating and Reviewing Shops

Help other players find quality shops by leaving ratings and reviews!

### Rate a Shop

```
/shop rate <shopId> <1-5> [review]
```

**Examples:**
```bash
/shop rate 42 5 Excellent shop, always stocked!
/shop rate 17 4 Good prices but slow to restock
/shop rate 8 3
```

**Rating Scale:**
- ★★★★★ (5) - Excellent
- ★★★★☆ (4) - Good
- ★★★☆☆ (3) - Average
- ★★☆☆☆ (2) - Below Average
- ★☆☆☆☆ (1) - Poor

**Notes:**
- You cannot rate your own shops
- Reviews are limited to 200 characters
- Console usage: `/shop rate <shopId> <rating> <playerName> [review]`

### View Shop Reviews

```
/shop reviews <shopId>
```

Displays:
- Average rating
- Individual ratings with review text
- Reviewer names and dates
- Star display for quick reference

---

## Shop Templates

Save time creating multiple shops with similar configurations!

### Save a Template

```
/shop template save <name> [description]
```

**Example:**
```bash
/shop template save DiamondShop Sells diamonds for emeralds
```

**Permission**: `bartershops.template.save`

### Load a Template

```
/shop template load <name>
```

Apply a saved template to create a new shop quickly.

**Permission**: `bartershops.template.load`

### List Templates

```
/shop template list                    # Your templates
/shop template list <player>           # Another player's templates
/shop template list category:<name>    # Filter by category
/shop template list tags:<tags>        # Filter by tags
```

### Template Management

```
/shop template info <name>     # View template details
/shop template delete <name>   # Delete a template
```

**Server Presets**: Admins can create server-wide templates with `bartershops.template.preset.create`.

---

## Viewing Statistics

Track your trading performance and see server-wide trends!

### Your Personal Stats

```
/shop stats
```

**Displays:**
- Shops owned
- Trades completed
- Items traded
- Average rating
- Most traded items (top 5)

**Example Output:**
```
===== YourName's Statistics =====

Shops Owned: 3
Trades Completed: 127
Items Traded: 2,458

Average Rating: ★★★★☆ (4.3/5.0)

Most Traded Items:
  1. Diamond x 245
  2. Emerald x 189
  3. Gold Ingot x 156
```

### View Another Player's Stats

```
/shop stats <player>
```

**Permission**: `bartershops.stats.admin` (for viewing other players)

### Server-Wide Statistics

```
/shop stats server
```

**Displays:**
- Total shops
- Active shops
- Total trades
- Items traded server-wide
- Top shops by trade count
- Most traded items
- Average trades per shop

**Permission**: `bartershops.stats.admin`

---

## Economy Features

BarterShops integrates with Vault for economy features.

### Listing Fees

When creating shops, you may be charged:
- **Barter shops**: Base fee (default: 100 coins)
- **Currency shops**: Higher fee (default: 500 coins)

**Commands:**
```
/shop fee list               # View current fee structure
```

### Trade Taxes

Currency-based trades are taxed:
- **Default rate**: 5% of trade value
- Tax is deducted from the seller's earnings

**Commands:**
```
/shop tax info                    # View tax information
/shop tax calculate <amount>      # Calculate tax for amount
```

**Example:**
```bash
/shop tax calculate 1000
# Output: Tax on 1000 coins: 50 coins (5%)
#         Seller receives: 950 coins
```

### Mixed Trades

Combine items and currency in trades:
- Minimum currency amount: 1.0 (configurable)
- Enable with `economy.mixed_trades.enabled: true`

---

## Region Protection

Protect your shops from griefing with region protection!

### Supported Plugins

- **WorldGuard**: Automatic region creation around shops
- **GriefPrevention**: Claim integration
- **Auto-protection**: Default 3-block radius

### Check Protection Status

```
/shop region status         # View protection system status
/shop region info           # Check protection at current location
```

**Example Output:**
```
===== Region Protection Status =====
Status: ENABLED
Provider: WorldGuard
Auto-protect radius: 3 blocks
Max shops per player: 5

Your current location:
Protected: YES
Region: shop-42-protection
Owner: PlayerName
```

**Permission**: `bartershops.region.status`, `bartershops.region.info`

---

## Managing Your Shops

### List Your Shops

```
/shop list
```

**Shows:**
- Shop ID
- Shop name
- Location (world, coordinates)
- Shop type
- Status (Active/Inactive)

### View Shop Details

```
/shop info <shopId>
```

**Displays:**
- Owner
- Location
- Shop type and mode
- Trade configuration
- Creation date
- Last modified
- Ratings and statistics

### Find Nearby Shops

```
/shop nearby [radius]
```

**Default radius**: 50 blocks (configurable by admin)

**Example:**
```bash
/shop nearby        # Search 50 blocks
/shop nearby 100    # Search 100 blocks
```

**Output includes:**
- Shop ID and name
- Distance and direction
- Owner
- Trade configuration
- Ratings

### Remove a Shop

```
/shop remove <shopId>
```

**What happens:**
1. Shop sign is removed
2. Shop data is deleted
3. Chest remains (items preserved)
4. Region protection is removed

**Warning**: This action cannot be undone. Empty your chest first!

---

## Notifications

BarterShops keeps you informed about your shop activity!

### Notification Types

- **Trade Request**: When someone wants to trade
- **Trade Complete**: When a trade succeeds
- **Trade Cancelled**: When a trade is cancelled
- **Shop Stock Low**: When inventory runs low (default threshold: 5 items)
- **Shop Sale**: When someone buys from your shop
- **Review Received**: When someone rates your shop
- **Price Change**: When shop prices are modified (disabled by default)
- **System**: Important system messages

### Notification Channels

- **Chat**: Messages in chat
- **Action Bar**: Notification above hotbar
- **Title**: Large screen notification
- **Sound**: Audio notification

### Manage Notifications

```
/shop notifications                  # View current settings
/shop notifications <channel> <on|off>
```

**Examples:**
```bash
/shop notifications chat on       # Enable chat notifications
/shop notifications actionBar off # Disable action bar
/shop notifications sound on      # Enable sounds
/shop notifications title off     # Disable title notifications
```

**Permission**: `bartershops.notifications`

---

## Commands Reference

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/shop create <name>` | Create a shop at sign you're looking at | `bartershops.create` |
| `/shop list` | List all your shops | `bartershops.list` |
| `/shop info <shopId>` | View shop details | Default |
| `/shop remove <shopId>` | Delete a shop | Default |
| `/shop nearby [radius]` | Find shops near you | `bartershops.nearby` |
| `/shop rate <shopId> <1-5> [review]` | Rate a shop | Default |
| `/shop reviews <shopId>` | View shop reviews | Default |
| `/shop stats [player\|server]` | View statistics | `bartershops.stats` |
| `/shop template <action> [args]` | Manage templates | `bartershops.template` |
| `/shop notifications <channel> <on\|off>` | Manage notifications | `bartershops.notifications` |
| `/shop fee list` | View listing fees | `bartershops.economy.fee` |
| `/shop tax info` | View tax information | `bartershops.economy.tax` |
| `/shop tax calculate <amount>` | Calculate tax | `bartershops.economy.tax` |
| `/shop region status` | Protection system status | `bartershops.region.status` |
| `/shop region info` | Check protection | `bartershops.region.info` |

### Command Aliases

- `/barter` - Alias for `/shop`
- `/shops` - Alias for `/shop`

---

## Troubleshooting

### Shop Creation Issues

**"You do not have permission to create this sign!"**
- Missing `bartershops.create` permission
- Contact a server administrator

**"A shop already exists at this location"**
- Sign is already a shop
- Use `/shop remove <id>` first or choose different sign

**"Insufficient funds for listing fee"**
- You need more money to create this shop type
- Check fees with `/shop fee list`

**"Maximum shops reached"**
- You've hit the shop creation limit
- Default: 5 shops per player (bypass with `bartershops.admin.unlimited`)

### Trading Issues

**"Trade validation failed"**
- You don't have required items
- Your inventory is full
- Shop chest is empty or full
- Items don't match exactly (wrong quantity/type)

**"You cannot rate your own shop"**
- You can only rate shops owned by other players

### Template Issues

**"Template not found"**
- Check template name spelling
- Use `/shop template list` to see available templates

**"Permission denied loading template"**
- Template is owned by another player
- Need `bartershops.template.load.other` permission

### Protection Issues

**"Cannot place shop here - region protected"**
- Location is protected by another plugin
- You don't have build permission in this region
- Move to a different location

---

## Tips & Best Practices

✅ **Stock Management**: Regularly check your shop chest to restock items

✅ **Fair Trades**: Set reasonable exchange rates to attract customers and get better ratings

✅ **Use Templates**: Save time creating multiple shops with similar configurations

✅ **Check Reviews**: Look at shop ratings before trading to find reliable shops

✅ **Protect Your Shops**: Build in protected regions to prevent griefing

✅ **Monitor Statistics**: Track which items are most popular and adjust inventory

✅ **Notification Setup**: Configure notifications to stay informed without spam

✅ **Economy Integration**: Use currency features for more flexible trading options

---

## Getting Help

If you encounter issues not covered in this guide:

1. **In-game**: Right-click a shop sign in HELP mode
2. **Commands**: Use `/shop help` for quick command reference
3. **Server Staff**: Contact administrators for assistance
4. **Documentation**: See [ADMIN_GUIDE.md](ADMIN_GUIDE.md) for configuration details
5. **Developers**: See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for API documentation

---

**Happy Trading!**

For administrator documentation, see [ADMIN_GUIDE.md](ADMIN_GUIDE.md)
For developer documentation, see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)
For API documentation, see [API_REFERENCE.md](API_REFERENCE.md)
