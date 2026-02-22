package org.fourz.BarterShops.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.fourz.BarterShops.api.ShopApiEndpoint.ApiResponse;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Jetty servlet for routing BarterShops REST API requests.
 * Handles HTTP methods and delegates to ShopApiEndpoint implementation.
 *
 * <p>Supported routes:</p>
 * <ul>
 *     <li>GET /api/shops - List all shops</li>
 *     <li>GET /api/shops/{id} - Get shop details</li>
 *     <li>GET /api/shops/nearby - Get nearby shops</li>
 *     <li>GET /api/trades/recent - Get recent trades</li>
 *     <li>GET /api/trades/{id} - Get trade details</li>
 *     <li>GET /api/stats - Get server statistics</li>
 *     <li>GET /api/stats/shops - Get shop statistics</li>
 *     <li>GET /api/health - Health check</li>
 * </ul>
 */
public class ShopApiServlet extends HttpServlet {

    private final ShopApiEndpoint apiEndpoint;
    private final Gson gson;

    // Route patterns
    private static final Pattern SHOP_BY_ID_PATTERN = Pattern.compile("^/shops/([0-9]+)$");
    private static final Pattern TRADE_BY_ID_PATTERN = Pattern.compile("^/trades/([0-9a-f-]+)$");
    private static final Pattern STATS_SHOPS_PATTERN = Pattern.compile("^/stats/shops/?(.*)$");

