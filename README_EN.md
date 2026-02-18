<p align="center">
  <img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard Logo" width="280">
</p>

<h1 align="center">⚛️ AtomGuard — Enterprise Minecraft Server Security</h1>

<p align="center">
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/actions"><img src="https://img.shields.io/github/actions/workflow/status/ATOMGAMERAGA/AtomGuard/build.yml?branch=main&style=for-the-badge&logo=github" alt="Build Status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/ATOMGAMERAGA/AtomGuard?style=for-the-badge" alt="License"></a>
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/releases"><img src="https://img.shields.io/github/v/release/ATOMGAMERAGA/AtomGuard?style=for-the-badge&color=brightgreen" alt="Release"></a>
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk" alt="Java 21">
  <img src="https://img.shields.io/badge/Paper-1.21.4-blue?style=for-the-badge" alt="Paper 1.21.4">
  <img src="https://img.shields.io/badge/Velocity-3.x-purple?style=for-the-badge" alt="Velocity">
</p>

<p align="center">
  <strong>Full-spectrum security for Paper 1.21.4 + Velocity — 44+ modules protecting against DDoS attacks, bot floods, crash exploits, and duplication bugs.</strong>
</p>

---

## 🚀 Why AtomGuard?

| Feature | AtomGuard | Others |
|---|---|---|
| Velocity Proxy Protection | ✅ Full integration, 12+ modules | ❌ None or very limited |
| DDoS / Bot Protection | ✅ Multi-layer, AI-assisted | ⚠️ Basic only |
| VPN/Proxy Detection | ✅ 7-provider chain | ⚠️ 1-2 APIs |
| IPTables Integration | ✅ Kernel-level blocking | ❌ None |
| Real-Time Threat Score | ✅ Multi-factor scoring | ❌ None |
| Crash & Dupe Protection | ✅ 44+ modules | ⚠️ 10-20 modules |
| Open Developer API | ✅ Maven artifact | ❌ None |

---

## 🛡️ Velocity Proxy Module (NEW)

The AtomGuard Velocity module stops threats before they ever reach your backend servers. It works independently and synchronizes with the core plugin via Redis or Plugin Messaging.

### ⚔️ DDoS & Connection Protection

- **SmartThrottle Engine**: Adaptive rate limiting across Normal / Careful / Aggressive / Lockdown levels
- **SYN Flood Detection**: Automatically blocks IPs exceeding 50 connections per second
- **Slowloris Detection**: Identifies and terminates slow-drip connection attacks
- **Ping Flood Detector**: Tracks per-IP ping request counts
- **IP / Subnet / Global Rate Limiting**: Sliding-window algorithm with multi-layer enforcement

### 🤖 Bot Protection (AtomShield™ Velocity)

- **Multi-Factor Threat Score**: Combines connection speed, handshake, client brand, join pattern, username, geolocation, and protocol analysis into a single threat score
- **Brand Analyzer**: Recognizes legitimate clients (Fabric, Forge, Lunar, Badlion, LabyMod…) and blocks exploit/bot clients
- **Handshake Validator**: Filters invalid or suspicious handshake packets
- **Join Pattern Detector**: Statistically identifies bot swarm behavior
- **CAPTCHA System**: Routes suspicious players to a limbo server with math challenges
- **Nickname Blocker**: Regex, prefix, suffix, and character-pattern analysis to block bot nicknames
- **Verified Player Cache**: Fast-tracks clean players for up to 48 hours

### 🌐 VPN / Proxy Detection (7-Layer Chain)

Providers are queried in order — stops as soon as a verdict is reached:

| # | Provider | Description |
|---|---|---|
| 1 | **Local List** | Instant local blocklist |
| 2 | **CIDR Blocker** | IP range-based blocking |
| 3 | **DNSBL** | Spamhaus, DroneBL, and custom lists |
| 4 | **IPHub** | Commercial VPN/proxy database |
| 5 | **ProxyCheck.io** | Real-time proxy lookup |
| 6 | **AbuseIPDB** | Abuse history scoring |
| 7 | **IPApi** | ASN + hosting provider detection |

- **Ip2Proxy Database**: Offline local queries — never hits API rate limits
- **ASN Blocking**: Bulk-block entire hosting provider ASNs
- **Trust Score Threshold**: Allow / warn / block decisions based on configurable score

### 🌍 Geo / Country Filtering

- MaxMind GeoIP2 integration for country-level whitelist/blacklist
- Automatic weekly database updates
- Configurable policy for unknown countries

### 🔒 Firewall & Account Protection

- **IP Reputation Engine**: Each IP accumulates a reputation score based on login successes, flood events, and exploits
- **Auto-Ban Engine**: Rule-based automatic permanent/temporary banning
- **TempBan Manager**: Automatically removes bans after the configured duration
- **Account Firewall**: Mojang API verification, account age check, cracked-account policy
- **Blacklist / Whitelist**: JSON-based, hot-reloadable at runtime

### ⚡ IPTables Integration

- Real-time kernel-level IP blocking via iptables / nftables
- Automatic rule cleanup on startup and shutdown
- Subnet banning — block entire /24 ranges in one command

### 🔄 Reconnect & Protocol Control

- **Crash Loop Detection**: Triggers when 3+ disconnects occur within 30 seconds
- **Short Session Detection**: Challenge players whose sessions last under 3 seconds
- **Protocol Version Filter**: Restrict which client versions can connect
- **Packet Size Limit**: Block oversized or malformed packets

### 💬 Chat & Exploit Protection

