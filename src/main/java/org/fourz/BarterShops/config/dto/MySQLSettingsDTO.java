package org.fourz.BarterShops.config.dto;

/**
 * DTO for MySQL connection configuration.
 * Transfers MySQL config from ConfigManager to ConnectionProviderImpl.
 */
public class MySQLSettingsDTO {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSSL;
    private final String tablePrefix;
    private final int poolSize;

    public MySQLSettingsDTO(String host, int port, String database, String username,
                             String password, boolean useSSL, String tablePrefix, int poolSize) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.useSSL = useSSL;
        this.tablePrefix = tablePrefix;
        this.poolSize = poolSize;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isUseSSL() { return useSSL; }
    public String getTablePrefix() { return tablePrefix != null ? tablePrefix : ""; }
    public int getPoolSize() { return poolSize; }
}
