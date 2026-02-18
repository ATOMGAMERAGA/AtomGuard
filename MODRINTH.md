<p align="center">
  <img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard Logo" width="260">
</p>

<h1 align="center">⚛️ AtomGuard — Enterprise Minecraft Server Security</h1>

<p align="center">
  <strong>The most comprehensive security plugin for Paper 1.21.4 + Velocity.</strong><br>
  44+ crash/exploit fixes, multi-layer DDoS protection, AI-assisted bot detection, 7-provider VPN blocking, and kernel-level IPTables integration — all in one plugin.
</p>

---

## ✨ What Makes AtomGuard Different?

Most security plugins protect against one or two threat categories. AtomGuard covers the entire attack surface:

- **Before the handshake**: Velocity-level DDoS throttling, SYN flood detection, ping flood blocking
- **During connection**: Bot detection, VPN/proxy filtering, country blocking, protocol validation
- **After login**: Exploit fixes, crash prevention, duplication patches, performance limiting
- **Across all servers**: Redis-powered synchronization, shared banlists, network-wide attack mode

---

## 🛡️ Velocity Proxy Module

Deploy AtomGuard on your Velocity proxy to stop threats before they ever reach your backend.

### DDoS & Connection Floods
- **SmartThrottle Engine** — Four threat levels: Normal → Careful → Aggressive → Lockdown. Automatically scales based on incoming connection rate.
- **SYN Flood Detector** — Blocks IPs that attempt more than 50 connections/second.
- **Slowloris Detector** — Identifies and kills slow-drip HTTP-style connection attacks.
- **Per-IP / Per-Subnet / Global Rate Limits** — Sliding-window algorithm prevents any single source from overwhelming the proxy.

### Bot Detection (AtomShield™ Velocity)
Threat score calculated from 7 weighted factors:

| Factor | Weight |
|---|---|
| Connection Speed | 20% |
| Join Pattern | 20% |
| Handshake | 15% |
| Client Brand | 15% |
| Geo / Country | 10% |
| Username Pattern | 10% |
| Protocol | 10% |

- **Score < 40**: Allow
- **Score 40–60**: Flag
- **Score 60–75**: CAPTCHA challenge (limbo server, math question)
- **Score 75–90**: Kick
- **Score 90+**: Automatic ban

- **Brand Analyzer** — Recognizes Fabric, Forge, Lunar, Badlion, LabyMod, OptiFine, Sodium, Essential. Blocks crasher/exploit/bot clients.
- **Nickname Blocker** — Regex, prefix/suffix lists, length limits, all-digit and special-character checks.
- **Verified Player Cache** — Players with clean history bypass checks for up to 48 hours.

### VPN / Proxy Detection (7-Provider Chain)

| Provider | Type |
|---|---|
| Local Blocklist | Instant lookup |
| CIDR Blocker | IP range blocking |
| DNSBL (Spamhaus, DroneBL) | DNS-based reputation |
| IPHub | Commercial proxy database |
| ProxyCheck.io | Real-time proxy detection |
| AbuseIPDB | Abuse history scoring |
| IPApi | ASN + hosting detection |

Includes **Ip2Proxy** offline database support and **ASN-level bulk blocking** for hosting providers.

### Country / Geo Filtering
MaxMind GeoIP2 integration. Runs in whitelist or blacklist mode. Auto-updates weekly.

### Firewall & Account Protection
- IP reputation scoring (decays over time, boosted by clean logins)
- Automatic ban engine with configurable rules
- TempBan manager with auto-expiry
- Account age verification via Mojang API
- Cracked account policy (allow / block / challenge)
- JSON-based blacklist and whitelist (hot-reloadable)

### IPTables Integration
Block IPs at the **kernel level** — bypasses the JVM entirely. Supports `iptables`, `ip6tables`, and `nftables`. Rules are cleaned up automatically on restart.

### More Velocity Features
- Reconnect loop detection (3+ disconnects in 30 seconds → challenge)
- Short session detection (< 3 seconds → suspicious flag)
- Protocol version filtering
- Chat rate limiting + duplicate message detection + caps/link filtering
- Tab-complete flood blocker
- Command flood rate limiter
- Server-switch spam prevention
- Password brute-force protection (AuthMe integration, 10k+ common passwords)
- Redis bridge for cross-server ban sync
- Plugin Messaging channel for Core ↔ Velocity communication
- Discord webhook notifications for every threat type

---

## 🔨 Core Plugin (Paper 1.21.4)

### Exploit & Crash Fixes (44+ Modules)

**Packet & Network**
- Netty-level invalid packet filtering
- Oversized packet blocking
- Offline packet injection prevention
- Packet timing analysis

**NBT & Item Attacks**
- Nested NBT depth limiting
- Oversized NBT payload detection
- Bundle crash prevention
- Item sanitization on all inventory operations

**World Crashers**
- Book / Lectern exploit fix
- Map label crash fix
- Item frame crash fix
- Sign exploit prevention

**Duplication**
- Bundle duplication fix
- Inventory click duplication fix
- Cow / Mule duplication fix
- General dupe prevention

**Performance Limiters**
- Redstone circuit rate limiting
- Explosion limiter
- Piston limiter
- Falling block limiter
- Entity limiter per chunk

### AtomShield™ Core Bot Protection
9 behavioral checks: connection rate, gravity validation, packet timing, ping handshake, protocol, username patterns, first-join behavior, post-join behavior. Heuristic engine builds player profiles and flags anomalies in real time.

### Integrations
- **MySQL + HikariCP** — Connection-pool database with dependency shading (no conflicts)
- **Redis Pub/Sub** — Network-wide synchronization
- **Discord Webhooks** — Instant alerts for every blocked event
- **Web Panel** — Live browser dashboard

---

## 📦 Requirements

| | Version | Notes |
|---|---|---|
| Java | 21+ | Required |
| Paper | 1.21.4 | Or compatible forks |
| PacketEvents | 2.6.0+ | Required for core |
| Velocity | 3.x | For proxy module |
| MySQL | 8.0+ | Optional |
| Redis | 7.x | Optional, for cross-server sync |
| MaxMind License | — | Optional, for GeoIP |

---

## 🚀 Quick Start

**Paper:**
1. Install [PacketEvents](https://modrinth.com/plugin/packetevents)
2. Drop `AtomGuard-core.jar` into `plugins/`
3. Start server, edit `plugins/AtomGuard/config.yml`

**Velocity:**
1. Drop `AtomGuard-velocity.jar` into Velocity `plugins/`
2. Start proxy, edit `plugins/atomguard-velocity/config.yml`
3. Enable Redis on both sides for full network sync (optional)

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

Listen to `ExploitBlockedEvent`, `AttackModeToggleEvent`, `PlayerReputationCheckEvent`, and `ModuleToggleEvent`. Toggle modules at runtime. Query IP reputation scores. Full JavaDoc included.

---

## 💻 Commands

| Command | Description |
|---|---|
| `/atomguard status` | Live module status |
| `/atomguard reload` | Hot-reload config |
| `/atomguard stats` | Statistics overview |
| `/panic` | Emergency lockdown |

---

<p align="center">
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard">GitHub</a> •
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/issues">Report a Bug</a> •
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/blob/main/CONTRIBUTING.md">Contribute</a>
</p>

<p align="center">
  Made with ❤️ by <strong>AtomGuard Team</strong>
</p>
