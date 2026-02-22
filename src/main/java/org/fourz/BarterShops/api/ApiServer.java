package org.fourz.BarterShops.api;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.fourz.BarterShops.service.IShopDatabaseService;
import org.fourz.BarterShops.service.IShopService;
import org.fourz.BarterShops.service.ITradeService;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * Jetty-based REST API server for BarterShops web integration.
 * Manages HTTP server lifecycle and endpoint registration.
 *
 * <p>Configuration (config.yml):</p>
 * <pre>
 * api:
 *   enabled: true
 *   port: 8080
 *   cors:
 *     enabled: true
 *     allowedOrigins:
 *       - "http://localhost:3000"
 *       - "https://fourz.org"
 *   authentication:
 *     required: false
 *     keys:
 *       - "dev-key-12345"
 * </pre>
 */
public class ApiServer {

    private final Plugin plugin;
    private final Logger logger;
    private final FileConfiguration config;
    private final IShopService shopService;
    private final ITradeService tradeService;
    private final IShopDatabaseService databaseService;

    private Server server;
    private boolean running = false;

    /**
     * Creates a new API server.
     *
     * @param plugin Plugin instance
     * @param shopService Shop service implementation
     * @param tradeService Trade service implementation
     * @param databaseService Database service implementation
     */
    public ApiServer(
        Plugin plugin,
        IShopService shopService,
        ITradeService tradeService,
        IShopDatabaseService databaseService
    ) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = plugin.getConfig();
        this.shopService = shopService;
        this.tradeService = tradeService;
        this.databaseService = databaseService;
    }

    /**
     * Starts the API server if enabled in configuration.
     *
     * @return true if server started successfully
     */
    public boolean start() {
        if (!config.getBoolean("api.enabled", false)) {
            logger.info("API server is disabled in config.yml");
            return false;
        }

        int port = config.getInt("api.port", 8080);

        try {
            // Create Jetty server
            server = new Server(port);

            // Create servlet context
            ServletContextHandler context = new ServletContextHandler();
            context.setContextPath("/api");

            // Add CORS filter if enabled
            if (config.getBoolean("api.cors.enabled", false)) {
                addCorsFilter(context);
            }

            // Add authentication filter if required
            if (config.getBoolean("api.authentication.required", false)) {
                addAuthFilter(context);
            }

            // Register API servlet
            registerServlet(context);

            server.setHandler(context);
            server.start();

            running = true;
            logger.info("API server started on port " + port);
            logger.info("Available endpoints:");
            logger.info("  - GET /api/shops");
            logger.info("  - GET /api/shops/{id}");
            logger.info("  - GET /api/shops/nearby");
            logger.info("  - GET /api/trades/recent");
            logger.info("  - GET /api/stats");
            logger.info("  - GET /api/health");

            return true;
        } catch (Exception e) {
            logger.severe("Failed to start API server: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Stops the API server.
     */
    public void stop() {
        if (server == null || !running) {
            return;
        }

        try {
            server.stop();
            running = false;
            logger.info("API server stopped");
        } catch (Exception e) {
            logger.severe("Error stopping API server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if the API server is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running && server != null && server.isRunning();
    }

    /**
     * Gets the server port.
     *
     * @return The configured port
     */
    public int getPort() {
        return config.getInt("api.port", 8080);
    }

    // ========================================================
    // Private Setup Methods
    // ========================================================

    /**
     * Registers the API servlet.
     */
    private void registerServlet(ServletContextHandler context) {
        // Create API endpoint implementation
        ShopApiEndpoint apiEndpoint = new ShopApiEndpointImpl(
            shopService,
            tradeService,
            databaseService
        );

        // Create and register servlet
        ShopApiServlet servlet = new ShopApiServlet(apiEndpoint);
        ServletHolder servletHolder = new ServletHolder(servlet);
        context.addServlet(servletHolder, "/*");
    }

    /**
     * Adds CORS filter for web client support.
     */
    private void addCorsFilter(ServletContextHandler context) {
        List<String> allowedOrigins = config.getStringList("api.cors.allowedOrigins");

        FilterHolder corsFilter = new FilterHolder(new Filter() {
            @Override
            public void init(FilterConfig filterConfig) {}

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
                HttpServletRequest httpReq = (HttpServletRequest) request;
                HttpServletResponse httpResp = (HttpServletResponse) response;

                String origin = httpReq.getHeader("Origin");

                // Check if origin is allowed
                if (origin != null && allowedOrigins.contains(origin)) {
                    httpResp.setHeader("Access-Control-Allow-Origin", origin);
                    httpResp.setHeader("Access-Control-Allow-Methods",
                        "GET, POST, PUT, DELETE, OPTIONS");
                    httpResp.setHeader("Access-Control-Allow-Headers",
                        "Content-Type, X-API-Key, Authorization");
                    httpResp.setHeader("Access-Control-Max-Age", "3600");
                }

                // Handle preflight requests
                if ("OPTIONS".equalsIgnoreCase(httpReq.getMethod())) {
                    httpResp.setStatus(HttpServletResponse.SC_OK);
                    return;
                }

                chain.doFilter(request, response);
            }

            @Override
            public void destroy() {}
        });

        context.addFilter(corsFilter, "/*", EnumSet.of(DispatcherType.REQUEST));
        logger.info("CORS filter enabled for origins: " + allowedOrigins);
    }

    /**
     * Adds API key authentication filter.
     */
    private void addAuthFilter(ServletContextHandler context) {
        List<String> validApiKeys = config.getStringList("api.authentication.keys");

        FilterHolder authFilter = new FilterHolder(new Filter() {
            @Override
            public void init(FilterConfig filterConfig) {}

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
                HttpServletRequest httpReq = (HttpServletRequest) request;
                HttpServletResponse httpResp = (HttpServletResponse) response;

                // Skip auth for health endpoint
                if (httpReq.getPathInfo() != null && httpReq.getPathInfo().equals("/health")) {
                    chain.doFilter(request, response);
                    return;
                }

                String apiKey = httpReq.getHeader("X-API-Key");

                if (apiKey == null || !validApiKeys.contains(apiKey)) {
                    httpResp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    httpResp.setContentType("application/json");
                    httpResp.getWriter().write(
                        "{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\"," +
                        "\"message\":\"Invalid or missing API key\"}}"
                    );
                    return;
                }

                chain.doFilter(request, response);
            }

            @Override
            public void destroy() {}
        });

        context.addFilter(authFilter, "/*", EnumSet.of(DispatcherType.REQUEST));
        logger.info("API authentication enabled");
    }
}
