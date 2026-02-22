# BarterShops Phase 2 Integration - Test Plan

**Date**: February 14, 2026
**Phase**: 2 Integration (Tasks 1-5 Complete)
**Build**: ✅ SUCCESS (mvn clean package)
**Target**: RVNK Dev Server (`1eb313b1-40f7-4209-aa9d-352128214206`)

---

## Test Objectives

Verify that Phase 2 integration fixes the following issues and maintains system stability:

1. **bug-36 Fix**: Type locking validation enforced continuously in ALL modes
2. **Issue #1 Fix**: Owner preview mode shows customer pagination view
3. **Reactive Updates**: Sign updates when items are added/removed (250ms debounce)
4. **Error Notifications**: Invalid items ejected with player messages
5. **System Stability**: No crashes, proper error handling, clean console logs

---

## Test Environment

| Component | Value |
|-----------|-------|
| Server | RVNK Dev (SparkedHost) |
| Server ID | `1eb313b1-40f7-4209-aa9d-352128214206` |
| Plugin | BarterShops-1.0.1.jar |
| Build Method | `mvn clean package` |
| Test Duration | ~30 minutes |

---

## Pre-Test Checklist

- [ ] Build successful (`mvn clean package`)
- [ ] No compilation errors
- [ ] JAR file created: `target/BarterShops-1.0.1.jar`
- [ ] RVNK Dev server accessible
- [ ] Server status: running
- [ ] Database accessible (MySQL/SQLite)
- [ ] Console command access available

---

## Test Cases

### **Test 1: Type Locking in SETUP Mode (Baseline)**

**Objective**: Verify type detection works as before

**Steps**:
1. Create new BARTER shop with sign
2. Place iron ingots in chest
3. Check sign shows type lock detected
4. Try adding diamonds → should be rejected
5. Add more iron ingots → should succeed

**Expected Result**: ✅ Type lock enforced in SETUP mode

**Pass Criteria**: Invalid item rejected, valid item accepted

---

### **Test 2: Type Locking in BOARD Mode (bug-36 Fix)**

**Objective**: Verify type locking works in BOARD mode (main bug-36 fix)

**Setup**:
- Use shop from Test 1 (type locked to iron)
- Switch to BOARD mode

**Steps**:
1. Right-click sign to cycle to BOARD mode
2. Attempt to add diamonds via inventory click
3. Observe rejection
4. Try adding iron ingots
5. Observe acceptance

**Expected Result**: ✅ Type lock enforced in BOARD mode (was broken before)

**Pass Criteria**:
- Diamonds rejected in BOARD mode
- Iron ingots accepted in BOARD mode
- Error message shown to player

**Console Output**:
```
[BarterShops] Type locking validation: REJECTED diamonds (expected: iron ingots)
```

---

### **Test 3: Type Locking via Hopper (Auto-Bypass Test)**

**Objective**: Verify hoppers cannot bypass type locking

**Setup**:
- Use type-locked shop from Test 1
- Place hopper above chest

**Steps**:
1. Fill hopper with diamonds
2. Let hopper attempt to move items (5 sec delay)
3. Check chest inventory
4. Repeat with iron ingots

**Expected Result**: ✅ Hoppers cannot bypass type locking

**Pass Criteria**:
- Diamonds NOT transferred to chest
- Iron ingots transferred successfully

---

### **Test 4: Owner Preview Mode Pagination (Issue #1 Fix)**

**Objective**: Verify owner can see customer payment pagination

**Setup**:
- Create BARTER shop with 3 payment options:
  - Option 1: 10 diamonds
  - Option 2: 5 emeralds
  - Option 3: 20 gold ingots

**Steps**:
1. Switch to BOARD mode (sign shows owner summary by default)
2. Right-click sign to enter "preview mode" (shift+right-click)
3. Observe sign changes to customer view
4. Right-click again to cycle through payment pages
5. Verify all 3 payments displayed across pagination

**Expected Result**: ✅ Owner sees customer pagination (was broken before)

**Pass Criteria**:
- Preview mode shows "Page 1/3", "Page 2/3", "Page 3/3"
- Each page shows correct payment option
- Sign updates on right-click (pagination cycle)

**Sign Display Expected**:
```
[Barter]              [Barter]              [Barter]
Qty Offering          Qty Offering          Qty Offering
for: 10x             for: 5x               for: 20x
diamonds (1/3)       emeralds (2/3)        gold (3/3)
```

---

### **Test 5: Reactive Sign Updates (Debounce Test)**

**Objective**: Verify sign updates when items added/removed

**Setup**:
- Use type-locked shop from Test 1
- Open chest in inventory

**Steps**:
1. Note current sign display
2. Add items to chest slowly (1 item per second)
3. Observe sign updates
4. Remove items and observe sign updates
5. Add items rapidly (5 items in 1 second)
6. Check update frequency (should debounce to ~250ms)

**Expected Result**: ✅ Sign updates reflect inventory changes

**Pass Criteria**:
- Sign updates within 500ms of inventory change
- No excessive updates when items added rapidly
- Debounce prevents spam (max ~4 updates/sec)

**Console Output**:
```
[BarterShops] Reactive update: 5 iron ingots in chest
[BarterShops] Reactive update: 10 iron ingots in chest
```

---

