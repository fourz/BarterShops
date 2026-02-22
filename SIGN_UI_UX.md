# BarterShops Sign UI/UX Guide

**Last Updated**: February 13, 2026
**Status**: Complete (Phases 8-13 implemented)
**Minecraft Version**: Paper 1.20+

## Table of Contents

1. [Overview](#overview)
2. [Sign Modes](#sign-modes)
3. [Mode Rotation and Cycling](#mode-rotation-and-cycling)
4. [Owner Interactions](#owner-interactions)
5. [Customer Interactions](#customer-interactions)
6. [Payment Pagination](#payment-pagination)
7. [Owner Preview Mode](#owner-preview-mode)
8. [Item Name Wrapping](#item-name-wrapping)
9. [Display Behavior Matrix](#display-behavior-matrix)
10. [Known Limitations](#known-limitations)

---

## Overview

BarterShops uses signs as the primary interface for player-to-player trading. Players place a sign with `[barter]` on line 1 above a chest to create a trading shop. The sign acts as both a configuration interface for the owner and a trading interface for customers.

### Sign Location
- **Wall Sign**: Behind the chest (1 block back from chest)
- **Standing Sign**: Below the chest

### Owner vs Customer View
The sign displays different information based on who's looking:
- **Owner**: Configuration summary, payment options count, and settings
- **Customer**: Offering and payment details, pagination indicators

---

## Sign Modes

The sign cycles through 5 operational modes:

### 1. SETUP Mode
**Purpose**: Configure shop offering and payment options

**Display**:
```
§5[Setup]
§b(Configuration prompt)
§7Item: name (stackable)
§7Ready to configure
```

**Owner Actions**:
- **Left-click with item**: Set offering item (stackable or unstackable)
- **Left-click with currency/items**: Configure payment options (BARTER) or price (BUY/SELL)
- **Shift+Left-click**: Remove payment option (BARTER only)
- **Right-click**: Advance to next mode (TYPE)

**Duration**: Until owner right-clicks or no interaction for 10 seconds

---

### 2. TYPE Mode
**Purpose**: Select shop type (BARTER, BUY, or SELL)

**Display**:
```
§e[Shop Type]
§7Type: BARTER
§7(Left-click to cycle)
§7Types: BARTER/BUY/SELL
```

**Owner Actions**:
- **Left-click**: Cycle through shop types
- **Right-click**: Advance to BOARD mode

**Shop Types**:
- **BARTER**: Item-for-item trades (no currency required)
- **BUY**: Owner buys items from players (requires Vault/economy)
- **SELL**: Owner sells items to players (requires Vault/economy)

---

### 3. BOARD Mode
**Purpose**: Active shop state where customers can trade

**Display** (Owner View):
```
§2[Barter]
§b10x Diamond
§73 payment options
§7(Sneak+R: preview)
```

**Display** (Customer View - Page 1/4):
```
§2[Barter]
§b10x Diamond
§7for: 5x Gold /
§7Ingot (1/4)
```

**Owner Actions**:
- **Left-click with offering item**: Adjust quantity (+1)
- **Shift+Left-click with offering item**: Adjust quantity (-1, minimum 1)
- **Sneak+Right-click**: Toggle preview mode (see customer view)
- **Right-click**: Advance to DELETE mode

**Customer Actions**:
- **Left-click with correct payment**: Initiate trade
- **Right-click**: Browse next payment option (BARTER only)
- **Chat feedback**: "Payment option 2/4: 3x Emerald"

---

### 4. DELETE Mode
**Purpose**: Confirm shop deletion

**Display**:
```
§c[Delete?]
§cClick to confirm
§c(waiting...)
§c(5 second timer)
```

**Owner Actions**:
- **Left-click**: Start deletion timer (5 seconds)
- **Left-click again**: Confirm deletion (within 5 seconds)
- **Wait 10 seconds**: Auto-revert to BOARD if inactive

**Result**:
- Sign is destroyed
- Chest and items remain intact
- Shop data removed from database

---

### 5. HELP Mode
**Purpose**: Display usage information

**Display**:
```
§b[Help]
§7Right-click: Toggle mode
§7Sneak+R: Preview customer
§7Delete: Enter delete mode
```

**Owner Actions**:
- **Right-click**: Return to BOARD mode

---

## Mode Rotation and Cycling

### Full Cycle (Owner Right-Click)
```
SETUP → TYPE → BOARD → DELETE → SETUP
  ↑                                  ↓
  └──────────────────────────────────┘
```

### Reset on Inactivity
- **Timeout**: 10 seconds of inactivity in any non-BOARD mode
- **Behavior**: Auto-revert to BOARD
- **Exception**: DELETE mode timer (5 second confirmation window)

### Navigation
```
Right-click: Cycle forward to next mode
      ↓
Next Mode = (Current Mode → getNextMode())
      ↓
Reset inactivity timer
Reschedule 10s revert task
```

---

## Owner Interactions

### Configuration Flow

#### Step 1: Enter SETUP Mode
1. Place sign with `[barter]` on line 1 above chest
2. Sign automatically enters SETUP mode
3. Display shows configuration prompt

#### Step 2: Configure Offering
**In SETUP Mode**:
1. Hold offering item in hand
2. Left-click sign
3. System determines stackable/unstackable based on first item
4. Sign confirms: "§7Item: Qx ItemName"

#### Step 3: Configure Payments (BARTER) or Price (BUY/SELL)
**For BARTER Shops** (Step 2b in SETUP):
1. Hold payment item in hand
2. Left-click sign to add payment option
3. Repeat for multiple payment options
4. Shift+Left-click to remove payment option
5. Sign shows: "§73 payment options"

**For BUY/SELL Shops** (Step 2b in SETUP):
1. Hold currency item in hand
2. Left-click sign to set price
3. Left-click again to adjust amount (+1)
4. Shift+Left-click to decrease (-1, minimum 1)

#### Step 4: Activate Shop (BOARD Mode)
1. Right-click sign in SETUP to advance to TYPE
2. Right-click sign in TYPE to advance to BOARD
3. Shop is now active and customers can trade
4. Owner sees: payment count summary
5. Customers see: offering item and first payment option

### Quantity Adjustment (BOARD Mode)
While shop is active:
1. Hold offering item in hand
2. **Left-click**: +1 quantity
3. **Shift+Left-click**: -1 quantity (minimum 1)
4. Sign updates in real-time

### Preview Mode (BOARD Mode)
To see what customers see:
1. **Sneak+Right-click** (crouch + right-click)
2. Sign switches to customer view showing pagination
3. Right-click to browse payment options
4. **Sneak+Right-click again** to exit preview

### Deletion
1. Right-click sign multiple times to reach DELETE mode
2. Left-click once: Start 5-second confirmation timer
3. Left-click again within 5 seconds: Confirm deletion
4. Sign destroyed, chest and items remain

---

## Customer Interactions

### Browsing Offerings (Phase 8)
**Single Payment Shops**:
```
Sign shows: Offering + Payment (no pagination)
Pages: None (all info on one view)
```

**Multiple Payment Shops**:
```
Page 1 (Summary): "3 payment options"
Pages 2-4: Individual payment options
Right-click: Browse next page
Chat feedback: "Payment option 2/4: 3x Emerald"
```

### Trading Flow
1. **Browse** offerings and payment options (right-click for pagination)
2. **Hold correct payment item** in hand
3. **Left-click sign** to initiate trade
4. **Confirm** in trade GUI
5. Items exchanged atomically

### Survival Mode Requirement
- Only survival mode players can trade
- Creative mode players see full details but can't trade

---

## Payment Pagination

### System (Phase 8.5)
- **1 payment per page** for maximum readability
- **Summary page** shows payment option count
- **Total pages** = 1 (summary) + N (payments)

### Example: 3 Payment Options
```
Page 1: "3 payment options"
Page 2: "for: 5x Gold Ingot (2/3)"
Page 3: "for: 64x Iron Ore (2/3)"
Page 4: "for: 22x Rotten Flesh (3/3)"
```

### Pagination Controls
- **Right-click**: Advance to next page (wraps at end)
- **Left-click with payment item**: Initiate trade with current page's payment
- **Chat feedback**: Shows page number and payment option

---

## Owner Preview Mode

### Activation (Phase 8)
**In BOARD mode**:
```
Owner: Sneak+Right-click sign
Chat: "[Preview] Customer view enabled"
Sign: Switches to customer pagination view
```

### What Owner Sees
- Offering item as customers see it
- Current payment page with pagination indicator
- Right-click to cycle through payment pages
- See exactly what customers are browsing

### Deactivation
**Sneak+Right-click again**:
```
Chat: "[Preview] Customer view disabled"
Sign: Returns to owner summary view
```

### Use Cases
- Verify payment options are configured correctly
- Check item name wrapping for readability
- Ensure offering quantity is set properly

---

## Item Name Wrapping

### Phase 9: Offering Wrapping
**Problem**: Long offering names cramped on single line
**Solution**: Wrap names > 15 chars across lines 1-2

**Example - Short Offering** (≤15 chars):
```
§2[Barter]
§b2x Diamond
§7for: 5x Gold /
§7Ingot
```

**Example - Long Offering** (>15 chars):
```
§2[Barter]
§b2x Enchanted
§7Golden Apple
§7for: 5x Diamond
```

### Phase 8.5: Payment Wrapping
**Problem**: Long payment names wrapped awkwardly
**Solution**: 1 payment per page, wrap names if needed

**Example - Long Payment** (>15 chars):
```
§2[Barter]
§b2x Diamond
§7for: 22x Rotten
§7Flesh (2/3)
```

### Phases 10-13: Dual-Wrap Mode
**Problem**: Both offering AND payment long → truncation
**Solution**: Remove [Barter] header when both items > 15 chars

**Example - Short Offering + Short Payment** (unchanged):
```
§2[Barter]
§b2x Diamond
§7for: 5x /
§7Gold Ingot
```

**Example - Long Offering + Long Payment** (NEW - dual-wrap):
```
§b2x Enchanted
§7Golden Apple
§7for: 22x Rotten
§7Flesh
```
*(No [Barter] header, all 4 lines used)*

### Wrapping Algorithm
**When name > 15 characters**:
1. Find last space before position 15
2. If no space found: split at position 15
3. Line 1: Characters before split
4. Line 2: Characters after split (trimmed)

---

## Display Behavior Matrix

### Single Payment BARTER Shops

| Offering Length | Payment Length | Header? | Display |
|---|---|---|---|
| ≤15 | ≤15 | ✅ Yes | Header + 1-line offering + 2-line payment |
| ≤15 | >15 | ✅ Yes | Header + 1-line offering + 2-line payment (wrapped) |
| >15 | ≤15 | ✅ Yes | Header + 2-line offering (wrapped) + 1-line payment (condensed) |
| >15 | >15 | ❌ **No** | **Dual-wrap mode**: 2-line offering + 2-line payment (no header) |

### Multiple Payment BARTER Shops
- Always shows header: "§2[Barter]"
- Owner view: "N payment options"
- Customer view (page 1): "N payment / options"
- Pagination: 1 payment per page
- Right-click to browse

### BUY/SELL Shops
- Single price display (no pagination)
- Standard wrapping applied to price item
- Owner can adjust price in SETUP mode

---

## Known Limitations

### Active Issues
1. **bug-30**: Chest break destruction - needs investigation
   - Expected: Chest break prevented, shop preserved
   - Actual: Chest break prevented, but shop deleted from database
   - Workaround: Use DELETE mode to remove shops, not chest breaking

2. **bug-34**: Customer preview mode not showing pagination
   - Expected: Owner toggles preview, sees customer pagination view
   - Actual: Shows owner summary view instead
   - Workaround: Check payment options manually in TYPE mode

3. **bug-32/33**: Auto-revert scheduler (FIXED - regression testing pending)
   - Status: Fixed in commit 89f2e23
   - Verification: Test 10-second inactivity timeout in all modes

### Planned Enhancements
1. **Database persistence for quantities**: Currently in-memory only
2. **Shop recovery/undo**: Recover deleted shops within X minutes
3. **Configuration migration**: Easier shop template creation
4. **Advanced tab completion**: Live search for entity names

---

## Testing Checklist

### For Developers
- [ ] Owner can configure shop through all modes
- [ ] Payment pagination works for 3+ payments
- [ ] Item name wrapping works for names > 15 chars
- [ ] Dual-wrap mode removes header when both items long
- [ ] Auto-revert to BOARD after 10s inactivity (all modes)
- [ ] Preview mode shows customer view correctly
- [ ] Customers can browse and trade
- [ ] Creative mode prevents trading
- [ ] Quantity adjustment works in BOARD mode
- [ ] DELETE mode requires 5-second confirmation

### For Players
- [ ] Sign text is readable (no cramping)
- [ ] Payment options browsable via right-click
- [ ] Offering and payment items clear and distinct
- [ ] Chat feedback helpful when browsing
- [ ] Trades execute reliably

---

## Architecture References

### Implementation Files
- **SignDisplay.java**: Rendering logic with router pattern
- **SignInteraction.java**: Player interaction handling
- **BarterSign.java**: Data model (session-only pagination state)
- **ShopMode.java**: Mode enumeration and rotation logic

### Key Methods
- `displayCustomerPaymentPage()`: Customer view with pagination
- `displayOwnerBoardView()`: Owner view with summary
- `displayOfferingWithWrapping()`: Handle long offering names
- `displayPaymentWithWrapping()`: Handle long payment names
- `displayDualWrapMode()`: Conditional header removal for dual-wrap

### Design Patterns
- **Router Pattern**: BOARD mode display delegates to customer/owner views
- **Session-Only State**: Pagination fields don't persist to database
- **Wrapping Algorithm**: Consistent 15-char threshold across item types
- **Conditional Header Removal**: Only when both items exceed threshold

---

## FAQ

**Q: Why does the sign show 4 lines of text?**
A: Minecraft signs have 4 lines (0-3). Line 0 contains the [Barter] header or offering (in dual-wrap mode).

**Q: Can I set multiple payment options for BARTER shops?**
A: Yes! Left-click in SETUP mode with each payment item. Right-click to browse pages. Each payment option is shown on a separate page.

**Q: What happens if I break the chest?**
A: The chest break is prevented with an error message. Use the DELETE mode on the sign to properly remove the shop.

**Q: How do I adjust offering quantity?**
A: Hold the offering item in BOARD mode and left-click (+1) or shift-left-click (-1). Minimum 1 item.

**Q: Can customers see what other players offered?**
A: No. Each customer sees the current offering and can browse payment options. The system tracks individual trades, not player exchanges.

**Q: What's the difference between BARTER and BUY/SELL?**
A: BARTER requires no economy plugin (item-for-item only). BUY/SELL require Vault API for currency exchange.

---

## Version History

| Phase | Date | Feature | Status |
|---|---|---|---|
| Phase 1-5 | Feb 9-10 | Core setup flow, type system, validation | Complete |
| Phase 6 | Feb 11 | Multi-step SETUP, multiple payments | Complete |
| Phase 7 | Feb 11 | Database persistence for configs | Complete |
| Phase 8 | Feb 12 | Customer pagination + preview mode | Complete |
| Phase 8.5 | Feb 12 | 1 payment per page with wrapping | Complete |
| Phase 9 | Feb 13 | Offering name wrapping | Complete |
| Phases 10-13 | Feb 13 | Dual-wrap mode for long names | Complete |

---

**Last Updated**: February 13, 2026
**Current Version**: 1.0.1
**Deployment Status**: Active on RVNK Dev
