# Changelog

Tüm önemli değişiklikler bu dosyada belgelenir.
Bu proje [Semantic Versioning](https://semver.org/lang/tr/) kullanır.

## [2.1.2] - 2026-03-26

### ✨ Yeni Özellikler

- 

### 🔧 İyileştirmeler

- 

### 🐛 Hata Düzeltmeleri

- 

## [2.1.1] - 2026-03-25

### 🐛 Bug Fixes

- **Core — MiniMessage placeholders broken across all message files**: All 196 dynamic placeholders in the four message files (`messages_tr.yml`, `messages_en.yml`, `velocity/messages_tr.yml`, `velocity/messages_en.yml`) were using curly-brace format (`{sayi}`, `{ip}`, `{oyuncu}`, etc.), which MiniMessage never resolves — it only processes `<tag>` syntax. Every command output, kick message, and admin notification that contained a dynamic value (counts, IPs, player names, durations) was therefore rendered as a raw literal string. All 196 occurrences converted to angle-bracket format (`<sayi>`, `<ip>`, `<oyuncu>`, etc.).
- **Velocity — ExternalListManager: CIDR subnet blocking silently ignored**: `isBlocked()` contained an empty `// TODO: Proper CIDR check` branch — any entry with a `/` in it (e.g. `192.168.0.0/16`) was skipped without matching, making all subnet-based external blacklist rules completely inoperative. Replaced with a correct byte-level CIDR matching implementation using `InetAddress` and bit-mask arithmetic; IPv6 is supported by address-length parity check.
- **Core — PlaceholderAPI `%atomguard_player_reputation%` always returned "100"**: The expansion returned a hardcoded `"100"` string regardless of the player's actual trust score. Wired to `TrustScoreManager.getScore(UUID)` with a `null`-safe fallback. Added two additional placeholders: `%atomguard_trust_tier%` (returns the player's `TrustTier` enum name) and `%atomguard_attack_mode_status%` (returns `"true"` / `"false"`).
- **Core — BotProtectionModule: NullPointerException on `getAddress()`**: Nine call sites in `BotProtectionModule` dereferenced `user.getAddress().getAddress().getHostAddress()` without a null guard, and two more used `player.getAddress()` the same way. Proxy connections or mid-handshake disconnects can leave the address as `null`, causing an NPE that propagates into the packet handler thread. Null checks added at all eleven sites; affected handlers return early when the address is unavailable.
- **Core — RateLimitMiddleware: NullPointerException on `getRemoteAddress()`**: `checkRateLimit()` unconditionally called `exchange.getRemoteAddress().getAddress().getHostAddress()`. A broken or prematurely closed HTTP connection yields a `null` remote address, causing an NPE. A `null` check is now performed first; if the address is unavailable the request is allowed through.
- **Velocity — PrometheusExporter: thread leak on plugin reload**: The HTTP server executor was created with `Executors.newSingleThreadExecutor()` as an anonymous local — its reference was never stored, so `stop()` called `httpServer.stop(0)` but never shut down the executor. On each plugin reload a new thread was leaked. The executor is now stored in a field (`httpExecutor`), created as a named daemon thread, and `shutdownNow()` + `awaitTermination(3 s)` are called in `stop()`.
- **Core — ForensicsManager: silent I/O failures**: Four `catch (Exception ignored) {}` blocks in `ForensicsManager` (`onAttackStart`, `recordMetrics`, `loadRecentSnapshots`, `getSnapshot`) swallowed disk I/O errors, JSON parse failures, and serialization exceptions without any log output. An admin could lose forensic attack records with no indication of why. All four blocks now log a `WARNING` with the failure context and message.
- **Core — IPReputationManager: silent CIDR parse exception**: `isInBlockedCidr()` swallowed `InetAddress.getByName()` and `CidrRange.contains()` exceptions silently. Misconfigured CIDR entries would fail invisibly. Exception now logged at `WARNING` level with the offending IP and error message.
- **Core — DiscordWebhookManager: silent TPS monitoring exception**: The periodic TPS check scheduled task caught all exceptions with `ignored`. Any error (e.g. server API unavailable during shutdown) would silently suppress the failure. Now logged via `LogManager.warning()`.
- **Core — ConnectionThrottleModule: hardcoded Turkish kick message with legacy color codes**: The connection-throttle kick used `LegacyComponentSerializer.legacyAmpersand().deserialize("&cCok fazla baglanti denemesi!")` — a hardcoded Turkish string with old `&c` color syntax, bypassing both `MessageManager` and locale support. Replaced with `plugin.getMessageManager().getMessage("engelleme.baglanti-siniri")`, which is already defined in both `messages_tr.yml` and `messages_en.yml`.
- **Core — BotProtectionModule: hardcoded strings with legacy `ChatColor` API**: Three player-facing messages (verification prompt, verification success, bot-kick) used `ChatColor.translateAlternateColorCodes('&', ...)` with hardcoded Turkish strings. Migrated to `MessageManager` (`antibot.dogrulama-mesaji`, `antibot.dogrulandi-mesaji`, `antibot.kick-mesaji`). Corresponding keys added to both locale files. `ChatColor` import removed.
- **Core — ModuleManager: BotProtectionModule and AntiBotModule active simultaneously**: `@Deprecated` `BotProtectionModule` could run alongside `AntiBotModule` if both were enabled in config, causing double-kick and double-ban for the same connection. `enableAllModules()` now checks for this conflict after all modules are started and automatically disables `BotProtectionModule` with a `WARNING` log entry explaining the reason.

