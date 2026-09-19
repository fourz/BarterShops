package org.fourz.BarterShops.data.repository.impl;

import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.config.ConfigManager;
import org.fourz.BarterShops.data.connection.PoolDelegate;
import org.fourz.rvnkcore.config.dto.DatabaseSettingsDTO;
import org.fourz.rvnkcore.util.log.LogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared mode while RVNKCore has fallen back to SQLite: BarterShops' own config still says mysql,
 * but the schema must follow the pool it is actually handed (#2103).
 */
class ConnectionProviderSqliteFallbackTest {

    @TempDir
    Path dir;

    @Test
    void mysqlConfigOnSqlitePoolBuildsSqliteSchema() throws Exception {
        BarterShops plugin = mock(BarterShops.class);
        ConfigManager config = mock(ConfigManager.class);
        DatabaseSettingsDTO settings = mock(DatabaseSettingsDTO.class);
        when(plugin.getConfigManager()).thenReturn(config);
        when(config.getDatabaseSettings()).thenReturn(settings);
        when(settings.isMySQL()).thenReturn(true);
        when(settings.getTablePrefix()).thenReturn("bs_");

        String url = "jdbc:sqlite:" + dir.resolve("core.db");
        ConnectionProviderImpl provider = new ConnectionProviderImpl(plugin, mock(LogManager.class));
        provider.initializeWith(sqlitePool(url));
        provider.initializeWith(sqlitePool(url)); // a second start must be a no-op

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name LIKE 'bs_%'")) {
            assertEquals(9, rs.getInt(1));
        }
    }

    private static PoolDelegate sqlitePool(String url) {
        return new PoolDelegate() {
            @Override public void initialize() { }
            @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url); }
            @Override public void shutdown() { }
            @Override public String getDatabaseType() { return "sqlite"; }
        };
    }
}
