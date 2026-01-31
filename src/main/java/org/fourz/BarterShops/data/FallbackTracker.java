package org.fourz.BarterShops.data;

import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks database failures and manages fallback mode for graceful degradation.
 *
 * <p>When database operations fail repeatedly, the tracker enters fallback mode
 * to prevent cascading failures. Automatic recovery is attempted after a
 * configurable time period.</p>
 *
 * <p>Pattern based on RVNKLore/RVNKWorlds FallbackTracker for cross-plugin consistency.</p>
 */
public class FallbackTracker {

    private final int maxFailures;
    private final long recoveryTimeMs;
    private final AtomicInteger failureCount;
    private final AtomicLong fallbackStartTime;
    private final LogManager logger;
    private volatile String fallbackReason;

    /**
     * Creates a new FallbackTracker with configuration from plugin.
     *
     * @param plugin The BarterShops plugin instance
     */
    public FallbackTracker(BarterShops plugin) {
        // Default: 3 failures trigger fallback, 30 second recovery window
        this.maxFailures = plugin.getConfigManager().getInt("database.max-failures", 3);
        this.recoveryTimeMs = plugin.getConfigManager().getLong("database.recovery-time-ms", 30000L);
        this.failureCount = new AtomicInteger(0);
        this.fallbackStartTime = new AtomicLong(0);
        this.logger = LogManager.getInstance(plugin, "FallbackTracker");
        this.fallbackReason = null;
    }

    /**
     * Creates a FallbackTracker with explicit configuration.
     * Useful for testing.
     *
     * @param maxFailures Maximum failures before entering fallback mode
     * @param recoveryTimeMs Time in milliseconds before attempting recovery
     * @param logger LogManager for logging events
     */
    public FallbackTracker(int maxFailures, long recoveryTimeMs, LogManager logger) {
        this.maxFailures = maxFailures;
        this.recoveryTimeMs = recoveryTimeMs;
        this.failureCount = new AtomicInteger(0);
        this.fallbackStartTime = new AtomicLong(0);
        this.logger = logger;
        this.fallbackReason = null;
    }

    /**
     * Checks if currently in fallback mode.
     * Also handles automatic recovery if recovery time has elapsed.
     *
     * @return true if in fallback mode, false otherwise
     */
    public boolean isInFallbackMode() {
        long startTime = fallbackStartTime.get();
        if (startTime == 0) {
            return false;
        }

        // Check if recovery window has passed
        if (System.currentTimeMillis() - startTime > recoveryTimeMs) {
            logger.info("Fallback recovery window elapsed, attempting automatic recovery");
            reset();
            return false;
        }

        return true;
    }

    /**
     * Records a database operation failure.
     * May trigger fallback mode if failure threshold is reached.
     *
     * @param reason Description of the failure
     */
    public void recordFailure(String reason) {
        int failures = failureCount.incrementAndGet();
        logger.warning("Database failure recorded (" + failures + "/" + maxFailures + "): " + reason);

        if (failures >= maxFailures && fallbackStartTime.get() == 0) {
            enterFallbackMode(reason);
        }
    }

    /**
     * Records a successful database operation.
     * Resets the failure count on success.
     */
    public void recordSuccess() {
        int previous = failureCount.getAndSet(0);
        if (previous > 0) {
            logger.debug("Database operation succeeded, reset failure count from " + previous);
        }
    }

    /**
     * Manually enters fallback mode.
     *
     * @param reason Description of why fallback mode was entered
     */
    public void enterFallbackMode(String reason) {
        if (fallbackStartTime.compareAndSet(0, System.currentTimeMillis())) {
            this.fallbackReason = reason;
            logger.warning("Entering fallback mode: " + reason);
            logger.warning("Will attempt recovery in " + (recoveryTimeMs / 1000) + " seconds");
        }
    }

    /**
     * Resets the tracker, exiting fallback mode.
     * Note: This does not guarantee database connectivity is restored;
     * that happens via time-based recovery in {@link #isInFallbackMode()}.
     */
    public void reset() {
        failureCount.set(0);
        fallbackStartTime.set(0);
        fallbackReason = null;
        logger.info("FallbackTracker reset - normal operation resumed");
    }

    /**
     * Gets the current failure count.
     *
     * @return The number of consecutive failures
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Gets the maximum failures before fallback.
     *
     * @return The failure threshold
     */
    public int getMaxFailures() {
        return maxFailures;
    }

    /**
     * Gets the recovery time in milliseconds.
     *
     * @return Recovery window duration
     */
    public long getRecoveryTimeMs() {
        return recoveryTimeMs;
    }

    /**
     * Gets the reason for entering fallback mode.
     *
     * @return The fallback reason, or null if not in fallback mode
     */
    public String getFallbackReason() {
        return fallbackReason;
    }

    /**
     * Gets the time remaining until recovery attempt.
     *
     * @return Milliseconds until recovery, or 0 if not in fallback mode
     */
    public long getTimeUntilRecovery() {
        long startTime = fallbackStartTime.get();
        if (startTime == 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, recoveryTimeMs - elapsed);
    }
}
