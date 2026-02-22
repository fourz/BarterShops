# BarterShops Production Deployment Plan - February 13, 2026

**Status**: ✅ READY FOR QA TESTING & MANUAL DEPLOYMENT

**Document Version**: 1.0
**Prepared**: 2026-02-13 22:17 UTC
**Implementation**: Task 7 - QA Testing + Bug Resolution + Production Deployment

---

## Executive Summary

BarterShops v1.0.1 includes Phases 8-14 customer UI refactor and critical bug fixes. Development is complete. Production deployment requires:

1. **Manual QA Testing** (7 scenarios) → PENDING USER EXECUTION
2. **Bug Verification** (regression testing) → INCLUDED IN QA
3. **Production Deployment** (SparkedHost panel) → MANUAL STEPS PROVIDED

---

## Build Status

### Production JAR

```
File: target/BarterShops-1.0.1.jar
Size: 16 MB
Built: 2026-02-13 22:17:05 UTC
Status: ✅ COMPILATION SUCCESSFUL
```

### Build Command

```bash
cd repos/BarterShops
mvn clean package -DskipTests
```

---

## Bug Fixes Completed

### bug-30: Chest Break Destruction ✅ FIXED

**Issue**: Sign break only cleared in-memory cache, leaving orphaned database records

**Fix**:
- `SignManager.removeBarterSign(Location)` now retrieves shopId before removing from cache
- Calls `repository.deleteById(shopId)` asynchronously
- Removes sign block to clear PDC data
- Logs database deletion success/failure

**Commit**: `2f6aef4`
**Files**: `SignManager.java`

### bug-34: Customer Preview Pagination ✅ FIXED

**Issue**: Owner preview mode always showed owner summary view, never customer pagination

**Fix**:
- `SignDisplay.updateSign(Sign, BarterSign)` overload now checks `ownerPreviewMode`
- Passes `barterSign.isOwnerPreviewMode()` to three-parameter overload
- BOARD mode displays correct view based on preview state

**Commit**: `6a6c0a9`
**Files**: `SignDisplay.java`

### bug-32/33: Auto-Revert Scheduler ✅ FIXED (Feb 11)

**Status**: Fixed in earlier commit `89f2e23`
**Regression Testing**: Included in QA test-32

---

## QA Test Suite Created

7 independent test tasks created in Archon project `bd4e478b-...`:

| Task | Description | Regression Test |
|------|-------------|-----------------|
| test-28 | Multi-payment shop owner flow | Preview mode functionality |
| test-29 | Customer pagination flow | Page cycling & wrapping |
| test-30 | Single-payment shop | No pagination indicator |
| test-31 | Long item name wrapping | Dual-wrap mode |
| test-32 | Auto-revert timer regression | 10-second timeout in all modes |
| test-33 | BUY/SELL shops unaffected | Standard shop behavior |
| test-34 | Type locking validation | Item type restrictions |

**Test Environment**: RVNK Dev server (`1eb313b1-40f7-4209-aa9d-352128214206`)

### QA Prerequisite: Manual Testing

User must execute all 7 QA scenarios on RVNK Dev before production deployment. See `PHASE8_CUSTOMER_UI_REFACTOR.md` lines 1-100 for detailed test checklist.

---

## Production Deployment Steps

### ⚠️ PREREQUISITES (MUST COMPLETE BEFORE DEPLOYMENT)

- [ ] **QA Testing**: All 7 scenarios PASS on RVNK Dev
- [ ] **Bug-32/33**: test-32 regression PASS
- [ ] **No Critical Issues**: QA identifies no blocking bugs
- [ ] **Production Backup**: SparkedHost server backup completed
- [ ] **Rollback Plan**: Confirmed and documented

### Deployment (SparkedHost Panel)

Production server (`140324c4`) is READ-ONLY. Requires manual steps via SparkedHost panel:

**Step 1: Login to SparkedHost**
1. Navigate to SparkedHost control panel
2. Select server `140324c4` (Ravenkraft - Production)

**Step 2: File Manager**
1. Click "File Manager" → `/plugins/`
2. Locate old JAR: `BarterShops-1.0.0.jar` (if exists)
3. **DELETE** old JAR to prevent duplicate loading
4. Upload new JAR: `BarterShops-1.0.1.jar` (from `repos/BarterShops/target/`)

**Step 3: Restart Server**
1. Stop server (must be full restart, NOT reload)
2. Wait 30 seconds for clean shutdown
3. Start server
4. Wait for startup to complete (monitor startup logs)

**Step 4: Verify Deployment**
```bash
# Use skill from main context
/rvnkdev-query 140324c4 startup    # Check startup logs
/rvnkdev-query 140324c4 plugin BarterShops  # Verify plugin loaded
/rvnkdev-query 140324c4 errors     # Check for errors
```

