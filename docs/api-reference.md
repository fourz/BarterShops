# BarterShops REST API Reference

**Base URL**: `https://{host}:{port}/api/bartershops`
**Authentication**: `X-API-Key` header (all endpoints)
**Content-Type**: `application/json`
**Servlet**: Registered with RVNKCore `IServletRegistrationService` — returns **503** if BarterShops is not loaded.

All endpoints use the standard RVNKCore `ApiResponse` envelope:

```json
// Success
{ "success": true, "data": { ... }, "meta": { "timestamp": "...", "version": "1.0" } }

// Paginated success
{ "success": true, "data": [...], "page": 1, "limit": 20, "total": 47, "meta": { ... } }

// Error
{ "success": false, "error": { "code": "ERROR_CODE", "message": "..." }, "meta": { ... } }
```

---

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/bartershops/shops` | List shops with optional filters |
| GET | `/bartershops/shops/nearby` | Find shops near coordinates |
| GET | `/bartershops/shops/{id}` | Get shop by ID |
| GET | `/bartershops/groups` | List shop groups for an owner |
| GET | `/bartershops/groups/{id}` | Get group detail with shops and co-owners |
| GET | `/bartershops/trades/recent` | Recent trade activity |
| GET | `/bartershops/trades/{tradeId}` | Trade by ID |
| GET | `/bartershops/stats` | Server-wide statistics |
| GET | `/bartershops/stats/shops` | Shop statistics |
| GET | `/bartershops/stats/shops/{shopId}` | Statistics for a specific shop |
| GET | `/bartershops/health` | Plugin health check |

---

## GET /bartershops/shops

List all shops with optional filters, sorting, and pagination.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `owner` | UUID | No | Filter by owner UUID |
| `type` | string | No | Filter by shop type: `BARTER`, `SELL`, `BUY`, `ADMIN` |
| `world` | string | No | Filter by world name |
| `sort` | string | No | Sort field: `createdAt` (default), `name`, `owner`, `type` |
| `order` | string | No | Sort direction: `desc` (default), `asc` |
| `page` | int | No | Page number (default: 1) |
| `limit` | int | No | Results per page (default: 20, max: 100) |

**Response:** Paginated list of `ShopDataDTO` objects. Each shop object:

```json
{
  "shopId": 42,
  "ownerUuid": "550e8400-e29b-41d4-a716-446655440000",
  "shopName": "Diamond Exchange",
  "shopType": "BARTER",
  "locationWorld": "world",
  "locationX": 128.0,
  "locationY": 64.0,
  "locationZ": -200.0,
  "chestLocationWorld": "world",
  "chestLocationX": 128.0,
  "chestLocationY": 63.0,
  "chestLocationZ": -200.0,
  "isActive": true,
  "createdAt": "2026-01-15T10:30:00Z",
  "lastModified": "2026-03-01T14:22:00Z",
  "groupId": 7,
  "metadata": {
    "ownerName": "PlayerName"
  }
}
```

> **Note**: `metadata` contains sanitized shop config keys (trailing commas in JSON values are stripped) and an injected `ownerName` resolved via `PlayerLookup`. Raw `shop_config_*` keys are present when set.

**Example:**

```bash
curl -k -s -H "X-API-Key: <key>" \
  "https://noctani1.internal.fourz.org:8081/api/bartershops/shops?world=world&limit=10"
