<div align="center">

<img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard" width="220">

<br>

<h1>⚛️ AtomGuard</h1>

<p><i>Advanced Minecraft Server Security — Paper 1.21.4 + Velocity</i></p>

<br>

[![Build](https://img.shields.io/github/actions/workflow/status/ATOMGAMERAGA/AtomGuard/build.yml?branch=main&style=for-the-badge&logo=github&logoColor=white&label=Build&color=22c55e)](https://github.com/ATOMGAMERAGA/AtomGuard/actions)
[![Release](https://img.shields.io/github/v/release/ATOMGAMERAGA/AtomGuard?style=for-the-badge&color=00C7B7&label=Release)](https://github.com/ATOMGAMERAGA/AtomGuard/releases)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Paper](https://img.shields.io/badge/Paper-1.21.4-00AA00?style=for-the-badge)](https://papermc.io)
[![Velocity](https://img.shields.io/badge/Velocity-3.x-8A6DF7?style=for-the-badge)](https://papermc.io/software/velocity)
[![License](https://img.shields.io/badge/License-BSD--3-4A90D9?style=for-the-badge)](https://github.com/ATOMGAMERAGA/AtomGuard/blob/main/LICENSE)

<br>

> **44+ security modules · DDoS & flood protection · Multi-layer bot detection**
> **7-provider VPN filtering · Kernel-level IPTables · Full exploit & crash fixes**

</div>

<br>

---

<div align="center">

## 🗺️ Full Attack Surface Coverage

</div>

AtomGuard defends every stage of the connection lifecycle — from the very first TCP packet to in-game actions.

<br>

<div align="center">

| 🔌 Before Handshake | 🤝 During Connection | 🎮 After Login | 🌐 Network-Wide |
|:---:|:---:|:---:|:---:|
| DDoS throttling | Bot scoring | Crash fixes | Redis sync |
| SYN flood blocking | VPN filtering | Exploit patches | Shared banlists |
| Ping flood detection | Country blocking | Dupe prevention | Attack mode |
| Rate limiting | Protocol validation | Performance limits | Discord alerts |

</div>

<br>

---

<div align="center">

## 🛡️ Velocity Proxy Module

*Stop threats before they ever reach your backend servers*

</div>

<br>

### ⚔️ DDoS & Flood Protection

**SmartThrottle Engine** — adapts in real time across four threat levels:

<div align="center">

`🟢 Normal` → `🟡 Careful` → `🟠 Aggressive` → `🔴 Lockdown`

</div>

<br>

- **SYN Flood Detector** — instantly blocks IPs exceeding 50 connections/second
- **Slowloris Detector** — identifies and kills slow-drip connection drain attacks
- **Ping Flood Guard** — caps per-IP ping request rate
- **Sliding-Window Rate Limits** — enforced simultaneously at per-IP, per-subnet, and global levels

<br>

### 🤖 Bot Detection — AtomShield™

Threat score built from **7 weighted behavioral signals:**

<div align="center">

| Signal | Weight |
|:---|:---:|
| Connection Speed | `20%` |
| Join Pattern | `20%` |
| Handshake Validity | `15%` |
| Client Brand | `15%` |
| Geo / Country | `10%` |
| Username Pattern | `10%` |
| Protocol Version | `10%` |

</div>

<br>

**Score → Action mapping:**

<div align="center">

| Score | Result |
|:---:|:---|
| `< 40` | ✅ Pass |
| `40 – 60` | ⚠️ Flagged |
| `60 – 75` | 🔐 CAPTCHA — limbo server + math challenge |
| `75 – 90` | 🚫 Kick |
| `90+` | 🔨 Auto-ban |

</div>

<br>

- **Brand Analyzer** — whitelists Fabric, Forge, Lunar, Badlion, LabyMod, OptiFine, Sodium; blocks crasher & bot clients
- **Nickname Blocker** — regex patterns, prefix/suffix lists, length limits, special-character analysis
- **Verified Player Cache** — clean players bypass all checks for up to 48 hours

<br>

### 🌐 VPN & Proxy Detection

Parallel queries with **consensus voting** — minimum 2 positive hits required to block. Fail-open on timeout, so legitimate players are never affected by a slow API.

<div align="center">

| # | Provider | Method |
|:---:|:---|:---|
| 1 | **Local Blocklist** | Instant local lookup |
| 2 | **CIDR Blocker** | IP range rules |
| 3 | **DNSBL** | Spamhaus, DroneBL + custom lists |
| 4 | **IPHub** | Commercial VPN/proxy database |
| 5 | **ProxyCheck.io** | Real-time proxy detection |
| 6 | **AbuseIPDB** | Abuse history scoring |
| 7 | **IPApi** | ASN + hosting provider check |

</div>

<br>

- **Ip2Proxy Offline DB** — local queries, never hits API rate limits
- **ASN Bulk Blocking** — block entire hosting provider ASNs in one rule
- **Residential Bypass** — prevents false positives from legitimate ISPs
- **Result Cache** — verified clean IPs are cached indefinitely

<br>

### 🌍 Additional Protections

<details>
<summary><b>🌍 Country / Geo Filtering</b></summary>

<br>

MaxMind GeoIP2 integration — **whitelist or blacklist** entire countries. Automatic weekly database updates. Configurable fallback policy for unknown countries.

</details>

<details>
<summary><b>🔒 Firewall & Account Protection</b></summary>

<br>

- **IP Reputation Engine** — scores decay over time; successful logins grant a −15 point reward
- **Auto-Ban Engine** — rule-based permanent or temporary banning; first 3 violations are grace-period exempt
- **TempBan Manager** — automatic expiry and cleanup
- **Account Firewall** — Mojang API verification, account age check, cracked-account policy
- **Blacklist / Whitelist** — JSON-based, hot-reloadable without restart

</details>

<details>
<summary><b>⚡ IPTables — Kernel-Level Blocking</b></summary>

<br>

Block IPs at the **kernel level** — bypasses the JVM entirely for the fastest possible response.

- Supports `iptables`, `ip6tables`, and `nftables`
- Subnet banning — block entire `/24` ranges in a single rule
- Rules cleaned up automatically on startup and shutdown

</details>

<details>
<summary><b>💬 Chat, Command & Protocol Shields</b></summary>

<br>

| Feature | Details |
|:---|:---|
| Chat Rate Limiter | Per-second limit with configurable burst |
| Duplicate Detection | Tracks last N messages per player |
| Pattern Analysis | Caps ratio, repeated chars, link blocking |
| Tab-Complete Flood | Blocks > 5 tab requests/second |
| Command Flood | Per-second command rate limit |
| Server-Switch Abuse | Prevents rapid server-hop spam |
| Protocol Filter | Restrict client versions |
| Packet Size Limit | Block oversized / malformed packets |
| Crash Loop Detection | 3+ disconnects in 30s → challenge |
| Short Session | Sessions < 3s flagged as suspicious |

</details>

<details>
<summary><b>🔐 Password Security (AuthMe Integration)</b></summary>

<br>

- Temporary ban after 5 failed login attempts
- 10,000+ known weak passwords blocked at login
- Password similarity detection across the same IP

</details>

<br>

---

<div align="center">

## 🔨 Core Plugin — Paper 1.21.4

*44+ modules for crash fixes, exploit patches, and bot detection*

</div>

<br>

<details>
<summary><b>📦 Packet & Network Exploits</b></summary>

<br>

- Invalid packet filtering at the Netty pipeline level
- Oversized packet blocking
- Offline packet injection prevention
- Packet timing & delay abuse detection

</details>

<details>
<summary><b>🗂️ NBT & Item Attacks</b></summary>

<br>

- Nested NBT depth limiting
- Oversized NBT payload detection
- Bundle crash prevention
- Item sanitization on all inventory operations

</details>

<details>
<summary><b>🌍 World & Chunk Crashers</b></summary>

<br>

- Book & Lectern exploit fix
- Map label crash fix
- Item frame crash fix
- Sign exploit prevention
- Chunk crash protection

</details>

<details>
<summary><b>♊ Duplication Fixes</b></summary>

<br>

- Bundle duplication fix
- Inventory click duplication fix
- Cow & Mule duplication fix
- General dupe prevention engine

</details>

<details>
<summary><b>⚙️ Performance Limiters</b></summary>

<br>

- Redstone circuit rate limiting
- Explosion limiter
- Piston limiter
- Falling block limiter
- Per-chunk entity limiter

</details>

<br>

### 🤖 AtomShield™ Core

<div align="center">

`Connection Rate` · `Gravity Validation` · `Packet Timing` · `Ping & Handshake`
`Protocol` · `Username Pattern` · `First-Join Behavior` · `Post-Join Behavior` · `Heuristic Profiling`

</div>

<br>

- Builds a per-player behavioral profile and flags statistical anomalies in real time
- Suspicious players receive challenges before being whitelisted
- Attack Mode auto-activates when TPS drops or connection floods are detected

<br>

### ⚡ Integrations

<div align="center">

| Integration | Details |
|:---|:---|
| MySQL + HikariCP | Connection-pool database, shaded — zero classpath conflicts |
| Redis Pub/Sub | Network-wide synchronization |
| Discord Webhooks | Instant alerts for every blocked event |
| Web Panel | Browser-based live statistics dashboard |
| Async Logging | 7-day log rotation, fully off-thread |
| Hot Reload | Config changes applied without restart |

</div>

<br>

---

<div align="center">

## 📦 Requirements

| Component | Version | Status |
|:---|:---:|:---:|
| Java | 21+ | ✅ Required |
| Paper (or fork) | 1.21.4 | ✅ Required |
| PacketEvents | 2.6.0+ | ✅ Required for Core |
| Velocity | 3.x | ⚠️ Proxy module only |
| MySQL | 8.0+ | ⚠️ Optional |
| Redis | 7.x | ⚠️ Optional |
| MaxMind License | — | ⚠️ GeoIP only |

</div>

<br>

---

<div align="center">

## 🚀 Quick Start

</div>

**Paper Server**
```
1. Install PacketEvents  →  plugins/
2. Drop AtomGuard-core.jar  →  plugins/
3. Start server  →  config auto-generated
4. Edit  →  plugins/AtomGuard/config.yml
```

**Velocity Proxy**
```
1. Drop AtomGuard-velocity.jar  →  plugins/
2. Start proxy  →  config auto-generated
3. Edit  →  plugins/atomguard-velocity/config.yml
4. (Optional) Enable redis section on both sides
```

<br>

---

<div align="center">

## 💻 Commands & Permissions

| Command | Description | Permission |
|:---|:---|:---|
| `/atomguard status` | Live module overview | `atomguard.admin` |
| `/atomguard reload` | Hot-reload config | `atomguard.reload` |
| `/atomguard stats` | Statistics dashboard | `atomguard.admin` |
| `/panic` | Emergency lockdown | `atomguard.panic` |

| Permission | Effect |
|:---|:---|
| `atomguard.bypass` | Bypasses all protections |
| `atomguard.notify` | Receives exploit alerts in chat |

</div>

<br>

---

<div align="center">

## 🔌 Developer API

</div>

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

Available events: `ExploitBlockedEvent` · `AttackModeToggleEvent` · `PlayerReputationCheckEvent` · `ModuleToggleEvent`

<br>

---

<div align="center">

**[📂 GitHub](https://github.com/ATOMGAMERAGA/AtomGuard)** &nbsp;•&nbsp; **[🐛 Report a Bug](https://github.com/ATOMGAMERAGA/AtomGuard/issues)** &nbsp;•&nbsp; **[🤝 Contribute](https://github.com/ATOMGAMERAGA/AtomGuard/blob/main/CONTRIBUTING.md)**

<br>

<img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard" width="48">

<br>

Made with ❤️ by **ATOMLAND Studios**

</div>
