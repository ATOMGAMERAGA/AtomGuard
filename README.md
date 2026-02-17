# 🛡️ Atom Guard — Advanced Server Security

[![Build Status](https://img.shields.io/github/actions/workflow/status/ATOMGAMERAGA/AtomGuard/build.yml?branch=main)](https://github.com/ATOMGAMERAGA/AtomGuard/actions)
[![License](https://img.shields.io/github/license/ATOMGAMERAGA/AtomGuard)](LICENSE)
[![Release](https://img.shields.io/github/v/release/ATOMGAMERAGA/AtomGuard)](https://github.com/ATOMGAMERAGA/AtomGuard/releases)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Paper-1.21.4-blue)](https://papermc.io/)

**Atom Guard** is a high-performance, professional exploit fixer and server protection plugin specifically designed for **Paper 1.21.4**. It provides a comprehensive suite of over 44 protection modules to mitigate crashes, duplication glitches, packet exploits, and bot attacks.

---

## ✨ Özellikler

### 🛡️ Crash Koruması
- **Packet Exploit Fixer**: Netty seviyesinde geçersiz paketleri filtreler.
- **NBT Koruması**: Aşırı büyük NBT verilerini ve derinliği sınırlar.
- **World Crasher Fix**: Tabela, kitap, harita ve item frame exploitlerini engeller.
- **Chunk Ban Fix**: Aşırı entity/tile entity yükünü önler.

### 🤖 AtomShield™ Bot Koruması
- **Akıllı Analiz**: Bağlantı hızı, ping, davranış ve protokol analizi.
- **IP Reputation**: Proxy/VPN ve hosting IP'lerini otomatik engeller.
- **Saldırı Modu**: Yüksek yük altında otomatik devreye giren katı koruma.
- **Velocity Desteği**: Proxy seviyesinde koruma için Velocity modülü.

### ⚡ Performans & Optimizasyon
- **Lag Tespiti**: Sunucu TPS düştüğünde ağır işlemleri sınırlar.
- **Redstone Limiter**: Aşırı redstone devrelerini yavaşlatır.
- **Entity Limiter**: Chunk başına entity sayısını optimize eder.
- **Async İşlemler**: Veritabanı ve ağ işlemleri ana thread'i bloke etmez.

### 🌐 Entegrasyon
- **MySQL & Redis**: Çoklu sunucu desteği ve veri senkronizasyonu.
- **Discord Webhook**: Saldırı ve exploit bildirimlerini Discord'a gönderir.
- **Web Panel**: Tarayıcı üzerinden canlı istatistik ve yönetim.
- **Çoklu Dil**: Türkçe ve İngilizce dil desteği.

---

## 🏗️ Mimari

```
AtomGuard/
├── api/          # Geliştiriciler için Public API
├── core/         # Ana eklenti (Bukkit/Paper)
└── velocity/     # Velocity Proxy modülü
```

## 📦 Gereksinimler

| Gereksinim | Versiyon |
| :--- | :--- |
| **Java** | 21 veya üzeri |
| **Sunucu** | Paper 1.21.4 (veya forkları) |
| **PacketEvents** | 2.6.0+ (Zorunlu) |

## 🚀 Kurulum

1.  **PacketEvents** pluginini sunucunuza yükleyin.
2.  En son **AtomGuard-1.0.0.jar** dosyasını [Releases](https://github.com/ATOMGAMERAGA/AtomGuard/releases) sayfasından indirin.
3.  Dosyayı `plugins` klasörüne atın.
4.  Sunucuyu başlatın.
5.  `plugins/AtomGuard/config.yml` dosyasını düzenleyin.

## 💻 Komutlar ve İzinler

| Komut | Açıklama | İzin |
| :--- | :--- | :--- |
| `/atomguard` | Ana komut (yardım menüsü) | `atomguard.admin` |
| `/atomguard reload` | Konfigürasyonu yeniler | `atomguard.reload` |
| `/atomguard status` | Modül durumlarını gösterir | `atomguard.admin` |
| `/panic` | Acil durum modu (bot saldırısı) | `atomguard.panic` |

**Diğer İzinler:**
- `atomguard.bypass`: Tüm korumaları atlar.
- `atomguard.notify`: Bildirimleri görür.

## 🔌 API Kullanımı

Maven projenize ekleyin:

```xml
<dependency>
    <groupId>com.atomguard</groupId>
    <artifactId>AtomGuard-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

```java
// Örnek: Bir oyuncunun itibarını kontrol et
int score = AtomGuardAPI.getReputation(player);
```

## 🔨 Derleme

Proje kaynak kodunu indirin ve Maven ile derleyin:

```bash
git clone https://github.com/ATOMGAMERAGA/AtomGuard.git
cd AtomGuard
mvn clean package
```

Çıktı dosyası `core/target/` dizininde olacaktır.

## 🤝 Katkıda Bulunma

Lütfen [CONTRIBUTING.md](CONTRIBUTING.md) dosyasını okuyun.

## 📜 Lisans

Bu proje **BSD 3-Clause** lisansı ile lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasına bakın.

---
<div align="center">
  Made with ❤️ by <strong>AtomGuard Team</strong>
</div>