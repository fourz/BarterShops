package org.fourz.BarterShops.data.connection;

import org.bukkit.plugin.Plugin;
import org.fourz.BarterShops.BarterShops;
import org.fourz.rvnkcore.RVNKCore;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Borrows the shared HikariCP pool from RVNKCore's ServiceRegistry.
 * shutdown() is a no-op — we do not own the pool.
 *
 * This class intentionally imports RVNKCore types. It is only instantiated
 * when database.mode=shared; the classloader will not touch it in standalone
 * mode, so NoClassDefFoundError cannot surface there.
 */
class SharedPoolDelegate implements PoolDelegate {

    private final BarterShops plugin;
    private ConnectionProvider borrowed;

    SharedPoolDelegate(BarterShops plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() throws SQLException {
        Plugin corePlugin = plugin.getServer().getPluginManager().getPlugin("RVNKCore");
        if (!(corePlugin instanceof RVNKCore)) {
            throw new IllegalStateException(
                "database.mode=shared but RVNKCore is not loaded. " +
                "Enable RVNKCore or set database.mode=standalone.");
        }
        RVNKCore core = (RVNKCore) corePlugin;
        borrowed = core.getService(ConnectionProvider.class);
        if (borrowed == null || !borrowed.isValid()) {
            throw new IllegalStateException(
                "database.mode=shared: RVNKCore ConnectionProvider is unavailable or not healthy.");
        }
        plugin.getLogger().info("[BarterShops] Using shared RVNKCore connection pool (" + getDatabaseType() + ")");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return borrowed.getConnection();
    }

    @Override
    public void shutdown() {
        // No-op — RVNKCore owns the pool lifecycle
        borrowed = null;
    }

    @Override
    public String getDatabaseType() {
        return borrowed != null ? borrowed.getDatabaseType() : "unknown";
    }
}
