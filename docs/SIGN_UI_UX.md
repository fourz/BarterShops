# BarterShops Sign UI/UX System

**Version**: 2.0 (Feb 2026)
**Scope**: Sign-based shop creation and configuration
**Based on**: BarterSignsPlus reference implementation with modern enhancements

---

## Overview

The sign UI/UX system provides an intuitive interface for shop owners to create and manage barter shops. The system uses a mode-based interaction pattern with left-click for configuration and right-click for mode cycling.

**Design Philosophy**:
- Modal state machine for clear user flow
- Type auto-detection from chest contents for smart defaults
- Type locking to prevent accidental reconfiguration
- Two distinct shop types (stackable vs non-stackable) for different use cases
- Real-time validation with user feedback

---

## Shop Types

### 1. Stackable Shops
- **When**: Owner places single item type that can stack (maxStackSize > 1)
- **What**: Trades multiples of the same item (e.g., 64 dirt)
- **Validation**: Only items of the locked Material type allowed in chest
- **Example**: Trading 64 dirt for 10 emeralds
- **Auto-Detection**: When first stackable item placed in chest

### 2. Non-Stackable Shops
- **When**: Owner places unstackable items (maxStackSize = 1)
- **What**: Trades individual unique items (e.g., one enchanted sword)
- **Validation**: Only unstackable items allowed in chest (supports mixed items)
- **Example**: Trading any enchanted sword for 5 diamonds
- **Auto-Detection**: When first unstackable item placed in chest

---

## Sign Modes (State Machine)

Sign displays different content and supports different interactions based on its current mode.

### Mode Cycle: SETUP → TYPE → BOARD → DELETE → SETUP

```
        RIGHT-CLICK (Owner)
┌───────────────────────────┐
│                           ▼
SETUP ───→ TYPE ───→ BOARD ───→ DELETE ───→ (back to SETUP)
 ▲                                              │
 └──────────────────────────────────────────────┘
```

### SETUP Mode

**Purpose**: Initial shop configuration
**Sign Display**:
```
[Setup]
L-Click to set
item for trade     (if no type detected yet)

OR

[Setup]
L-Click to set     (BARTER - if type locked)
item for trade

[Setup]
L-Click to set     (BUY - if type locked)
buy price

[Setup]
L-Click to set     (SELL - if type locked)
sell price
```

**Owner Interactions**:
- **Left-click with item in hand**: Configure shop offering
  - Stackable shop: Item type is locked, quantity set to item stack size
  - Non-stackable shop: Chest items are used, right-click to set price
- **Right-click**: Advance to TYPE mode

**How It Works**:
1. Owner places sign (enters SETUP automatically)
2. Owner places items in attached chest
3. Plugin auto-detects shop type from first item
4. Sign shows type-specific setup message
5. Owner configures offering via left-click or uses chest items

---

### TYPE Mode

**Purpose**: Change shop economy type (BARTER, BUY, SELL)
**Sign Display**:
```
[Type Mode]
L-Click to
cycle type:
BARTER
```

**Owner Interactions**:
- **Left-click**: Cycle through available SignTypes (BARTER → SELL → BUY → BARTER)
  - Always available, can be changed at any time
  - **BARTER**: No fees, item-for-item trading
  - **BUY**: Economy-based, player buys from shop with currency
  - **SELL**: Economy-based, player sells to shop for currency
- **Right-click**: Advance to BOARD mode

**Key Rules**:
- SignType (BARTER/BUY/SELL) can ALWAYS be cycled via left-click
- This is different from inventory type locking (STACKABLE vs UNSTACKABLE)
- Prevents accidental type changes breaking shop behavior

---

### BOARD Mode

**Purpose**: Active shop, ready for customer trades
**Sign Display**:
```
[Barter Shop]
64x dirt
for
10x emerald
```

**Owner Interactions**:
- **Left-click**: Show shop info (temporary message)
- **Right-click**: Advance to DELETE mode

**Customer Interactions**:
- **Right-click**: Initiate trade
  - Opens trade confirmation GUI
  - Validates item availability and player payment
  - Executes trade if confirmed

---

### DELETE Mode

**Purpose**: Safe deletion with confirmation
**Sign Display**:
```
[DELETE?]
L-Click to
delete shop
R-Click cancel
```