### 🔧 Improvements

- **Core — messages_en.yml: 23 missing keys added**: The `adli-analiz`, `bal-kupu`, `guven`, and `istihbarat` sections were entirely absent from the English message file. Players or admins triggering these features on English-locale servers received `[Missing: key]` errors. All 23 keys now present with proper English translations.
- **Velocity — messages_en.yml: 6 missing kick keys added**: `kick.ddos-seviye`, `kick.ddos-subnet`, `kick.ddos-parmak-izi`, `kick.ddos-itibar`, `kick.ddos-slot`, and `kick.genel` were present in the Turkish file but absent from English. Added with corresponding English text.

## [2.1.0] - 2026-03-25

### ✨ New Features

- **Velocity — BurstAllowance system**: New `BurstAllowance` class detects legitimate short-lived traffic spikes (server restarts, event starts) and temporarily multiplies all rate limits by a configurable `burst-multiplier` (default 3×). Activation requires CPS to exceed `burst-threshold` (default 30) **and** the verified-player ratio to be above `min-verified-ratio-percent` (default 30%). At most `max-bursts-per-hour` (default 10) activations are permitted per hour to prevent abuse. Configurable under `modules.ddos-protection.burst-allowance`.
- **Velocity — DDoS protection dry-run mode**: New `modules.ddos-protection.dry-run: false` config key. When set to `true`, connections that would be blocked are only logged (`[DRY-RUN] Engellenecekti: <ip> | Sebep: <reason>`) and counted under the `ddos_dryrun_would_block` statistic, but are not actually rejected. Allows safe threshold tuning on live servers before fully enabling protection.
- **Velocity — `network-protection.enabled` now defaults to `true`**: The master toggle for all Velocity network protections (DDoS, AntiBot, VPN, Firewall) is now enabled out of the box. False-positive root causes have been resolved (see Fixes below), making it safe to ship protection on by default.

### 🔧 Improvements

- **Velocity — Threshold recalibration (false-positive prevention)**: All DDoS protection thresholds raised to tolerate normal multi-player reconnect patterns. `attack-mode.threshold` 30 → **50** CPS, `attack-mode.min-unique-ips` 10 → **15**, `connection-limit.per-ip-per-minute` 5 → **10**, `smart-throttle.normal-limit` 10 → **15** / `cautious-limit` 5 → **8** / `lockdown-limit` 0 → **1**. `attack-levels.hysteresis-rise-seconds` 3 → **5** s, `hysteresis-fall-seconds` 15 → **30** s. `rate-limit.connections-per-second.per-ip-max` 3 → **5**, `global-max` 50 → **100**.
- **Velocity — AttackLevelManager CPS multipliers raised**: Level transition thresholds increased so that small concurrent join bursts (6–7 players) no longer trigger ELEVATED mode. ELEVATED now requires 2× base CPS (was 1.5×), HIGH 3× (was 2×), CRITICAL 5× (was 3×), LOCKDOWN 8× (was 5×).
- **Velocity — SmartThrottleEngine: sliding window replaces cumulative counter**: The per-IP throttle counter was a Caffeine `AtomicInteger` that only reset after a 5-minute TTL, allowing a player who reconnected 11 times within 5 minutes to be permanently throttled until the window expired. Replaced with `ConcurrentLinkedDeque<Long>` timestamps and a 60-second sliding window — only connections within the last 60 seconds count toward the limit.
- **Velocity — ConnectionThrottler: verified-player bypass**: `tryConnect(ip, isVerified)` and `tryConnectAttackMode(ip, isVerified)` overloads added. Verified players (those who have previously logged in successfully) receive `max(limitPerMinute × 3, 15)` connections per minute in normal mode and `limitPerMinute` (instead of `limitPerMinute / 2`) in attack mode, eliminating throttle-caused reconnect loops for returning players.

