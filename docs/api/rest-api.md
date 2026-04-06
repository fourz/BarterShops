---
title: BarterShops REST API Reference
plugin: BarterShops
category: api
tags: [rest, api, http, endpoints, groups, shops, trades]
created: 2026-04-04
updated: 2026-04-04
version: 1.1.23
---

# BarterShops REST API Reference

REST endpoints provided by BarterShops via RVNKCore's embedded Jetty server.

## Base URL

```
https://<server>:<port>/api/bartershops
```

## Authentication

All requests require the `X-API-Key` header:

```
X-API-Key: <configured-api-key>
```

## Response Envelope

All responses use the standard `ApiResponse` wrapper:

```json
{
  "success": true,
  "data": { ... },
  "page": 1,
  "limit": 20,
  "total": 42
}
```

```json
{
  "success": false,
  "error": {
    "code": "NOT_FOUND",
    "message": "Group with ID 99 not found"
  }
}
```

---

## Shops

### `GET /shops`

List all active shops with optional filters and pagination.

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `owner` | UUID | — | Filter by owner UUID |
| `type` | string | — | Filter by shop type (`BARTER`, `BUY`, `SELL`, `ADMIN`) |
| `world` | string | — | Filter by world name |
| `sort` | string | `createdAt` | Sort field |
| `order` | string | `desc` | `asc` or `desc` |
| `page` | int | `1` | Page number |
| `limit` | int | `20` | Results per page (max 100) |

**Response** — paginated array of `ShopDataDTO`

---

### `GET /shops/{id}`

Get a single shop by numeric ID.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | int | Shop ID |

**Response** — single `ShopDataDTO`

---

### `GET /shops/nearby`

Find shops within a radius of given coordinates.

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `world` | string | ✓ | World name |
| `x` | double | ✓ | X coordinate |
| `y` | double | ✓ | Y coordinate |
| `z` | double | ✓ | Z coordinate |
| `radius` | double | — | Search radius in blocks (default `50`, max `500`) |

**Response** — array of `ShopDataDTO`

---

## Trades

### `GET /trades/recent`

Get recent trade activity.

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `limit` | int | Number of records (default `20`) |
| `shop` | int | Filter by shop ID |
| `player` | UUID | Filter by player UUID (buyer or seller) |

**Response** — array of enriched `TradeRecordDTO` with `buyerName` and `sellerName` resolved

---

### `GET /trades/{id}`

Get a single trade record by transaction ID.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | string | Transaction UUID |

**Response** — single `TradeRecordDTO`

---

## Statistics

### `GET /stats`

Server-wide statistics.

**Response**

```json
{
  "totalShops": 42,
  "activeShops": 38,
  "totalTrades": 1204,
  "shopsByType": {
    "BARTER": 35,
    "BUY": 4,
    "SELL": 3
  }
}
```

---

### `GET /stats/shops/{id}`

Statistics for a specific shop.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | int | Shop ID (omit for aggregate) |

**Response**

```json
{
  "shopId": 39,
  "shopName": "My Shop",
  "ownerUuid": "...",
  "totalTrades": 17,
  "isActive": true,
  "createdAt": "2026-03-01T12:00:00Z"
}
```

---

### `GET /health`

Service health check. Does not require authentication.

**Response**

```json
{
  "status": "healthy",
  "fallbackMode": false,
  "database": "mysql",
  "uptime": 3600000,
  "timestamp": "2026-04-04T08:00:00Z"
}
```

`status` is `"degraded"` when the repository is in SQLite fallback mode.

---

## Groups

### `GET /groups`

List shop groups for a player.

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `owner` | UUID | ✓ | Player UUID whose groups to list |
| `world` | string | — | Filter by world name |
| `page` | int | — | Page number (default `1`) |
| `limit` | int | — | Results per page (default `20`, max `100`) |

**Response** — paginated array of enriched group objects including `ownerName`, `coOwners`, `shopCount`

---

### `GET /groups/{id}`

Get a group by ID with full shop list and co-owner details.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | int | Group ID |

**Response**

```json
{
  "groupId": 1,
  "groupName": "Main Market",
  "ownerUuid": "...",
  "ownerName": "wizardofire",
  "world": "world",
  "isActive": true,
  "createdAt": "2026-03-01T12:00:00Z",
  "lastModified": "2026-04-01T10:00:00Z",
  "coOwners": [
    { "uuid": "...", "name": "antnachos" }
  ],
  "shops": [ ... ],
  "shopCount": 7
}
```

---

### `POST /groups/{id}/coowners`

Add a player as co-owner of a group. Requester must be the group owner.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | int | Group ID |

**Request Body**

```json
{
  "requesterUuid": "550e8400-e29b-41d4-a716-446655440000",
  "coOwnerUuid":   "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
}
```

**Response**

```json
{
  "groupId": 1,
  "coOwnerUuid": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "coOwnerName": "antnachos",
  "action": "added"
}
```

**Error Codes**

| Code | HTTP | Meaning |
|------|------|---------|
| `FORBIDDEN` | 403 | `requesterUuid` is not the group owner |
| `NOT_FOUND` | 404 | Group not found |
| `INVALID_REQUEST` | 400 | Missing or malformed UUID fields |

---

### `DELETE /groups/{id}/coowners/{coOwnerUuid}`

Remove a co-owner from a group. Requester must be the group owner.

**Path Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | int | Group ID |
| `coOwnerUuid` | UUID | UUID of the co-owner to remove |

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `requesterUuid` | UUID | ✓ | UUID of the player making the request |

**Response**

```json
{
  "groupId": 1,
  "coOwnerUuid": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "action": "removed"
}
```

**Error Codes**

| Code | HTTP | Meaning |
|------|------|---------|
| `FORBIDDEN` | 403 | `requesterUuid` is not the group owner |
| `NOT_FOUND` | 404 | Group not found |
| `INVALID_REQUEST` | 400 | Missing or malformed UUID |

---

## Error Reference

| Code | Typical HTTP Status | Meaning |
|------|--------------------|---------| 
| `INVALID_REQUEST` | 400 | Missing required parameter or malformed value |
| `FORBIDDEN` | 403 | Requester lacks permission for the operation |
| `NOT_FOUND` | 404 | Resource does not exist |
| `SERVICE_UNAVAILABLE` | 503 | BarterShops plugin not loaded or group service offline |
| `INTERNAL_ERROR` | 500 | Unexpected server-side failure |

---

## WebUI Integration Notes

- All requests must be made server-side (Server Actions in Next.js). Never expose `X-API-Key` via `NEXT_PUBLIC_`.
- `requesterUuid` for mutation endpoints comes from the authenticated NextAuth session (player UUID).
- Player UUID lookup for the co-owner add flow: use `GET /api/v1/players?name={username}` (RVNKCore native endpoint).
- See fourz/Ravenkaft-Dev#595 for the WebUI co-owner management UI task.
