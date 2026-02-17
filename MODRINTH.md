<p align="center">
  <img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard Logo" width="250">
</p>

# 🛡️ Atom Guard — Advanced Server Security

**Atom Guard** is a high-performance, professional-grade exploit fixer and server protection plugin for **Paper 1.21.4**. Designed for competitive SMPs and large networks, it mitigates crashes, duplication glitches, and bot attacks with over 44 modular protection layers.

---

## ✨ Key Features

### 🛡️ Exploit & Crash Fixes
*   **Packet-Level Filtering:** Intercepts and blocks malicious packets at the Netty/PacketEvents level.
*   **NBT Sanitization:** Deep-scans items to prevent oversized NBT data and nested bundle crashes.
*   **World Protection:** Fixes exploits related to Signs, Books, Lecterns, and Item Frames.
*   **Chunk Safety:** Prevents "Chunk Bans" by limiting excessive entity and tile entity counts.

### 🤖 AtomShield™ Bot Protection
*   **Heuristic Analysis:** Intelligent behavior tracking to detect bot-like movements and rotations.
*   **IP Reputation:** Real-time checking against proxy, VPN, and hosting provider databases.
*   **Attack Mode:** Automatically ramps up security during high-load connection floods.
*   **Velocity Support:** Seamlessly integrates with Velocity proxies for network-wide protection.

### ⚡ Performance Optimization
*   **Smart Lag Detection:** Automatically throttles heavy operations (like Redstone or Falling Blocks) when TPS drops.
*   **Async Processing:** Database and logging operations run off-thread to ensure zero main-thread impact.
*   **Limiters:** Configurable limits for Redstone, Explosions, Pistons, and Entities.

### 🌐 Management & Integration
*   **Web Panel:** Live dashboard for real-time statistics and module control.
*   **Discord Webhooks:** Get instant notifications for blocked exploits and attacks.
*   **MySQL & Redis:** Scalable data storage and cross-server synchronization.

---

## 📦 Requirements

*   **Java:** 21 or higher.
*   **Server:** Paper 1.21.4 (or compatible forks).
*   **Dependency:** [PacketEvents 2.6.0+](https://modrinth.com/plugin/packetevents) (Required).

## 🚀 Installation

1.  Download and install **PacketEvents** on your server.
2.  Place the **AtomGuard.jar** in your `plugins` folder.
3.  Restart your server.
4.  Configure your settings in `plugins/AtomGuard/config.yml`.

---

## 🔌 API for Developers

Easily integrate with AtomGuard using our comprehensive API:

```xml
<dependency>
    <groupId>com.atomguard</groupId>
    <artifactId>AtomGuard-api</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

<p align="center">
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard">GitHub</a> • 
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/issues">Report a Bug</a> • 
  Made with ❤️ by <strong>AtomGuard Team</strong>
</p>
