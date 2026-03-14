---
title: Sign Display API
plugin: BarterShops
category: api
tags: [sign, display, renderer, layout, ui]
created: 2026-03-03
updated: 2026-03-03
version: 1.0.29
---

# Sign Display API

Documents the public surface of the BarterShops sign rendering system: `SignDisplay`, `SignLayoutFactory`, `SignRenderUtil`, and `ISignModeRenderer`.

---

## Package Structure

```
org.fourz.BarterShops.sign
├── SignDisplay.java          — Dispatcher: routes render calls to mode renderers
├── BarterSign.java           — Data model; holds session-only UI state
├── SignType.java             — Enum: BARTER, BUY, SELL
├── SignMode.java             — Enum (interaction layer, separate from ShopMode)
├── factory/
│   └── SignLayoutFactory.java  — Legacy 4-line layout builders
└── renderer/
    ├── ISignModeRenderer.java  — Renderer interface
    ├── BoardModeRenderer.java  — BOARD mode (live trading)
    ├── SetupModeRenderer.java  — SETUP mode (shop configuration)
    ├── TypeModeRenderer.java   — TYPE mode (type selection)
    ├── DeleteModeRenderer.java — DELETE mode (deletion confirmation)
    ├── HelpModeRenderer.java   — HELP mode (owner instructions)
    └── SignRenderUtil.java     — Shared rendering helpers
```

---

## SignDisplay

**Package**: `org.fourz.BarterShops.sign`

Thin dispatcher that maps each `ShopMode` value to an `ISignModeRenderer` and delegates
all rendering work. Owns two ad-hoc helpers for ephemeral confirmation overlays.

```java
public class SignDisplay
```

### Static Fields

```java
private static final Map<ShopMode, ISignModeRenderer> RENDERERS;
// Populated at class load:
//   ShopMode.SETUP  → SetupModeRenderer
//   ShopMode.BOARD  → BoardModeRenderer
//   ShopMode.TYPE   → TypeModeRenderer
//   ShopMode.HELP   → HelpModeRenderer
//   ShopMode.DELETE → DeleteModeRenderer
```

### Static Methods

#### `updateSign(Sign, BarterSign, boolean)`

```java
public static void updateSign(Sign sign, BarterSign barterSign, boolean isCustomerView)
```

Primary render entry point. Reads `barterSign.getMode()` to select the renderer, then
calls `renderer.render(frontSide, barterSign, isCustomerView)` and commits with `sign.update()`.

| Parameter | Type | Description |
|-----------|------|-------------|
| `sign` | `Sign` | Bukkit sign block to update |
| `barterSign` | `BarterSign` | Sign data model |
| `isCustomerView` | `boolean` | `true` = render customer perspective; `false` = owner perspective |

**Side effect**: Sets `barterSign.signSideDisplayFront` to the retrieved `SignSide` before rendering.

---

#### `updateSign(Sign, BarterSign)`

```java
public static void updateSign(Sign sign, BarterSign barterSign)
```

Backward-compatible overload. Resolves `isCustomerView` automatically:

```java
boolean shouldShowCustomerView = barterSign.isOwnerPreviewMode();
updateSign(sign, barterSign, shouldShowCustomerView);
```

Use this overload for all standard refresh calls. Use the 3-parameter form when you need
to force a specific perspective (e.g., sending a preview snapshot to another player).

---

#### `displayTemporaryMessage(Sign, String, String)`

```java
public static void displayTemporaryMessage(Sign sign, String line1, String line2)
```

Writes a transient 2-line status message without modifying `BarterSign` state. The header
is always `§2[Barter Shop]`.

| Parameter | Type | Description |
|-----------|------|-------------|
| `sign` | `Sign` | Sign block to update |
| `line1` | `String` | Text for sign line 1 |
| `line2` | `String` | Text for sign line 2; `null` renders as empty string |

Lines 0 and 3 are always `"§2[Barter Shop]"` and `""` respectively.

---

#### `displayDeleteConfirmation(Sign)`

```java
public static void displayDeleteConfirmation(Sign sign)
```

Renders the 5-second deletion-confirmation overlay:

```
§c[CONFIRM?]
§cL-Click AGAIN
§cto confirm
§e(5s timeout)
```

Called by `SignInteraction` after the first DELETE-mode left-click. Sign state is NOT
changed — the next click within 5 seconds completes deletion.

---

#### `displayTypeConfirmation(Sign, SignType)`

```java
public static void displayTypeConfirmation(Sign sign, SignType nextType)
```

Renders the type-change confirmation overlay, replacing `CONFIRM?` text with the
target type name:

```
§e[CONFIRM?]
§eL-Click AGAIN
§eto <NEXTTYPE>
§e(5s timeout)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `sign` | `Sign` | Sign block to update |
| `nextType` | `SignType` | The type the shop will switch to on confirmation |

---

#### `formatItemName(ItemStack)` (package-private)

```java
static String formatItemName(ItemStack item)
```

Delegates to `SignRenderUtil.formatItemName(item)`. Kept for backward compatibility with
`SignInteraction` callers that imported `SignDisplay` directly.

---

## ISignModeRenderer

**Package**: `org.fourz.BarterShops.sign.renderer`

OCP-compliant interface: one implementation per `ShopMode`.

```java
public interface ISignModeRenderer {

    /**
     * Writes the sign text for this mode onto the given side.
     *
     * @param side           Sign side to populate (lines 0–3)
     * @param barterSign     Sign data model
     * @param isCustomerView true for customer perspective, false for owner
     */
    void render(SignSide side, BarterSign barterSign, boolean isCustomerView);
}
```

### Implementations

| Class | Mode | Notes |
|-------|------|-------|
| `SetupModeRenderer` | SETUP | Step 1 (set offering) vs step 2 (set payment/price) based on `barterSign.getItemOffering()` |
| `BoardModeRenderer` | BOARD | Delegates to customer or owner sub-renderers; handles pagination and dual-wrap |
| `TypeModeRenderer` | TYPE | Type cycling display; shows lock status if type is detected |
| `DeleteModeRenderer` | DELETE | Initial delete prompt (not the confirmation overlay) |
| `HelpModeRenderer` | HELP | Static owner-instructions text |

---

## BoardModeRenderer

**Package**: `org.fourz.BarterShops.sign.renderer`

The most complex renderer. Handles the live-trading state for all shop types and
both customer and owner perspectives.

### Routing Logic

```
render(side, barterSign, isCustomerView)
  ├── barterSign.isConfigured() == false  →  renderNotConfigured()
  ├── isCustomerView == true              →  renderCustomerPaymentPage()
  └── isCustomerView == false             →  renderOwnerBoardView()
```

### Customer Payment Page (`renderCustomerPaymentPage`)

Determines single-payment BARTER mode to decide header usage:

```java
boolean isSinglePaymentBarter = (type == SignType.BARTER && payments.size() == 1);
int offeringStartLine = isSinglePaymentBarter ? 0 : 1;
```

- **Header absent** (single-payment BARTER): offering starts at line 0, payment on lines 2-3
- **Header present** (multi-payment or BUY/SELL): header on line 0, offering on line 1

Delegates payment rendering to:
- `renderSinglePaymentCustomer()` — handles dual-wrap when both names overflow
- `renderPaginatedPayment()` — multi-payment: page layout with `§6page N of M` on line 3
- `renderBuySellPrice()` — shared with owner view; condensed or wrapped by `offeringWrapped`

### Paginated Payment Page Format

```
Line 0: §efor Qx
Line 1: §e<payment name or first wrap>
Line 2: §e<payment name continuation (or empty)>
Line 3: §6page N of M
```

Where `N = currentPaymentIndex + 1` (display page), `M = payments.size() + 1` (total pages = 1 summary + N payments).

### Owner Board View (`renderOwnerBoardView`)

When `barterSign.isOwnerPreviewMode() == true`:
- Compact: `§eCustomer View` on line 3 (if offering wrapped)
- Full: lines 2-3 show `§e[Customer View]` / `§eSneak+R: exit`

When not in preview:
- BARTER: payment count or single-payment details via `renderBarterOwnerLines()`
- BUY/SELL: price via `renderBuySellPrice()`

---

## SignRenderUtil

**Package**: `org.fourz.BarterShops.sign.renderer`

All methods are `public static`. Utility class — no instances.

### `getTypeHeader(SignType)`

```java
public static String getTypeHeader(SignType type)
```

Returns the coloured header string for a shop type:

| Input | Output |
|-------|--------|
| `BARTER` | `§2[Barter]` |
| `BUY` | `§e[We Buy]` |
| `SELL` | `§a[We Sell]` |

---

### `formatItemName(ItemStack)`

```java
public static String formatItemName(ItemStack item)
```

Resolves a display-friendly item name:

1. If `item == null` or `item.getType().isAir()`: returns `"None"`
2. If `item.hasItemMeta() && item.getItemMeta().hasDisplayName()`: returns the custom display name
3. Otherwise: title-cases the material enum (e.g. `DIAMOND_SWORD` → `"Diamond Sword"`)

---

### `computeNameSplit(String, int)`

```java
public static int computeNameSplit(String name, int prefixLength)
```

Computes the optimal word-break index for a name that follows a fixed-width prefix on a sign line.

```
available = MAX_LINE_LENGTH - prefixLength
splitIndex = name.lastIndexOf(' ', available)
if (splitIndex <= 0) return Math.min(available, name.length())
else return splitIndex
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | `String` | Item name to split |
| `prefixLength` | `int` | Characters consumed by the prefix (e.g. `"70x "` = 4) |

