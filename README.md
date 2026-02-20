<p align="center">
  <img src="https://r.resimlink.com/pTtW512LDN9.png" alt="AtomGuard Logo" width="280">
</p>

<h1 align="center">⚛️ AtomGuard — Kurumsal Minecraft Sunucu Güvenliği</h1>

<p align="center">
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/actions"><img src="https://img.shields.io/github/actions/workflow/status/ATOMGAMERAGA/AtomGuard/build.yml?branch=main&style=for-the-badge&logo=github" alt="Build Status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/ATOMGAMERAGA/AtomGuard?style=for-the-badge" alt="License"></a>
  <a href="https://github.com/ATOMGAMERAGA/AtomGuard/releases"><img src="https://img.shields.io/github/v/release/ATOMGAMERAGA/AtomGuard?style=for-the-badge&color=brightgreen" alt="Release"></a>
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk" alt="Java 21">
  <img src="https://img.shields.io/badge/Paper-1.21.4-blue?style=for-the-badge" alt="Paper 1.21.4">
  <img src="https://img.shields.io/badge/Velocity-3.x-purple?style=for-the-badge" alt="Velocity">
</p>

<p align="center">
  <strong>Paper 1.21.4 + Velocity proxy için tasarlanmış, 44+ modül ile DDoS saldırıları, bot atakları, crash exploitleri ve dupe bug'larına karşı tam spektrum koruma sağlayan kurumsal güvenlik sistemi.</strong>
</p>

---

## 🚀 Neden AtomGuard?

| Özellik | AtomGuard | Diğer Pluginler |
|---|---|---|
| Velocity Proxy Koruması | ✅ Tam entegre, 12+ modül | ❌ Yok veya çok sınırlı |
| DDoS / Bot Koruması | ✅ Katmanlı, AI destekli | ⚠️ Temel düzey |
| VPN/Proxy Tespiti | ✅ 7 farklı provider zinciri | ⚠️ 1-2 API |
| IPTables Entegrasyonu | ✅ Kernel-level engelleme | ❌ Yok |
| Gerçek Zamanlı Tehdit Skoru | ✅ Çok katmanlı skor sistemi | ❌ Yok |
| Crash & Dupe Koruması | ✅ 44+ modül | ⚠️ 10-20 modül |
| Açık API | ✅ Maven artifact | ❌ Yok |

---

## 🛡️ Velocity Proxy Modülü (YENİ)

AtomGuard Velocity modülü, sunucunuza ulaşmadan önce tehditleri proxy katmanında engeller. Bağımsız çalışır, core ile Redis veya Plugin Messaging üzerinden senkronize olur.

### ⚔️ DDoS & Bağlantı Koruması

- **SmartThrottle Engine**: Normal / Dikkatli / Agresif / Lockdown modlarıyla adaptif hız sınırlama
- **SYN Flood Tespiti**: Saniyede 50'den fazla bağlantıyı otomatik engeller
- **Slowloris Tespiti**: Yavaş bağlantı saldırılarını tespit edip keser
- **Ping Flood Dedektörü**: IP başına ping sayısını takip eder
- **IP/Subnet/Global Rate Limit**: Sliding window algoritmasıyla çok katmanlı hız sınırlama

### 🤖 Bot Koruması (AtomShield™ Velocity)

- **Çok Faktörlü Tehdit Skoru**: Bağlantı hızı, handshake, brand, join pattern, kullanıcı adı, geo ve protokol analizi
- **Brand Analizi**: İzinli client'ları (Fabric, Forge, Lunar, Badlion, LabyMod…) tanır; bot/exploit client'larını engeller
- **Handshake Doğrulaması**: Geçersiz veya şüpheli handshake paketlerini filtreler
- **Join Pattern Dedektörü**: Bot sürüsü davranışlarını istatistiksel olarak tespit eder
- **CAPTCHA Sistemi**: Şüpheli oyuncuları limbo sunucusuna yönlendirir, matematik sorusu çözdürür
- **Nick Engelleme**: Regex, prefix, suffix ve karakter analizi ile bot nick'leri engeller
- **Doğrulanmış Oyuncu Cache'i**: Temiz geçmişi olan oyuncuları 48 saate kadar hızlı geçirir

### 🌐 VPN / Proxy Tespiti (7 Katmanlı)

Tehdidin tipi ve kaynağına göre otomatik provider zinciri:

| # | Provider | Açıklama |
|---|---|---|
| 1 | **Local List** | Yerel kara liste (anlık) |
| 2 | **CIDR Blocker** | IP aralığı bazlı engelleme |
| 3 | **DNSBL** | Spamhaus, DroneBL ve özel listeler |
| 4 | **IPHub** | Ticari VPN/proxy veritabanı |
| 5 | **ProxyCheck.io** | Gerçek zamanlı proxy kontrolü |
| 6 | **AbuseIPDB** | Kötüye kullanım geçmişi kontrolü |
| 7 | **IPApi** | ASN + hosting tespiti |

