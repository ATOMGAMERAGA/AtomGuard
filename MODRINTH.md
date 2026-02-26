<div align="center">

<img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard" width="200">

<br>

# ⚛️ AtomGuard

*Advanced Minecraft Server Security — Paper 1.21.4 + Velocity*

<br>

[![Build](https://img.shields.io/github/actions/workflow/status/ATOMGAMERAGA/AtomGuard/build.yml?branch=main&style=for-the-badge&logo=github&logoColor=white&label=Build&color=22c55e)](https://github.com/ATOMGAMERAGA/AtomGuard/actions)
[![Release](https://img.shields.io/github/v/release/ATOMGAMERAGA/AtomGuard?style=for-the-badge&color=00C7B7&label=Release)](https://github.com/ATOMGAMERAGA/AtomGuard/releases)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Paper](https://img.shields.io/badge/Paper-1.21.4-00AA00?style=for-the-badge)](https://papermc.io)
[![Velocity](https://img.shields.io/badge/Velocity-3.x-8A6DF7?style=for-the-badge)](https://papermc.io/software/velocity)
[![License](https://img.shields.io/badge/License-BSD--3-4A90D9?style=for-the-badge)](https://github.com/ATOMGAMERAGA/AtomGuard/blob/main/LICENSE)

<br>

**44+ security modules · DDoS & flood protection · Multi-layer bot detection**
**7-provider VPN filtering · Kernel-level IPTables · Full exploit & crash fixes**
**Threat Intelligence · Player Trust Score · Forensic Analysis · Honeypot**

</div>

---

## 🗺️ What AtomGuard Covers

AtomGuard defends every stage of the connection lifecycle — from the very first TCP packet to in-game actions.

**🔌 Before Handshake**
- DDoS throttling · SYN flood blocking · Ping flood detection · Rate limiting

**🤝 During Connection**
- Bot scoring · VPN filtering · Country blocking · Protocol validation

**🎮 After Login**
- Crash fixes · Exploit patches · Dupe prevention · Performance limits

**🌐 Network-Wide**
- Redis sync · Shared banlists · Attack mode · Discord alerts

---

## 🛡️ Velocity Proxy Module

*Stop threats before they ever reach your backend servers*

<br>

### ⚔️ DDoS & Flood Protection

**SmartThrottle Engine** — adapts in real time across five threat levels:

<div align="center">

`🟢 Normal` → `🟡 Elevated` → `🟠 High` → `🔴 Critical` → `⛔ Lockdown`

</div>

- **SYN Flood Detector** — instantly blocks IPs exceeding the connection threshold per second
- **Slowloris Detector** — identifies and kills slow-drip connection drain attacks
- **TrafficAnomalyDetector** — Z-score, slow-ramp, and pulse attack detection
- **ConnectionFingerprinter** — protocol + hostname + timing fingerprint to detect bot armies
- **SubnetAnalyzer** — coordinated botnet detection at /24 and /16 level
- **Ping Flood Guard** — caps per-IP ping request rate
- **Sliding-Window Rate Limits** — enforced at per-IP, per-subnet, and global levels simultaneously
- **VerifiedPlayerShield** — guarantees a slot for clean players during CRITICAL/LOCKDOWN
- **AttackSessionRecorder** — full session log from start to end, JSON export

<br>

### 🤖 Bot Detection

Threat score built from **8 weighted behavioral signals:**

| Signal | Weight |
|:---|:---:|
| Connection Speed | `20%` |
| Join Pattern | `20%` |
| Handshake Validity | `15%` |
| Client Brand | `15%` |
| Geo / Country | `10%` |
| Username Pattern | `10%` |
| Protocol Version | `10%` |

**Score → Action mapping:**

| Score | Result |
|:---:|:---|
| `< 40` | ✅ Pass |
| `40 – 60` | ⚠️ Flagged |
| `60 – 75` | 🔐 CAPTCHA — limbo + math challenge |
| `75 – 90` | 🚫 Kick |
| `90+` | 🔨 Auto-ban |

- **Brand Analyzer** — whitelists Fabric, Forge, Lunar, Badlion, LabyMod, OptiFine, Sodium; blocks crasher & bot clients
- **Nickname Blocker** — regex patterns, prefix/suffix lists, length limits, special-character analysis
- **Verified Player Cache** — clean players bypass all checks for up to 48 hours

<br>

### 🌐 VPN & Proxy Detection

Parallel queries with **consensus voting** — minimum 2 positive hits required to block. Fail-open on timeout.

| # | Provider | Method |
|:---:|:---|:---|
| 1 | **Local Blocklist** | Instant local lookup |
| 2 | **CIDR Blocker** | IP range rules |
| 3 | **DNSBL** | Spamhaus, DroneBL + custom lists |
| 4 | **IPHub** | Commercial VPN/proxy database |
| 5 | **ProxyCheck.io** | Real-time proxy detection |
| 6 | **AbuseIPDB** | Abuse history scoring |
| 7 | **IPApi** | ASN + hosting provider check |

- **Ip2Proxy Offline DB** — local queries, never hits API rate limits
- **ASN Bulk Blocking** — block entire hosting provider ASNs in one rule
- **Residential Bypass** — prevents false positives from legitimate ISPs (e.g. Turkish ISPs)
- **Result Cache** — verified clean IPs cached and skip re-checking entirely

<br>

<details>
<summary><b>🌍 Country / Geo Filtering</b></summary>

<br>

MaxMind GeoIP2 integration — **whitelist or blacklist** entire countries. Automatic weekly database updates. Configurable fallback policy for unknown countries.

</details>

<details>
<summary><b>🔒 Firewall & Account Protection</b></summary>

<br>

- **IP Reputation Engine** — scores decay every 5 min; successful logins grant a −15 point reward
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

---

## 🔨 Core Plugin

*44+ modules for crash fixes, exploit patches, and bot detection*

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

**9 behavioral signals analyzed per player:**

`Connection Rate` · `Gravity Validation` · `Packet Timing` · `Ping & Handshake`
`Protocol` · `Username Pattern` · `First-Join Behavior` · `Post-Join Behavior` · `Heuristic Profiling`

- Builds a per-player behavioral profile and flags statistical anomalies in real time
- Suspicious players receive challenges before being whitelisted
- Attack Mode auto-activates when TPS drops or connection floods are detected

<br>

### 🧠 Threat Intelligence Engine *(v1.2.0)*

- **168-hour EMA traffic profile** — baseline built from 7 days of rolling traffic data
- **Z-Score anomaly detection** — ELEVATED / HIGH / CRITICAL threat levels
- **3-minute confirmation window** — consecutive minutes required before escalation (prevents false positives)
- **Auto attack-mode activation** on CRITICAL anomaly
- Command: `/ag intel <status|reset>`

<br>

### 🏅 Player Trust Score *(v1.2.0)*

Four trust tiers based on play history:

| Tier | Description | Benefit |
|:---|:---|:---|
| 🆕 New | Fresh account | Full checks applied |
| 📅 Regular | Some history | Standard protection |
| ✅ Trusted | Clean record | Skips attack mode checks |
| ⭐ Veteran | Long history | Skips bot & VPN checks |

- EMA-weighted formula: playtime + clean sessions + violation history
- Persistent storage via `trust-scores.json`
- Command: `/ag trust <info|set|reset|top>`

<br>

### 🔬 Forensic Analysis *(v1.2.0)*

- **Attack snapshots** — UUID, timeline, peak rate, blocked IPs, module stats
- **4 severity levels** — LOW / MEDIUM / HIGH / CRITICAL
- **Auto-export** to `forensics/attack-<uuid>.json`
- **`AttackSnapshotCompleteEvent`** API event
- Command: `/ag replay <list|latest|<id>|export>`

<br>

### 🍯 Honeypot Module *(v1.2.0)*

- **Fake TCP Minecraft server** (SLP protocol) — lures bot scanners
- **Auto-blacklist** IPs that probe the honeypot
- **`HoneypotTrapEvent`** API event
- Command: `/ag honeypot <status|stats>`

<br>

### ⚡ Integrations

| Integration | Details |
|:---|:---|
| MySQL + HikariCP | Connection-pool database, shaded — zero classpath conflicts |
| Redis Pub/Sub | Network-wide synchronization |
| Discord Webhooks | Instant alerts for every blocked event + intelligence + forensics |
| Web Panel | Browser-based live statistics dashboard |
| Config Migration | Automatic migration chain with pre-migration backups |
| Async Logging | 7-day log rotation, fully off-thread |
| Hot Reload | Config changes applied without restart |

---

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

---

## 🚀 Quick Start

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

---

## 💻 Commands & Permissions

**Core Commands**

| Command | Description | Permission |
|:---|:---|:---|
| `/atomguard status` | Live module overview | `atomguard.admin` |
| `/atomguard reload` | Hot-reload config | `atomguard.reload` |
| `/atomguard stats` | Statistics dashboard | `atomguard.admin` |
| `/ag intel <status\|reset>` | Threat intelligence status | `atomguard.admin` |
| `/ag trust <info\|set\|reset\|top>` | Player trust scores | `atomguard.admin` |
| `/ag replay <list\|latest\|export>` | Forensic attack replay | `atomguard.admin` |
| `/ag honeypot <status\|stats>` | Honeypot module info | `atomguard.admin` |
| `/panic` | Emergency lockdown | `atomguard.panic` |

**Permissions**

| Permission | Effect |
|:---|:---|
| `atomguard.bypass` | Bypasses all protections |
| `atomguard.notify` | Receives exploit alerts in chat |
| `atomguard.admin` | Full access to all commands |
| `atomguard.reload` | Config reload only |
| `atomguard.panic` | Emergency lockdown |

---

## 🔌 Developer API

```xml
<dependency>
    <groupId>com.atomguard</groupId>
    <artifactId>AtomGuard-api</artifactId>
    <version>1.2.2</version>
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

// Trust score system (v1.2.0+)
AtomGuardAPI.getInstance().getTrustScoreManager();

// Forensics & intelligence (v1.2.0+)
AtomGuardAPI.getInstance().getForensicsManager();
AtomGuardAPI.getInstance().getIntelligenceEngine();

// Listen for blocked exploits
@EventHandler
public void onExploitBlocked(ExploitBlockedEvent event) {
    String module = event.getModuleName();
    Player player  = event.getPlayer();
}
```

**Available Events:**
`ExploitBlockedEvent` · `AttackModeToggleEvent` · `PlayerReputationCheckEvent` · `ModuleToggleEvent`
`ThreatScoreChangedEvent` · `HoneypotTrapEvent` · `IntelligenceAlertEvent` · `AttackSnapshotCompleteEvent`

---

<div align="center">

**[📂 GitHub](https://github.com/ATOMGAMERAGA/AtomGuard)** &nbsp;•&nbsp; **[🐛 Report a Bug](https://github.com/ATOMGAMERAGA/AtomGuard/issues)** &nbsp;•&nbsp; **[🤝 Contribute](https://github.com/ATOMGAMERAGA/AtomGuard/blob/main/CONTRIBUTING.md)**

<br>

<img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard" width="48">

<br>

Made with ❤️ by **ATOMLAND Studios**

</div>