Returns an index into `name`: `[0..index)` stays on the current line, `[index..)` wraps to the next.

---

### `displayOfferingWithWrapping(SignSide, ItemStack, int)`

```java
public static boolean displayOfferingWithWrapping(SignSide side, ItemStack offering, int startLine)
```

Writes the offering item as `"§bQx Name"` with wrapping when the full line overflows `MAX_LINE_LENGTH`.

- Non-wrapped: `side.setLine(startLine, "§b" + prefix + itemName)`
- Wrapped: `side.setLine(startLine, prefix + first)`, `side.setLine(startLine+1, remainder)`

Returns `true` if the name was wrapped across two lines (callers use this to adjust remaining line positions).

**Overload** (defaults `startLine=1`):

```java
public static boolean displayOfferingWithWrapping(SignSide side, ItemStack offering)
```

---

### `displayPaymentWithWrapping(SignSide, ItemStack, int)`

```java
public static boolean displayPaymentWithWrapping(SignSide side, ItemStack payment, int startLine)
```

Writes the payment item as `"§efor: Qx Name"` with wrapping. Same algorithm as offering wrapping.
Prefix is `"for: " + amount + "x "`.

Returns `true` if the name was wrapped.

---

### `displayDualWrapMode(SignSide, ItemStack, ItemStack)`

```java
public static void displayDualWrapMode(SignSide side, ItemStack offering, ItemStack payment)
```

Header-free layout. Uses all 4 sign lines when both offering and payment names overflow.

```
Line 0: §b<offeringPrefix><offering name part 1>
Line 1: §b<offering name part 2>
Line 2: §e<paymentPrefix><payment name part 1>
Line 3: §e<payment name part 2>
```

Only called for **single-payment BARTER** shops when `offeringWrapped && paymentNeedsWrapping`.

---

### `applyLayoutToSign(SignSide, String[])`

```java
public static void applyLayoutToSign(SignSide side, String[] layout)
```

Writes a 4-element `String[]` to sign lines 0–3. `null` elements are written as `""`.
Primarily used by `SignLayoutFactory` layout consumers.

---

## SignLayoutFactory

**Package**: `org.fourz.BarterShops.sign.factory`

Generates complete 4-line `String[]` layouts. Primarily used by legacy callers and
some renderer implementations. New code should prefer `SignRenderUtil` helpers directly.

### Constants

```java
public static final int MAX_LINE_LENGTH = 15;
```

Maximum usable characters per sign line. Used as the wrap threshold throughout the system.

### Methods

#### `truncateForSign(String, int)`

```java
public static String truncateForSign(String text, int maxLength)
```

Truncates `text` to `maxLength` characters. If truncation is required, replaces the last 3
characters with `"..."`. Returns `""` for null or empty input.

---

#### `createSetupLayout(BarterSign)`

```java
public static String[] createSetupLayout(BarterSign barterSign)
```

Generates the SETUP mode sign layout. Two phases based on `barterSign.getItemOffering()`:

- **Phase 1** (no offering set): prompt to hold item and left-click
- **Phase 2** (offering set, BARTER): payment count + `L-Click: add` / `Shift+L: remove`
- **Phase 2** (offering set, BUY/SELL): price display + `L±1, Shift±16`

---

#### `createBoardOwnerSummaryLayout(BarterSign)`

```java
public static String[] createBoardOwnerSummaryLayout(BarterSign barterSign)
```

Owner BOARD view. Format:

```
Line 0: §2[<type>]
Line 1: Qx <item>
Line 2: N Payment  (or "No Payments")
Line 3: Options    (or "Configured")
```

---

#### `createBoardCustomerPageLayout(BarterSign, int)`

```java
public static String[] createBoardCustomerPageLayout(BarterSign barterSign, int pageIndex)
```

Customer BOARD view for a specific payment page (0-based). Wraps `pageIndex` with
`Math.floorMod`. If multiple payments, adds a `§8(N/M)` page indicator on line 3.

---

#### `createTypeLayout(BarterSign)`

```java
public static String[] createTypeLayout(BarterSign barterSign)
```

TYPE mode layout. Shows `§f[barter]` header and formatted type on line 1.

---

#### `createSetupStep1Layout(BarterSign)` / `createSetupStep2Layout(BarterSign)`

```java
public static String[] createSetupStep1Layout(BarterSign barterSign)
public static String[] createSetupStep2Layout(BarterSign barterSign)
```

