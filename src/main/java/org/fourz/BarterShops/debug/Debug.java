package org.fourz.BarterShops.debug;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.HashMap;
import java.io.StringWriter;
import java.io.PrintWriter;

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
    private static final AtomicInteger errorCount = new AtomicInteger(0);
    
    // Performance monitoring
    private static final Map<String, AtomicLong> operationTimers = new HashMap<>();
    private static final Map<String, Long> operationStartTimes = new HashMap<>();
    
    // Operation timing thresholds (in ms)
    private static final long WARNING_THRESHOLD = 1000; // 1 second
    private static final long SEVERE_THRESHOLD = 5000;  // 5 seconds

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
            // Map FINE to INFO when actually logging to ensure visibility
            Level logLevel = (level == Level.FINE) ? Level.INFO : level;
            plugin.getLogger().log(logLevel,
                String.format("[%s] %s%s", 
                    className,
                    (level == Level.FINE) ? "[DEBUG] " : "",
                    message));
        }
    }

    public void error(String message, Throwable e) {
        errorCount.incrementAndGet();
        log(Level.SEVERE, message);
        if (e != null && shouldLog(Level.SEVERE)) {
            logException(e);
        }
    }
    
    /**
     * Log a detailed exception with stack trace and cause analysis
     */
    private void logException(Throwable e) {
        // Standard stack trace
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        
        log(Level.SEVERE, "Exception details: " + e.getMessage());
        log(Level.SEVERE, "Stack trace: \n" + sw.toString());
        
        // Root cause analysis
        Throwable cause = e.getCause();
        if (cause != null) {
            log(Level.SEVERE, "Caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
        }
        
        // Check for common issues
        String stackTrace = sw.toString();
        
        if (stackTrace.contains("NoClassDefFoundError") || stackTrace.contains("ClassNotFoundException")) {
            log(Level.SEVERE, "DIAGNOSTIC: Missing dependency detected");
            log(Level.SEVERE, "SOLUTION: Ensure all required libraries are properly included in the plugin");
        }
        else if (stackTrace.contains("ConcurrentModificationException")) {
            log(Level.SEVERE, "DIAGNOSTIC: Thread safety issue detected - data was modified while being iterated");
            log(Level.SEVERE, "SOLUTION: Use synchronized blocks or thread-safe collections when working with shared data");
        }
        else if (stackTrace.contains("OutOfMemoryError")) {
            log(Level.SEVERE, "DIAGNOSTIC: Server ran out of memory");
            log(Level.SEVERE, "SOLUTION: Increase server memory allocation or optimize plugin resource usage");
        }
        else if (stackTrace.contains("NullPointerException")) {
            log(Level.SEVERE, "DIAGNOSTIC: Null value encountered - check for proper initialization");
            log(Level.SEVERE, "SOLUTION: Add null checks and ensure proper object initialization");
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
    
    /**
     * Start timing an operation
     * 
     * @param operationName Name of the operation to time
     */
    public void startTiming(String operationName) {
        if (!debugEnabled) return;
        
        String key = className + "." + operationName;
        operationStartTimes.put(key, System.currentTimeMillis());
        debug("Starting operation: " + operationName);
    }
    
    /**
     * End timing an operation and log the results
     * 
     * @param operationName Name of the operation that was timed
     * @return The duration in milliseconds
     */
    public long endTiming(String operationName) {
        if (!debugEnabled) return 0;
        
        String key = className + "." + operationName;
        Long startTime = operationStartTimes.remove(key);
        
        if (startTime == null) {
            warning("Tried to end timing for operation that wasn't started: " + operationName);
            return 0;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Get or create the counter for this operation
        AtomicLong counter = operationTimers.computeIfAbsent(key, k -> new AtomicLong(0));
        counter.incrementAndGet();
        
        // Log based on duration
        if (duration > SEVERE_THRESHOLD) {
            severe("Operation '" + operationName + "' took " + duration + "ms - THIS IS CAUSING SERVER LAG");
            logOperationWarning(operationName, duration);
        } else if (duration > WARNING_THRESHOLD) {
            warning("Operation '" + operationName + "' took " + duration + "ms - this may impact performance");
        } else {
            debug("Operation '" + operationName + "' completed in " + duration + "ms");
        }
        
        return duration;
    }
    
    /**
     * Log detailed warning information for slow operations
     */
    private void logOperationWarning(String operationName, long duration) {
        if (operationName.contains("lore") || operationName.contains("quest") || operationName.contains("dialog")) {
            severe("DIAGNOSTIC: Lore system operation '" + operationName + "' is taking too long");
            severe("SOLUTION: Consider using async methods or caching frequently accessed lore data");
            severe("IMPACT: This operation blocks the main server thread and can cause timeouts");
        } 
        else if (operationName.contains("inventory") || operationName.contains("player")) {
            severe("DIAGNOSTIC: Player data operation '" + operationName + "' is taking too long");
            severe("SOLUTION: Consider using caching or database connection pooling to improve access times");
        }
        else if (operationName.contains("save") || operationName.contains("config")) {
            severe("DIAGNOSTIC: File I/O operation '" + operationName + "' is taking too long");
            severe("SOLUTION: Consider moving file operations to an async thread");
        }
    }
    
    /**
     * Log a potential deadlock or thread blocking issue
     * 
     * @param operation The operation that might be causing blocking
     * @param details Additional details about the situation
     */
    public void logPotentialBlockingIssue(String operation, String details) {
        severe("THREAD BLOCKING DETECTED: " + operation);
        severe("DETAILS: " + details);
        severe("SOLUTION: This operation should be moved to an async thread or chunked into smaller operations");
        
        // Log current thread state
        Thread currentThread = Thread.currentThread();
        severe("Current thread: " + currentThread.getName() + " (ID: " + currentThread.getId() + ", State: " + currentThread.getState() + ")");
        
        // Get stack trace for the current thread
        StackTraceElement[] stackTrace = currentThread.getStackTrace();
        StringBuilder traceBuilder = new StringBuilder("Stack trace:\n");
        for (int i = 1; i < Math.min(10, stackTrace.length); i++) { // Skip first element (the getStackTrace call)
            traceBuilder.append("  at ").append(stackTrace[i]).append("\n");
        }
        severe(traceBuilder.toString());
    }

    private boolean shouldLog(Level messageLevel) {
        if (logLevel == Level.OFF) {
            return false;
        }
        
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

    public static Debug createDebugger(JavaPlugin plugin, String className, Level level) {
        return new Debug(plugin, className, level) {};
    }

    public boolean isDebugLevel(Level level) {
        return shouldLog(level);
    }
    
    public static int getErrorCount() {
        return errorCount.get();
    }
    
    public static void resetErrorCount() {
        errorCount.set(0);
    }
}