- **Ip2Proxy Veritabanı**: Offline yerel sorgu, API limitine takılmaz
- **ASN Engelleme**: Bilinen hosting ASN'lerini toplu engeller
- **Güven Skoru Eşiği**: Skora göre izin ver / uyar / engelle kararları

### 🌍 Coğrafi Filtreleme

- MaxMind GeoIP2 entegrasyonu ile ülke bazlı whitelist/blacklist
- Otomatik veritabanı güncellemesi (haftalık)
- Bilinmeyen ülkeler için özelleştirilebilir politika

### 🔒 Güvenlik Duvarı & Hesap Koruması

- **IP İtibar Motoru**: Her IP başarılı giriş/flood/exploit geçmişine göre puan alır
- **Otomatik Ban Motoru**: Kural bazlı otomatik kalıcı/geçici ban
- **TempBan Yöneticisi**: Süre dolunca otomatik kaldırma
- **Hesap Güvenlik Duvarı**: Mojang API doğrulaması, hesap yaşı kontrolü, cracked hesap politikası
- **Kara Liste / Beyaz Liste**: JSON tabanlı, runtime güncellenebilir

### ⚡ IPTables Entegrasyonu

- Kernel seviyesinde gerçek zamanlı IP engelleme
- iptables ve nftables desteği
- Otomatik kural temizleme (başlatma/kapatma)
- Subnet ban ile /24 blok engelleme

### 🔄 Yeniden Bağlantı & Protokol Kontrolü

- **Crash Döngüsü Tespiti**: 30 saniyede 3'ten fazla bağlantı kesintisi
- **Kısa Oturum Tespiti**: 3 saniyeden kısa oturumlar için challenge
- **Protokol Versiyonu Filtresi**: İzinli client sürümlerini kısıtlar
- **Paket Boyutu Sınırı**: Büyük / geçersiz paketleri engeller

### 💬 Chat & Exploit Koruması

- **Chat Rate Limiter**: Burst izni ile saniye başına mesaj sınırı
- **Duplicate Mesaj Tespiti**: Son N mesajı hafızada tutar
- **Pattern Analizi**: Büyük harf oranı, tekrar eden karakter, link engelleme
- **Tab Complete Flood**: Saniyede 5'ten fazla tab isteğini keser
- **Komut Flood Engeli**: Saniyede komut limiti
- **Sunucu Geçiş Abuse**: Server switch spam koruması

### 🔐 Şifre Güvenliği (AuthMe Entegrasyonu)

- **Brute Force Koruması**: 5 başarısız denemede geçici ban
- **Yaygın Şifre Kontrolü**: 10.000+ bilinen zayıf şifre listesi
- **Şifre Benzerlik Tespiti**: Aynı IP'den benzer şifre kullanım tespiti

### 📡 Ağ & Senkronizasyon

- **Redis Bridge**: Sunucular arası anlık ban/alert senkronizasyonu
- **Plugin Messaging**: Core ↔ Velocity güvenli iletişim kanalı
- **Discord Webhook**: Saldırı, bot, VPN, DDoS anlık bildirimleri
- **Saldırı Modu**: Eşik aşıldığında tüm modüller otomatik sıkılaşır

---

## 🔨 Core Modülü (Paper Plugin)

### 💥 Crash & Exploit Koruması (44+ Modül)

| Kategori | Modüller |
|---|---|
| **Packet Exploitler** | PacketExploitModule, OfflinePacketModule, NettyCrashModule, PacketDelayModule |
| **NBT Saldırıları** | NBTCrasherModule, ItemSanitizerModule, CustomPayloadModule, AdvancedPayloadModule |
| **Dünya Crasherleri** | BookCrasherModule, LecternCrasherModule, MapLabelCrasherModule, FrameCrashModule |
| **Chunk / Entity** | ChunkCrashModule, EntityInteractCrashModule, ContainerCrashModule |
| **Duplikasyon** | BundleDuplicationModule, InventoryDuplicationModule, CowDuplicationModule, MuleDuplicationModule, DuplicationFixModule |
| **Envanter** | InvalidSlotModule, BundleLockModule, CreativeItemsModule, AnvilCraftCrashModule |
| **Hareket** | MovementSecurityModule, NormalizeCoordinatesModule |
| **Komutlar** | CommandsCrashModule, ComponentCrashModule |
| **Performans** | RedstoneLimiterModule, ExplosionLimiterModule, PistonLimiterModule, FallingBlockLimiterModule |
| **Bot Koruması** | AntiBotModule, BotProtectionModule, ConnectionThrottleModule |

### 🤖 AtomShield™ (Core Bot Koruması)

