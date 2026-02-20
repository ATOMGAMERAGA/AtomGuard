# Changelog

Tüm önemli değişiklikler bu dosyada belgelenir.
Bu proje [Semantic Versioning](https://semver.org/lang/tr/) kullanır.

## [1.1.0] - 2026-02-20

### 🔥 DDoS Koruma Modülü — Tam Yeniden Yazım (Velocity)

Velocity proxy DDoS koruma motoru sıfırdan yeniden yazıldı. 16 alt sistem, bellek sızıntısı olmayan Caffeine önbellekleri ve 5 kademeli saldırı yönetimi ile.

#### Yeni Alt Sistemler

- **`AttackLevelManager`**: 5 kademeli saldırı seviyesi (NONE → ELEVATED → HIGH → CRITICAL → LOCKDOWN), hysteresis ile ani geçişler engellendi
- **`SubnetAnalyzer`**: /24 ve /16 subnet bazlı koordineli botnet tespiti, Caffeine önbelleği
- **`TrafficAnomalyDetector`**: Z-skoru anomali tespiti, yavaş rampa ve nabız saldırısı dedektörü
- **`ConnectionFingerprinter`**: Bağlantı parmak izi (`protokol|hostname_pattern|timing_class`) ile bot ordusu tespiti
- **`EnhancedSlowlorisDetector`**: IP başına bekleyen bağlantı izleme, sistem genelinde oran alarmı
- **`IPReputationTracker`**: DDoS'a özgü itibar skoru (0–100), otomatik 1h/24h ban
- **`AttackSessionRecorder`**: Tam saldırı oturumu kaydı — tepe CPS, sürü IP'leri, JSON çıktısı
- **`AttackClassifier`**: 7 saldırı tipi sınıflandırması (VOLUMETRIC, SLOWLORIS, APPLICATION_LAYER…)
- **`VerifiedPlayerShield`**: CRITICAL/LOCKDOWN seviyesinde doğrulanmış oyunculara garantili slot
- **`DDoSMetricsCollector`**: Gerçek zamanlı metrikler — CPS ortalamaları, engelleme oranı, bant genişliği tahmini
- **`DDoSCheck`**: Modüler kontrol pipeline arayüzü, kısa devre desteği

#### Düzeltilen Hatalar

- **`isVerified` bug** (`pipeline/DDoSCheck.java`): `ddos.checkConnection(ip, false)` her zaman `false` gönderiyordu; artık `antiBot.isVerified(ip)` kullanılıyor
- **`SmartThrottleEngine` bellek sızıntısı**: `connectionCounts` `ConcurrentHashMap` → Caffeine önbelleği (5dk TTL)
- **`GeoBlocker` reflection**: `DatabaseReader` artık doğrudan MaxMind API ile kullanılıyor
- **`SynFloodDetector` ani de-escalation**: 10 saniye tutarlı düşük CPS gerekliliği ile hysteresis eklendi
- **`AttackSnapshot` kullanılmıyordu**: `AttackSessionRecorder` periyodik snapshot alıyor

#### Güncellenen Bileşenler

| Dosya | Değişiklik |
|---|---|
| `DDoSProtectionModule.java` | Tamamen yeniden yazıldı — 16 alt sistem entegrasyonu |
| `RateLimiter.java` | Caffeine önbelleği ile bellek sızıntısı giderildi |
| `ConnectionThrottler.java` | Caffeine 70s TTL, sınır güncelleme API'si |
| `SmartThrottleEngine.java` | `AttackLevelManager` entegrasyonu, Caffeine connectionCounts |
| `SynFloodDetector.java` | Anomali dedektörü, oturum kaydedici, sınıflandırıcı bağlantıları |
| `PingFloodDetector.java` | MOTD önbelleği Caffeine'e taşındı |
| `NullPingDetector.java` | `invalidCounts` ve `blockedIPs` Caffeine'e taşındı |
| `GeoBlocker.java` | Reflection kaldırıldı, doğrudan `DatabaseReader` API |
| `pipeline/DDoSCheck.java` | `isVerified` bug düzeltmesi |
| `config.yml` (velocity) | `moduller.ddos-koruma` altına 40+ yeni ayar |
| `messages_tr.yml` | Yeni kick mesajları: `kick.ddos-seviye`, `kick.ddos-subnet` vb. |

---

### 🛡️ Anti-False-Positive Overhaul — Velocity Proxy Modülü

Normal oyuncuların hatalı olarak engellenmesine yol açan köklü sorunlar giderildi.

#### Düzeltilen Sorunlar

- **VPN/Proxy False Positive**: Normal oyuncular (özellikle Türk ISP kullanıcıları) artık "VPN tespit edildi" mesajıyla atılmıyor
- **"Şüpheli IP" False Positive**: Birkaç küçük ihlalden sonra otomatik ban tetiklenmiyordu
- **"Bot saldırısı" False Positive**: 3-4 reconnect yapan normal oyuncular artık bot olarak algılanmıyor

#### Yeni Özellikler

- **Çoklu sağlayıcı konsensüs sistemi** (`VPNProviderChain`): Tek sağlayıcı pozitif oy verdiğinde artık engellenmiyor; en az 2 sağlayıcı konsensüsü gerekiyor
- **Residential bypass**: ip-api'den gelen `hosting=true` (proxy=false) sinyali tek başına engelleme yapmıyor
- **`VPNCheckResult`**: Yeni model — isVPN, confidenceScore, detectedBy, method alanları
- **`IPApiProvider.checkDetailed()`**: proxy vs. hosting ayrımı; `isVPN()` artık yalnızca `proxy=true` için true döner
- **Verified clean IP cache** (`VPNDetectionModule`): Temiz geçmiş IP'ler tekrar VPN kontrolüne girmiyor
- **Per-analysis score reset** (`ThreatScore.resetForNewAnalysis()`): Skor birikme sorunu giderildi
- **Single-category penalty reduction**: Tek kategoride yüksek skor artık engelleyemiyor (flagCount <= 1 → %60 indirim)
- **`ThreatScore.isHighRisk/isMediumRisk()`**: Artık `flagCount >= 2` şartı da aranıyor
- **Verified player cache** (`BotDetectionEngine`): Başarılı login yapan oyuncular bot analizini atlıyor
- **Contextual scoring** (`IPReputationEngine.addContextualScore()`): İhlal türüne göre farklı çarpanlar (bot-tespiti=0.7×, crash-girisimi=2.0×)
- **Grace period**: İlk 3 ihlalde otomatik ban tetiklenmiyor
- **Başarılı login ödülü**: `rewardSuccessfulLogin()` — −15 puan + verified işareti
- **Hızlandırılmış decay**: 5 dakikada bir 10 puan azalma (önceden 10dk'da 5)

#### Değiştirilen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `VPNProviderChain.java` | Tamamen yeniden yazıldı — konsensüs sistemi |
| `VPNCheckResult.java` | Yeni sınıf |
| `IPApiProvider.java` | Proxy/hosting ayrımı, `checkDetailed()` |
| `VPNDetectionModule.java` | Verified cache, `markAsVerifiedClean()` |
| `ThreatScore.java` | `resetForNewAnalysis()`, `flagCount`, `applyTimeDecay()` |
| `BotDetectionEngine.java` | Verified player cache, per-analysis reset |
| `ConnectionAnalyzer.java` | Min threshold=8, grace period, smoothed rate |
| `JoinPatternDetector.java` | Min thresholds, quit decay, yumuşatılmış skorlar |
| `IPReputationEngine.java` | Contextual scoring, grace period, min threshold=150 |
| `AutoBanEngine.java` | `addContextualScore()` kullanımı |
| `FirewallModule.java` | 5dk maintenance, 10pt decay |
| `VelocityAntiBotModule.java` | Yeni varsayılan eşikler, verified proxy metodları |
| `ConnectionListener.java` | Verified bypass entegrasyonu, başarılı login handler |
| `config.yml` (velocity) | Yeni parametreler eklendi |

#### Konfigürasyon Değişiklikleri

`bot-koruma` altına eklendi:
- `analiz-penceresi: 15`
- `supheli-esik: 8`
- `yuksek-risk-esik: 75`
- `orta-risk-esik: 45`

`vpn-proxy-engelleme` altına eklendi:
- `konsensus-esigi: 2`
- `guven-skoru-esigi: 60`
- `saldiri-modu-esigi: 40`
- `residential-bypass: true`

`guvenlik-duvari` güncellendi:
- `oto-yasak-esik: 150` (önceden 100)
- `decay-dakika: 5` (önceden 10)
- `decay-miktar: 10` (önceden 5)
- `grace-violations: 3`
- `basarili-login-bonus: 15`

---

## [1.0.0] - 2026-02-17

### 🎉 İlk Sürüm — Atom Guard

AtomSMPFixer projesinin Atom Guard olarak yeniden doğuşu.

#### Özellikler
- 44+ güvenlik modülü (crasher, dupe, packet exploit, NBT koruması)
- AtomShield™ bot koruma sistemi (hibrit analiz, IP reputation, ASN engelleme)
- Heuristik analiz motoru (lag tespiti, davranış analizi)
- Redis Pub/Sub ile sunucular arası senkronizasyon
- Velocity proxy desteği
- MySQL + HikariCP veri depolama
- WebPanel arayüzü
- Discord webhook entegrasyonu
- Çoklu dil desteği (TR/EN)
- Modüler mimari ve API desteği
- Semantic Versioning sistemi

#### Teknik
- Java 21, Paper 1.21.4, PacketEvents 2.6.0
- Maven multi-module yapısı (api, core, velocity)
