package org.fourz.BarterShops.data;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.data.repository.ITradeRepository;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

/**
 * Schedules periodic archiving of trade records older than the configured retention window.
 *
 * <p>Runs once per 24 hours (1,728,000 ticks) with a 5-minute startup delay to
 * avoid adding load during server startup. Only active when
 * {@code retention.enabled: true} in config.yml.</p>
 *
 */
public class RetentionManager {

    private static final long TICKS_PER_DAY = 1_728_000L;   // 24 h × 60 m × 60 s × 20 ticks
    private static final long TICKS_START_DELAY = 6_000L;   // 5 minutes after startup

    private final BarterShops plugin;
    private final ITradeRepository tradeRepository;
    private final LogManager logger;
    private final int activeDays;

    private int taskId = -1;

    public RetentionManager(BarterShops plugin, ITradeRepository tradeRepository) {
        this.plugin = plugin;
        this.tradeRepository = tradeRepository;
        this.logger = LogManager.getInstance(plugin, "RetentionManager");
        this.activeDays = plugin.getConfigManager().getRetentionActiveDays();
    }

    /**
     * Starts the 24-hour retention scheduler.
     */
    public void start() {
        taskId = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::runRetention, TICKS_START_DELAY, TICKS_PER_DAY)
                .getTaskId();
        logger.info("Retention scheduler started (active-days=" + activeDays + ", action=archive)");
    }

    /**
     * Cancels the scheduled task on plugin shutdown.
     */
    public void stop() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
            logger.info("Retention scheduler stopped");
        }
    }

    /**
     * Executes one retention cycle: aggregate summaries, archive, then prune archive.
     */
    private void runRetention() {
        Timestamp threshold = Timestamp.from(Instant.now().minus(activeDays, ChronoUnit.DAYS));
        logger.debug("Running retention cycle (threshold=" + threshold + ")");

        String action = plugin.getConfigManager().getRetentionAction();
        if ("archive".equalsIgnoreCase(action)) {
            tradeRepository.populateDailySummaries(threshold)
                    .thenCompose(d -> tradeRepository.rollupMonthlySummaries(threshold))
                    .thenCompose(m -> tradeRepository.archiveOlderThan(threshold))
                    .thenCompose(archiveCount -> {
                        if (archiveCount > 0) {
                            logger.info("Retention: archived " + archiveCount + " trade records older than " + activeDays + " days");
                        } else {
                            logger.debug("Retention: no records to archive");
                        }
                        int archiveDays = plugin.getConfigManager().getRetentionArchiveDays();
                        if (archiveDays > 0) {
                            Timestamp pruneThreshold = Timestamp.from(
                                Instant.now().minus(archiveDays, ChronoUnit.DAYS));
                            return tradeRepository.pruneArchive(pruneThreshold)
                                .thenApply(pruned -> {
                                    if (pruned > 0) logger.info("Pruned " + pruned + " records from archive");
                                    return archiveCount;
                                });
                        }
                        return CompletableFuture.completedFuture(archiveCount);
                    })
                    .exceptionally(ex -> {
                        logger.error("Retention archival failed: " + ex.getMessage());
                        return null;
                    });
        } else {
            logger.debug("Retention action '" + action + "' is not yet implemented; skipping");
        }
    }
}
