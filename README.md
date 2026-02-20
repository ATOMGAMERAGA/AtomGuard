<p align="center">
  <img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard Logo" width="240">
</p>

<h1 align="center">⚛️ AtomGuard</h1>

<p align="center">
  <strong>Paper 1.21.4 + Velocity için çok katmanlı Minecraft sunucu güvenlik eklentisi</strong>
</p>

<p align="center">
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/releases/latest">
    <img src="https://img.shields.io/github/v/release/ATOMGAMERAGA/AtomGuard?style=flat-square&color=5865F2&label=son+s%C3%BCr%C3%BCm" alt="Son Sürüm">
  </a>
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/actions">
    <img src="https://img.shields.io/github/actions/workflow/status/ATOMGAMERAGA/AtomGuard/build.yml?branch=main&style=flat-square&label=build" alt="Build">
  </a>
  <img src="https://img.shields.io/badge/java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/paper-1.21.4-00AA00?style=flat-square" alt="Paper 1.21.4">
  <img src="https://img.shields.io/badge/velocity-3.x-7B2FBE?style=flat-square" alt="Velocity">
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/ATOMGAMERAGA/AtomGuard?style=flat-square&color=lightgrey" alt="Lisans">
  </a>
</p>

<p align="center">
  <a href="README_EN.md">🇬🇧 English</a> &nbsp;|&nbsp;
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/releases">📦 İndirmeler</a> &nbsp;|&nbsp;
  <a href="CHANGELOG.md">📋 Değişiklik Günlüğü</a>
</p>

---

AtomGuard, Minecraft sunucunuzu **DDoS saldırıları**, **bot atakları**, **crash exploitleri** ve **duplikasyon açıklarına** karşı koruyan açık kaynaklı bir güvenlik eklentisidir. Paper 1.21.4 ile birlikte Velocity proxy üzerinde de çalışır; tehditleri backend'e ulaşmadan durdurur.

---

## İçindekiler

