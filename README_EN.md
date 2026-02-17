<p align="center">
  <img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard Logo" width="250">
</p>

# 🛡️ Atom Guard — Advanced Server Security

[![Build Status](https://img.shields.io/github/actions/workflow/status/ATOMGAMERAGA/AtomGuard/build.yml?branch=main)](https://github.com/ATOMGAMERAGA/AtomGuard/actions)
[![License](https://img.shields.io/github/license/ATOMGAMERAGA/AtomGuard)](LICENSE)
[![Release](https://img.shields.io/github/v/release/ATOMGAMERAGA/AtomGuard)](https://github.com/ATOMGAMERAGA/AtomGuard/releases)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21.4-blue)](https://papermc.io/)

**Atom Guard** is a high-performance, professional exploit fixer and server protection plugin specifically designed for **Paper 1.21.4**. It provides a comprehensive suite of over 44 protection modules to mitigate crashes, duplication glitches, packet exploits, and bot attacks.

---

## ✨ Features

### 🛡️ Crash Protection
- **Packet Exploit Fixer**: Filters invalid packets at the Netty level.
- **NBT Protection**: Limits excessive NBT data size and depth.
- **World Crasher Fix**: Prevents sign, book, map, and item frame exploits.
- **Chunk Ban Fix**: Mitigates excessive entity/tile entity loading.

### 🤖 AtomShield™ Bot Protection
- **Smart Analysis**: Connection rate, ping, behavior, and protocol analysis.
- **IP Reputation**: Automatically blocks Proxy/VPN and hosting IPs.
- **Attack Mode**: Strict protection that activates automatically under high load.
- **Velocity Support**: Velocity module for proxy-level protection.

### ⚡ Performance & Optimization
- **Lag Detection**: Limits heavy operations when server TPS drops.
- **Redstone Limiter**: Slows down excessive redstone circuits.
- **Entity Limiter**: Optimizes entity count per chunk.
- **Async Operations**: Database and network operations do not block the main thread.

### 🌐 Integration
- **MySQL & Redis**: Multi-server support and data synchronization.
- **Discord Webhook**: Sends attack and exploit notifications to Discord.
- **Web Panel**: Live statistics and management via browser.
- **Multi-language**: English and Turkish language support.

---

## 🏗️ Architecture

```
AtomGuard/
├── api/          # Public API for developers
├── core/         # Main plugin (Bukkit/Paper)
└── velocity/     # Velocity Proxy module
```

## 📦 Requirements

| Requirement | Version |
| :--- | :--- |
| **Java** | 21 or higher |
| **Server** | Paper 1.21.4 (or forks) |
| **PacketEvents** | 2.6.0+ (Required) |

## 🚀 Installation

1.  Install **PacketEvents** plugin on your server.
2.  Download the latest **AtomGuard-1.0.0.jar** from [Releases](https://github.com/ATOMGAMERAGA/AtomGuard/releases).
3.  Drop the file into your `plugins` folder.
4.  Start the server.
5.  Configure `plugins/AtomGuard/config.yml`.

## 💻 Commands and Permissions

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/atomguard` | Main command (help menu) | `atomguard.admin` |
| `/atomguard reload` | Reloads configuration | `atomguard.reload` |
| `/atomguard status` | Shows module status | `atomguard.admin` |
| `/panic` | Emergency mode (bot attack) | `atomguard.panic` |

**Other Permissions:**
- `atomguard.bypass`: Bypasses all protections.
- `atomguard.notify`: Receives notifications.

## 🔌 API Usage

Add to your Maven project:

```xml
<dependency>
    <groupId>com.atomguard</groupId>
    <artifactId>AtomGuard-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

```java
// Example: Check a player's reputation
int score = AtomGuardAPI.getReputation(player);
```

## 🔨 Building

Clone the source and build with Maven:

```bash
git clone https://github.com/ATOMGAMERAGA/AtomGuard.git
cd AtomGuard
mvn clean package
```

The output file will be in `core/target/`.

## 🤝 Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md).

## 📜 License

This project is licensed under the **BSD 3-Clause** license. See [LICENSE](LICENSE) for details.

---
<div align="center">
  Made with ❤️ by <strong>AtomGuard Team</strong>
</div>