```

---

## GET /bartershops/shops/nearby

Find shops within a radius of the given coordinates.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `world` | string | Yes | World name |
| `x` | double | Yes | X coordinate |
| `y` | double | Yes | Y coordinate |
| `z` | double | Yes | Z coordinate |
| `radius` | double | No | Search radius in blocks (default: 50, max: 500) |

**Response:** Array of `ShopDataDTO` objects (same format as `/shops`), not paginated.

**Error Responses:**
- `INVALID_REQUEST` — Missing `world` parameter

---

## GET /bartershops/shops/{id}

Get a single shop by its numeric ID.

**Path Parameters:**
- `id` (int) — Shop ID (numeric)

**Response:** Single `ShopDataDTO` object (same format as `/shops` list items).

**Error Responses:**
- `INVALID_REQUEST` — Shop ID is not numeric
- `NOT_FOUND` — Shop with given ID not found
- `INTERNAL_ERROR` — Server error

---

## GET /bartershops/groups

List shop groups owned by a player, with optional world filter and pagination.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `owner` | UUID | Yes | Owner UUID (required) |
| `world` | string | No | Filter by world name |
| `page` | int | No | Page number (default: 1) |
| `limit` | int | No | Results per page (default: 20, max: 100) |

**Response:** Paginated list of group objects:

```json
{
  "success": true,
  "data": [
    {
      "groupId": 7,
      "groupName": "Market Row",
      "ownerUuid": "550e8400-e29b-41d4-a716-446655440000",
      "ownerName": "PlayerName",
      "world": "world",
      "isActive": true,
      "createdAt": "2026-01-15T10:30:00Z",
      "coOwners": [
        { "uuid": "661f9511-...", "name": "CoOwnerName" }
      ],
      "shopCount": 3
    }
  ],
  "page": 1,
  "limit": 20,
  "total": 1,
  "meta": { "timestamp": "...", "version": "1.0" }
}
```

**Field descriptions:**

| Field | Type | Description |
|-------|------|-------------|
| `groupId` | int | Numeric group identifier |
| `groupName` | string | Display name of the group |
| `ownerUuid` | string | Owner's UUID |
| `ownerName` | string | Owner's resolved player name |
| `world` | string | World the group is associated with |
| `isActive` | boolean | Whether the group is active |
| `createdAt` | string | ISO 8601 creation timestamp |
| `coOwners` | array | Co-owners as `[{uuid, name}]` objects |
| `shopCount` | int | Number of shops in this group |

**Error Responses:**
- `INVALID_REQUEST` — Missing or invalid `owner` UUID
- `SERVICE_UNAVAILABLE` — Shop group service not available
- `INTERNAL_ERROR` — Server error

**Example:**

```bash
curl -k -s -H "X-API-Key: <key>" \
  "https://noctani1.internal.fourz.org:8081/api/bartershops/groups?owner=550e8400-e29b-41d4-a716-446655440000"
```

---

## GET /bartershops/groups/{id}

Get full group detail by ID, including all shops and co-owners.

**Path Parameters:**
- `id` (int) — Group ID (numeric)

**Response:** Single group object with all list fields plus:

```json
{
  "success": true,
  "data": {
    "groupId": 7,
    "groupName": "Market Row",
    "ownerUuid": "550e8400-e29b-41d4-a716-446655440000",
    "ownerName": "PlayerName",
    "world": "world",
    "isActive": true,
    "createdAt": "2026-01-15T10:30:00Z",
    "lastModified": "2026-03-10T09:15:00Z",
    "coOwners": [
      { "uuid": "661f9511-...", "name": "CoOwnerName" }
    ],
    "shops": [
      {
        "shopId": 42,
        "ownerUuid": "550e8400-e29b-41d4-a716-446655440000",
        "shopName": "Diamond Exchange",
        "shopType": "BARTER",
        "locationWorld": "world",
        "locationX": 128.0,
        "locationY": 64.0,
        "locationZ": -200.0,
        "isActive": true,
        "createdAt": "2026-01-15T10:30:00Z",
        "lastModified": "2026-03-01T14:22:00Z",
        "groupId": 7,
        "metadata": { "ownerName": "PlayerName" }
      }
    ],
    "shopCount": 1
  },
  "meta": { "timestamp": "...", "version": "1.0" }
}
```

**Additional fields (detail only):**

| Field | Type | Description |
|-------|------|-------------|
| `lastModified` | string | ISO 8601 timestamp of last modification |
| `shops` | array | Full `ShopDataDTO` objects for all shops in the group (sanitized metadata, with `ownerName`) |
| `shopCount` | int | Total number of shops in the group |

**Error Responses:**
- `INVALID_REQUEST` — Group ID is not numeric
- `NOT_FOUND` — Group with given ID not found
- `SERVICE_UNAVAILABLE` — Shop group service not available
- `INTERNAL_ERROR` — Server error

**Example:**

```bash
curl -k -s -H "X-API-Key: <key>" \
  "https://noctani1.internal.fourz.org:8081/api/bartershops/groups/7"
