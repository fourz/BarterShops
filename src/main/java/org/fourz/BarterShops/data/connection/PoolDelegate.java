package org.fourz.BarterShops.data.connection;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Internal abstraction for pool lifecycle. ConnectionProviderImpl delegates
 * to either SharedPoolDelegate (borrows RVNKCore pool) or StandalonePoolDelegate
 * (own shaded HikariCP pool) based on database.mode config.
 */
public interface PoolDelegate {
    void initialize() throws SQLException;
    Connection getConnection() throws SQLException;
    void shutdown();
    String getDatabaseType();
}