- **9 Farklı Check**: Bağlantı hızı, gravity, paket timing, ping/handshake, protokol, username pattern, ilk katılım davranışı, katılım sonrası davranış
- **Heuristik Motor**: Oyuncu başına profil oluşturur, anormallik tespit eder
- **Doğrulama Sistemi**: Şüpheli oyuncuları whitelist'e almadan önce challenge uygular
- **Saldırı Modu**: TPS düştüğünde veya flood tespitinde otomatik aktif

### ⚡ Performans & Entegrasyon

- **MySQL + HikariCP**: Bağlantı havuzu ile yüksek performanslı veri depolama
- **Redis Pub/Sub**: Ağ genelinde senkronizasyon
- **Discord Webhook**: Anlık exploit ve saldırı bildirimleri
- **Web Panel**: Tarayıcı tabanlı canlı istatistik paneli
- **Async Logging**: 7 günlük rotasyon, async dosya yazımı
- **ConfigManager**: Sıcak yeniden yükleme (hot-reload) desteği

---

## 📦 Gereksinimler

| Bileşen | Versiyon | Zorunlu |
|---|---|---|
| Java | 21+ | ✅ |
| Paper / Forks | 1.21.4 | ✅ |
| PacketEvents | 2.6.0+ | ✅ (Core için) |
| Velocity | 3.x | ⚠️ (Proxy için) |
| MySQL | 8.0+ | ⚠️ (İsteğe bağlı) |
| Redis | 7.x | ⚠️ (İsteğe bağlı) |
| MaxMind Lisansı | — | ⚠️ (GeoIP için) |

---

## 🚀 Kurulum

### Paper Sunucu
1. [PacketEvents](https://modrinth.com/plugin/packetevents) pluginini `plugins/` klasörüne atın.
2. `AtomGuard-core-1.1.0.jar` dosyasını `plugins/` klasörüne atın.
3. Sunucuyu başlatın — config otomatik oluşturulur.
4. `plugins/AtomGuard/config.yml` dosyasını yapılandırın.

### Velocity Proxy
1. `AtomGuard-velocity-1.1.0.jar` dosyasını Velocity `plugins/` klasörüne atın.
2. Proxy'yi başlatın — config otomatik oluşturulur.
3. `plugins/atomguard-velocity/config.yml` dosyasını yapılandırın.
4. Redis kullanıyorsanız her iki tarafta da `redis` bölümünü etkinleştirin.

---

## 💻 Komutlar & İzinler

| Komut | Açıklama | İzin |
|---|---|---|
| `/atomguard` | Yardım menüsü | `atomguard.admin` |
| `/atomguard reload` | Config yenileme | `atomguard.reload` |
| `/atomguard status` | Modül durumları | `atomguard.admin` |
| `/atomguard stats` | İstatistikler | `atomguard.admin` |
| `/panic` | Acil durum — tüm modüller sıkılaşır | `atomguard.panic` |

| İzin | Açıklama |
|---|---|
| `atomguard.bypass` | Tüm korumaları atlar |
| `atomguard.notify` | Exploit bildirimlerini görür |

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
// IP itibarını kontrol et
IReputationService rep = AtomGuardAPI.getInstance().getReputationService();
int score = rep.getScore(player.getAddress().getAddress());

// Modülü runtime'da aç/kapat
IModuleManager modules = AtomGuardAPI.getInstance().getModuleManager();
modules.setEnabled("bot-koruma", false);

// Exploit engellendiğinde dinle
@EventHandler
public void onExploitBlocked(ExploitBlockedEvent event) {
    String moduleName = event.getModuleName();
    Player player = event.getPlayer();
}
```

---

## 🔨 Derleme

```bash
git clone https://github.com/ATOMGAMERAGA/AtomGuard.git
cd AtomGuard
mvn clean package -DskipTests
# Core çıktı: core/target/AtomGuard-1.1.0.jar
# Velocity çıktı: velocity/target/AtomGuard-velocity-1.1.0.jar
```

---

## 🏗️ Mimari

```
AtomGuard/
├── api/       → Geliştiriciler için kararlı public interface'ler
├── core/      → Paper 1.21.4 ana plugin (44+ modül, bot koruma, exploit fix)
└── velocity/  → Velocity proxy modülü (DDoS, bot, VPN, geo, IPTables)
```

---

## 🤝 Katkıda Bulunma

[CONTRIBUTING.md](CONTRIBUTING.md) dosyasını okuyun. Her PR memnuniyetle karşılanır.

## 📜 Lisans

**BSD 3-Clause** — Ayrıntılar için [LICENSE](LICENSE) dosyasına bakın.

---

<div align="center">
  <strong>⚛️ AtomGuard</strong> — Sunucunuzu koruma altına alın.<br>
  Made with ❤️ by <strong>AtomGuard Team</strong>
</div>
