# BarterShops Copilot Instructions

These guidelines must be followed when modifying or creating code for the BarterShops plugin to ensure consistency and maintainability. Reference project documentation in `docs/` for further details.

## General Directive

- **Do not create data migration methods unless explicitly requested. Assume no persistent data needs migration after schema changes.**

## Commenting Guidelines

### JavaDoc Comments

#### Class Documentation

- Clearly state the class's purpose and its role in the BarterShops system.
- Note any important design patterns or architectural decisions.
- Focus on "why" the class exists, not just "what" it does.

```java
/**
 * Handles player-to-player barter shop creation and management.
 * Centralizes all sign and chest shop logic for the BarterShops plugin.
 */
```

#### Method Documentation

- Describe the method's purpose and behavior, not implementation details.
- Document parameters, return values, and exceptions.
- Include usage examples for complex methods.

```java
/**
 * Registers a new barter shop at the specified location.
 *
 * @param player The player creating the shop
 * @param sign The sign block used for the shop
 * @throws IllegalArgumentException if the sign is invalid
 */
```

### Code Comments

- Explain "why" code exists, not just "what" it does.
- Place comments above the code they describe.
- Keep comments concise and meaningful.
- Use TODO and FIXME sparingly, with clear context.
- Clarify complex logic, business rules, or non-obvious decisions.

## Message Formatting Standards

### Player-Facing Messages

Use these standardized message prefixes:

- `&a[Success]` for successful actions
- `&c[Error]` for errors
- `&e[Warning]` for warnings
- `&b[Info]` for informational messages
- `&6[Help]` for help and usage instructions

### Console and Debug Messages

- Use the `Debug` class for all plugin logging.
- **Do not use color codes or symbols in console output.**
- Write clear, concise messages with context.
- For errors, include actionable information.
- Use appropriate log levels (INFO, WARNING, SEVERE, DEBUG).

## Logging Manager Standard

- Use the `Debug` class for all info, warning, error, and debug logging.
- Always declare as `private final Debug debug;` or similar.
- Initialize with `this.debug = new Debug(plugin, "ClassName", plugin.getDebugger().getLogLevel()) {};`
- Use `debug.info()`, `debug.warning()`, `debug.error()`, and `debug.debug()` for all logging.
- Do not use `System.out.println()` or direct logger calls.

**Example:**

```java
private final Debug debug;

public MyClass(Main plugin) {
    this.debug = new Debug(plugin, "MyClass", plugin.getDebugger().getLogLevel()) {};
}

public void doSomething() {
    debug.info("Something happened");
    debug.warning("A warning");
    debug.error("An error occurred", exception);
}
```

## Database Architecture Guidelines

- Use `DatabaseManager` as the central hub for connections.
- Implement table-specific logic in dedicated classes.
- Use DTOs for clean data flow between layers.
- All database operations should be asynchronous where possible.

## Code Structure Best Practices

### Command Implementation

- Follow the established command class hierarchy.
- Validate permissions and arguments before execution.
- Provide clear user feedback and error handling.

### Event Handling

- Register handlers in the plugin lifecycle.
- Keep event handlers focused and efficient.
- Clean up listeners on plugin disable.

### Resource Management

- Properly initialize and clean up resources.
- Use try-with-resources for closeable resources.
- Unregister listeners and cancel tasks on plugin disable.

## Performance Considerations

- Use asynchronous operations for I/O and database access.
- Implement caching for frequently accessed data.
- Batch operations when possible.
- Monitor resource usage and performance.

## Development Workflow

### Building and Testing

- Use Maven to build and package the plugin.
- Test changes on a local or staging server before deploying to Ravenkraft.
- Reload or restart the server as needed for testing.

### Copilot Usage Best Practices

- Accept Copilot suggestions only if they match project structure and standards.
- Refactor generated code to fit conventions and architecture.
- Reject code that violates schema, naming, or logging standards.
- Validate event registration and plugin lifecycle alignment.

## Documentation Reference

- [README](../README.md)
- [ROADMAP](../ROADMAP.md)
- [Overview](../docs/overview.md)
- [BarterShop Database Implementation](../docs/bartershop-database-implementation.md)


## Project Context

BarterShops is a custom plugin for the Ravenkraft Minecraft server, focused on player-driven barter trading using signs and chests. All code and documentation should reflect this context and server-specific requirements.
