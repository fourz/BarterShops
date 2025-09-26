package org.fourz.BarterShops.data;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseManager {
    void connect() throws SQLException;
    void disconnect() throws SQLException;
    Connection getConnection();
    void setupTables() throws SQLException;
}
