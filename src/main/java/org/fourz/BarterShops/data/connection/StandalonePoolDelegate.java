package org.fourz.BarterShops.data.connection;

import org.fourz.BarterShops.BarterShops;
import org.fourz.bartershops.shaded.hikari.HikariConfig;
import org.fourz.bartershops.shaded.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Own HikariCP pool built from storage.mysql.* config using shaded HikariCP.
 * Zero RVNKCore imports — safe to load when RVNKCore is absent.
 */
class StandalonePoolDelegate implements PoolDelegate {

    private final BarterShops plugin;
    private HikariDataSource dataSource;
    private final String databaseType;

    StandalonePoolDelegate(BarterShops plugin) {
        this.plugin = plugin;
        this.databaseType = plugin.getConfigManager().getStorageType();
    }

    @Override
    public void initialize() throws SQLException {
        HikariConfig cfg = new HikariConfig();

        if ("mysql".equalsIgnoreCase(databaseType)) {
            var mysql = plugin.getConfigManager().getDatabaseSettings().getMysqlSettings();
            cfg.setJdbcUrl("jdbc:mysql://" + mysql.getHost() + ":" + mysql.getPort()
                + "/" + mysql.getDatabase()
                + "?useSSL=" + mysql.isUseSSL()
                + "&allowPublicKeyRetrieval=true"
                + "&characterEncoding=UTF-8");
            cfg.setUsername(mysql.getUsername());
            cfg.setPassword(mysql.getPassword());
            cfg.setMaximumPoolSize(mysql.getPoolSize());
            cfg.setMinimumIdle(2);
            cfg.setIdleTimeout(300_000L);
            cfg.setMaxLifetime(580_000L);
            cfg.setConnectionTimeout(30_000L);
            cfg.addDataSourceProperty("cachePrepStmts", "true");
            cfg.addDataSourceProperty("prepStmtCacheSize", "250");
            cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            cfg.addDataSourceProperty("useServerPrepStmts", "true");
            cfg.addDataSourceProperty("rewriteBatchedStatements", "true");
        } else {
            var sqlite = plugin.getConfigManager().getDatabaseSettings().getSqliteSettings();
            cfg.setJdbcUrl("jdbc:sqlite:" + sqlite.getFilePath());
            cfg.setMaximumPoolSize(1);
        }

        cfg.setPoolName("BarterShops-" + databaseType.toUpperCase() + "-Pool");
        cfg.setConnectionTestQuery("SELECT 1");

        try {
            dataSource = new HikariDataSource(cfg);
        } catch (Exception e) {
            throw new SQLException("Failed to initialize standalone connection pool: " + e.getMessage(), e);
        }

        plugin.getLogger().info("[BarterShops] Standalone pool initialized (" + databaseType + ")");
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Standalone connection pool is not initialized");
        }
        return dataSource.getConnection();
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        dataSource = null;
    }

    @Override
    public String getDatabaseType() {
        return databaseType;
    }
}