- [Neden AtomGuard?](#-neden-atomguard)
- [Velocity Proxy Modülü](#-velocity-proxy-modülü)
- [Core Modülü (Paper)](#-core-modülü-paper)
- [Gereksinimler](#-gereksinimler)
- [Kurulum](#-kurulum)
- [Komutlar & İzinler](#-komutlar--i̇zinler)
- [Developer API](#-developer-api)
- [Derleme](#-derleme)
- [Mimari](#-mimari)
- [Lisans](#-lisans)

---

## 🚀 Neden AtomGuard?

| Özellik | AtomGuard | Diğer Eklentiler |
|---|---|---|
| Velocity Proxy Koruması | ✅ 12+ modül, tam entegre | ❌ Yok / çok sınırlı |
| DDoS & Bot Koruması | ✅ 5 kademeli saldırı yönetimi | ⚠️ Temel düzey |
| VPN / Proxy Tespiti | ✅ 7 farklı sağlayıcı zinciri | ⚠️ 1–2 API |
| IPTables Entegrasyonu | ✅ Kernel seviyesi engelleme | ❌ Yok |
| Gerçek Zamanlı Tehdit Skoru | ✅ Çok faktörlü skor | ❌ Yok |
| Crash & Dupe Koruması | ✅ 44+ modül | ⚠️ 10–20 modül |
| Geliştirici API | ✅ Maven artifact | ❌ Yok |

---

## 🛡️ Velocity Proxy Modülü

AtomGuard'ın Velocity modülü, tehditler backend sunucularınıza ulaşmadan proxy katmanında durdurun. Redis veya Plugin Messaging ile core modülüyle senkronize çalışır.

### ⚔️ DDoS & Bağlantı Koruması

| Bileşen | Açıklama |
|---|---|
| **AttackLevelManager** | 5 kademeli saldırı yönetimi — NONE / ELEVATED / HIGH / CRITICAL / LOCKDOWN; hysteresis ile ani geçiş engeli |
| **SmartThrottle Engine** | Saldırı seviyesine göre otomatik hız sınırlama |
| **SYN Flood Dedektörü** | Saniyede eşik üstü bağlantıyı otomatik engeller |
| **TrafficAnomalyDetector** | Z-skoru, yavaş rampa ve nabız saldırısı tespiti |
| **EnhancedSlowloris** | IP başına bekleyen bağlantı izleme, sistem geneli alarm |
| **ConnectionFingerprinter** | Protokol + hostname + timing parmak izi ile bot ordusu tespiti |
| **SubnetAnalyzer** | /24 ve /16 bazlı koordineli botnet tespiti |
| **IPReputationTracker** | DDoS'a özgü itibar skoru (0–100), otomatik geçici ban |
| **AttackSessionRecorder** | Saldırı başlangıcından bitişine tam oturum kaydı, JSON çıktısı |
| **VerifiedPlayerShield** | CRITICAL/LOCKDOWN seviyesinde temiz oyunculara garantili slot |

### 🤖 Bot Koruması

- **Çok Faktörlü Tehdit Skoru** — bağlantı hızı, handshake, client brand, join pattern, kullanıcı adı, coğrafi konum ve protokol analizi
- **Brand Analizi** — Fabric, Forge, Lunar, Badlion, LabyMod gibi bilinen client'ları tanır; bot/exploit client'larını engeller
- **Join Pattern Dedektörü** — bot sürüsü davranışlarını istatistiksel olarak tespit eder
- **CAPTCHA Sistemi** — şüpheli oyuncuları limbo'ya yönlendirir, matematik sorusu çözdürür
- **Doğrulanmış Oyuncu Cache'i** — başarılı giriş geçmişi olan oyuncular analizi atlar

### 🌐 VPN / Proxy Tespiti — 7 Katman

| # | Sağlayıcı | Açıklama |
|---|---|---|
| 1 | **Yerel Liste** | Anlık yerel kara liste |
| 2 | **CIDR Blocker** | IP aralığı bazlı engelleme |
| 3 | **DNSBL** | Spamhaus, DroneBL ve özel listeler |
| 4 | **IPHub** | Ticari VPN/proxy veritabanı |
| 5 | **ProxyCheck.io** | Gerçek zamanlı proxy kontrolü |
| 6 | **AbuseIPDB** | Kötüye kullanım geçmişi |
| 7 | **IPApi** | ASN + hosting tespiti |

> **Konsensüs sistemi:** Engelleme kararı için en az 2 sağlayıcı onayı gerekir. Tek sağlayıcı false positive oluşturamaz.

### 🌍 Coğrafi Filtreleme

- MaxMind GeoIP2 ile ülke bazlı whitelist / blacklist
- Bilinmeyen ülkeler için özelleştirilebilir politika

### 🔒 Güvenlik Duvarı & Hesap Koruması

- **IP İtibar Motoru** — başarılı giriş, flood ve exploit geçmişine göre skor
- **Otomatik Ban Motoru** — kural bazlı geçici / kalıcı ban
- **Hesap Güvenlik Duvarı** — Mojang API doğrulaması, hesap yaşı kontrolü, cracked politikası
- **Kara / Beyaz Liste** — JSON tabanlı, runtime güncellenebilir

### ⚡ IPTables Entegrasyonu

- Kernel seviyesinde gerçek zamanlı IP engelleme (iptables / nftables)
- Otomatik kural temizleme
- /24 subnet ban desteği

### 🔄 Protokol & Bağlantı Kontrolü

- **Crash Döngüsü Tespiti** — 30 saniyede 3'ten fazla bağlantı kesintisi
- **Protokol Filtresi** — izinli client versiyonlarını kısıtlar
- **Paket Boyutu Sınırı** — büyük / geçersiz paketleri engeller

### 💬 Chat & Komut Koruması

- Chat rate limit, duplicate mesaj tespiti, pattern analizi
- Tab-complete flood, komut flood, sunucu geçiş spam koruması

### 📡 Senkronizasyon

- **Redis Bridge** — sunucular arası anlık ban / alert senkronizasyonu
- **Plugin Messaging** — Core ↔ Velocity güvenli iletişim
- **Discord Webhook** — saldırı, bot, VPN, DDoS bildirimleri

---

## 🔨 Core Modülü (Paper)

### 💥 Crash & Exploit Koruması — 44+ Modül

| Kategori | Modüller |
|---|---|
| Packet Exploitler | PacketExploitModule, OfflinePacketModule, NettyCrashModule, PacketDelayModule |
| NBT Saldırıları | NBTCrasherModule, ItemSanitizerModule, CustomPayloadModule, AdvancedPayloadModule |
| Dünya Crasherleri | BookCrasherModule, LecternCrasherModule, MapLabelCrasherModule, FrameCrashModule |
| Chunk / Entity | ChunkCrashModule, EntityInteractCrashModule, ContainerCrashModule |
| Duplikasyon | BundleDuplicationModule, InventoryDuplicationModule, CowDuplicationModule, MuleDuplicationModule |
| Envanter | InvalidSlotModule, BundleLockModule, CreativeItemsModule, AnvilCraftCrashModule |
| Hareket | MovementSecurityModule, NormalizeCoordinatesModule |
| Komutlar | CommandsCrashModule, ComponentCrashModule |
| Performans | RedstoneLimiterModule, ExplosionLimiterModule, PistonLimiterModule, FallingBlockLimiterModule |
| Bot Koruması | AntiBotModule, BotProtectionModule, ConnectionThrottleModule |

### 🤖 AtomShield™ — Core Bot Koruması

- **9 Kontrol** — bağlantı hızı, gravity, paket timing, ping/handshake, protokol, kullanıcı adı pattern, ilk katılım ve katılım sonrası davranış
- **Heuristik Motor** — oyuncu başına profil, istatistiksel anormallik tespiti
- **Doğrulama Sistemi** — şüpheli oyunculara challenge uygulanır
- **Saldırı Modu** — TPS düştüğünde veya flood tespitinde otomatik aktif

### ⚡ Performans & Entegrasyonlar

- **MySQL + HikariCP** — bağlantı havuzu ile hızlı veri depolama
- **Redis Pub/Sub** — ağ genelinde senkronizasyon
- **Discord Webhook** — anlık exploit ve saldırı bildirimleri
- **Web Panel** — tarayıcı tabanlı canlı istatistik paneli
- **Async Logging** — 7 günlük rotasyon, async dosya yazımı

---

## 📦 Gereksinimler

| Bileşen | Versiyon | Durum |
|---|---|---|
| Java | 21+ | Zorunlu |
| Paper / Forks | 1.21.4 | Zorunlu |
| PacketEvents | 2.6.0+ | Zorunlu (Core) |
| Velocity | 3.x | Proxy için |
| MySQL | 8.0+ | İsteğe bağlı |
| Redis | 7.x | İsteğe bağlı |
| MaxMind GeoIP2 | — | İsteğe bağlı |

---

## 🚀 Kurulum

### Paper Sunucu

```bash
1. PacketEvents → plugins/ klasörüne koyun
   https://modrinth.com/plugin/packetevents

2. AtomGuard-core-1.1.0.jar → plugins/ klasörüne koyun

3. Sunucuyu başlatın — config otomatik oluşturulur

4. plugins/AtomGuard/config.yml dosyasını düzenleyin
```

### Velocity Proxy

```bash
1. AtomGuard-velocity-1.1.0.jar → Velocity plugins/ klasörüne koyun

2. Proxy'yi başlatın — config otomatik oluşturulur

3. plugins/atomguard-velocity/config.yml dosyasını düzenleyin

4. Redis senkronizasyonu için her iki tarafta da
   redis.aktif: true yapın
```

---

## 💻 Komutlar & İzinler

| Komut | Açıklama | İzin |
|---|---|---|
| `/atomguard` | Yardım menüsü | `atomguard.admin` |
| `/atomguard reload` | Config yenileme | `atomguard.reload` |
| `/atomguard status` | Modül durumları | `atomguard.admin` |
| `/atomguard stats` | İstatistikler | `atomguard.admin` |
| `/panic` | Acil mod — tüm modüller sıkılaşır | `atomguard.panic` |

| İzin | Açıklama |
|---|---|
| `atomguard.bypass` | Tüm korumaları atlar |
| `atomguard.notify` | Exploit bildirimlerini alır |

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
// IP itibar skoru
IReputationService rep = AtomGuardAPI.getInstance().getReputationService();
int score = rep.getScore(player.getAddress().getAddress());

// Modülü runtime'da aç/kapat
IModuleManager modules = AtomGuardAPI.getInstance().getModuleManager();
modules.setEnabled("bot-koruma", false);

// Exploit engelleme olayı
@EventHandler
public void onExploitBlocked(ExploitBlockedEvent event) {
    String module = event.getModuleName();
    Player player  = event.getPlayer();
}
```

---

## 🔧 Derleme

```bash
git clone https://github.com/ATOMGAMERAGA/AtomGuard.git
cd AtomGuard
mvn clean package -DskipTests

# Çıktılar:
#   core/target/AtomGuard-core-1.1.0.jar
#   velocity/target/AtomGuard-velocity-1.1.0.jar
```

Gereksinim: **Java 21 JDK** + **Maven 3.8+**

---

## 🏗️ Mimari

```
AtomGuard/
├── api/       → Geliştiriciler için public interface'ler
├── core/      → Paper 1.21.4 ana eklenti (44+ modül)
└── velocity/  → Velocity proxy modülü (DDoS, bot, VPN, firewall)
```

---

## 🤝 Katkıda Bulunma

Katkıda bulunmak ister misiniz? [CONTRIBUTING.md](CONTRIBUTING.md) dosyasına göz atın. Her pull request değerlendirilir.

## 📜 Lisans

Bu proje **BSD 3-Clause** lisansı ile dağıtılmaktadır. Ayrıntılar için [LICENSE](LICENSE) dosyasına bakın.

---

<p align="center">
  <strong>⚛️ AtomGuard</strong> — Sunucunuzu koruyun.<br>
  <sub>Made with ❤️ by <strong>AtomGuard Team</strong></sub>
</p>
