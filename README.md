<p align="center">
  <img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard Logo" width="240">
</p>

<h1 align="center">⚛️ AtomGuard</h1>

<p align="center">
  <strong>Multi-layered Minecraft server security plugin for Paper 1.21.4 + Velocity</strong>
</p>

<p align="center">
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/releases/latest">
    <img src="https://img.shields.io/github/v/release/ATOMGAMERAGA/AtomGuard?style=flat-square&color=5865F2&label=latest" alt="Latest Release">
  </a>
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/actions">
    <img src="https://img.shields.io/github/actions/workflow/status/ATOMGAMERAGA/AtomGuard/build.yml?branch=main&style=flat-square&label=build" alt="Build">
  </a>
  <img src="https://img.shields.io/badge/java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/paper-1.21.4-00AA00?style=flat-square" alt="Paper 1.21.4">
  <img src="https://img.shields.io/badge/velocity-3.x-7B2FBE?style=flat-square" alt="Velocity">
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/ATOMGAMERAGA/AtomGuard?style=flat-square&color=lightgrey" alt="License">
  </a>
</p>

<p align="center">
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/releases">📦 Downloads</a> &nbsp;|&nbsp;
  <a href="CHANGELOG.md">📋 Changelog</a>
</p>

---

AtomGuard is an open-source, enterprise-grade security plugin that protects your Minecraft server against **DDoS attacks**, **bot floods**, **crash exploits**, and **duplication glitches**. It runs on both Paper 1.21.4 and Velocity proxy, stopping threats before they ever reach your backend.

---

## Table of Contents