### 🐛 Bug Fixes

- **Velocity — DDoSProtectionModule config completely disconnected from `config.yml`**: The module was registered under the name `"ddos-koruma"` but `config.yml` uses `modules.ddos-protection`. Additionally, all 43 config keys in `onEnable()` were Turkish (e.g. `saldiri-modu.esik`, `akilli-throttle.normal-limit`) while the YAML uses English keys. As a result, every config read fell through to the Java hardcoded default and `config.yml` edits had no effect on DDoS behaviour. The module name is now `"ddos-protection"` and all keys are aligned with `config.yml`.
- **Velocity — VelocityAntiBotModule config keys mismatched**: `onEnable()` read Turkish keys (`analiz-penceresi`, `supheli-esik`, `yuksek-risk-esik`, `captcha.aktif`, etc.) while `config.yml` uses English (`analysis-window`, `suspicious-threshold`, `high-risk-threshold`, `captcha.enabled`). All 11 keys corrected. Additionally, the `bot-protection` YAML section was at the top level of the file while `VelocityModule.getConfigXxx()` prefixes with `modules.`; the section has been moved under `modules:` so reads now resolve correctly.
- **Velocity — NicknameBlocker read from wrong YAML path**: `NicknameBlocker.reload()` read from `bot-protection.nick-engelleme.*` (top-level). After moving `bot-protection` under `modules:`, all paths updated to `modules.bot-protection.nick-engelleme.*`.
- **Velocity — FirewallModule config keys mismatched**: `onEnable()` and `onConfigReload()` used Turkish keys (`oto-yasak-esik`, `oto-yasak-sure`, `kalici-yasak-esik`, `decay-dakika`); these now match the YAML (`auto-ban-threshold`, `auto-ban-duration`, `permanent-ban-threshold`, `decay-minutes`). `permanent-ban-threshold: 500` added to `config.yml`.
- **Velocity — VPNDetectionModule `premium-vpn-politikasi` key never read**: The premium-bypass policy was hardcoded to `"izin-ver"` because the config key didn't exist in `config.yml`. Key renamed to `premium-vpn-policy: "allow"` and added to the `modules.vpn-proxy-block` section.
- **Velocity — ProxyExploitModule config section missing**: The module was registered as `"exploit-koruma"` with no matching `modules.exploit-koruma` section in `config.yml`. Module renamed to `"exploit-protection"`, Turkish keys (`sohbet-limit`, `tekrar-mesaj-limit`, `komut-limit`, `sunucu-degistirme-limit`) corrected to English, and a full `modules.exploit-protection` block added to `config.yml`.

## [2.0.11] - 2026-03-24

### 🐛 Bug Fixes

- **Core — TrustScoreManager: `IllegalStateException` on player quit**: `PostVerificationEvent` is declared as an async event (`super(true)`), but `recalculate()` was firing it synchronously via `Bukkit.getPluginManager().callEvent()` from the main server thread. This was triggered on every player disconnect through the `PlayerQuitEvent → recordQuit() → recalculate()` call chain, as well as from `recordViolation()` and `recordKick()`. Paper 1.21.4 enforces that async events may only be called from off-thread, throwing `IllegalStateException` and leaving the trust score partially updated. The event dispatch in `recalculate()` is now wrapped in `Bukkit.getScheduler().runTaskAsynchronously()`, covering all three call sites at once.

## [2.0.10] - 2026-03-24

### 🐛 Bug Fixes