**Owner Interactions**:
- **Left-click (first)**: Request confirmation
  - Sign shows: "L-Click AGAIN to confirm (5s timeout)"
  - 5-second window to confirm
- **Left-click (second, within 5s)**: Delete shop
  - Chest remains, items stay in it
  - Shop removed from database
  - Type detection reset (can recreate with different type)
- **Right-click**: Return to SETUP mode (cancel deletion)

**Why Confirmation?**: Prevents accidental deletions from stray clicks

---

## Inventory Type Detection & Locking

Locks the **inventory type** (STACKABLE vs UNSTACKABLE) once detected from first item placed in chest.
Note: **SignType** (BARTER, BUY, SELL) can be cycled anytime in TYPE mode - it is NOT locked.

### Auto-Detection Flow

```
Sign Created (SETUP mode, no type)
         │
         ▼ (owner places item in chest)
Periodic validation task (every 5 ticks)
         │
         ├─→ First item found?
         │    │
         │    ├─→ YES: Check if stackable?
         │    │
         │    ├─→ Stackable → Lock as STACKABLE shop
         │    │              Store item.getType() as locked type
         │    │
         │    └─→ Unstackable → Lock as NON-STACKABLE shop
         │                      Any unstackable item allowed
         │
         └─→ Type LOCKED, cannot be changed
             Can only delete/recreate to change type
```

### Locked Type Enforcement

**Stackable Shop Validation** (every 5 ticks):
- Only items matching locked Material type allowed
- Rejects different item types
- Rejects unstackable items
- Returns rejected items to owner

Example: Locked to DIRT
- ✅ Accept: Dirt
- ❌ Reject: Stone (different type)
- ❌ Reject: Enchanted Sword (unstackable)

**Non-Stackable Shop Validation** (every 5 ticks):
- Only unstackable items allowed
- Rejects all stackable items
- Supports mixed unstackable items (e.g., different enchanted swords)
- Returns rejected items to owner

Example: Non-stackable shop
- ✅ Accept: Enchanted Sword #1
- ✅ Accept: Enchanted Sword #2 (different enchantments)
- ✅ Accept: Enchanted Bow
- ❌ Reject: Dirt (stackable)

---

## Sign Interaction Protocol

### Owner Left-Click (Configuration)

**SETUP Mode with no type detected**:
```
Owner holds item
└─→ Left-click sign
    └─→ Item type detected and locked
        └─→ Type-specific setup message shown
```

**SETUP Mode with type detected**:
```
No additional left-click behavior in SETUP
(type is already locked, just configure right-clicks)
```

**TYPE Mode**:
```
Owner left-clicks
└─→ If type NOT detected: Cycle to next type
└─→ If type detected: Show lock message, prevent cycling
```

### Owner Right-Click (Mode Cycling)

**Any mode**:
```
Owner right-clicks sign
└─→ Advance mode: SETUP → TYPE → BOARD → DELETE → SETUP
```

---

## Item Return System

When invalid items are placed in the chest, they are immediately detected and returned to the shop owner.

### Removal & Return Flow

1. **Detection** (every 5 ticks): Scan chest for invalid items
2. **Collection**: Gather all invalid items into a list
3. **Removal**: Remove from chest using proper inventory slot updates
4. **Return Phase**:
   - Try to add to owner's inventory if nearby (within 30 blocks)
   - If inventory full or owner not nearby: Drop at chest location
   - Send notification: "Returned Nx ITEM (reason)"

### Removal Reasons