    /**
     * Creates a new API servlet.
     *
     * @param apiEndpoint The API endpoint implementation
     */
    public ShopApiServlet(ShopApiEndpoint apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .create();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo() != null ? req.getPathInfo() : "/";

        try {
            // Route matching
            if (pathInfo.equals("/shops")) {
                handleGetShops(req, resp);
            } else if (pathInfo.equals("/shops/nearby")) {
                handleGetShopsNearby(req, resp);
            } else if (SHOP_BY_ID_PATTERN.matcher(pathInfo).matches()) {
                handleGetShopById(req, resp, pathInfo);
            } else if (pathInfo.equals("/trades/recent")) {
                handleGetRecentTrades(req, resp);
            } else if (TRADE_BY_ID_PATTERN.matcher(pathInfo).matches()) {
                handleGetTradeById(req, resp, pathInfo);
            } else if (pathInfo.equals("/stats")) {
                handleGetStats(req, resp);
            } else if (STATS_SHOPS_PATTERN.matcher(pathInfo).matches()) {
                handleGetShopStats(req, resp, pathInfo);
            } else if (pathInfo.equals("/health")) {
                handleGetHealth(req, resp);
            } else {
                sendErrorResponse(resp, 404, "NOT_FOUND", "Endpoint not found: " + pathInfo);
            }
        } catch (Exception e) {
            sendErrorResponse(resp, 500, "INTERNAL_ERROR", "Server error: " + e.getMessage());
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        // CORS preflight response
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    // ========================================================
    // Route Handlers
    // ========================================================

    private void handleGetShops(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, String> filters = extractQueryParams(req);

        apiEndpoint.getShops(filters)
            .thenAccept(response -> sendJsonResponse(resp, 200, response))
            .exceptionally(ex -> {
                try {
                    sendErrorResponse(resp, 500, "INTERNAL_ERROR", ex.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }

    private void handleGetShopById(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        Matcher matcher = SHOP_BY_ID_PATTERN.matcher(pathInfo);
        if (!matcher.matches()) {
            sendErrorResponse(resp, 400, "INVALID_REQUEST", "Invalid shop ID format");
            return;
        }

        String shopId = matcher.group(1);

        apiEndpoint.getShopById(shopId)
            .thenAccept(response -> sendJsonResponse(resp, 200, response))
            .exceptionally(ex -> {
                try {
                    sendErrorResponse(resp, 500, "INTERNAL_ERROR", ex.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }

    private void handleGetShopsNearby(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String world = req.getParameter("world");
        String xStr = req.getParameter("x");
        String yStr = req.getParameter("y");
        String zStr = req.getParameter("z");
        String radiusStr = req.getParameter("radius");

        // Validate required parameters
        if (world == null || xStr == null || yStr == null || zStr == null) {
            sendErrorResponse(resp, 400, "INVALID_REQUEST",
                "Missing required parameters: world, x, y, z");
            return;
        }

        try {
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);
            double z = Double.parseDouble(zStr);
            double radius = radiusStr != null ? Double.parseDouble(radiusStr) : 50.0;

            apiEndpoint.getShopsNearby(world, x, y, z, radius)
                .thenAccept(response -> sendJsonResponse(resp, 200, response))
                .exceptionally(ex -> {
                    try {
                        sendErrorResponse(resp, 500, "INTERNAL_ERROR", ex.getMessage());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                });
        } catch (NumberFormatException e) {
            sendErrorResponse(resp, 400, "INVALID_REQUEST",
                "Invalid coordinate format: " + e.getMessage());
        }
    }

    private void handleGetRecentTrades(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String limitStr = req.getParameter("limit");
        String shopId = req.getParameter("shop");
        String playerUuidStr = req.getParameter("player");

        int limit = limitStr != null ? parseIntOrDefault(limitStr, 20) : 20;
        UUID playerUuid = null;

        if (playerUuidStr != null) {
            try {
                playerUuid = UUID.fromString(playerUuidStr);
            } catch (IllegalArgumentException e) {
                sendErrorResponse(resp, 400, "INVALID_REQUEST", "Invalid player UUID format");
                return;
            }
        }

        UUID finalPlayerUuid = playerUuid;
        apiEndpoint.getRecentTrades(limit, shopId, finalPlayerUuid)
            .thenAccept(response -> sendJsonResponse(resp, 200, response))
            .exceptionally(ex -> {
                try {
                    sendErrorResponse(resp, 500, "INTERNAL_ERROR", ex.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }

    private void handleGetTradeById(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        Matcher matcher = TRADE_BY_ID_PATTERN.matcher(pathInfo);
        if (!matcher.matches()) {
            sendErrorResponse(resp, 400, "INVALID_REQUEST", "Invalid transaction ID format");
            return;
        }

        String transactionId = matcher.group(1);

        apiEndpoint.getTradeById(transactionId)
            .thenAccept(response -> sendJsonResponse(resp, 200, response))
            .exceptionally(ex -> {
                try {
                    sendErrorResponse(resp, 500, "INTERNAL_ERROR", ex.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }

    private void handleGetStats(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        apiEndpoint.getServerStats()
            .thenAccept(response -> sendJsonResponse(resp, 200, response))
            .exceptionally(ex -> {
                try {
                    sendErrorResponse(resp, 500, "INTERNAL_ERROR", ex.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }

    private void handleGetShopStats(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        Matcher matcher = STATS_SHOPS_PATTERN.matcher(pathInfo);
        if (!matcher.matches()) {
            sendErrorResponse(resp, 400, "INVALID_REQUEST", "Invalid stats endpoint");
            return;
        }

        String shopId = matcher.group(1);
        if (shopId != null && !shopId.isEmpty()) {
            shopId = shopId.replaceAll("^/|/$", ""); // Remove leading/trailing slashes
        }

        String finalShopId = shopId;
        apiEndpoint.getShopStats(finalShopId)
            .thenAccept(response -> sendJsonResponse(resp, 200, response))
            .exceptionally(ex -> {
                try {
                    sendErrorResponse(resp, 500, "INTERNAL_ERROR", ex.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }

    private void handleGetHealth(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        apiEndpoint.getHealthStatus()
            .thenAccept(response -> sendJsonResponse(resp, 200, response))
            .exceptionally(ex -> {
                try {
                    sendErrorResponse(resp, 500, "INTERNAL_ERROR", ex.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            });
    }

    // ========================================================
    // Helper Methods
    // ========================================================

    /**
     * Extracts all query parameters from the request.
     */
    private Map<String, String> extractQueryParams(HttpServletRequest req) {
        Map<String, String> params = new HashMap<>();
        req.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    /**
     * Sends a JSON response.
     */
    private void sendJsonResponse(HttpServletResponse resp, int status, Object data) {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            resp.getWriter().write(gson.toJson(data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends an error response.
     */
    private void sendErrorResponse(HttpServletResponse resp, int status, String code, String message) throws IOException {
        ApiResponse<?> errorResponse = ApiResponse.error(code, message);
        sendJsonResponse(resp, status, errorResponse);
    }

    /**
     * Parses an integer from a string, returning default value on error.
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
