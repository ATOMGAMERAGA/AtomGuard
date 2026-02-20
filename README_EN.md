<p align="center">
  <img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard Logo" width="240">
</p>

<h1 align="center">⚛️ AtomGuard</h1>

<p align="center">
  <strong>Multi-layer Minecraft server security for Paper 1.21.4 + Velocity</strong>
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
  <a href="README.md">🇹🇷 Türkçe</a> &nbsp;|&nbsp;
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/releases">📦 Downloads</a> &nbsp;|&nbsp;
  <a href="CHANGELOG.md">📋 Changelog</a>
</p>

---

AtomGuard is an open-source security plugin that protects Minecraft servers against **DDoS attacks**, **bot floods**, **crash exploits**, and **duplication bugs**. It runs on Paper 1.21.4 and also on Velocity proxy, stopping threats before they ever reach your backend.

---

## Table of Contents

- [Why AtomGuard?](#-why-atomguard)
- [Velocity Proxy Module](#-velocity-proxy-module)
- [Core Module (Paper)](#-core-module-paper)
- [Requirements](#-requirements)
- [Installation](#-installation)
- [Commands & Permissions](#-commands--permissions)
- [Developer API](#-developer-api)
- [Building from Source](#-building-from-source)
- [Architecture](#-architecture)
- [License](#-license)

---

## 🚀 Why AtomGuard?

| Feature | AtomGuard | Others |
|---|---|---|
| Velocity Proxy Protection | ✅ 12+ modules, full integration | ❌ None or very limited |
| DDoS & Bot Protection | ✅ 5-level attack management | ⚠️ Basic only |
| VPN / Proxy Detection | ✅ 7-provider consensus chain | ⚠️ 1–2 APIs |
| IPTables Integration | ✅ Kernel-level blocking | ❌ None |
| Real-Time Threat Score | ✅ Multi-factor scoring | ❌ None |
| Crash & Dupe Protection | ✅ 44+ modules | ⚠️ 10–20 modules |
| Developer API | ✅ Maven artifact | ❌ None |

---

## 🛡️ Velocity Proxy Module

AtomGuard's Velocity module intercepts threats at the proxy layer before they reach your backend servers. It works standalone and synchronizes with the core plugin via Redis or Plugin Messaging.

### ⚔️ DDoS & Connection Protection

| Component | Description |
|---|---|
| **AttackLevelManager** | 5-level attack management — NONE / ELEVATED / HIGH / CRITICAL / LOCKDOWN with hysteresis to prevent rapid toggling |
| **SmartThrottle Engine** | Adaptive rate limiting that scales with the current attack level |
| **SYN Flood Detector** | Blocks IPs that exceed the CPS threshold automatically |
| **TrafficAnomalyDetector** | Z-score anomaly detection, slow-ramp and pulse-attack detection |
| **EnhancedSlowloris** | Per-IP pending connection tracking with system-wide ratio alarm |
| **ConnectionFingerprinter** | Protocol + hostname + timing fingerprint to detect bot armies |
| **SubnetAnalyzer** | /24 and /16 subnet-level coordinated botnet detection |
| **IPReputationTracker** | DDoS-specific reputation score (0–100) with automatic temp-ban |
| **AttackSessionRecorder** | Full session recording from start to end with JSON file output |
| **VerifiedPlayerShield** | Guaranteed slot pool for verified players during CRITICAL/LOCKDOWN |

### 🤖 Bot Protection

- **Multi-Factor Threat Score** — connection speed, handshake, client brand, join pattern, username, geolocation, and protocol analysis combined
- **Brand Analyzer** — recognizes known clients (Fabric, Forge, Lunar, Badlion, LabyMod…); blocks exploit/bot clients
- **Join Pattern Detector** — statistically identifies bot swarm behavior
- **CAPTCHA System** — routes suspicious players to a limbo server with math challenges
- **Verified Player Cache** — players with a clean login history skip analysis entirely

### 🌐 VPN / Proxy Detection — 7-Layer Chain

| # | Provider | Description |
|---|---|---|
| 1 | **Local List** | Instant local blocklist |
| 2 | **CIDR Blocker** | IP range-based blocking |
| 3 | **DNSBL** | Spamhaus, DroneBL, and custom lists |
| 4 | **IPHub** | Commercial VPN/proxy database |
| 5 | **ProxyCheck.io** | Real-time proxy lookup |
| 6 | **AbuseIPDB** | Abuse history scoring |
| 7 | **IPApi** | ASN + hosting provider detection |

> **Consensus system:** A block decision requires at least 2 providers to agree — a single provider cannot cause a false positive.

### 🌍 Geo / Country Filtering

- MaxMind GeoIP2 integration for country-level whitelist / blacklist
- Configurable policy for unknown countries

### 🔒 Firewall & Account Protection

- **IP Reputation Engine** — scores each IP based on login successes, flood events, and exploits
- **Auto-Ban Engine** — rule-based automatic temporary / permanent banning
- **Account Firewall** — Mojang API verification, account age check, cracked-account policy
- **Blacklist / Whitelist** — JSON-based, hot-reloadable at runtime

### ⚡ IPTables Integration

- Kernel-level IP blocking via iptables / nftables
- Automatic rule cleanup on startup and shutdown
- /24 subnet ban support

### 🔄 Protocol & Connection Control

- **Crash Loop Detection** — triggers on 3+ disconnects within 30 seconds
- **Protocol Version Filter** — restrict which client versions are allowed
- **Packet Size Limit** — blocks oversized or malformed packets

### 💬 Chat & Command Protection

- Chat rate limit, duplicate message detection, pattern analysis
- Tab-complete flood, command flood, and server-switch spam protection

### 📡 Synchronization

- **Redis Bridge** — instant ban / alert sync across all servers
- **Plugin Messaging** — secure Core ↔ Velocity communication
- **Discord Webhooks** — real-time alerts for attacks, bots, VPNs, and DDoS events

---

## 🔨 Core Module (Paper)

### 💥 Crash & Exploit Protection — 44+ Modules

| Category | Modules |
|---|---|
| Packet Exploits | PacketExploitModule, OfflinePacketModule, NettyCrashModule, PacketDelayModule |
| NBT Attacks | NBTCrasherModule, ItemSanitizerModule, CustomPayloadModule, AdvancedPayloadModule |
| World Crashers | BookCrasherModule, LecternCrasherModule, MapLabelCrasherModule, FrameCrashModule |
| Chunk / Entity | ChunkCrashModule, EntityInteractCrashModule, ContainerCrashModule |
| Duplication | BundleDuplicationModule, InventoryDuplicationModule, CowDuplicationModule, MuleDuplicationModule |
| Inventory | InvalidSlotModule, BundleLockModule, CreativeItemsModule, AnvilCraftCrashModule |
| Movement | MovementSecurityModule, NormalizeCoordinatesModule |
| Commands | CommandsCrashModule, ComponentCrashModule |
| Performance | RedstoneLimiterModule, ExplosionLimiterModule, PistonLimiterModule, FallingBlockLimiterModule |
| Bot Protection | AntiBotModule, BotProtectionModule, ConnectionThrottleModule |

### 🤖 AtomShield™ — Core Bot Protection

- **9 Checks** — connection rate, gravity, packet timing, ping/handshake, protocol, username pattern, first-join behavior, post-join behavior
- **Heuristic Engine** — builds a per-player profile and detects statistical anomalies
- **Verification System** — applies challenges to suspicious players before whitelisting
- **Attack Mode** — activates automatically when TPS drops or a flood is detected

### ⚡ Performance & Integrations

- **MySQL + HikariCP** — high-performance connection-pool database storage
- **Redis Pub/Sub** — network-wide synchronization
- **Discord Webhook** — instant exploit and attack alerts
- **Web Panel** — browser-based live statistics dashboard
- **Async Logging** — 7-day rotation, fully off-thread file writing

---

## 📦 Requirements

| Component | Version | Status |
|---|---|---|
| Java | 21+ | Required |
| Paper / Forks | 1.21.4 | Required |
| PacketEvents | 2.6.0+ | Required (Core) |
| Velocity | 3.x | Proxy only |
| MySQL | 8.0+ | Optional |
| Redis | 7.x | Optional |
| MaxMind GeoIP2 | — | Optional |

---

## 🚀 Installation

### Paper Server

```bash
1. Download PacketEvents and place it in plugins/
   https://modrinth.com/plugin/packetevents

2. Place AtomGuard-core-1.1.0.jar in plugins/

3. Start the server — config files are generated automatically

4. Edit plugins/AtomGuard/config.yml
```

### Velocity Proxy

```bash
1. Place AtomGuard-velocity-1.1.0.jar in Velocity plugins/

2. Start the proxy — config files are generated automatically

3. Edit plugins/atomguard-velocity/config.yml

4. For Redis sync, set redis.enabled: true on both sides
```

---

## 💻 Commands & Permissions

| Command | Description | Permission |
|---|---|---|
| `/atomguard` | Help menu | `atomguard.admin` |
| `/atomguard reload` | Reload configuration | `atomguard.reload` |
| `/atomguard status` | Module status overview | `atomguard.admin` |
| `/atomguard stats` | Statistics dashboard | `atomguard.admin` |
| `/panic` | Emergency mode — all modules tighten | `atomguard.panic` |

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
    <version>1.1.0</version>
    <scope>provided</scope>
</dependency>
```

```java
// IP reputation score
IReputationService rep = AtomGuardAPI.getInstance().getReputationService();
int score = rep.getScore(player.getAddress().getAddress());

// Toggle a module at runtime
IModuleManager modules = AtomGuardAPI.getInstance().getModuleManager();
modules.setEnabled("bot-koruma", false);

// Listen for blocked exploits
@EventHandler
public void onExploitBlocked(ExploitBlockedEvent event) {
    String module = event.getModuleName();
    Player player  = event.getPlayer();
}
```

---

## 🔧 Building from Source

```bash
git clone https://github.com/ATOMGAMERAGA/AtomGuard.git
cd AtomGuard
mvn clean package -DskipTests

# Output:
#   core/target/AtomGuard-core-1.1.0.jar
#   velocity/target/AtomGuard-velocity-1.1.0.jar
```

Requirements: **Java 21 JDK** + **Maven 3.8+**

---

## 🏗️ Architecture

```
AtomGuard/
├── api/       → Public interfaces for third-party developers
├── core/      → Paper 1.21.4 main plugin (44+ modules)
└── velocity/  → Velocity proxy module (DDoS, bot, VPN, firewall)
```

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