**Stackable Shop**:
- "wrong item type" - Item Material doesn't match locked type
- "stackable items not allowed" - Unstackable items in non-stackable shop (shouldn't happen in stackable)

**Non-Stackable Shop**:
- "stackable items not allowed" - Stackable items rejected

---

## User Feedback System

### Sign Display Messages

Real-time feedback on sign itself showing:
- Current mode
- Available actions
- Shop type (when determined)
- Type lock status

### Chat Messages

Owners receive chat feedback for:
- Type auto-detection: "Shop type auto-set to [stackable/unstackable] (locked until deletion)"
- Type lock attempts: "✗ Shop type is locked! (Locked type: BARTER). Delete and recreate shop to change type"
- Item returns: "Returned 5x DIRT (wrong item type)"
- Mode transitions: Shown on sign (no spam)

---

## Design Decisions

### Why Type Auto-Detection?

- **Smart defaults**: Owner doesn't have to manually set type, plugin infers from first item
- **Less configuration**: Reduces steps to get shop operational
- **Prevents mistakes**: Type is visible and locked once detected
- **Clear intent**: What you put in chest determines what shop does

### Why Type Locking?

- **Prevents accidents**: Owner can't accidentally change type and break shop
- **State consistency**: Type matches validation rules for inventory
- **Immutability**: Simplifies state management (no type change → no validation rule update)
- **Clear recovery**: Need to delete to change? Explicit and understandable

### Why Two Shop Types?

- **Stackable**: For bulk item trading (64 dirt, 32 wood, 10 emeralds)
- **Non-stackable**: For unique/enchanted items (each sword is different)
- **Both serve real use cases**: Players trade both bulk and unique items
- **Clear validation rules**: Each type has clear rules for what's allowed

### Why Periodic Validation?

- **Reliable**: Event-based validation missed some interactions
- **Real-time**: Every 5 ticks = 250ms response time (imperceptible to players)
- **Simple**: No complex event handling, just scan and validate
- **Predictable**: Always runs, always works same way

---

## Implementation Details

### Key Components

| Component | File | Purpose |
|-----------|------|---------|
| Mode State Machine | `ShopMode.java` | Enum + getNextMode() for cycling |
| Sign Display | `SignDisplay.java` | Renders sign text based on mode |
| Interaction Handler | `SignInteraction.java` | Processes player clicks |
| Sign Manager | `SignManager.java` | Periodic validation, type detection |
| Sign Model | `BarterSign.java` | Stores mode, type, locked state |
| Type Detection | `BarterSign.detectAndSetTypeFromChest()` | Auto-detect and lock type |

### Critical Fields (BarterSign)

```java
private ShopMode mode;              // Current interaction mode
private SignType type;              // BARTER, SELL, or BUY
private boolean typeDetected;       // Flag: type is locked
private Material lockedItemType;    // For stackable shops: locked Material
private boolean isStackable;        // Stackable vs non-stackable
private ItemStack itemOffering;     // For stackable shops
private ItemStack priceItem;        // Trade payment item
private int priceAmount;            // Trade payment quantity
```

---

## Migration from BarterSignsPlus

This implementation is derived from BarterSignsPlus but with modern enhancements:

| Feature | BarterSignsPlus | BarterShops | Change |
|---------|-----------------|-------------|--------|
| Sign Trigger | [barter] | [barter] | Same |
| Modes | 6 modes | 5 modes (unified) | Simplified |
| Mode Cycling | Menu-based | Right-click cycle | Faster |
| Type System | 3 types | 3 types | Same (BARTER/SELL/BUY) |
| Auto-Detection | None | Full | Smart defaults |
| Type Locking | None | Full | Prevents accidents |
| Stackable/Unstackable | None | Full | Two distinct paths |
| Database | Flatfile YAML | SQLite/MySQL | Persistent |
| Validation | Event-based | Periodic task | Reliable |
| Item Return | Manual | Automatic | Better UX |

---

## Troubleshooting

### Cannot change inventory type (stackable ↔ unstackable)

**Problem**: Placed a stackable item but want non-stackable shop (or vice versa)
**Cause**: Inventory type was auto-detected when first item placed in chest
**Solution**: Delete shop (/shop remove <id>) and recreate with desired item type (stackable or unstackable)
**Note**: You CAN change SignType (BARTER ↔ BUY ↔ SELL) anytime via left-click in TYPE mode

### "Returned 5x DIAMOND (wrong item type)"

**Problem**: Wrong item type keeps appearing in chat
**Cause**: Player is placing wrong item type in stackable shop
**Solution**: Use the locked item type only, or recreate as non-stackable shop if mixing needed

### Items keep disappearing from chest

**Problem**: Items vanish when placed in locked shop
**Cause**: Items don't match locked type - being validated out
**Solution**: Check sign for locked type, only place matching items

### Sign shows blank after creation

**Problem**: Sign is empty
**Cause**: Display update hasn't run yet (1-2 tick delay)
**Solution**: Wait 1 second or right-click sign to force update

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 2.0 | Feb 2026 | Type detection, locking, item return, periodic validation |
| 1.0 | Jan 2026 | Basic sign system, mode cycling |

