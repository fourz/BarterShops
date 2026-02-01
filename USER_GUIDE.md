# BarterShops User Guide

**Version**: 1.0-SNAPSHOT
**For**: Players and Shop Owners
**Last Updated**: February 2026

Welcome to BarterShops, a modern item-for-item trading system for Minecraft. This guide will help you create and manage your own barter shops.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Creating Your First Shop](#creating-your-first-shop)
3. [Shop Modes](#shop-modes)
4. [Trading at Shops](#trading-at-shops)
5. [Managing Your Shops](#managing-your-shops)
6. [Finding Shops](#finding-shops)
7. [Common Issues](#common-issues)

---

## Getting Started

### What is BarterShops?

BarterShops enables player-to-player trading without currency. You set up a shop by placing a chest and sign, then configure what items you want to trade. Other players can interact with your shop to exchange items.

**Key Features:**
- Item-for-item barter trading (no money required)
- Sign-based shop interfaces
- Chest-backed inventory storage
- Secure transaction validation
- Easy shop management commands

### Requirements

To create a barter shop, you need:
- Permission: `bartershops.create` (default: all players)
- Materials:
  - 1 chest (to store traded items)
  - 1 sign (any sign type: oak, birch, spruce, etc.)
  - Items to trade

---

## Creating Your First Shop

### Step 1: Place Your Chest

1. Choose a location for your shop
2. Place a chest on the ground
3. This chest will store items from trades

**Tip**: You can access the chest at any time to restock or collect items.

### Step 2: Create the Shop Sign

1. **Look at a sign** within 5 blocks of you
2. Run the command:
   ```
   /shop create <shop_name>
   ```
   Example: `/shop create Derek's Trading Post`

3. The sign will be converted to a shop sign

**Alternative Method** (Manual Sign Creation):
1. Place a sign near your chest
2. Write `[barter]` on the first line
3. The plugin will automatically convert it to a shop

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

---

## Shop Modes

Shop signs can display different modes based on their state:

### SETUP Mode

**Display:**
```
[Shop Name]
L-Click with
item you want
to sell
```

**When it appears**: When first creating a shop or reconfiguring trades

**What to do**: Left-click with the item you want to trade away

---

### BOARD Mode (Main Display)

**Display:**
```
Barter Shop
(right-click)
[Item Offering]
[Item Requesting]
```

**When it appears**: After shop is fully configured

**What to do**:
- Players: Right-click to initiate a trade
- Owners: Right-click to access shop management

---

### TYPE Mode

**Display:**
```
Shop Type:
BARTER
(or SELL/BUY/ADMIN)
```

**When it appears**: When configuring shop type

**What to do**: Select the shop type (BARTER is default)

**Shop Types**:
- **BARTER**: Item-for-item exchange (default)
- **SELL**: Players sell items to you for currency (requires economy plugin)
- **BUY**: Players buy items from you with currency (requires economy plugin)
- **ADMIN**: Server-managed shop with unlimited stock

---

### HELP Mode

**Display:**
```
BarterShops
Help & Info
Right-click
for details
```

**When it appears**: When accessing shop help

**What to do**: Right-click for usage instructions

---

### DELETE Mode

**Display:**
```
Delete Shop?
Left-click to
confirm removal
Right-click cancel
```

**When it appears**: When removing a shop

**What to do**:
- Left-click to confirm deletion
- Right-click to cancel

---

## Trading at Shops

### How to Trade

1. **Find a shop** (use `/shop nearby` to locate shops)
2. **Right-click the shop sign** to open the trade interface
3. **Review the trade**:
   - What the shop owner wants (input)
   - What you'll receive (output)
4. **Confirm the trade** in the GUI interface
5. Items are automatically exchanged

### Trade Requirements

To complete a trade, you must:
- Have the required items in your inventory
- Have enough inventory space to receive the items
- Be within interaction range of the shop

### Trade Validation

The plugin automatically checks:
- You have the exact items required
- The shop has the items to give you
- Both inventories have space for the exchange
- The shop is active and not in setup mode

---

## Managing Your Shops

### List Your Shops

View all shops you own:
```
/shop list
```

**Output**:
```
Your Shops (3):
#1: Derek's Trading Post - BARTER - [Active]
   Location: world: 123, 64, -456
#2: Diamond Exchange - BARTER - [Active]
   Location: world: 200, 70, 300
#3: Tool Shop - BARTER - [Inactive]
   Location: world_nether: -50, 64, 100
```

### View Shop Details

Get detailed information about a specific shop:
```
/shop info <shop_id>
```

Example:
```
/shop info 1
```

**Output**:
```
Shop #1: Derek's Trading Post
Type: BARTER
Owner: Derek
Status: Active
Location: world (123, 64, -456)
Created: 2026-01-15 14:30:00
Last Modified: 2026-02-01 10:15:00

Trade Configuration:
Offering: 64x Diamond
Requesting: 1x Netherite Ingot
```

### Remove a Shop

Delete one of your shops:
```
/shop remove <shop_id>
```

Example:
```
/shop remove 3
```

**What happens**:
1. Shop sign is removed
2. Shop data is deleted from database
3. Chest remains (items inside are preserved)
4. Confirmation message is displayed

**Warning**: This action cannot be undone. Make sure to empty your chest before removing the shop.

---

## Finding Shops

### Find Nearby Shops

Locate shops within a specific radius:
```
/shop nearby [radius]
```

**Default radius**: 50 blocks
**Maximum radius**: 200 blocks (configurable by admin)

**Examples**:
```
/shop nearby          # Search within 50 blocks
/shop nearby 100      # Search within 100 blocks
```

**Output**:
```
Nearby Shops (3 found within 50 blocks):

Shop #1: Derek's Trading Post (15 blocks away)
Owner: Derek
Direction: Northeast
Trade: 64x Diamond -> 1x Netherite Ingot

Shop #5: Emerald Exchange (32 blocks away)
Owner: Alice
Direction: South
Trade: 32x Emerald -> 1x Diamond Block

Shop #7: Food Market (45 blocks away)
Owner: Bob
Direction: West
Trade: 16x Cooked Beef -> 8x Golden Apple
```

### Browse All Shops

List all active shops on the server:
```
/shop list
```

**Note**: Without arguments, this lists YOUR shops. Admins can add flags to list all shops.

---

## Common Issues

### "You do not have permission to create this sign!"

**Cause**: Missing `bartershops.create` permission

**Solution**: Contact a server administrator to grant you permission

---

### "A shop already exists at this location"

**Cause**: Trying to create a shop on a sign that's already a shop

**Solution**:
1. Remove the existing shop first: `/shop remove <id>`
2. Or choose a different sign location

---

### "You must be looking at a sign to create a shop"

**Cause**: Not looking directly at a sign when running `/shop create`

**Solution**:
1. Stand within 5 blocks of a sign
2. Look directly at the sign (center your crosshair on it)
3. Run the command again

---

### "No shops found within X blocks"

**Cause**: No shops exist in your search radius

**Solution**:
- Try increasing the search radius: `/shop nearby 100`
- Explore a different area
- Create your own shop to start trading

---

### "Trade validation failed"

**Cause**: One or more trade requirements not met

**Common reasons**:
- You don't have the required items
- Your inventory is full
- The shop chest is empty or full
- Items don't match exactly (wrong quantity or type)

**Solution**:
- Verify you have the exact items needed
- Free up inventory space
- Contact the shop owner if the shop appears broken

---

### Shop sign shows "[Shop Name]" but no trade info

**Cause**: Shop is in SETUP mode (not fully configured)

**Solution**:
- If you're the owner: Complete the setup by left-clicking with items
- If you're a customer: The shop isn't ready yet - try another shop

---

## Tips & Best Practices

### For Shop Owners

1. **Stock Management**: Regularly check your shop chest to restock items
2. **Fair Trades**: Set reasonable exchange rates to attract customers
3. **Clear Names**: Use descriptive shop names so players know what you trade
4. **Accessible Locations**: Place shops in high-traffic areas
5. **Protection**: Use land claiming plugins to protect your shop from griefing

### For Traders

1. **Compare Prices**: Check multiple shops for the best deals
2. **Bulk Trading**: Some shops may offer better rates for larger quantities
3. **Build Relationships**: Regular trading with the same shops can lead to better deals
4. **Report Issues**: If a shop appears broken or exploited, report to admins

---

## Quick Command Reference

| Command | Description | Example |
|---------|-------------|---------|
| `/shop create <name>` | Create a shop at the sign you're looking at | `/shop create My Shop` |
| `/shop list` | List all your shops | `/shop list` |
| `/shop info <id>` | View details about a shop | `/shop info 1` |
| `/shop remove <id>` | Remove one of your shops | `/shop remove 1` |
| `/shop nearby [radius]` | Find shops near you | `/shop nearby 100` |

**Aliases**: You can also use `/barter` or `/shops` instead of `/shop`

---

## Getting Help

If you encounter issues not covered in this guide:

1. **In-game**: Right-click a shop sign in HELP mode
2. **Commands**: Contact server staff for assistance
3. **Server Forums**: Check your server's community forums or Discord
4. **Report Bugs**: Notify administrators of any technical issues

---

**Happy Trading!**

For administrator documentation, see [ADMIN_GUIDE.md](ADMIN_GUIDE.md)
For developer documentation, see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)
