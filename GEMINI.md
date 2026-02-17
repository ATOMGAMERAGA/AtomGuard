# GEMINI.md

## Project Overview

**AtomGuard** is a high-performance, professional exploit fixer and server protection plugin specifically designed for **Paper 1.21.4**. It provides a comprehensive suite of over 30 protection modules to mitigate crashes, duplication glitches, and packet-level exploits.

### Key Technologies
- **Java 21**: Utilizes modern Java features.
- **Paper API 1.21.4**: Targeted at the latest Paper server software.
- **PacketEvents API 2.6.0+**: Provides low-level packet interception and filtering.
- **Netty**: Used for direct pipeline injection for advanced network protection.
- **Maven**: Build and dependency management.
- **MiniMessage**: For rich, color-formatted Turkish messaging.

## Architecture

The plugin follows a modular and manager-based architecture:

### 1. Core Singleton
- **`AtomGuard`**: The central entry point (`JavaPlugin`). Orchestrates the lifecycle (`onLoad`, `onEnable`, `onDisable`) and provides access to all managers via a singleton instance.

### 2. Manager Layer
- **`ConfigManager`**: Handles `config.yml` loading, caching, and hot-reloading.
- **`MessageManager`**: Manages `messages.yml` using the MiniMessage format.
- **`LogManager`**: Provides an asynchronous logging system with daily rotation and retention policies.
- **`ModuleManager`**: Controls the lifecycle of all exploit-fixer modules, tracking statistics and managing their enabled/disabled states.

### 3. Module System
All exploit fixes are implemented as individual modules extending **`AbstractModule`**. 
- Each module's `name` corresponds to its Turkish configuration key in `config.yml`.
- Modules are registered in `AtomGuard.registerModules()`.
- They provide high-level abstractions for configuration access and logging.

### 4. Listener Layer
- **`PacketListener`**: Integrates with PacketEvents for filtering incoming and outgoing packets.
- **`BukkitListener`**: Handles standard Minecraft events (blocks, entities, etc.).
- **`InventoryListener`**: Specifically targets inventory-related exploits and duplication glitches.
- **`NettyCrashHandler`**: Injected into the server's Netty pipeline to catch low-level network exploits (e.g., NaN/Infinity coordinates).

### 5. Utility & Data Layer
- **`util/`**: Includes specialized helpers like `BookUtils`, `NBTUtils`, `PacketUtils`, `CooldownManager`, and `ItemSanitizer`.
- **`data/`**: Manages session-based data like `PlayerData` and `ChunkBookTracker`.

## Building and Running

### Requirements
- **Java 21 JDK**
- **Maven 3.8+**
- **Paper 1.21.4 Server**
- **PacketEvents Plugin** (Required dependency on the server)

### Build Commands
```bash
# Build the plugin JAR (output: target/AtomGuard-{version}.jar)
mvn clean package

# Build while skipping tests (currently no unit tests exist)
mvn clean package -DskipTests

# Update project version
mvn versions:set -DnewVersion=X.X.X
```

## Development Conventions

### General Guidelines
- **Language**: Source code is in English (class names, variables, comments), but user-facing configuration keys and messages are in **Turkish**.
- **Config Keys**: Module names in the code must exactly match their Turkish keys in `config.yml` (e.g., `cok-fazla-kitap`).
- **Thread Safety**: The plugin is designed to be highly concurrent. Use `ConcurrentHashMap`, `AtomicLong`, and `volatile` where appropriate.
- **Dependencies**: All external dependencies (Paper API, PacketEvents, Netty, Annotations) are marked as `provided` in `pom.xml`. Do not shade them into the final JAR.
- **Annotations**: Use JetBrains `@NotNull` and `@Nullable` annotations for better code safety and IDE support.

### Adding a New Protection Module
1. Create a new class in `com.atomguard.module` extending `AbstractModule`.
2. Use the Turkish config key as the name in the `super` constructor.
3. Add the corresponding configuration section in `src/main/resources/config.yml`.
4. Register the module in `AtomGuard.registerModules()`.
5. Implement the check logic in the relevant listener and call the module's validation methods.

## Directory Structure
- `src/main/java/com/atomguard/`: Source code.
  - `command/`: Command executors and tab completers.
  - `data/`: Data models and trackers.
  - `listener/`: Event and packet listeners.
  - `manager/`: Core management classes.
  - `module/`: Exploit-specific protection modules.
  - `util/`: Helper classes and specific validation checks.
- `src/main/resources/`: Configuration files and `plugin.yml`.