- [Why AtomGuard?](#-why-atomguard)
- [What's New in v2.0](#-whats-new-in-v20)
- [Velocity Proxy Module](#-velocity-proxy-module)
- [Core Module (Paper)](#-core-module-paper)
- [Requirements](#-requirements)
- [Installation](#-installation)
- [Commands & Permissions](#-commands--permissions)
- [Developer API](#-developer-api)
- [Building](#-building)
- [Architecture](#-architecture)
- [License](#-license)

---

## 🚀 Why AtomGuard?

| Feature | AtomGuard | Other Plugins |
|---|---|---|
| Velocity Proxy Protection | ✅ 12+ modules, fully integrated | ❌ None / very limited |
| DDoS & Bot Protection | ✅ 5-level attack management | ⚠️ Basic |
| VPN / Proxy Detection | ✅ 7-provider consensus chain | ⚠️ 1–2 APIs |
| IPTables Integration | ✅ Kernel-level blocking | ❌ None |
| Real-Time Threat Score | ✅ Multi-factor scoring | ❌ None |
| Crash & Dupe Protection | ✅ 44+ modules | ⚠️ 10–20 modules |
| Forensics & Attack Recording | ✅ Full session capture | ❌ None |
| Traffic Intelligence Engine | ✅ EWMA + Isolation Forest | ❌ None |
| Trust Score System | ✅ Per-player, persistent | ❌ None |
| Developer API | ✅ Maven artifact | ❌ None |
| Config Language | ✅ English (v2.0.2+) | — |

---

## ✨ What's New in v2.0

### v2.0.2 — Full English Translation
- All config keys, YAML sections, and Java source strings translated from Turkish to English
- Default language is now `en`; config keys renamed across all 44+ modules and managers
- Automatic migration step for existing installations (no manual config edits needed)

### v2.0.1 — Async Connection Pipeline
- `ConnectionListener.onPreLogin()` now returns `EventTask.resumeWhenComplete()` — eliminates the 5-second blocking that caused player timeouts
- VPNCheck and AccountFirewallCheck migrated from `.get()` to non-blocking `CompletableFuture` chains with timeouts
- `ConnectionCheck` interface gains `checkAsync()` default method; full `processAsync()` pipeline

### v2.0.0 — Major Release
- **Extended Public API** — `ITrustService`, `IForensicsService`, `IConnectionPipeline` exposed via `AtomGuardAPI`
- **Forensics System** — `ForensicsManager` / `PacketRecorder` / `RecordingSession` record attack-moment packet sessions to disk
- **Traffic Intelligence Engine** — `AdaptiveThresholdManager`, `EWMADetector`, `IsolationForestDetector` for adaptive anomaly detection
- **Notification Manager** — multi-provider routing (Discord, Telegram, Slack, console)
- **Trust Score Manager** — per-player persistent trust scores with tier system and JSON persistence
- **Executor Manager** — shared thread pool management across the plugin
- **Web Panel** — JWT authentication, API handlers, SSE live feed, geo-map dashboard
- **Config Migration** — automatic 1.x → 2.0 migration for both core and Velocity configs
- **Velocity Bedrock Support** — `BedrockSupportModule` distinguishes Bedrock players from bots
- **New API Events** — `NotificationSentEvent`, `PostVerificationEvent`, `PreConnectionCheckEvent`, `TrustScoreChangeEvent`

---

## 🛡️ Velocity Proxy Module

AtomGuard's Velocity module stops threats at the proxy layer before they reach your backend servers. It synchronizes with the core module via Redis or Plugin Messaging.

### ⚔️ DDoS & Connection Protection

| Component | Description |
|---|---|
| **AttackLevelManager** | 5-level attack management — NONE / ELEVATED / HIGH / CRITICAL / LOCKDOWN with hysteresis to prevent rapid oscillation |
| **SmartThrottle Engine** | Automatic rate limiting scaled to current attack level |
| **SYN Flood Detector** | Blocks connections exceeding per-second threshold automatically |
| **TrafficAnomalyDetector** | Z-score, slow-ramp, and pulse attack detection |
| **EnhancedSlowloris** | Per-IP pending connection tracking with system-wide alarm |
| **ConnectionFingerprinter** | Protocol + hostname + timing fingerprint for botnet army detection |
| **SubnetAnalyzer** | /24 and /16 coordinated botnet detection |
| **IPReputationTracker** | DDoS-specific reputation score (0–100) with automatic temporary ban |
| **AttackSessionRecorder** | Full session capture from attack start to end, JSON output |
| **VerifiedPlayerShield** | Guaranteed slots for clean players at CRITICAL/LOCKDOWN levels |

### 🤖 Bot Protection

- **Multi-Factor Threat Score** — connection speed, handshake, client brand, join pattern, username, geolocation, and protocol analysis
- **Brand Analysis** — recognizes Fabric, Forge, Lunar, Badlion, LabyMod and other known clients; blocks bot/exploit clients
- **Join Pattern Detector** — statistically detects bot swarm behavior
- **CAPTCHA System** — routes suspicious players to limbo for a math challenge
- **Verified Player Cache** — players with a successful login history skip analysis entirely

### 🌐 VPN / Proxy Detection — 7 Layers

| # | Provider | Description |
|---|---|---|
| 1 | **Local List** | Instant local blacklist |
| 2 | **CIDR Blocker** | IP range-based blocking |
| 3 | **DNSBL** | Spamhaus, DroneBL, and custom lists |
| 4 | **IPHub** | Commercial VPN/proxy database |
| 5 | **ProxyCheck.io** | Real-time proxy verification |
| 6 | **AbuseIPDB** | Abuse history database |
| 7 | **IPApi** | ASN + hosting detection |

> **Consensus system:** At least 2 providers must agree before blocking. A single provider cannot cause a false positive.

### 🌍 Geo Filtering

- Country-based whitelist / blacklist via MaxMind GeoIP2
- Configurable policy for unknown countries

### 🔒 Firewall & Account Protection

- **IP Reputation Engine** — score based on successful logins, flood, and exploit history
- **Auto-Ban Engine** — rule-based temporary / permanent bans
- **Account Firewall** — Mojang API verification, account age check, cracked policy
- **Blacklist / Whitelist** — JSON-based, updatable at runtime

### ⚡ IPTables Integration

- Real-time kernel-level IP blocking (iptables / nftables)
- Automatic rule cleanup
- /24 subnet ban support

### 🔄 Protocol & Connection Control

- **Crash Loop Detection** — more than 3 disconnects within 30 seconds
- **Protocol Filter** — restricts to allowed client versions
- **Packet Size Limit** — blocks oversized / malformed packets

### 💬 Chat & Command Protection

- Chat rate limit, duplicate message detection, pattern analysis
- Tab-complete flood, command flood, server-switch spam protection

### 📡 Synchronization

- **Redis Bridge** — instant cross-server ban / alert synchronization
- **Plugin Messaging** — secure Core ↔ Velocity communication
- **Discord / Telegram / Slack Webhooks** — attack, bot, VPN, DDoS notifications

---

## 🔨 Core Module (Paper)

### 💥 Crash & Exploit Protection — 44+ Modules

| Category | Modules |
|---|---|
| Packet Exploits | packet-exploit, offline-packet, netty-crash, packet-delay |
| NBT Attacks | nbt-crash, item-sanitizer, custom-payload, advanced-payload |
| World Crashers | book-crash, lectern-crash, map-label-crash, frame-crash |
| Chunk / Entity | chunk-crash, entity-interact-crash, container-crash |
| Duplication | bundle-duplication, inventory-duplication, cow-duplication, mule-duplication, advanced-duplication |
| Inventory | invalid-slot, bundle-lock, creative-items, anvil-craft-crash |
| Movement | movement-security, coordinate-normalize |
| Commands | command-crash, component-crash |
| Performance | redstone-limiter, explosion-limiter, piston-limiter, falling-block-limiter, smart-lag |
| Bot Protection | anti-bot, bot-protection, connection-throttle, token-bucket, honeypot |
| Visual / Render | visual-crasher, view-distance-mask, shulker-byte |
| Storage | storage-entity-lock |

### 🤖 AtomShield™ — Core Bot Protection

- **9 Checks** — connection rate, gravity, packet timing, ping/handshake, protocol, username pattern, first-join behavior, post-join behavior, brand analysis
- **Heuristic Engine** — per-player profile with statistical anomaly detection
- **Verification System** — challenge applied to suspicious players
- **Attack Mode** — auto-activates when TPS drops or flood is detected

### 🔬 Forensics & Intelligence (v2.0+)

- **ForensicsManager** — records packet sessions at attack moments; snapshots saved to disk
- **PacketRecorder** — configurable buffer, concurrent recording sessions, auto-record threshold
- **TrafficIntelligenceEngine** — EWMA-based adaptive thresholds, Isolation Forest anomaly detection
- **AdaptiveThresholdManager** — learns traffic patterns, adjusts thresholds automatically

### 📊 Trust Score System (v2.0+)

- Per-player persistent trust score with tier system (LOW / NORMAL / TRUSTED / ADMIN)
- Score increases on clean sessions, decreases on violations
- Trusted players can bypass bot checks and VPN checks automatically
- JSON persistence with auto-save

### 🌐 Web Panel (v2.0+)

- JWT-based authentication (`/api/login`)
- Live event feed via SSE
- Module status dashboard
- Geo-map of recent events
- Attack history and statistics

### ⚡ Performance & Integrations

- **MySQL + HikariCP** — connection pool with WAL mode for SQLite fallback
- **Redis Pub/Sub** — network-wide synchronization
- **Caffeine Cache** — all TTL-based caches (bypass, cooldown, API results) use Caffeine
- **Notification Manager** — multi-channel alert routing (Discord, Telegram, Slack)
- **Async Logging** — 7-day rotation, async file writing

---

## 📦 Requirements

| Component | Version | Required |
|---|---|---|
| Java | 21+ | Yes |
| Paper / Forks | 1.21.4 | Yes |
| PacketEvents | 2.6.0+ | Yes (Core) |
| Velocity | 3.x | Proxy only |
| MySQL | 8.0+ | Optional |
| Redis | 7.x | Optional |
| MaxMind GeoIP2 | — | Optional |

---

## 🚀 Installation

### Paper Server

```bash
# 1. Drop PacketEvents into plugins/
#    https://modrinth.com/plugin/packetevents

# 2. Drop AtomGuard-core-2.0.2.jar into plugins/

# 3. Start the server — config is generated automatically

# 4. Edit plugins/AtomGuard/config.yml
```

### Velocity Proxy

```bash
# 1. Drop AtomGuard-velocity-2.0.2.jar into Velocity plugins/

# 2. Start the proxy — config is generated automatically

# 3. Edit plugins/atomguard-velocity/config.yml

# 4. For Redis sync, enable on both sides:
#    redis.enabled: true
```

> **Upgrading from 1.x / 2.0.x?** AtomGuard automatically migrates your existing config on first startup. No manual changes needed.

---

## 💻 Commands & Permissions

| Command | Description | Permission |
|---|---|---|
| `/atomguard` | Help menu | `atomguard.admin` |
| `/atomguard reload` | Reload config | `atomguard.reload` |
| `/atomguard status` | Module statuses | `atomguard.admin` |
| `/atomguard stats` | Statistics | `atomguard.admin` |
| `/panic` | Emergency mode — tightens all modules | `atomguard.panic` |

| Permission | Description |
|---|---|
| `atomguard.bypass` | Bypasses all protections |
| `atomguard.notify` | Receives exploit notifications |

---

## 🔌 Developer API

```xml
<dependency>
    <groupId>com.atomguard</groupId>
    <artifactId>AtomGuard-api</artifactId>
    <version>2.0.2</version>
    <scope>provided</scope>
</dependency>
```

```java
AtomGuardAPI api = AtomGuardAPI.getInstance();

// IP reputation score
IReputationService rep = api.getReputationService();
int score = rep.getScore(player.getAddress().getAddress());

// Trust score (v2.0+)
ITrustService trust = api.getTrustService();
int trustScore = trust.getScore(player.getUniqueId());

// Forensics recording (v2.0+)
IForensicsService forensics = api.getForensicsService();
forensics.startRecording(player.getUniqueId());

// Toggle a module at runtime
IModuleManager modules = api.getModuleManager();
modules.setEnabled("anti-bot", false);

// Listen for exploit blocks
@EventHandler
public void onExploitBlocked(ExploitBlockedEvent event) {
    String module = event.getModuleName();
    Player player  = event.getPlayer();
}

// Listen for trust score changes (v2.0+)
@EventHandler
public void onTrustChange(TrustScoreChangeEvent event) {
    int newScore = event.getNewScore();
}
```

---

## 🔧 Building

```bash
git clone https://github.com/ATOMGAMERAGA/AtomGuard.git
cd AtomGuard
mvn clean package -DskipTests

# Output:
#   core/target/AtomGuard-core-2.0.2.jar
#   velocity/target/AtomGuard-velocity-2.0.2.jar
```

Requirements: **Java 21 JDK** + **Maven 3.8+**

---

## 🏗️ Architecture

```
AtomGuard/
├── api/       → Public interfaces for developers (stable API contract)
├── core/      → Paper 1.21.4 main plugin (44+ modules, web panel, forensics)
└── velocity/  → Velocity proxy module (DDoS, bot, VPN, firewall, geo)
```

Key design principles:
- **PacketEvents** for all packet-level interception (loaded in `onLoad()`)
- **Single `PacketListener`** — all modules register handlers via `registerReceiveHandler()`
- **`AbstractModule`** base — unified lifecycle, config access, exploit blocking, and event firing
- **`AtomGuardAPI` singleton** — initialized after all managers and modules, stable across versions

---

## 🤝 Contributing

Want to contribute? Check [CONTRIBUTING.md](CONTRIBUTING.md). All pull requests are welcome.

## 📜 License

This project is distributed under the **BSD 3-Clause** license. See [LICENSE](LICENSE) for details.

---

<p align="center">
  <strong>⚛️ AtomGuard</strong> — Protect your server.<br>
  <sub>Made with ❤️ by <strong>AtomGuard Team</strong></sub>
</p>
