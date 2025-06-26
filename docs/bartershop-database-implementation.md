# BarterShop Database Implementation

This document outlines how BarterShops accesses and manages data in a dialect-agnostic manner, ensuring flexibility across different SQL backends (SQLite, MySQL, etc.).

## Overview
- All database operations are channeled through a standard interface, allowing different implementations (MySQL, SQLite) without changing client code.
- Connections are established when the plugin starts, and gracefully closed on plugin shutdown.
- Table schemas and setup logic reside in each implementation class.

## Usage Patterns
1. Obtain the active DatabaseManager by calling `DatabaseFactory.getDatabaseManager()`.
2. Use `getConnection()` for queries. Always handle `SQLException` within a try-with-resources.
3. Use `setupTables()` once on server startup to ensure required tables exist.
4. Implement any additional table-specific logic in separate classes to keep concerns isolated.

## Best Practices
- Handle concurrency: perform I/O or large queries asynchronously when performance is critical.
- Maintain consistent logging for successful queries and errors.
- Keep statements short and clear with prepared statements to prevent SQL injection.

## Example Flow
1. On plugin enable:
   - `DatabaseFactory.initialize(plugin)` → decides the SQL dialect from config (`mysql` or `sqlite`).
   - Connection established; tables are created if missing.
2. During runtime:
   - `DatabaseFactory.getDatabaseManager().getConnection()` → get the connection.
   - Execute queries within a try-with-resources block:
     ```java
     try (var conn = dbManager.getConnection();
          var stmt = conn.prepareStatement("SELECT * FROM shops")) {
         ResultSet rs = stmt.executeQuery();
         // ... fetch data ...
     }
     ```
3. On plugin disable:
   - `DatabaseFactory.getDatabaseManager().disconnect()` → closes connections.

