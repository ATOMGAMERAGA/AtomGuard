<div align="center">

<img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard Logo" width="200">

<br>

# ⚛️ AtomGuard

**Advanced Minecraft Server Security for Paper 1.21.4 + Velocity**

<br>

[![Build Status](https://img.shields.io/github/actions/workflow/status/ATOMGAMERAGA/AtomGuard/build.yml?branch=main&style=flat-square&logo=github&logoColor=white&label=Build)](https://github.com/ATOMGAMERAGA/AtomGuard/actions)
[![Latest Release](https://img.shields.io/github/v/release/ATOMGAMERAGA/AtomGuard?style=flat-square&color=00C7B7&label=Release)](https://github.com/ATOMGAMERAGA/AtomGuard/releases)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Paper 1.21.4](https://img.shields.io/badge/Paper-1.21.4-00AA00?style=flat-square)](https://papermc.io)
[![Velocity 3.x](https://img.shields.io/badge/Velocity-3.x-8A6DF7?style=flat-square)](https://papermc.io/software/velocity)
[![License BSD-3](https://img.shields.io/badge/License-BSD%203--Clause-4A90D9?style=flat-square)](https://github.com/ATOMGAMERAGA/AtomGuard/blob/main/LICENSE)

<br>

*44+ security modules — DDoS & flood protection — Multi-layer bot detection — 7-provider VPN filtering — Kernel-level IPTables — Full exploit & crash fixes*

</div>

---

## 🗺️ What AtomGuard Covers

AtomGuard defends every stage of the connection lifecycle — from the very first TCP packet to in-game actions:

| Stage | Protection |
|---|---|
| 🔌 **Before handshake** | DDoS throttling, SYN flood blocking, ping flood detection |
| 🤝 **During connection** | Bot scoring, VPN filtering, country blocking, protocol validation |
| 🎮 **After login** | Crash fixes, exploit patches, duplication prevention, performance limiting |
| 🌐 **Across the network** | Redis sync, shared banlists, network-wide attack mode |

---

## 🛡️ Velocity Proxy Module

Deploy AtomGuard on your Velocity proxy and stop threats **before they ever touch your backend servers**.

<br>

### ⚔️ DDoS & Flood Protection

> **SmartThrottle Engine** — four adaptive threat levels:
> `Normal` → `Careful` → `Aggressive` → `Lockdown`
> Scales automatically based on live connection rate.

- **SYN Flood Detector** — blocks IPs exceeding 50 connections/second instantly
- **Slowloris Detector** — kills slow-drip connection drain attacks
- **Ping Flood Guard** — tracks and caps per-IP ping requests
- **Sliding-Window Rate Limits** — per-IP, per-subnet, and global layers simultaneously

<br>

### 🤖 Bot Detection — AtomShield™ Velocity

Threat score built from **7 weighted signals**:

| Signal | Weight |
|---|---|
| Connection Speed | 20% |
| Join Pattern | 20% |
| Handshake Validity | 15% |
| Client Brand | 15% |
| Geo / Country | 10% |
| Username Pattern | 10% |
| Protocol Version | 10% |

**Score thresholds:**

| Score | Action |
|---|---|
| < 40 | ✅ Allow |
| 40 – 60 | ⚠️ Flag |
| 60 – 75 | 🔐 CAPTCHA (limbo + math challenge) |
| 75 – 90 | 🚫 Kick |
| 90+ | 🔨 Auto-ban |

**Additional bot defenses:**
- **Brand Analyzer** — recognizes Fabric, Forge, Lunar, Badlion, LabyMod, OptiFine, Sodium; blocks crasher/bot clients
- **Nickname Blocker** — regex patterns, prefix/suffix lists, length checks, special-character analysis
- **Verified Player Cache** — players with clean history bypass checks for up to 48 hours

<br>

### 🌐 VPN & Proxy Detection — 7-Provider Chain

Parallel provider queries with **consensus voting** (minimum 2 positive hits to block). Fail-open on timeout — legitimate players are never blocked by a slow API.

| # | Provider | Type |
|---|---|---|
| 1 | **Local Blocklist** | Instant local lookup |
| 2 | **CIDR Blocker** | IP range blocking |
| 3 | **DNSBL** | Spamhaus, DroneBL + custom lists |
| 4 | **IPHub** | Commercial VPN/proxy database |
| 5 | **ProxyCheck.io** | Real-time proxy detection |
| 6 | **AbuseIPDB** | Abuse history scoring |
| 7 | **IPApi** | ASN + hosting provider detection |

- **Ip2Proxy Offline DB** — local queries, never hits API rate limits
- **ASN Bulk Blocking** — block entire hosting provider ASNs in one rule
- **Residential Bypass** — prevents false positives from legitimate ISPs
- **Result Cache** — IPs cached for 1 hour, verified clean IPs cached permanently

<br>

### 🌍 Geo / Country Filtering

MaxMind GeoIP2 integration — **whitelist or blacklist** by country. Automatic weekly database updates. Configurable policy for unknown countries.

<br>

### 🔒 Firewall & Account Protection

- **IP Reputation Engine** — scores decay over time; clean logins grant −15 point reward
- **Auto-Ban Engine** — rule-based permanent or temporary banning; minimum 3 violations before ban
- **TempBan Manager** — automatic expiry and removal
- **Account Firewall** — Mojang API verification, account age check, cracked-account policy
- **Blacklist / Whitelist** — JSON-based, hot-reloadable at runtime

<br>

### ⚡ IPTables Integration

Block IPs at the **kernel level** — bypasses the JVM entirely for the fastest possible response.

- Supports `iptables`, `ip6tables`, and `nftables`
- Subnet banning — block entire `/24` ranges in a single rule
- Rules are cleaned up automatically on startup and shutdown

<br>

### 💬 Chat, Command & Protocol Protection

| Feature | Details |
|---|---|
| Chat Rate Limiter | Per-second limit with configurable burst allowance |
| Duplicate Detection | Tracks last N messages per player |
| Pattern Analysis | Caps ratio, repeated chars, link blocking |
| Tab-Complete Flood | Blocks > 5 tab requests/second |
| Command Flood | Per-second command rate limit |
| Server-Switch Abuse | Prevents rapid server-hop spam |
| Protocol Filter | Restrict which client versions may connect |
| Packet Size Limit | Block oversized or malformed packets |
| Crash Loop Detection | 3+ disconnects in 30s triggers challenge |
| Short Session | Sessions < 3s are flagged as suspicious |

<br>

### 🔐 Password Security (AuthMe Integration)

- Temporary ban after 5 failed login attempts
- 10,000+ known weak passwords blocked
- Password similarity detection across the same IP

<br>

### 📡 Sync & Alerts

- **Redis Bridge** — instant ban / alert sync across all backend servers
- **Plugin Messaging** — secure Core ↔ Velocity communication channel
- **Discord Webhooks** — real-time notifications for DDoS, bots, VPN hits, and exploits
- **Attack Mode** — all modules automatically tighten when thresholds are exceeded

---

## 🔨 Core Plugin — Paper 1.21.4

### 💥 Exploit & Crash Fixes — 44+ Modules

<details>
<summary><strong>📦 Packet & Network</strong></summary>

- Invalid packet filtering at the Netty pipeline level
- Oversized packet blocking
- Offline packet injection prevention
- Packet timing / delay analysis

</details>

<details>
<summary><strong>🗂️ NBT & Item Attacks</strong></summary>

- Nested NBT depth limiting
- Oversized NBT payload detection
- Bundle crash prevention
- Item sanitization on all inventory operations

</details>

<details>
<summary><strong>🌍 World Crashers</strong></summary>

- Book & Lectern exploit fix
- Map label crash fix
- Item frame crash fix
- Sign exploit prevention
- Chunk crash protection

</details>

<details>
<summary><strong>♊ Duplication Fixes</strong></summary>

- Bundle duplication fix
- Inventory click duplication fix
- Cow & Mule duplication fix
- General dupe prevention engine

</details>

<details>
<summary><strong>⚙️ Performance Limiters</strong></summary>

- Redstone circuit rate limiting
- Explosion limiter
- Piston limiter
- Falling block limiter
- Entity limiter per chunk

</details>

<br>

### 🤖 AtomShield™ Core Bot Protection

9 behavioral checks running in real time:

> Connection rate · Gravity validation · Packet timing · Ping/handshake · Protocol · Username pattern · First-join behavior · Post-join behavior · Heuristic profiling

- Heuristic engine builds a per-player profile and flags statistical anomalies
- Suspicious players receive challenges before being whitelisted
- Attack Mode auto-activates when TPS drops or connection floods are detected

<br>

### ⚡ Integrations

| Integration | Details |
|---|---|
| MySQL + HikariCP | Connection-pool database, shaded — zero classpath conflicts |
| Redis Pub/Sub | Network-wide synchronization |
| Discord Webhooks | Instant alerts for every blocked event |
| Web Panel | Browser-based live statistics dashboard |
| Async Logging | 7-day log rotation, fully off-thread |
| Hot Reload | Config changes applied without restart |

---

## 📦 Requirements

| Component | Version | Required? |
|---|---|---|
| Java | 21+ | ✅ Required |
| Paper (or fork) | 1.21.4 | ✅ Required |
| PacketEvents | 2.6.0+ | ✅ Required (core) |
| Velocity | 3.x | ⚠️ Proxy module only |
| MySQL | 8.0+ | ⚠️ Optional |
| Redis | 7.x | ⚠️ Optional (cross-server sync) |
| MaxMind License | — | ⚠️ Optional (GeoIP filtering) |

---

## 🚀 Quick Start

**Paper Server**
1. Download and install [PacketEvents](https://modrinth.com/plugin/packetevents) into `plugins/`
2. Drop `AtomGuard-core.jar` into `plugins/`
3. Start the server — config is auto-generated
4. Edit `plugins/AtomGuard/config.yml`

**Velocity Proxy**
1. Drop `AtomGuard-velocity.jar` into the Velocity `plugins/` folder
2. Start the proxy — config is auto-generated
3. Edit `plugins/atomguard-velocity/config.yml`
4. *(Optional)* Enable the `redis` section on both sides for network-wide sync

---

## 💻 Commands & Permissions

| Command | Description | Permission |
|---|---|---|
| `/atomguard status` | Live module status | `atomguard.admin` |
| `/atomguard reload` | Hot-reload config | `atomguard.reload` |
| `/atomguard stats` | Statistics overview | `atomguard.admin` |
| `/panic` | Emergency lockdown | `atomguard.panic` |

| Permission | Effect |
|---|---|
| `atomguard.bypass` | Bypasses all protections |
| `atomguard.notify` | Receives exploit alerts in chat |

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
// Query IP reputation score
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

Custom events available: `ExploitBlockedEvent` · `AttackModeToggleEvent` · `PlayerReputationCheckEvent` · `ModuleToggleEvent`

---

<div align="center">

**[GitHub](https://github.com/ATOMGAMERAGA/AtomGuard)** · **[Report a Bug](https://github.com/ATOMGAMERAGA/AtomGuard/issues)** · **[Contribute](https://github.com/ATOMGAMERAGA/AtomGuard/blob/main/CONTRIBUTING.md)**

<br>

Made with ❤️ by **AtomGuard Team**

</div>
