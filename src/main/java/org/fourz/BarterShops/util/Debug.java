package org.fourz.BarterShops.util;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

/**
 * Debug logging utility class that provides structured logging with different severity levels.
 * 
 * Recommended Log Level Usage:
 * - SEVERE: Critical errors that prevent core functionality from working
 *          Examples: Database connection failures, configuration errors, plugin initialization failures
 * 
 * - WARNING: Important issues that don't break core functionality but need attention
 *          Examples: Deprecated feature usage, recoverable errors, performance issues
 * 
 * - INFO: General operational information about the plugin's normal functioning
 *          Examples: Plugin startup/shutdown, feature activation, major state changes
 * 
 * - FINE (DEBUG): Detailed information useful for debugging and development
 *          Examples: Method entry/exit, variable values, detailed flow control
 * 
 * - OFF: Completely disables all logging
 */
public abstract class Debug {
    private final JavaPlugin plugin;
    private final String className;
    private Level logLevel;
    private boolean debugEnabled;

    protected Debug(JavaPlugin plugin, String className, Level level) {
        this.plugin = plugin;
        this.className = className;
        setLogLevel(level);
    }

    public void info(String message) {
        log(Level.INFO, message);
    }

    private void log(Level level, String message) {
        if (shouldLog(level)) {
            // Map FINE to INFO when actually logging
            Level logLevel = (level == Level.FINE) ? Level.INFO : level;
            plugin.getLogger().log(logLevel,
                String.format("[%s] %s%s", 
                    className,
                    (level == Level.FINE) ? "[DEBUG] " : "",
                    message));
        }
    }

    public void error(String message, Throwable e) {
        log(Level.SEVERE, message);
        if (e != null && shouldLog(Level.SEVERE)) {
            e.printStackTrace();
        }
    }

    public void warning(String message) {
        log(Level.WARNING, message);
    }
     public void severe(String message) {
        log(Level.SEVERE, message);
    }

    public void debug(String message) {
        log(Level.FINE, message);
    }

    private boolean shouldLog(Level messageLevel) {
        if (logLevel == Level.OFF) {
            return false;
        }
        // Special handling for FINE (debug) level
        if (messageLevel == Level.FINE) {
            return logLevel == Level.FINE;
        }
        return messageLevel.intValue() >= logLevel.intValue();
    }

    public static Level getLevel(String levelStr) {
        if (levelStr == null) return Level.INFO;
        try {
            if (levelStr.equalsIgnoreCase("DEBUG")) {
                return Level.FINE;
            }
            return Level.parse(levelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }

    public void setLogLevel(Level level) {
        this.logLevel = level;
        debugEnabled = (level == Level.FINE);
    }

    public Level getLogLevel() {
        return logLevel;
    }
}