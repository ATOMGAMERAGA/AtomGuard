# Changelog

Tüm önemli değişiklikler bu dosyada belgelenir.
Bu proje [Semantic Versioning](https://semver.org/lang/tr/) kullanır.

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
