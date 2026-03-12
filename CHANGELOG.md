# Changelog

Tüm önemli değişiklikler bu dosyada belgelenir.
Bu proje [Semantic Versioning](https://semver.org/lang/tr/) kullanır.

## [2.0.0] - 2026-03-12

### ✨ Yeni Özellikler

- **Genişletilmiş Public API**: `AtomGuardAPI` artık `ITrustService`, `IForensicsService`, `IConnectionPipeline` arayüzlerini sunuyor; harici eklentiler güven skoru, adli kayıt ve bağlantı hattına erişebilir
- **Forensics Sistemi**: `ForensicsManager` / `PacketRecorder` / `RecordingSession` — paket kayıt oturumları, saldırı anı anlık görüntüleri disk üzerine kaydediliyor
- **Traffic Intelligence Engine**: `AdaptiveThresholdManager`, `EWMADetector`, `IsolationForestDetector` — adaptif eşik ve anomali tespiti
- **Notification Manager**: `NotificationManager` — çok sağlayıcılı bildirim yönlendirme (Discord, konsol, custom)
- **Trust Score Manager**: `TrustScoreManager` (`ITrustService`) — oyuncu başına güven puanı, tier sistemi, JSON kalıcılığı
- **Executor Manager**: `ExecutorManager` — paylaşımlı thread pool yönetimi
- **Web Panel Yenilendi**: JWT kimlik doğrulama (`JWTAuthProvider`, `SessionManager`), API handler'ları, middleware katmanı
- **Config Migration (Core)**: `ConfigMigrationManager` — `1.x → 2.0.0` otomatik yapılandırma göçü
- **Config Migration (Velocity)**: `VelocityConfigMigrationManager` — Velocity yapılandırma göçü
- **Velocity Bedrock Desteği**: `BedrockSupportModule` — Bedrock oyuncu kimlik tespiti ve bot ayırt etme
- **Yeni API Olayları**: `NotificationSentEvent`, `PostVerificationEvent`, `PreConnectionCheckEvent`, `TrustScoreChangeEvent`
- **IForensicsService / ITrustService API arayüzleri** api modülüne eklendi
- **IConnectionPipeline API arayüzü** api modülüne eklendi

### 🔧 İyileştirmeler

- **HttpClientUtil**: `HttpURLConnection` tabanlı yardımcı sınıf; `IPReputationManager`, `DiscordWebhookManager`, `ExternalListFetcher`, `GeoIPUpdater` tümü HttpClientUtil'e geçirildi
- **Caffeine Önbelleği**: `CooldownManager`, `PacketListener` bypass cache, `IPReputationManager` API cache, `WebPanel` oran sınırlama — tüm el ile TTL yönetimi Caffeine ile değiştirildi
- **Bypass Cache Basitleştirildi**: `Cache<UUID, Boolean>` Caffeine 10 dk TTL; Netty-safe, kilitsiz
- **Javadoc**: `AtomGuard`, `AbstractModule`, `PacketListener`, `ModuleManager`, `TrustScoreManager`, `ForensicsManager`, `NotificationManager` — sınıf düzeyi İngilizce Javadoc eklendi
- **Hardcoded URL'ler kaldırıldı**: Velocity VPN sağlayıcıları (`ProxyCheckProvider`, `IPApiProvider`, `AbuseIPDBProvider`, `IPHubProvider`) ve `AccountFirewallModule` Ashcon API URL'leri artık `harici-servisler` config bölümünden okunuyor
- **Statik Analiz Araçları**: SpotBugs, PMD, Checkstyle, OWASP Dependency Check eklentileri parent POM'a eklendi (`pluginManagement`)
- **Build Süreci**: GitHub Actions workflow güncellendi; Checkstyle doğrulaması CI'ya entegre edildi

### 🧪 Testler

- **92 core testi** (TrustScoreManagerTest 21, HeuristicEngineTest 10, ConfigMigrationManagerTest 11, SessionManagerTest 10, JWTAuthProviderTest 10, vb.)
- **51 velocity testi** (BedrockSupportModuleTest 21, VPNResultCacheTest 12, ConnectionPipelineTest 12, vb.)
- **Yeni testler (bu sürüm)**: `ThreatScoreCalculatorTest`, `PacketTimingCheckTest`, `PingHandshakeCheckTest`, `PostJoinBehaviorCheckTest`, `ProtocolCheckTest`, `UsernamePatternCheckTest`, `RateLimiterTest` (velocity), `WhitelistManagerTest` (velocity), `AttackLevelManagerTest` (velocity)
- **Toplam: 266 test**, 0 hata

### 🐛 Hata Düzeltmeleri

- **@Override eksiklikleri giderildi**: Velocity `LatencyCheckModule`, `CountryFilterModule`, `ReconnectControlModule` — `onEnable()`/`onDisable()` üzerindeki `@Override` etiketleri zaten mevcuttu, doğrulandı

## [1.2.9] - 2026-03-06

### 🐛 Hata Düzeltmeleri

- **FrameCrashModule — EntityRemoveEvent chunk yükleme hatası düzeltildi**: `handleEntityRemoval()` içinde `entity.getLocation().getChunk()` çağrısı, chunk unload sırasında `IllegalStateException: Cannot update ticket level while unloading chunks` hatasına yol açıyordu. `getChunk()` yerine `Location` koordinatlarından bit-shift ile chunk koordinatı hesaplayan `ChunkKey.fromLocation()` metoduna geçildi.

## [1.2.8] - 2026-03-05

### 🐛 Hata Düzeltmeleri

- **ThreatScoreChangedEvent async hatası düzeltildi**: `ThreatScoreCalculator`, `HeuristicEngine` (Rotation Spike ve Low Click Variance) içindeki `ThreatScoreChangedEvent` çağrıları main thread yerine `runTaskAsynchronously` ile çağrılacak şekilde düzeltildi (Paper 1.21.4 `IllegalStateException` hatası giderildi)

## [1.2.7] - 2026-03-04

### ✨ Yeni Özellikler

- 

### 🔧 İyileştirmeler

- 

### 🐛 Hata Düzeltmeleri

- 

## [1.2.6] - 2026-03-04

### 🔧 İyileştirmeler

- **VelocityStorageProvider — Kod Tekrarı Giderildi**: `saveBehaviorProfile()` ve `saveBehaviorProfileSync()` içindeki JSON inşa mantığı `buildProfileJson()` özel yardımcı metoduna taşındı; bakımı kolaylaştırıldı.
- **AttackSnapshot — Koleksiyon Optimizasyonu**: Saldırı sırasında yoğun yazma yapılan `timeline` ve `triggeredModules` alanları `CopyOnWriteArrayList`'ten `Collections.synchronizedList(new ArrayList<>())` türüne değiştirildi; GC baskısı azaltıldı.
- **ConfigManager — Gereksiz Değişken Kaldırıldı**: `loadMessages()` içindeki anlamsız `final String finalFileName = fileName` ataması temizlendi; `fileName` doğrudan kullanılıyor.

### 🐛 Hata Düzeltmeleri

- **VelocityStatisticsManager / TempBanManager — TOCTOU Yarış Koşulu**: `save()` metodlarındaki `Files.exists()` ön kontrolü kaldırıldı; `Files.createDirectories()` doğrudan çağrılıyor (idempotent). Çok iş parçacıklı ortamda dizin oluşturma yarış koşulu giderildi.
- **WebPanel — Düz Metin Şifre Loglama**: Varsayılan web panel şifresi artık loglara açık metin olarak yazılmıyor; yalnızca ilk 3 karakter ve maskelenmiş kısım gösteriliyor.
- **AtomGuard — `onDisable()` Kapatma Sırası**: Modüller ve yöneticiler doğru sırayla kapatılıyor (modüller → yöneticiler → depolama → log); `discordWebhookManager.stop()`, `PacketEvents listener unregister`, outgoing/incoming plugin channel unregister eklendi.
- **RedisManager — Daemon Thread ve PubSub Takılması**: PubSub thread'i daemon olarak işaretlendi; `stop()` artık `activePubSub.unsubscribe()` çağırarak JedisPubSub'ın `subscribe()` blokajını serbest bırakıyor.
- **AttackModeManager — Sayaç Sıfırlama Thread Safety**: `lastReset` alanı `volatile long`'dan `AtomicLong`'a yükseltildi; `recordConnection()` CAS ile tek thread'in sıfırlama yapmasını garantiliyor.
- **PanicCommand / BukkitListener — Null Address NPE**: Oyuncu IP adresi null kontrolü eklendi; bağlantı sırasında adresi henüz atanmamış oyuncularda `NullPointerException` önlendi.
- **ConnectionPipeline — ArrayList Yerine CopyOnWriteArrayList**: Çok iş parçacıklı ortamda eş zamanlı okuma-yazma güvenliği sağlandı.
- **SmartLagModule — Watchdog Görev Sızıntısı**: `onEnable()` içinde zamanlayıcı görev ID'si kaydediliyor; `onDisable()` bu ID ile `cancelTask()` çağırarak kaynakları doğru serbest bırakıyor.
- **CorePasswordCheckModule — DoS Güvenlik Açığı**: 128 karakteri aşan şifreler için SHA-256 hesaplaması artık yapılmıyor; uzun girdilerle hash işlemi kötüye kullanımı engellendi.
- **VelocityStorageProvider — Bağlantı Havuzu Boyutu**: SQLite için `MaximumPoolSize=1` (WAL modu zorunluluğu), MySQL için `MaximumPoolSize=5` olarak düzeltildi; önceki yanlış yapılandırma veritabanı hatalarına yol açabiliyordu.
- **ConnectionListener — Çevrimdışı Mod UUID Null**: Velocity `PreLoginEvent` çevrimdışı modda UUID'yi null döndürebiliyor; `UUID.nameUUIDFromBytes` ile güvenli yedek atama yapıldı.
- **ConfigManager — InputStream Kaynağı Sızıntısı**: `loadConfig()` ve `loadMessages()` içindeki `InputStream` / `InputStreamReader` nesneleri try-with-resources ile sarıldı; kaynaklar her durumda kapatılıyor.
- **IPReputationManager — InputStreamReader Kaynağı Sızıntısı**: İki ayrı `InputStreamReader` kullanımı try-with-resources ile güvence altına alındı.
- **AtomGuard — BotProtectionModule Kullanımdan Kaldırma**: `@Deprecated` `BotProtectionModule` `registerModules()` içinden kaldırıldı.

## [1.2.5] - 2026-03-01

### 🔧 İyileştirmeler

- **AbstractModule — Asenkron Ağır İşlemler**: `blockExploit()` içindeki olay fırlatma, heuristik güncelleme, güven skoru kaydı ve adli analiz kayıt adımları `runTaskAsynchronously` ile ana thread'den kaldırıldı; yüksek trafik altında gecikme azaltıldı.
- **AtomGuard — Periyodik Temizlik**: Her 5 dakikada bir tüm modüllerin `cleanup()` ve `HeuristicEngine.cleanupOfflinePlayers()` çağrıları yapılıyor; bellek sızıntısı önlendi.
- **BukkitListener — Bypass Önbelleği**: `PlayerJoinEvent`'te `checkAndCacheBypass()`, `PlayerQuitEvent`'te `removeBypassCache()` çağrıları eklendi; Netty thread'inden permission API çağrısı tamamen kaldırıldı.

### 🐛 Hata Düzeltmeleri

- **AtomGuard — Başlatma Sırası Kritik Hata**: `PacketListener`, `registerModules()` ve `enableAllModules()` çağrılarından **önce** oluşturulmaya başlandı. Eski sıralamada modüller `registerReceiveHandler()` çağırırken `packetListener` null olduğundan sunucu açılışta `NullPointerException` ile çöküyordu.
- **PacketListener — Netty Thread'den hasPermission() Çağrısı**: `hasBypass()` artık Bukkit permission API'sini çağırmıyor; tamamen önbellek tabanlı. Bypass olmayan oyuncular da `-1L` sentinel değeriyle önbelleğe alınıyor. `synchronized` liste `CopyOnWriteArrayList` ile değiştirildi. `getPlayer()` yalnızca rotasyon/animasyon paketleri için çağrılıyor.
- **7 Modül — PacketListenerAbstract Bağımsız Kayıt**: `PacketExploitModule`, `PacketDelayModule`, `OfflinePacketModule`, `BookCrasherModule`, `BundleDuplicationModule`, `InvalidSlotModule`, `CustomPayloadModule` kendi `PacketListenerAbstract` örneklerini kaydetmek yerine `registerReceiveHandler()` korumalı metodunu kullanacak şekilde yeniden yazıldı; handler takibi ve merkezi temizlik düzgün çalışıyor.
- **26 Modül — Çift Bukkit Olay Kaydı**: `AbstractModule.onEnable()` zaten `registerEvents()` çağırdığı halde bu modüller `onEnable()` override'larında ikinci kez manuel kayıt yapıyordu; tüm çift kayıtlar kaldırıldı.
- **ModuleManager — Başlatma Hatasında Rollback Eksikliği**: `enableAllModules()` ve `enableModule()` içindeki `catch` blokları artık `module.onDisable()` çağırarak yarım kalan modülü geri alıyor; tutarsız aktif-listesi durumu önlendi.
- **OfflinePacketModule — Güvenilmez Online Kontrolü**: `Bukkit.getPlayer(uuid)` yerine `player.isOnline()` kullanılıyor; null dereference riski ortadan kalktı.
- **NBTCrasherModule / AdvancedPayloadModule — Yanlış Handler Kaydı**: `plugin.getPacketListener().registerReceiveHandler()` yerine `protected registerReceiveHandler()` kullanılıyor; handler listesi tutarsızlığı ve temizlik hataları giderildi.
- **NettyCrashModule — Thread Safety**: `pipeline.addAfter()` ve `pipeline.remove()` çağrıları `channel.eventLoop().execute()` içine taşındı; Netty pipeline değişiklikleri artık kanal kendi event loop thread'inde yapılıyor.

## [1.2.4] - 2026-02-27

### 🐛 Hata Düzeltmeleri

- **AntiBotModule — NPE Düzeltme**: `getOrCreateProfile()` ve `handleIncomingPacket()` içinde `user.getAddress()` null döndürebiliyordu; pre-login paket işleyicilerinde sunucu çöküyordu. Null kontrolü eklendi, null durumda `"0.0.0.0"` kullanılıyor.
- **PlayerProfile — Thread Safety**: PacketEvents thread'leriyle paylaşılan 8 alan (`sentClientSettings`, `sentPositionPacket`, `interactedWithInventory`, `interactedWithWorld`, `lastSeen`, `cachedFirstJoinScore`, `currentThreatScore`, `successfulSessionCount`) `volatile` yapıldı. `maxThreatScore` check-then-set yarış koşulu `AtomicInteger.updateAndGet()` ile çözüldü.
- **AttackModeManager — Race Condition**: `attackModeStartTime` alanı birden fazla thread'den erişilirken `volatile` değildi; attack mode süre hesaplamaları yanlış olabiliyordu. `volatile` yapıldı.
- **AntiBotModule — Hatalı Offline Kontrol**: `cleanupProfiles()` içinde `Bukkit.getOfflinePlayer(uuid).isOnline()` güvenilmezdi (disk erişimi yapar, pahalı). `Bukkit.getPlayer(uuid) == null` ile değiştirildi.

### 🔧 İyileştirmeler

- `CHANGELOG.md` içindeki yinelenen boş `[1.2.3]` bölümleri temizlendi.

## [1.2.3] - 2026-02-27

### 🐛 Hata Düzeltmeleri

- **BotProtectionModule — Yanlış "Timed Out" Atması**: `dogrulama.aktif` varsayılanı `true`'dan `false`'a değiştirildi. Saldırı modunda `bot-korumasi` modülünün otomatik devreye girmesi kaldırıldı (artık `otomatik-moduller` listesinde yok). Hareket tabanlı doğrulama artık sohbet ve komut kullanımını da doğrulama olarak kabul ediyor (`PlayerCommandPreprocessEvent` ve `AsyncPlayerChatEvent` eklendi).
- **ActionExecutor — KEEP_ALIVE Race Condition**: `executePeriodic`'te kara listeye alma sırası düzeltildi. Artık önce oyuncu atılır (`player.kick()`), ardından 1 tick gecikmeyle IP kara listeye eklenir. Önceki sıralamada (kara liste → kick) KEEP_ALIVE yanıtları iptal edildiğinden sunucu "Timed Out" mesajı gösteriyordu.

### 🔧 İyileştirmeler

- `moduller.bot-korumasi` config bölümü eklendi (varsayılan devre dışı, tam dokümantasyonlu)
- `attack-mode.aksiyonlar.otomatik-moduller`'den `bot-korumasi` kaldırıldı; AtomShield protokol koruması `bot-koruma` (AntiBotModule) üzerinden zaten aktif

## [1.2.0] - 2026-02-24

### ✨ Yeni Özellikler

- **Tehdit İstihbarat Motoru** (`com.atomguard.intelligence`): 168 saatlik (24×7) EMA tabanlı trafik profili. Z-Score anomali tespiti (ELEVATED/HIGH/CRITICAL). 3 ardışık dakika gereksinimi ile yanlış pozitif koruması. Kritik anomalide otomatik saldırı modu aktivasyonu. `/ag intel <status|reset>` komutu.
- **Oyuncu Güven Skoru** (`com.atomguard.trust`): 0-100 arası puan, 4 kademe (Yeni/Düzenli/Güvenilir/Deneyimli). EMA formülü ile oynama süresi, temiz seans, ihlal geçmişi ağırlıklandırılır. TRUSTED+ oyuncular saldırı modunu, VETERAN+ bot/VPN kontrollerini atlar. Gson tabanlı `trust-scores.json` kalıcılığı. `/ag trust <info|set|reset|top>` komutu.
- **Adli Analiz & Saldırı Tekrarı** (`com.atomguard.forensics`): Saldırı anlık görüntüsü (UUID, zaman çizelgesi, peak rate, engellenen IP/modül istatistikleri). 4 önem seviyesi (LOW/MEDIUM/HIGH/CRITICAL). `forensics/attack-<uuid>.json` otomatik export. `AttackSnapshotCompleteEvent` API eventi. `/ag replay <list|latest|<id>|export>` komutu.
- **Config Migrasyon Sistemi** (`com.atomguard.migration`): Semantik versiyonlama ile zincirleme migrasyon. Her adım öncesi otomatik yedek (`config.yml.backup-<version>-<ts>`). 1.0.0→1.1.0→1.1.1→1.2.0 migrasyon zinciri.
- **Bal Kupu (Honeypot) Modülü** (`com.atomguard.module.honeypot`): Sahte TCP Minecraft sunucusu (SLP protokolü). Bot tarayıcılarını otomatik kara listeye ekler. `HoneypotTrapEvent` API eventi. `/ag honeypot <status|stats>` komutu.

### 🔌 API Güncellemeleri

- Yeni API eventi: `HoneypotTrapEvent`, `IntelligenceAlertEvent`, `AttackSnapshotCompleteEvent`
- `AtomGuardAPI`: `getTrustScoreManager()`, `getForensicsManager()`, `getIntelligenceEngine()` getter'ları

### 🔧 İyileştirmeler

- `DiscordWebhookManager`: `notifyIntelligenceAlert()` ve `notifyForensicsReport()` metodları eklendi
- `AbstractModule.blockExploit()`: Trust Score ihlal kaydı ve Forensics engel kaydı entegre edildi
- `AttackModeManager`: Forensics ve Intelligence Engine hook'ları eklendi
- `BukkitListener`: Trust Score ve Intelligence Engine join/quit hook'ları eklendi
- Tüm yeni sistemler için `config.yml` ve `messages_tr.yml` bölümleri eklendi

## [1.1.1] - 2026-02-23

### 🔒 Güvenlik Düzeltmeleri

- **WebPanel (CSRF)**: `origin.contains("localhost")` kontrolü `evil-localhost.com` gibi domain'lerle bypass edilebiliyordu. Tam eşleşme tabanlı `isOriginAllowed()` ve `isRefererAllowed()` metodları eklendi.
- **WebPanel (Brute-Force)**: `loginAttempts` IP'yi kalıcı olarak bloke ediyordu. 30 dakika sonra otomatik sıfırlama mekanizması eklendi.
- **WebPanel (Timing Attack)**: Basic Auth ve Login endpoint'inde `String.equals()` yerine `MessageDigest.isEqual()` tabanlı constant-time karşılaştırma kullanıldı.
- **AttackModeManager**: `lastReset` alanı `volatile` olarak işaretlendi (multi-thread visibility).

### 🐛 Hata Düzeltmeleri

- **Attack Mode asla kapanmıyordu**: `AttackModeManager.update()` hiç çağrılmıyordu. `AtomGuard.onEnable()`'a her saniye çalışan periyodik async Bukkit task eklendi.
- **Discord Webhook batch gönderimi çalışmıyordu**: `DiscordWebhookManager.start()` hiç çağrılmıyordu. `AtomGuard.onEnable()`'a eklendi.
- **StatisticsManager — veri kaybı (race condition)**: `volatile long totalBlockedAllTime++` ve `ModuleStats.total++` atomik değildi. Her ikisi de `AtomicLong`'a dönüştürüldü.
- **AttackModeManager.verifiedIps memory leak**: Sınırsız büyüyen `ConcurrentHashMap`'e 50.000 üst sınır ve yaklaşık LRU temizleme eklendi.
- **LogManager I/O darboğazı**: Her log entry'sinde `flush()` çağrılıyordu. Her 50 entry'de veya 5 saniyede bir toplu flush (batch flush) uygulandı.

### 🏗️ Mimari & Kod Kalitesi

- **BuildInfo.java**: `NAME = "Atom Guard"` → `"AtomGuard"` (marka tutarlılığı). `VERSION_MINOR` ve `VERSION_PATCH` doğru değerlere güncellendi.
- **VelocityBuildInfo.java**: Hard-coded `VERSION = "1.0.0"` → `"1.1.1"`. Banner Türkçe metin → İngilizce (`"Enterprise Proxy Security System"`). Dinamik genişlik formatlaması eklendi.
- **ConfigManager**: `checkConfigVersion()` içindeki `currentVersion = "1.0.0"` → `"1.1.1"`.
- **AtomGuardCommand.java**: Stale `v1.0.0` ve `v4.0.0` referansları temizlendi.
- **plugin.yml**: Komut açıklamaları Türkçe'den İngilizce'ye çevrildi (Bukkit API convention); izin açıklamaları düzeltildi.
- **release.yml**: `"AtomGuard Team Team"` typo düzeltildi. `api/target/AtomGuard-api-*.jar` release asset'lerine eklendi. Release adı `"Atom Guard"` → `"AtomGuard"` düzeltildi.

### 📦 Versiyon Güncellemeleri

- Tüm `pom.xml` dosyalarında versiyon `1.1.0` → `1.1.1`
- `velocity-plugin.json` versiyon `1.1.0` → `1.1.1`
- `VelocityBuildInfo.java` versiyon `1.0.0` → `1.1.1`

---

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