```

---

## GET /bartershops/trades/recent

List recent trade records with optional filters.

**Query Parameters:**

| Param | Type | Required | Description |
|-------|------|----------|-------------|
| `limit` | int | No | Maximum results (default: 20, max: 100) |
| `shop` | string (int) | No | Filter by shop ID |
| `player` | UUID | No | Filter by buyer or seller UUID |

**Response:** Array of enriched trade objects:

```json
{
  "transactionId": "a1b2c3d4-...",
  "shopId": "42",
  "buyerUuid": "550e8400-...",
  "sellerUuid": "661f9511-...",
  "buyerName": "BuyerName",
  "sellerName": "SellerName",
  "itemStackData": "...",
  "quantity": 16,
  "pricePaid": 0.0,
  "status": "COMPLETED",
  "tradeSource": "SIGN_INTERACTION",
  "completedAt": "2026-03-15T12:00:00Z"
}
```

**Error Responses:**
- `INVALID_REQUEST` — Invalid player UUID format
- `INTERNAL_ERROR` — Server error

---

## GET /bartershops/trades/{tradeId}

Get a single trade record by its UUID.

**Path Parameters:**
- `tradeId` (UUID) — Transaction UUID

**Response:** Single trade object (same format as `/trades/recent` items).

**Error Responses:**
- `INVALID_REQUEST` — Missing or invalid transaction UUID
- `NOT_FOUND` — Trade with given ID not found
- `INTERNAL_ERROR` — Server error

---

## GET /bartershops/stats

Server-wide BarterShops statistics.

**Response:**

```json
{
  "totalShops": 47,
  "totalTrades": 312,
  "activeShops": 44,
  "shopsByType": {
    "BARTER": 30,
    "SELL": 10,
    "BUY": 5,
    "ADMIN": 2
  }
}
```

---

## GET /bartershops/stats/shops

Alias for `/bartershops/stats` — returns the same server-wide statistics.

---

## GET /bartershops/stats/shops/{shopId}

Statistics for a single shop.

**Path Parameters:**
- `shopId` (int) — Shop ID (numeric)

**Response:**

```json
{
  "shopId": "42",
  "shopName": "Diamond Exchange",
  "ownerUuid": "550e8400-...",
  "totalTrades": 18,
  "isActive": true,
  "createdAt": "2026-01-15T10:30:00Z"
}
```

**Error Responses:**
- `NOT_FOUND` — Shop with given ID not found
- `INTERNAL_ERROR` — Server error

---

## GET /bartershops/health

BarterShops plugin health check.

**Response:**

```json
{
  "status": "healthy",
  "fallbackMode": false,
  "database": "connected",
  "uptime": 86400000,
  "timestamp": "2026-03-21T10:00:00Z"
}
```

`status` is `"degraded"` when the database is in fallback mode (too many consecutive failures). `database` is `"fallback"` in that state.

---

## Error Code Reference

| Code | HTTP | Meaning |
|------|------|---------|
| `INVALID_REQUEST` | 400 | Invalid or missing parameter (non-numeric ID, bad UUID, missing required param) |
| `NOT_FOUND` | 404 | Resource not found |
| `INTERNAL_ERROR` | 500 | Server-side error |
| `SERVICE_UNAVAILABLE` | 503 | Plugin service not registered (plugin not loaded or group service unavailable) |

---

## Notes

- **Shop IDs** are numeric integers, not UUIDs.
- **Trade IDs** (`transactionId`) are UUIDs.
- **Group IDs** are numeric integers.
- **Metadata sanitization**: Trailing commas in JSON metadata values are stripped automatically. `ownerName` is always injected into shop metadata in API responses.
- **Player name resolution**: Names are resolved via `PlayerLookup` (online players first, then database cache). Falls back to first 8 characters of UUID if lookup unavailable.
- **Shop group service**: Group endpoints return `SERVICE_UNAVAILABLE` if `IShopGroupService` failed to initialize. All other endpoints remain functional.

---

**Source**: `src/main/java/org/fourz/BarterShops/api/ShopApiEndpointImpl.java`
**Controller (RVNKCore)**: `repos/rvnktools/toolkitplugin/.../api/BarterShopsController.java`
**Master endpoint reference**: [`docs/api/rest-endpoint-reference.md`](../../docs/api/rest-endpoint-reference.md)

**Last Updated**: March 21, 2026

# BarterShops REST API Documentation

REST API for web-based shop browsing and trade activity monitoring.

## Configuration

Enable in `config.yml`:

```yaml
api:
  enabled: true
  port: 8080
```

## Endpoints

- **GET /api/shops** - List all shops (with filters)
- **GET /api/shops/{id}** - Get shop details
- **GET /api/shops/nearby** - Find nearby shops
- **GET /api/trades/recent** - Recent trade activity
- **GET /api/stats** - Server statistics
- **GET /api/health** - Health check

## Full Documentation

See complete API documentation and examples in the project wiki.

## Implementation Status

Task feat-03 implementation complete. All core endpoints implemented with:
- Pagination and filtering
- CORS support
- API key authentication
- OpenAPI specification (`openapi.yaml`)