- **Core — DuplicationFixModule: ArrayIndexOutOfBoundsException on hotbar swap**: `InventoryClickEvent.getHotbarButton()` returns `-1` for non-hotbar keyboard clicks (including `SWAP_OFFHAND`), causing `PlayerInventory.getItem(-1)` to throw `ArrayIndexOutOfBoundsException`. The slot value is now validated with `>= 0 && < inventory.getSize()` before use. The `SWAP_OFFHAND` (`F` key) click type is now handled separately — if the item in the player's offhand is a Shulker Box while a Shulker Box inventory is open, the swap is cancelled, closing the shulker-in-shulker duplication vector.
- **Core — HeuristicEngine: false-positive kicks for legitimate players**: Several thresholds were too aggressive for normal gameplay. `max-rotation-speed` default raised from `8.0` to `12.0` degrees/ms (high-DPI mice), `max-rotation-spikes` from `5` to `8`. Rotation suspicion added per burst lowered from `5.0` to `3.0`, click suspicion from `15.0` to `8.0`. The kick threshold in `checkSuspicionLevel()` raised from `100.0` to `150.0`; after a kick the suspicion score is now fully zeroed instead of only reducing by 50. Players with the `atomguard.bypass` permission now skip both rotation and click analysis entirely. Rotation analysis is skipped for the first 5 seconds after a player joins (session grace period) to avoid false spikes from initial position packets. Click intervals below 10 ms are filtered out as lag-induced packet merging artifacts. When speed falls below the threshold, spikes now decrement by 1 (`decrementRotationSpikes`) instead of resetting to zero, preventing a single normal packet from clearing accumulated evidence. The minimum click sample count raised from `10` to `15`.
- **Core — HeuristicProfile: decay too slow and suspicion cap too low**: `DECAY_RATE_PER_SECOND` raised from `0.5` to `2.0` (4× faster natural decay), `MAX_CLICK_SAMPLES` from `20` to `30`. The `addSuspicion()` internal cap raised from `100.0` to `200.0` so the score can actually reach the new `150.0` kick threshold. New `sessionStartTime` field (set to `System.currentTimeMillis()` at profile creation) with getter/setter for grace period checks. New `decrementRotationSpikes()` method decrements `rotationSpikes` by 1 if positive (gradual cooldown instead of instant reset).
- **Core — AbstractModule: blockExploit() applies uniform +1.0 heuristic weight regardless of module type**: High-frequency limiter modules (`piston-limiter`, `redstone-limiter`, `falling-block-limiter`, `explosion-limiter`) fire hundreds of times per second during normal lag spikes, causing innocent players to accumulate suspicion rapidly. These modules now contribute `+0.1` per block event; all other modules contribute `+0.5` (was always `+1.0`).

### 🔧 Improvements

- **Config — New `heuristic` section**: Added a dedicated `heuristic:` block to `config.yml` (placed before `external-services:`) exposing all tunable thresholds: `action`, `kick-threshold` (150.0), `max-rotation-speed` (12.0), `max-rotation-spikes` (8), `min-spike-interval-ms` (150), `min-click-samples` (15), `min-click-variance` (5.0), `max-avg-click-interval` (100.0). These values were previously hard-coded in the engine; operators can now raise them on high-activity servers to further reduce false positives without touching the source.

## [2.0.9] - 2026-03-22

### 🐛 Bug Fixes

- **Velocity — HandshakeValidator: missing protocol IDs for 1.21.2–1.21.4**: Protocol numbers `769` (1.21.4), `768` (1.21.3), and `767` (1.21.2) were absent from `KNOWN_PROTOCOLS`. With `enforce-known-protocols: true`, every legitimate 1.21.2+ client received a `handshakeScore=50`, which combined with other categories could trigger a false-positive kick. A new `ek-protokoller` config list allows adding future protocol numbers without a plugin update.
- **Velocity — BrandAnalyzer: unknown-brand false positives**: Many widely-used clients were missing from the legitimate brand list (`sodium`, `iris`, `fml,forge`, `fml`, `tlauncher`, `shiginima`, `pojav`, `meteor`, `wurst`). With `allow-unknown: false` these players received an unnecessary `brandScore=20`. A new `izinli-brandlar` config list allows operators to add their own allowed brands.
- **Velocity — ConnectionListener.onBrand() race condition**: `PlayerClientBrandEvent` can fire before `LoginEvent`, meaning `isVerified(ip)` returns `false` at brand-check time even for legitimate players. If the player already has a backend server connection (`getCurrentServer().isPresent()`), the handler now calls `markVerified(ip)` and returns immediately instead of scoring and potentially kicking.
- **Velocity — VelocityAntiBotModule: NicknameBlocker sets all 7 score categories to 100**: When a blocked nickname was detected, all categories (`connectionRate`, `handshake`, `brand`, `joinPattern`, `geo`, `protocol`) were forced to 100 in addition to `username`. This produced misleading audit logs and inflated `flagCount`, bypassing the single-category 60 % penalty in `ThreatScore.calculate()`. Now only `usernameScore=100` is set; `flagCount` stays at 1 and the penalty is applied correctly.
- **Velocity — recordBrand() double analyze clears previous scores**: `recordBrand()` called a full `engine.analyze()` as a second pass, which triggered `resetForNewAnalysis()` and wiped all scores built by the initial `analyzePreLogin()`. Brand is now applied via the new `BotDetectionEngine.updateBrandScore()` method, which sets `brandScore` and calls `calculate()` without resetting other categories.
- **Velocity — IPReputationEngine: grace-period race condition**: Two concurrent pipeline checks for the same IP could both pass the 3-violation grace period by reading `violations.get()` before either had incremented. The violation counter is now incremented atomically at the top of `addContextualScore()` and the method returns early if the IP is still within the grace window, preventing any score accumulation or downstream ban checks during the grace period.
- **Velocity — PlayerBehaviorProfile: username-diversity penalty on offline-mode servers**: The trust score applied a −20 penalty when more than 3 different usernames were seen from the same IP. On offline-mode servers (cracked launchers, multiple household accounts) this is completely normal behaviour. The threshold is raised to 6 unique usernames, the penalty reduced to −10, and a new `offlineModeLenient` flag disables the penalty entirely when set.
- **Velocity — LatencyCheckModule: fast-login threshold too aggressive**: The default minimum handshake-to-login duration was 15 ms, which caused false kicks for players connecting from the same data centre or through a local proxy. Default lowered to 5 ms.
- **Core — HeuristicEngine: high-DPI mouse users flagged for rotation speed**: Default `max-rotation-speed` raised from 5.0 to 8.0 degrees/ms, and `max-rotation-spikes` raised from 3 to 5. A new `min-spike-interval-ms` guard (default 100 ms) ensures that rapid successive packets at high FPS are counted as at most one spike per interval, preventing false suspicion accumulation from legitimate 360° PvP movement.

