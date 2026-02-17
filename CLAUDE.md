# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AtomGuard is an enterprise-grade Minecraft Paper 1.21.4 exploit fixer plugin written in Java 21. It detects and blocks 40+ different exploit types (crashers, duplication glitches, packet exploits, bot attacks) using PacketEvents 2.6.0+ for packet-level interception. The project is structured as a Maven multi-module build with a public API. All user-facing text is in Turkish.

## Build Commands

```bash
# Build all modules (output: core/target/AtomGuard-{version}.jar)
mvn clean package

# Build skipping tests
mvn clean package -DskipTests

# Update version (all modules)
mvn versions:set -DnewVersion=X.X.X
```

Requires Java 21 JDK and Maven 3.8+. No unit tests exist currently — CI validates successful compilation only.

## Maven Multi-Module Structure

```
AtomGuard/              (parent POM — packaging: pom)
├── api/                   (atomguard-api — public interfaces, no shading)
├── core/                  (atomguard-core — main plugin JAR, shades HikariCP/Jedis/SLF4J)
└── velocity/              (atomguard-velocity — Velocity proxy module, placeholder)
```

- **Parent POM** (`AtomGuard-parent`): defines `dependencyManagement`, `pluginManagement`, shared properties and repositories
- **api module**: only depends on paper-api and annotations (provided). Produces a clean JAR with public interfaces
- **core module**: depends on api (compile), paper-api, packetevents, netty (provided), HikariCP, Jedis, SLF4J (compile, shaded with relocation)
- **velocity module**: placeholder for Sprint 6

### Shade Relocations (core only)
- `com.zaxxer.hikari` → `com.atomguard.lib.hikari`
- `redis.clients` → `com.atomguard.lib.jedis`
- `org.apache.commons.pool2` → `com.atomguard.lib.pool2`
- `org.slf4j` → `com.atomguard.lib.slf4j`

## Architecture

**Singleton entry point:** `AtomGuard` (`core/.../AtomGuard.java`) — orchestrates lifecycle: `onLoad()` initializes PacketEvents API, `onEnable()` boots managers/listeners/commands and initializes the public API, `onDisable()` tears down. Access via `AtomGuard.getInstance()`.

**Public API:** `AtomGuardAPI` singleton (`api/.../AtomGuardAPI.java`) initialized in `onEnable()`. Provides `IModuleManager`, `IStorageProvider`, `IStatisticsProvider`, `IReputationService` interfaces. Other plugins depend on `atomguard-api` (provided scope).

**Manager layer** (7 managers, all accessed via getters on the main plugin class):
- `ConfigManager` — YAML config loading with caching and hot-reload
- `MessageManager` — MiniMessage format message rendering and permission-based sending
- `LogManager` — Async file writing with daily rotation and 7-day retention
- `ModuleManager` (implements `IModuleManager`) — Module registration, lifecycle, statistics
- `AttackModeManager` — Real-time attack detection and response
- `StatisticsManager` (implements `IStatisticsProvider`) — Persistent JSON statistics
- `DiscordWebhookManager` — Discord notification integration

**Module system:** All 40+ exploit fixers extend `AbstractModule` (implements `IModule`). Each module has a `name` matching its config key under `moduller.{name}` in `config.yml`. Modules use helper methods (`getConfigBoolean`, `getConfigInt`, etc.) that auto-prefix the config path. New modules must be registered in `AtomGuard.registerModules()`.

**Listener layer** (4 listeners):
- `PacketListener` — PacketEvents API integration for packet filtering
- `BukkitListener` — Standard Bukkit block/entity events
- `InventoryListener` — Inventory click/close events
- `NettyCrashHandler` — Netty pipeline protection

**Custom Bukkit Events** (in api module):
- `ExploitBlockedEvent` — fired when any exploit is blocked (cancellable, async)
- `AttackModeToggleEvent` — fired when attack mode changes state (async)
- `PlayerReputationCheckEvent` — fired during IP reputation check (cancellable, async)
- `ModuleToggleEvent` — fired when a module is toggled (cancellable)

## Key Conventions

- **Config keys are in Turkish** (e.g., `moduller.cok-fazla-kitap.aktif`, `moduller.paket-exploit.max-paket-boyutu`). The module `name` field must match the Turkish config key exactly.
- **Thread safety:** Uses `ConcurrentHashMap` and `AtomicLong` throughout. Module `enabled` state is `volatile`.
- **PacketEvents is a required dependency** (scope: provided). The plugin disables itself if PacketEvents is not present. PacketEvents is loaded in `onLoad()` and initialized in `onEnable()`.
- **Resource filtering is enabled** in core Maven module — `${project.version}` in `plugin.yml` is substituted at build time.
- **Shaded dependencies** in core: HikariCP, Jedis, commons-pool2, SLF4J are shaded with relocation to avoid classpath conflicts.
- **API interfaces must remain stable** — changes to `api/` module interfaces affect downstream plugins.

## Package Structure

```
api/src/main/java/com.atomguard.api
├── AtomGuardAPI.java          # Public API singleton
├── IReputationService.java       # IP reputation interface
├── module/                       # IModule, IModuleManager
├── storage/                      # IStorageProvider
├── stats/                        # IStatisticsProvider
└── event/                        # ExploitBlockedEvent, AttackModeToggleEvent, etc.

core/src/main/java/com.atomguard
├── AtomGuard.java             # Main plugin singleton
├── command/                      # AtomGuardCommand, AtomGuardTabCompleter, PanicCommand
├── data/                         # PlayerData, ChunkBookTracker, VerifiedPlayerCache
├── heuristic/                    # HeuristicEngine, HeuristicProfile
├── listener/                     # PacketListener, BukkitListener, InventoryListener, NettyCrashHandler
├── manager/                      # ConfigManager, MessageManager, LogManager, ModuleManager, AttackModeManager, StatisticsManager, DiscordWebhookManager
├── module/                       # AbstractModule + 40 concrete exploit fixer modules
├── reputation/                   # IPReputationManager
├── util/                         # CooldownManager, PacketUtils, NBTUtils, BookUtils, etc.
│   └── checks/                   # EnchantmentCheck, AttributeCheck, SkullCheck, FoodCheck
└── web/                          # WebPanel
```

## Adding a New Module

1. Create a class in `core/src/main/java/com/atomguard/module/` extending `AbstractModule`
2. Pass the Turkish config key as the `name` parameter to super constructor
3. Add corresponding config section under `moduller.{name}` in `core/src/main/resources/config.yml`
4. Register the module in `AtomGuard.registerModules()`
5. Wire the module's check logic into the appropriate listener (`PacketListener`, `BukkitListener`, or `InventoryListener`)

## Adding a New API Interface

1. Define the interface in `api/src/main/java/com/atomguard/api/`
2. Add it to `AtomGuardAPI` constructor and getter
3. Implement it in the core module
4. Wire the implementation in `AtomGuard.initializeAPI()`