---

## Post-Deployment Verification

### Critical Checks

1. **Plugin Version**
   - Command: `/plugins`
   - Expected: BarterShops v1.0.1
   - Status: ✅ PASS/❌ FAIL

2. **Services Registered**
   - Expected: 4 services loaded (IShopService, IRatingService, IStatsService, ITradeService)
   - Check: Server startup logs

3. **Existing Shops Load**
   - Action: Query database for shop count
   - Expected: All shops from previous version load correctly
   - Check: `/shop list` command works

4. **Create Test Shop**
   - Action: Create single-payment BARTER shop
   - Expected: Shop creation successful, sign displays correctly
   - Verify bug-30 fix: No orphaned records on deletion

5. **Customer Interaction**
   - Action: Customer views paginated payments (if multi-payment)
   - Expected: Pagination works correctly
   - Verify bug-34 fix: Owner preview shows pagination

6. **Owner Preview Mode** (bug-34 verification)
   - Action: Owner sneak+right-clicks in BOARD mode
   - Expected: Toggles between owner summary and customer pagination
   - Status: ✅ PASS/❌ FAIL

7. **Chest Break Cleanup** (bug-30 verification)
   - Action: Create shop, break sign/chest
   - Expected: Database records deleted, no orphaned entries
   - Check: Query `bs_barter_shops` table for orphaned shopId

---

## Rollback Plan

If critical issues found post-deployment:

1. **Stop Server** via SparkedHost panel
2. **Restore Backup JAR**
   - Delete `BarterShops-1.0.1.jar`
   - Upload backup `BarterShops-1.0.0.jar`
3. **Restart Server**
4. **Document Rollback**
   - Record rollback reason in Archon task
   - Mark test-* tasks as needing investigation

---

## Deployment Checklist

### Before Deployment
- [ ] QA testing complete (7/7 scenarios PASS)
- [ ] No critical bugs from QA
- [ ] Production backup confirmed
- [ ] Rollback plan reviewed
- [ ] Bug-30 fix verified in QA
- [ ] Bug-34 fix verified in QA
- [ ] bug-32/33 regression test PASS

### During Deployment
- [ ] Old JAR deleted from /plugins/
- [ ] New JAR uploaded successfully
- [ ] Server restarted (not reloaded)
- [ ] Startup logs checked for errors
- [ ] Plugin loads without exceptions

### After Deployment
- [ ] Plugin version confirmed: v1.0.1
- [ ] All services registered
- [ ] Test shop creation works
- [ ] Customer pagination verified
- [ ] Owner preview mode verified
- [ ] Chest break cleanup verified
- [ ] No critical errors in logs

---

## Documentation Links

- **QA Test Suite**: See Archon project `bd4e478b-...` tasks test-28 through test-34
- **Phase 8-14 Docs**:
  - `PHASE8_CUSTOMER_UI_REFACTOR.md` - Implementation details
  - `SIGN_UI_UX.md` - Comprehensive sign system guide (640 lines)
  - `COMPLETION_SUMMARY_FEB13_2026.md` - Work summary
- **Bug Reports**:
  - `BUG33_REVISIT_ANALYSIS.md` - bug-32/33 root cause
  - This document references bug fixes in code

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.1 | 2026-02-13 | Phases 8-14 customer UI refactor + bug-epic-01 fixes |
| 1.0.0 | 2026-02-11 | Phase 7 database persistence + bug fixes |
| (Earlier) | 2026-02-10 | Phases 1-6 implementation |

---

## Next Steps (Post-Deployment)

1. **Monitor Production** (24-48 hours)
   - Check error logs daily
   - Monitor player feedback
   - Verify backup strategy

2. **Future Features**
   - feat-23: Database persistence for quantities
   - feat-24: Service runtime initialization
   - refactor-01: Mode enum unification

3. **Documentation Updates**
   - Sync production docs to Archon KB
   - Update admin/user guides with new features

---

## Support

**Issue During QA?**
1. Create bug task in Archon: `bug-epic-01` board
2. Reference test scenario (test-28 through test-34)
3. Provide repro steps and error logs

**Issue During Deployment?**
1. Execute rollback plan immediately
2. Document issue in Archon
3. Investigate root cause before re-attempting

**Production Issues?**
1. Use `/rvnkdev-query 140324c4 errors` to check logs
2. Create critical task in bug-epic-01
3. Prepare rollback if needed

---

**Status**: ✅ READY FOR USER QA & MANUAL DEPLOYMENT
**Prepared By**: Claude Code (AI Assistant)
**Date**: 2026-02-13
**Version**: v1.0.1-production-ready