### **Test 6: Item Ejection on Validation Failure (Task 3 Fix)**

**Objective**: Verify invalid items are ejected with player notification

**Setup**:
- Use type-locked shop (iron)
- Have player ready to place items

**Steps**:
1. Player attempts to place diamond in chest
2. Observe item is rejected (inventory cancel)
3. Observe item appears on ground near chest (dropped)
4. Check player receives error notification

**Expected Result**: ✅ Item ejected with notification

**Pass Criteria**:
- Diamond appears on ground (not in chest)
- Player sees error message in chat
- Message format: "✗ [reason]" (e.g., "✗ This shop only accepts iron ingots")

**Player Chat**:
```
✗ This shop only accepts iron ingots
```

---

### **Test 7: Drag Event Validation**

**Objective**: Verify validation works when dragging items

**Setup**:
- Use type-locked shop (iron)
- Have items in inventory

**Steps**:
1. Player drags diamond to chest slots
2. Observe rejection
3. Player drags iron to chest slots
4. Observe acceptance

**Expected Result**: ✅ Drag validation enforced

**Pass Criteria**: Invalid drag cancelled, valid drag accepted

---

### **Test 8: Multiple Shops Independence**

**Objective**: Verify validation rules don't affect other shops

**Setup**:
- Create Shop A: Type-locked to iron ingots
- Create Shop B: Type-locked to emeralds
- Create Shop C: No type lock (accepts any)

**Steps**:
1. Place diamonds in Shop A chest → rejected
2. Place diamonds in Shop B chest → rejected
3. Place diamonds in Shop C chest → accepted
4. Place iron in Shop B chest → rejected
5. Place iron in Shop A chest → accepted

**Expected Result**: ✅ Each shop validates independently

**Pass Criteria**: Each shop enforces only its own rules

---

### **Test 9: Owner Access Validation**

**Objective**: Verify owners can configure even with type lock active

**Setup**:
- Use type-locked shop from Test 1

**Steps**:
1. Owner opens chest (no errors)
2. Owner can add/remove items
3. Owner can modify shop configuration
4. Validation still applies (rejects wrong types)

**Expected Result**: ✅ Owners have access with validation

**Pass Criteria**: Owner can access and configure freely

---

### **Test 10: Persistence Across Restart**

**Objective**: Verify validation rules survive server restart

**Setup**:
- Create type-locked shop
- Note shop ID and type
- Restart server
- Verify shop reloads with correct validation

**Steps**:
1. Create and configure shop
2. Restart RVNK Dev server
3. Try adding wrong item type
4. Observe rejection (validation rules persisted)

**Expected Result**: ✅ Validation rules persist

**Pass Criteria**: Type lock enforced after restart

---

## Test Execution Commands

### Deploy to RVNK Dev:
```bash
/rvnkdev-deploy 1eb313b1-40f7-4209-aa9d-352128214206 full
```

### Query server status:
```bash
/rvnkdev-query 1eb313b1-40f7-4209-aa9d-352128214206 status
```

### Check plugin loaded:
```bash
/rvnkdev-query 1eb313b1-40f7-4209-aa9d-352128214206 plugin BarterShops
```

### Monitor console for errors:
```bash
/rvnkdev-query 1eb313b1-40f7-4209-aa9d-352128214206 errors
```

---

## Test Report Template

### Summary
| Metric | Value |
|--------|-------|
| Tests Run | 10 |
| Tests Passed | ? |
| Tests Failed | ? |
| Coverage | % |
| Build Status | ✅ SUCCESS |
| Server Status | ? |

### Results by Test Case

- [ ] Test 1: Type locking in SETUP mode - **PASS** / **FAIL**
- [ ] Test 2: Type locking in BOARD mode (bug-36) - **PASS** / **FAIL** ⭐ CRITICAL
- [ ] Test 3: Hopper bypass prevention - **PASS** / **FAIL**
- [ ] Test 4: Owner preview pagination (issue #1) - **PASS** / **FAIL** ⭐ CRITICAL
- [ ] Test 5: Reactive sign updates - **PASS** / **FAIL**
- [ ] Test 6: Item ejection notification - **PASS** / **FAIL**
- [ ] Test 7: Drag event validation - **PASS** / **FAIL**
- [ ] Test 8: Multiple shop independence - **PASS** / **FAIL**
- [ ] Test 9: Owner access validation - **PASS** / **FAIL**
- [ ] Test 10: Persistence across restart - **PASS** / **FAIL**

### Issues Found
(None expected if Phase 2 integration is correct)

### Recommendations
- Proceed to Task 7 (deployment) if all critical tests pass
- Investigate any failures before deployment

---

## Success Criteria

**Phase 2 is VERIFIED if:**
1. ✅ bug-36 fixed: Type locking enforced in BOARD mode
2. ✅ Issue #1 fixed: Owner preview shows pagination
3. ✅ Reactive updates: Sign updates within 500ms
4. ✅ Error handling: Invalid items ejected with message
5. ✅ No regressions: All existing features still work
6. ✅ Console clean: No errors or warnings
7. ✅ Database: Persistence works across restart

---

## Notes

- All tests assume RVNK Dev server is accessible and configured
- Tests can run sequentially or in parallel (shops are independent)
- Each test case should take 2-3 minutes
- Total execution time: ~30 minutes
- Report results in Archon Task 6
