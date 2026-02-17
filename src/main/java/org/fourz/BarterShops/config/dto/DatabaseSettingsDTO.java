package org.fourz.BarterShops.config.dto;

/**
 * DTO for database configuration settings.
 * Encapsulates database type and connection settings into a single validated
 * object created by ConfigManager and injected into ConnectionProviderImpl.
 */
public class DatabaseSettingsDTO {
    public enum DatabaseType { MYSQL, SQLITE }

    private final DatabaseType type;
    private final MySQLSettingsDTO mysqlSettings;
    private final SQLiteSettingsDTO sqliteSettings;

    public DatabaseSettingsDTO(DatabaseType type, MySQLSettingsDTO mysqlSettings,
                                SQLiteSettingsDTO sqliteSettings) {
        this.type = type;
        this.mysqlSettings = mysqlSettings;
        this.sqliteSettings = sqliteSettings;
    }

    /**
     * Validates the configuration. Throws IllegalArgumentException if invalid.
     */
    public void validate() {
        if (type == null) throw new IllegalArgumentException("Database type must be specified");
        if (type == DatabaseType.MYSQL && mysqlSettings == null)
            throw new IllegalArgumentException("MySQL settings required when type is mysql");
        if (type == DatabaseType.SQLITE && sqliteSettings == null)
            throw new IllegalArgumentException("SQLite settings required when type is sqlite");
    }

    public DatabaseType getType() { return type; }
    public MySQLSettingsDTO getMysqlSettings() { return mysqlSettings; }
    public SQLiteSettingsDTO getSqliteSettings() { return sqliteSettings; }

    /** Convenience: true when type is MYSQL */
    public boolean isMySQL() { return type == DatabaseType.MYSQL; }

    /** Get the table prefix for whichever backend is active */
    public String getTablePrefix() {
        if (type == DatabaseType.MYSQL && mysqlSettings != null) return mysqlSettings.getTablePrefix();
        if (type == DatabaseType.SQLITE && sqliteSettings != null) return sqliteSettings.getTablePrefix();
        return "";
    }
}
