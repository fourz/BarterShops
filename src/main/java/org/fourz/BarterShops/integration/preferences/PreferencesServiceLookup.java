package org.fourz.BarterShops.integration.preferences;

import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.api.service.PlayerPreferencesService;
import org.fourz.rvnkcore.service.registry.ServiceRegistry;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

/**
 * Centralized access to PlayerPreferencesService via ServiceRegistry.
 * Handles initialization and availability checks for graceful degradation.
 *
 * Integration point for RVNKCore Player Preferences Phase 3.
 */
public class PreferencesServiceLookup {
    private final LogManager logger;
    private PlayerPreferencesService preferencesService;
    private boolean serviceAvailable = false;

    public PreferencesServiceLookup(Plugin plugin) {
        this.logger = LogManager.getInstance(plugin, "PreferencesServiceLookup");
        initService();
    }

    /**
     * Initialize PlayerPreferencesService from RVNKCore's ServiceRegistry.
     * Gracefully handles unavailable RVNKCore with detailed logging.
     */
    private void initService() {
        try {
            RVNKCore core = RVNKCore.getInstance();
            if (core == null) {
                logger.warning("RVNKCore not initialized - notifications will use fallback");
                return;
            }

            ServiceRegistry registry = core.getServiceRegistry();
            if (registry == null) {
                logger.warning("ServiceRegistry not available - notifications will use fallback");
                return;
            }

            this.preferencesService = registry.getService(PlayerPreferencesService.class);
            if (preferencesService == null) {
                logger.warning("PlayerPreferencesService not registered - notifications will use fallback");
                return;
            }

            this.serviceAvailable = true;
            logger.info("PlayerPreferencesService integration enabled");
        } catch (Exception e) {
            logger.error("Failed to initialize PlayerPreferencesService: " + e.getMessage(), e);
        }
    }

    /**
     * Get the PlayerPreferencesService instance.
     *
     * @return The service, or null if not available
     */
    public PlayerPreferencesService getService() {
        return preferencesService;
    }

    /**
     * Check if PlayerPreferencesService is available and ready.
     *
     * @return true if service is available and can be used
     */
    public boolean isAvailable() {
        return serviceAvailable;
    }
}