- **Chat Rate Limiter**: Per-second message limit with configurable burst allowance
- **Duplicate Message Detection**: Tracks last N messages per player
- **Pattern Analysis**: Caps-ratio, repeated characters, link blocking
- **Tab-Complete Flood**: Cuts off more than 5 tab requests per second
- **Command Flood Blocker**: Per-second command rate limiting
- **Server-Switch Abuse**: Prevents rapid server-hop spamming

### 🔐 Password Security (AuthMe Integration)

- **Brute Force Protection**: Temporary ban after 5 failed login attempts
- **Common Password Check**: 10,000+ known weak passwords blocked
- **Password Similarity Detection**: Flags multiple accounts on the same IP using similar passwords

### 📡 Network & Synchronization

- **Redis Bridge**: Instant ban/alert synchronization across all servers
- **Plugin Messaging**: Secure Core ↔ Velocity communication channel
- **Discord Webhooks**: Real-time notifications for attacks, bots, VPNs, and DDoS events
- **Attack Mode**: All modules automatically tighten when the configured threshold is exceeded

---

## 🔨 Core Module (Paper Plugin)

### 💥 Crash & Exploit Protection (44+ Modules)

| Category | Modules |
|---|---|
| **Packet Exploits** | PacketExploitModule, OfflinePacketModule, NettyCrashModule, PacketDelayModule |
| **NBT Attacks** | NBTCrasherModule, ItemSanitizerModule, CustomPayloadModule, AdvancedPayloadModule |
| **World Crashers** | BookCrasherModule, LecternCrasherModule, MapLabelCrasherModule, FrameCrashModule |
| **Chunk / Entity** | ChunkCrashModule, EntityInteractCrashModule, ContainerCrashModule |
| **Duplication** | BundleDuplicationModule, InventoryDuplicationModule, CowDuplicationModule, MuleDuplicationModule, DuplicationFixModule |
| **Inventory** | InvalidSlotModule, BundleLockModule, CreativeItemsModule, AnvilCraftCrashModule |
| **Movement** | MovementSecurityModule, NormalizeCoordinatesModule |
| **Commands** | CommandsCrashModule, ComponentCrashModule |
| **Performance** | RedstoneLimiterModule, ExplosionLimiterModule, PistonLimiterModule, FallingBlockLimiterModule |
| **Bot Protection** | AntiBotModule, BotProtectionModule, ConnectionThrottleModule |

### 🤖 AtomShield™ (Core Bot Protection)

- **9 Distinct Checks**: Connection rate, gravity, packet timing, ping/handshake, protocol, username pattern, first-join behavior, post-join behavior
- **Heuristic Engine**: Builds a per-player profile and detects statistical anomalies
- **Verification System**: Applies challenges to suspicious players before whitelisting them
- **Attack Mode**: Activates automatically when TPS drops or a connection flood is detected

### ⚡ Performance & Integrations

- **MySQL + HikariCP**: High-performance connection-pool database storage
- **Redis Pub/Sub**: Network-wide synchronization
- **Discord Webhook**: Instant exploit and attack alerts
- **Web Panel**: Browser-based live statistics dashboard
- **Async Logging**: 7-day rotation, fully off-thread file writing
- **ConfigManager**: Hot-reload without server restart

---

## 📦 Requirements

| Component | Version | Required |
|---|---|---|
| Java | 21+ | ✅ |
| Paper / Forks | 1.21.4 | ✅ |
| PacketEvents | 2.6.0+ | ✅ (Core) |
| Velocity | 3.x | ⚠️ (Proxy only) |
| MySQL | 8.0+ | ⚠️ (Optional) |
| Redis | 7.x | ⚠️ (Optional) |
| MaxMind License | — | ⚠️ (GeoIP only) |

---

## 🚀 Installation

### Paper Server
1. Place [PacketEvents](https://modrinth.com/plugin/packetevents) in your `plugins/` folder.
2. Place `AtomGuard-core-1.0.0.jar` in your `plugins/` folder.
3. Start the server — config files are generated automatically.
4. Configure `plugins/AtomGuard/config.yml`.

### Velocity Proxy
1. Place `AtomGuard-velocity-1.0.0.jar` in the Velocity `plugins/` folder.
2. Start the proxy — config files are generated automatically.
3. Configure `plugins/atomguard-velocity/config.yml`.
4. If using Redis, enable the `redis` section on both sides.

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
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

```java
// Check IP reputation
IReputationService rep = AtomGuardAPI.getInstance().getReputationService();
int score = rep.getScore(player.getAddress().getAddress());

// Toggle a module at runtime
IModuleManager modules = AtomGuardAPI.getInstance().getModuleManager();
modules.setEnabled("bot-koruma", false);

// Listen for blocked exploits
@EventHandler
public void onExploitBlocked(ExploitBlockedEvent event) {
    String moduleName = event.getModuleName();
    Player player = event.getPlayer();
}
```

---

## 🔨 Building from Source

```bash
git clone https://github.com/ATOMGAMERAGA/AtomGuard.git
cd AtomGuard
mvn clean package -DskipTests
# Core output:     core/target/AtomGuard-1.0.0.jar
# Velocity output: velocity/target/AtomGuard-velocity-1.0.0.jar
```

---

## 🏗️ Architecture

```
AtomGuard/
├── api/       → Stable public interfaces for developers
├── core/      → Paper 1.21.4 main plugin (44+ modules, bot protection, exploit fixes)
└── velocity/  → Velocity proxy module (DDoS, bot, VPN, geo, IPTables)
```

---

## 🤝 Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). All pull requests are welcome.

## 📜 License

**BSD 3-Clause** — See [LICENSE](LICENSE) for details.

---

<div align="center">
  <strong>⚛️ AtomGuard</strong> — Harden your server, not your players.<br>
  Made with ❤️ by <strong>AtomGuard Team</strong>
</div>