## [2.0.8] - 2026-03-21

### 🐛 Bug Fixes

- **Core — OfflinePacketModule auto-disable when no auth plugin detected**: If no known auth plugin (AuthMe, nLogin, OpeNLogin, LoginSecurity, JPremium, FastLogin, LimboAuth) is found at startup, OfflinePacketModule and AuthListener are no longer loaded. This was the root cause of players getting "timed out" on servers with custom login plugins. Can be force-enabled via `modules.offline-packet.zorla-aktif: true`.
- **Core — OfflinePacketModule KEEP_ALIVE/PONG packet exemption**: KEEP_ALIVE, PONG, CLIENT_SETTINGS, RESOURCE_PACK_STATUS, and PLUGIN_MESSAGE packets are now unconditionally exempted from all checks. Previously, these connection-critical packets could be cancelled after the grace period expired, causing the server to think the player was unresponsive and kicking them for timeout.
- **Core — OfflinePacketModule grace period check order fixed**: Grace period check now runs BEFORE IP validation. Previously, an IP mismatch (e.g. IPv4/IPv6 dual-stack) would cancel all packets including KEEP_ALIVE even during the grace period, because the IP check ran first.
- **Core — OfflinePacketModule IPv4/IPv6 IP comparison fix**: IP validation now uses `getHostAddress()` string comparison instead of `InetAddress.equals()`, which fails on dual-stack connections (e.g. `127.0.0.1` vs `::ffff:127.0.0.1`).
- **Core — AdvancedChatModule grace period bypass**: Chat rate limiting and spam detection now skip players in the auth grace period, preventing chat blocks during the login process.

### 🔧 Improvements

- **Config — offline-packet.tolerance-ms**: Default increased from 30000ms (30s) to 60000ms (60s) to accommodate slower auth flows.
- **Config — offline-packet.zorla-aktif**: New config key (default: `false`) to force-enable OfflinePacketModule even when no known auth plugin is detected.

## [2.0.7] - 2026-03-19

### 🐛 Hata Düzeltmeleri