Explicit step-1/step-2 SETUP layouts (used by some callers that need the raw layout
rather than the `createSetupLayout` auto-detection logic).

---

#### `createDeleteLayout()`

```java
public static String[] createDeleteLayout()
```

DELETE mode initial prompt:

```
§c[DELETE?]
Confirm
shop
removal
```

Note: The confirmation overlay (`L-Click AGAIN / 5s timeout`) is rendered by
`SignDisplay.displayDeleteConfirmation()`, not this method.

---

#### `createHelpLayout()`

```java
public static String[] createHelpLayout()
```

HELP mode static layout:

```
§b[Help]
§7Owner:
§7L-Click = mode
§7R-Click = act
```

---

#### `createNotConfiguredLayout(BarterSign)`

```java
public static String[] createNotConfiguredLayout(BarterSign barterSign)
```

Fallback shown to both owners and customers when shop is not yet configured.
Header varies by type (`§2[Barter]`, `§e[We Buy]`, `§a[We Sell]`).

---

#### `joinLayout(String[])`

```java
public static String joinLayout(String[] layout)
```

Debug helper. Joins a 4-element layout with `\n`. Returns `"Invalid layout"` if
length is not exactly 4.

---

## BarterSign Session-Only Fields

The following fields in `BarterSign` control UI state for customer-facing pagination and
owner preview mode. They are **never persisted** to the database.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `ownerPreviewMode` | `boolean` | `false` | Owner is viewing customer pagination view |
| `currentPaymentPage` | `int` | `0` | Current payment page index (0 = summary, 1..N = individual payments) |

### API

```java
// Preview mode toggle
boolean isOwnerPreviewMode()
void setOwnerPreviewMode(boolean previewMode)

// Pagination
int getCurrentPaymentPage()
void setCurrentPaymentPage(int page)   // wraps with Math.floorMod(page, totalPages)
void incrementPaymentPage()            // advances +1 with wraparound

// Reset both fields (call on shop config change)
void resetCustomerViewState()
```

### Pagination Page Layout

For a BARTER shop with N accepted payment options:

```
Page 0         : summary — "N payment options"
Pages 1 to N   : individual payments — "for Qx [item name]" + "§6page N of M"
Total pages = N + 1
```

`setCurrentPaymentPage(int)` uses `Math.floorMod(page, totalPages)` so negative values
wrap correctly (page `-1` becomes the last page).

---

## Display Behavior Matrix

### Single-Payment BARTER

| Offering Length | Payment Length | Header Present | Layout |
|---|---|---|---|
| ≤15 | ≤15 | Yes | Line 0: header; Line 1: offering; Lines 2-3: `for: Qx /` + name |
| ≤15 | >15 | Yes | Line 0: header; Line 1: offering; Lines 2-3: payment wrapped |
| >15 | ≤15 | Yes | Line 0: header; Lines 1-2: offering wrapped; Line 3: payment condensed |
| >15 | >15 | **No** | Lines 0-1: offering wrapped; Lines 2-3: payment wrapped (dual-wrap mode) |

### Multi-Payment BARTER

Always shows header. Customer view: summary page (page 0) or paginated individual payment
pages (pages 1..N). Right-click advances to next page (wraps).

### BUY / SELL

Single price display. No pagination. Standard offering wrapping applies.

---

## Sequence: Customer Views a Multi-Payment BARTER Sign

```
Customer right-clicks sign
       │
       ▼
SignInteraction.handleCustomerRightClick()
       │
       ▼
barterSign.incrementPaymentPage()           // advances currentPaymentPage
       │
       ▼
SignDisplay.updateSign(sign, barterSign)
       │  (overload resolves isCustomerView = false, preview = false)
       ▼
SignDisplay.updateSign(sign, barterSign, false=owner)
       │  ← WAIT, customer view should be true
       │  ← Actually: SignInteraction passes isCustomerView=true directly
       ▼
BoardModeRenderer.render(side, barterSign, isCustomerView=true)
       │
       ▼
renderCustomerPaymentPage()
       │
       ├── currentPaymentPage == 0  →  show "N payment options"
       └── currentPaymentPage > 0  →  renderPaginatedPayment(side, payments, page)
                                            Line 0: §efor Qx
                                            Line 1: §e<name>
                                            Line 2: §e<name continuation>
                                            Line 3: §6page N of M
```

---

## Known Issues

| Bug ID | Description | Workaround |
|--------|-------------|------------|
| bug-34 | Owner preview mode (`isOwnerPreviewMode=true`) renders owner summary instead of customer pagination | Check payment options manually in TYPE mode |
| bug-30 | Chest break deletes shop from database despite break prevention | Use DELETE mode on sign to remove shops |

---

*Last Updated*: March 3, 2026
*Plugin Version*: 1.0.29
