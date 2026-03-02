package org.fourz.BarterShops.api;

import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.util.log.LogManager;

import java.lang.reflect.Method;

/**
 * Initializer for BarterShops REST API integration with RVNKCore.
 *
 * <p>Registers the ShopApiServlet with RVNKCore's IServletRegistrationService
 * to expose REST endpoints under /api/bartershops/*.</p>
 *
 * <h2>API Endpoints:</h2>
 * <ul>
 *   <li>GET /api/bartershops/shops           - List all shops</li>
 *   <li>GET /api/bartershops/shops/{id}      - Get shop details</li>
 *   <li>GET /api/bartershops/shops/nearby    - Get nearby shops</li>
 *   <li>GET /api/bartershops/trades/recent   - Get recent trades</li>
 *   <li>GET /api/bartershops/trades/{id}     - Get trade details</li>
 *   <li>GET /api/bartershops/stats           - Get server statistics</li>
 *   <li>GET /api/bartershops/health          - Health check</li>
 * </ul>
 *
 * @since 1.1.0
 */
public class ShopApiInitializer {

    private static final String API_PATH = "/api/bartershops/*";
    private static final String DISPLAY_NAME = "BarterShops API";

    private final BarterShops plugin;
    private final LogManager logger;

    private boolean initialized = false;
    private Object servletService = null;

    /**
     * Creates a new API initializer.
     *
     * @param plugin The BarterShops plugin instance
     */
    public ShopApiInitializer(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "ShopApiInitializer");
    }

    /**
     * Initializes the REST API by registering with RVNKCore's servlet service.
     *
     * <p>Uses reflection to avoid hard dependency on RVNKCore classes.</p>
     *
     * @return true if API was successfully registered, false otherwise
     */
    public boolean initialize() {
        if (initialized) {
            logger.warning("API already initialized");
            return true;
        }

        try {
            // Get IServletRegistrationService from RVNKCore ServiceRegistry
            Class<?> rvnkCoreClass = Class.forName("org.fourz.rvnkcore.RVNKCore");
            Object coreInstance = rvnkCoreClass.getMethod("getInstance").invoke(null);
            if (coreInstance == null) {
                logger.warning("RVNKCore instance is null - REST API disabled");
                return false;
            }

            Object serviceRegistry = rvnkCoreClass.getMethod("getServiceRegistry").invoke(coreInstance);
            if (serviceRegistry == null) {
                logger.warning("ServiceRegistry is null - REST API disabled");
                return false;
            }

            Class<?> servletServiceClass = Class.forName("org.fourz.rvnkcore.api.service.IServletRegistrationService");
            Method getServiceMethod = serviceRegistry.getClass().getMethod("getService", Class.class);
            servletService = getServiceMethod.invoke(serviceRegistry, servletServiceClass);

            if (servletService == null) {
                logger.info("IServletRegistrationService not available - REST API server may not be running");
                return false;
            }

            // Check if server is running
            Method isRunningMethod = servletService.getClass().getMethod("isServerRunning");
            boolean serverRunning = (boolean) isRunningMethod.invoke(servletService);

            if (!serverRunning) {
                logger.info("REST API server not running - BarterShops API endpoints disabled");
                return false;
            }

            // Create and register the servlet
            ShopApiEndpointImpl endpoint = new ShopApiEndpointImpl(
                null,                      // IShopService - impl pending (impl-11)
                plugin.getTradeService(),   // ITradeService
                null                       // IShopDatabaseService - impl pending
            );
            ShopApiServlet servlet = new ShopApiServlet(endpoint);
            Method registerMethod = servletService.getClass().getMethod(
                "registerServlet", String.class, jakarta.servlet.http.HttpServlet.class, String.class, boolean.class
            );

            boolean registered = (boolean) registerMethod.invoke(servletService, API_PATH, servlet, DISPLAY_NAME, true);

            if (registered) {
                Method getBaseUrlMethod = servletService.getClass().getMethod("getBaseUrl");
                String baseUrl = (String) getBaseUrlMethod.invoke(servletService);

                initialized = true;
                logger.info("REST API registered at: " + baseUrl + "/api/bartershops");
                logger.info("Available endpoints: /shops, /trades, /stats, /health");
                return true;
            } else {
                logger.warning("Failed to register servlet - path may already be registered");
                return false;
            }

        } catch (ClassNotFoundException e) {
            logger.debug("RVNKCore API classes not found - REST API disabled");
            return false;
        } catch (Exception e) {
            logger.warning("Failed to initialize REST API: " + e.getMessage());
            return false;
        }
    }

    /**
     * Shuts down the REST API by unregistering from RVNKCore.
     */
    public void shutdown() {
        if (!initialized || servletService == null) {
            return;
        }

        try {
            Method unregisterMethod = servletService.getClass().getMethod("unregisterServlet", String.class);
            unregisterMethod.invoke(servletService, API_PATH);
            logger.info("REST API unregistered");
        } catch (Exception e) {
            logger.warning("Failed to unregister REST API: " + e.getMessage());
        }

        initialized = false;
        servletService = null;
    }

    /**
     * Checks if the API is currently initialized.
     *
     * @return true if API is active, false otherwise
     */
    public boolean isInitialized() {
        return initialized;
    }
}
