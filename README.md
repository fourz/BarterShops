# BarterShops

BarterShops is a modern re-imagining of the BarterSigns plugin, providing an intuitive, item-for-item trading system in Minecraft. This plugin enables shop owners to set up barter-based transactions using chests and signs, offering a streamlined and user-friendly trading experience.

## Features

- **Chest-Based Trading:** Customers interact with chests to initiate and complete trades
- **Sign Interfaces:** Shop owners use signs to configure and manage shop transactions
- **Flexible Trade Options:** Supports item stacks, partial stacks, and non-stacking items for barter transactions
- **Security & Transaction Integrity:** Ensures fair trading mechanics and prevents duplication exploits
- **Performance Optimized:** Designed for minimal server impact and smooth transactions
- **Integration Support:** Compatible with other economy and shop plugins

## Installation

1. Download the latest release from the [Releases](https://github.com/fourz/BarterShops/releases) page
2. Place the `BarterShops.jar` file into your server's plugins folder
3. Restart the server to generate the necessary configuration files
4. Configure settings in `config.yml` as needed

## Usage

### Commands

- `/shops list` - Display all barter shops you own
- `/shops nearby` - Show shops in your vicinity
- `/shops reload` - Reload plugin configuration

### Creating a Barter Shop

1. Place a chest where items will be stored
2. Attach a sign above or next to the chest
3. Write `[barter]` on the first line of the sign
4. The sign will automatically convert to a shop if you have permission

### Shop Modes

- **Setup Mode**: Initial configuration of trade items
- **Display Mode**: Player-customized default display
- **Type Mode**: Configure shop type settings
- **Help Mode**: Display usage instructions
- **Delete Mode**: Shop removal confirmation

### Permissions

- `bartershops.create` - Allow creating shops
- `bartershops.reload` - Allow reloading configuration

## Configuration

```yaml
general:
  logLevel: INFO  # OFF, SEVERE, WARNING, INFO, DEBUG

storage:
  type: sqlite    # Storage backend type  

messages:
  generic.error: "§cError:"
  generic.help: "§6=== BarterShops Help ==="
```

## Compatibility

- **Minecraft Versions:** 1.16 - 1.20+
- **Dependencies:** Vault (optional, for economy integration)

## Development

### Project Structure

```
src/main/java/org/fourz/BarterShops/
├── command/
│   ├── CommandManager.java     # Central command handler
│   ├── BaseCommand.java        # Abstract command base
│   ├── ListCommand.java        # Shop listing command
│   ├── NearbyCommand.java      # Nearby shops command
│   └── ReloadCommand.java      # Config reload command
├── config/
│   └── ConfigManager.java      # Configuration handler
├── sign/
│   ├── SignDisplay.java        # Sign UI management
│   ├── SignManager.java        # Shop interaction logic
│   ├── SignMode.java          # Shop state enumeration
│   └── BarterSign.java        # Sign data model
├── util/
│   └── Debug.java             # Logging utilities
└── Main.java                  # Plugin entry point
```

### Key Components

- **CommandManager**: Handles `/shops` commands with permission-based access
- **ConfigManager**: Manages plugin configuration and messages
- **SignManager**: Controls shop creation, interaction, and validation
- **SignDisplay**: Handles sign text display with modern API
- **Debug**: Provides structured logging with configurable levels

### Building from Source

1. Clone the repository:
```bash
git clone https://github.com/fourz/BarterShops.git
cd BarterShops
```

2. Build the project using Maven:
```bash
mvn clean package
```

The compiled .jar file will be located in the `target/` directory.

### Development Guidelines

1. **Logging Standards**
   - Use appropriate log levels:
     - SEVERE: Critical errors
     - WARNING: Important issues
     - INFO: General operation info
     - FINE/DEBUG: Development details
   - Always include context in log messages

2. **Sign Management**
   - Use `SignSide` API for sign text manipulation
   - Support modes: SETUP, MAIN, TYPE, HELP, DELETE
   - Always call `sign.update()` after modifications

### Contributing

- Fork the repository and submit pull requests for improvements
- Report bugs and suggest features via Issues

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

## Credits

Developed and maintained by Fourz and contributors.
