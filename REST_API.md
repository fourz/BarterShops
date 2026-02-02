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