- **Core — VerificationManager tick sayacı düzeltildi**: Timer periyodu 40→20 tick'e düşürüldü; `cycleCounter` eklendi. `ticksSinceJoin` artık gerçek saniyeyle 1:1 eşleşiyor (önceki versiyonda 40x yavaştı; whitelist'e ulaşmak 20 dakika sürüyordu).
- **Core — PlayerProfile client brand gerçekten okunuyor**: `recordPluginMessage()` artık `minecraft:brand` paketinin payload'ını VarInt prefix ile doğru parse ediyor. Configuration phase (`WrapperConfigClientPluginMessage`) ve Play phase ayrı ayrı handle ediliyor. Önceki versiyonda brand her zaman `"unknown"` döndürüyordu.
- **Core — PlayerProfile volatile eksikliği giderildi**: `protocolVersion`, `clientBrand`, `handshakeHostname` field'larına `volatile` eklendi (Netty thread'inde yazılır, async timer thread'inde okunur).
- **Core — KeepAlive ID bazlı eşleşme**: `recordKeepAliveSent()` / `recordKeepAliveResponse()` artık KeepAlive ID'sini kullanıyor. Önceki tek-timestamp yaklaşımı `0ms` response time üretiyor ve `+20` puan yazıyordu.
- **Core — AntiBotModule getOrCreateProfile race condition**: Pre-login'de IP profili oluşturuluyordu; Login'de UUID ile farklı profil açılıyordu. `CLIENT_SETTINGS` IP profilinde kalıyor, UUID profilinde yoktu → `+15` puan. Artık UUID geldiğinde IP profili birleştiriliyor.
- **Core — AttackTracker unique IP kontrolü**: Saldırı tespiti artık hem bağlantı sayısını hem de farklı IP sayısını kontrol ediyor (`attack-mode.min-unique-ips`, varsayılan: 10). Tek IP'den gelen 30 bağlantı artık saldırı olarak algılanmıyor.
- **Core — GravityCheck TPS 19-20 edge case**: Kademeli tolerans hesaplaması eklendi (`tps < 20.0` için lineer lagFactor). Önceki versiyonda yalnızca `tps < 19.0` koşulu vardı; 19-20 arası TPS'de false positive oluşuyordu.
- **Core — PostJoinBehaviorCheck mantık hatası**: Sohbet eden oyuncu muafiyeti düzeltildi (önceden dead code'du). AFK oyuncular artık cezalandırılmıyor; check yalnızca etkileşim olmayan ve hareketsiz oyuncuları puanlıyor. Maksimum skor 25→10'a düşürüldü.
- **Core — WhitelistManager ulaşılamaz eşikler**: `verify-timeout-ticks` 600→300, `max-verify-score` 15→25. Position packet zorunluluğu kaldırıldı (auth plugin altında hareket edilemiyor; client settings yeterli).
- **Core — PacketTimingCheck variance eşiği**: Variance eşiği `0.1 ms²`→`5.0 ms²` (önceki değer pratikte hiç tetiklenmiyordu).
- **Core — ConnectionRateCheck online oyuncu**: `firstJoinTime > 0` ise erken dönüş eklendi; sunucuda aktif oyuncuya gereksiz bağlantı hızı puanı verilmiyordu.
- **Core — ConfigManager NETWORK_MODULE_NAMES**: `"bot-protection"` eklendi; eski `BotProtectionModule` ağ koruma kontrolünden hariç tutuluyordu.

### 🔧 İyileştirmeler

- **Config — Score eşikleri yükseltildi**: `allow: 40→45`, `delay: 70→75`, `kick: 90→100`, `blacklist: 120→150` (false positive sonrası bant genişletme).
- **Config — AttackMode**: `shutdown-seconds` 30→60; `min-unique-ips: 10` yeni ayar eklendi.
- **Config — Whitelist**: `verify-timeout-ticks: 300`, `max-verify-score: 25`.
- **Config — Gravity**: `min-data-count` 8→10; `min-interval-ms` 30→25; `max-interval-ms` 150→200; `min-keepalive-ms` 2→1.
- **Config — PostJoinBehavior**: `analysis-ticks` 1200→600 (tick düzeltmesi sonrası gerçek anlamı aynı: 10 dakika).

## [2.0.6] - 2026-03-19

### ✨ Yeni Özellikler

- **Core — Özel Login Plugini Uyumluluğu**: AtomGuard artık herhangi bir özel login plugini ile tam uyumlu. `AuthListener.markAuthenticated(UUID)` API'si üzerinden login plugin'leri auth tamamlandığında AtomGuard'a bildirimde bulunabilir.
- **Core — Auth Grace Period (AntiBotModule)**: Auth bekleyen oyuncular artık bot olarak puanlanmıyor. `modules.anti-bot.checks.auth-grace-period-ms` (varsayılan: 120 saniye) süresince `ThreatScoreCalculator` auth bekleyen oyuncuyu serbest geçiriyor.
- **Core — AtomGuard.getAuthListener()**: `AuthListener` instance'ı artık dışarıdan erişilebilir. Login pluginleri `AtomGuard.getInstance().getAuthListener().markAuthenticated(uuid)` ile auth tamamlandığını bildirebilir.
- **Core — PlayerProfile.markAuthenticated()**: AntiBotModule player profiline auth durumu eklendi. Auth tamamlandığında profil işaretleniyor; tüm periyodik threat evaluation'lar bu durumu görerek skor hesaplamayı atlıyor.

### 🔧 İyileştirmeler

- **Core — TokenBucketModule grace period muafiyeti**: Auth grace period'u aktif oyunculara movement paketleri de dahil olmak üzere hiçbir rate limit uygulanmıyor (önceki versiyonda yalnızca komut paketleri muaftı, hareket paketleri cezalandırılıyordu).
- **Core — HeuristicEngine auth muafiyeti**: `PacketListener.handleLegacyIncoming()` artık auth grace period içindeki oyuncuların rotation/animation analizini atlıyor. Login ekranında fare hareketi artık yanlışlıkla suspicion olarak birikmiyor.
- **Core — VerifiedPlayerCache auth öncesi koruma**: Grace period bittiğinde oyuncu hâlâ auth bekliyor olabilir. `BukkitListener` artık auth tamamlanmadan oyuncuyu "verified" olarak işaretlemiyor; tamamlanmamışsa +30 saniye daha bekleyerek ikinci kontrol yapıyor.
- **Core — AntiBotModule.getPlayerProfile()**: `PlayerProfile` nesnesi artık dışarıdan erişilebilir public getter ile sunuluyor.
- **Core — OfflinePacketModule auth komut listesi genişletildi**: Varsayılan fallback listesine `/sifre`, `/şifre`, `/auth`, `/2fa`, `/totp`, `/pin` eklendi.
- **Core — config.yml**: `modules.anti-bot.checks.auth-grace-period-ms` ayarı eklendi (varsayılan: 120000 ms = 2 dakika).

## [2.0.5] - 2026-03-19

### 🐛 Hata Düzeltmeleri

- **Core — Login/Timeout sorunu düzeltildi**: `TokenBucketModule` artık auth komutlarını
  rate limit'lemiyor. `CHAT_COMMAND` paketleri ayrı `KOMUT` kovasına yönlendirildi.
  Auth komut listesi `modules.token-bucket.auth-exempt-commands` ile özelleştirilebilir.
- **Core — KeepAlive paketleri artık asla engellenmez**: `PacketDelayModule` ve
  `PacketExploitModule`, `KEEP_ALIVE` ve `PONG` paketlerini bypass ediyor.
- **Core — Grace period 30 saniyeye çıkarıldı**: Auth komutu algılandığında grace period
  otomatik yenilenir; auth tamamlandığında anında biter.
- **Core — Auth sistemi tamamen plugin-agnostic**: AuthMe hard-dependency kaldırıldı.
  `AuthListener` generic yeniden yazıldı — herhangi bir login plugini ile çalışır.
- **Core — PacketExploitModule race condition düzeltildi**: Tür bazlı sayaçlarda
  `HashMap` → `ConcurrentHashMap` (Netty thread güvenliği).
- **Core — CustomPayloadModule çifte kayıt kaldırıldı**: `AdvancedPayloadModule` zaten
  aynı işlevi kapsıyor; eski modülün kaydı `registerModules()`'den silindi.
- **Core — `atomguard:auth` kanalı whitelist'e eklendi**: `AdvancedPayloadModule` artık
  `atomguard:` prefix'li kanalları engellemez; `CorePasswordCheckModule` çalışıyor.
- **Core — NettyCrashHandler false positive düzeltildi**: `"buffer"` ve `"index"` tek
  başına crash tespiti tetiklemiyor; `"buffer overflow"` ve `"index out of bounds"` olarak
  daraltıldı.
- **Core — VerifiedPlayerCache zamanlaması düzeltildi**: Oyuncu artık grace period
  dolmadan `verified` olarak işaretlenmiyor (`BukkitListener` geciktirildi).
- **Velocity — Auth komut rate limiti düzeltildi**: `CommandFloodBlocker`'da login/register
  komutları artık 2 token değil, tamamen muaf tutuluyor.

### 🔧 İyileştirmeler

- **Core — Unicode crash filtresi daraltıldı**: `AdvancedChatModule` artık Arapça, İbranice,
  Korece ve CJK karakterlerini engellemiyor; yalnızca gerçek crash vektörü olan Unicode
  yön kontrol karakterleri filtreleniyor.
- **Core — SmartLagModule chunk tarama optimizasyonu**: Lag spike'ta tüm chunk'lar yerine
  rastgele `%20` örnekleme yapılıyor; maksimum 10 chunk freeze limiti eklendi.
- **Core — `PacketExploitModule` CHAT_COMMAND limiti eklendi**: Type-based limitlerde
  `CHAT_COMMAND: 30/s` varsayılanı eklendi.
- **Config — Yeni ayarlar**:
  - `modules.token-bucket.auth-exempt-commands`
  - `modules.token-bucket.buckets.command.kapasite` (varsayılan: 50)
  - `modules.token-bucket.buckets.command.dolum-saniye` (varsayılan: 20)
  - `modules.offline-packet.auth-commands`
  - `modules.offline-packet.tolerance-ms` varsayılanı `5000` → `30000`
  - `modules.packet-exploit.type-limits.CHAT_COMMAND` (varsayılan: 30)

## [2.0.4] - 2026-03-18

### ✨ Yeni Özellikler

-

### 🔧 İyileştirmeler

-

### 🐛 Hata Düzeltmeleri

-

## [2.0.3] - 2026-03-14

### 🐛 Hata Düzeltmeleri

- **Core — Tek oyuncu saldırı modu yanlış tetiklemesi düzeltildi**: `AttackModeManager`
  artık hem hız eşiğini (`threshold`) hem de benzersiz IP sayısını (`min-unique-ips`,
  varsayılan: 10) kontrol ediyor. Tek bir oyuncunun hızlı çık/gir yapması artık
  saldırı modunu tetikleyemiyor.
- **Velocity — Aynı düzeltme**: `SynFloodDetector`, benzersiz IP sayısı `min-unique-ips`
  altındaysa `effectiveCps=0` gönderiyor; `AttackLevelManager` NONE seviyesinde kalıyor.
  `DDoSProtectionModule.checkConnection()` artık IP'yi `recordConnection(ip)` ile iletiyor.

### 🔧 İyileştirmeler

- **Yeni config ayarı**: `attack-mode.min-unique-ips` (core) ve
  `ddos-protection.attack-mode.min-unique-ips` (velocity) — varsayılan 10

## [2.0.2] - 2026-03-13

### 🌐 Full English Translation

- **Config keys renamed (Turkish → English)** across all 40+ modules and all manager classes
- **Top-level sections renamed**: `moduller` → `modules`, `genel` → `general`, `istatistik` → `statistics`, `dogrulanmis-onbellek` → `verified-cache`, `metrikler` → `metrics`, `tehdit-istihbarati` → `threat-intelligence`, `guven-skoru` → `trust-score`, `adli-analiz` → `forensics`, `bildirimler` → `notifications`, `forensik` → `packet-forensics`, `harici-servisler` → `external-services`
- **Module names translated**: e.g. `cok-fazla-kitap` → `too-many-books`, `paket-exploit` → `packet-exploit`, `bot-koruma` → `anti-bot`, `bal-kupu` → `honeypot`, `jeton-kovasi` → `token-bucket`, and 40+ more
- **Common sub-keys translated**: `aktif` → `enabled`, `eylem` → `action`, `max-paket-boyutu` → `max-packet-size`, and 100+ more
- **Velocity config translated**: `depolama` → `storage`, `bot-koruma` → `bot-protection`, `moduller` → `modules`, all DDoS/VPN/firewall sub-keys
- **Default language changed**: `dil: "tr"` → `language: "en"` (both core and velocity configs)
- **Hardcoded Turkish strings** in Java source translated to English (AtomGuard.java, ForensicsManager, NettyCrashHandler, BukkitListener, ModuleManager, and others)
- **Migration step added** (`Migration_2_0_1_to_2_0_2`) — automatically renames all Turkish config keys to English for existing installations
- **Config version bumped** to `2.0.2`

## [2.0.1] - 2026-03-12

### 🐛 Hata Düzeltmeleri

- **Velocity — Oyuncu "Timed Out" sorunu düzeltildi**: `ConnectionListener.onPreLogin()` artık `EventTask.resumeWhenComplete()` döndürüyor; önceki senkron `process()` çağrısı Velocity event executor thread'ini 5 saneye kadar blokluyordu (VPNCheck `.get(3s)` + AccountFirewallCheck `.get(2s)`). Bu blokaj Minecraft istemcisinin bağlantı zaman aşımına çarpmasına neden oluyordu
- **VPNCheck — blokaj kaldırıldı**: `checkAsync()` override edildi; `.get(3, TimeUnit.SECONDS)` yerine `.orTimeout(3, TimeUnit.SECONDS).exceptionally(...).thenApply(...)` zinciri kullanılıyor
- **AccountFirewallCheck — blokaj kaldırıldı**: `checkAsync()` override edildi; `.get(2, TimeUnit.SECONDS)` yerine `.orTimeout(2, TimeUnit.SECONDS).exceptionally(...).thenApply(...)` zinciri kullanılıyor

### 🔧 İyileştirmeler

- **ConnectionCheck arayüzü**: `checkAsync(ctx)` default metodu eklendi — tüm mevcut check'ler uyumluluk bozmadan `CompletableFuture` desteği kazandı
- **ConnectionPipeline**: `processAsync(ctx)` metodu eklendi — check'leri sıralı `CompletableFuture` zinciri olarak çalıştırır, ilk reddedilen sonuçta kısa devre yapar

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
