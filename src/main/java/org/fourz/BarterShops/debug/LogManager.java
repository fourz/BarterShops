package org.fourz.BarterShops.debug;

import org.fourz.BarterShops.BarterShops;
import java.util.logging.Level;
import java.util.Map;
import java.util.HashMap;

/**
 * Centralized persistent logging manager for the RVNKLore plugin.
 * <p>
 * This class wraps the Debug class and provides unified logging methods for all components.
 * It ensures consistent log level and message formatting across the system, and manages
 * separate instances per class name to maintain proper logging context.
 * <p>
 * Usage pattern (standardized):
 * <pre>
 * private final LogManager logger;
 *
 * public MyClass(RVNKLore plugin) {
 *     this.logger = LogManager.getInstance(plugin, "MyClass");
 * }
 *
 * public void doSomething() {
 *     logger.info("Something happened");
 *     logger.warning("A warning");
 *     logger.error("An error occurred", exception);
 * }
 * </pre>
 * <p>
 * All info, warning, and error messages should use this manager. Use the Debug class only for debug-level or trace logging.
 *
 * @author Fourz
 */
public class LogManager {
    private static final Map<String, LogManager> instances = new HashMap<>();
    private final BarterShops plugin;
    private final String className;
    private Debug debug;

    private LogManager(BarterShops plugin, String className) {
        this.plugin = plugin;
        this.className = className;
        // Initialize debug lazily to avoid circular dependency
        initializeDebug();
    }    /**
     * Initialize the Debug instance, handling potential circular dependency issues.
     */
    private void initializeDebug() {
        try {
            // Use INFO as default, ConfigManager will update this after initialization
            Level logLevel = Level.INFO;
            this.debug = Debug.createDebugger(plugin, className, logLevel);
        } catch (Exception e) {
            // Fallback for any initialization issues
            this.debug = Debug.createDebugger(plugin, className, Level.INFO);
        }
    }

    /**
     * Get the LogManager instance for the specific plugin and class context.
     *
     * @param plugin The plugin instance
     * @param className The class name for logging context
     * @return The LogManager instance for the specified class
     */
    public static synchronized LogManager getInstance(BarterShops plugin, String className) {
        LogManager instance = instances.get(className);
        if (instance == null) {
            instance = new LogManager(plugin, className);
            instances.put(className, instance);
        }
        return instance;
    }

    /**
     * Log an informational message.
     * @param message The message to log
     */
    public void info(String message) {
        debug.info(message);
    }

    /**
     * Log a warning message.
     * @param message The message to log
     */
    public void warning(String message) {
        debug.warning(message);
    }

    /**
     * Log an error message with an exception.
     * @param message The error message
     * @param t The throwable to log
     */
    public void error(String message, Throwable t) {
        debug.error(message, t);
    }

    /**
     * Log a debug message.
     * @param message The message to log
     */
    public void debug(String message) {
        debug.debug(message);
    }    /**
     * Optionally expose the underlying Debug instance for advanced usage.
     * @return The underlying Debug instance
     */
    public Debug getDebug() {
        return debug;
    }

    /**
     * Set the log level for this LogManager instance.
     * @param level The new log level
     */
    public void setLogLevel(Level level) {
        if (debug != null) {
            debug.setLogLevel(level);
        }
    }

    /**
     * Get the current log level for this LogManager instance.
     * @return The current log level
     */
    public Level getLogLevel() {
        if (debug != null) {
            return debug.getLogLevel();
        }
        return Level.INFO; // Default fallback
    }

    /**
     * Update log level for all LogManager instances.
     * This is called when the global configuration changes.
     * @param level The new log level to apply to all instances
     */
    public static synchronized void updateAllLogLevels(Level level) {
        for (LogManager instance : instances.values()) {
            instance.setLogLevel(level);
        }
    }

    /**
     * Get the class name this LogManager instance is associated with.
     * @return The class name
     */
    public String getClassName() {
        return className;
    }
}
