package com.atomguard.velocity.module.antiddos;

/**
 * DDoS koruma kontrol pipeline'ı için temel arayüz.
 * Her kontrol bu arayüzü uygular, bağımsız istatistik tutar
 * ve short-circuit zinciri destekler.
 */
public interface DDoSCheck {

    /**
     * Bağlantıyı kontrol et.
     *
     * @param ip      IP adresi
     * @param context Kontrol bağlamı
     * @return Kontrol sonucu
     */
    DDoSCheckResult check(String ip, DDoSCheckContext context);

    /** Kontrolün kısa adını döndür. */
    String getName();

    /** Kontrol aktif mi? */
    boolean isEnabled();

    /** Bu kontrol tarafından engellenen toplam bağlantı sayısı. */
    long getBlockedCount();

    /** Periyodik temizlik — scheduler tarafından çağrılır. */
    default void cleanup() {}

    // ────────────────────────────────────────────────────────
    // Bağlam
    // ────────────────────────────────────────────────────────

    /**
     * Bir bağlantı hakkındaki tüm bağlamsal bilgileri taşır.
     *
     * @param ip              Kaynak IP adresi
     * @param isVerified      Oyuncu daha önce başarıyla giriş yapmış mı?
     * @param isAttackMode    Plugin attack mode aktif mi?
     * @param currentLevel    Güncel saldırı seviyesi
     * @param hostname        Bağlanılan hostname (handshake'den)
     * @param port            Bağlanılan port
     * @param protocolVersion Minecraft protokol versiyonu
     * @param fingerprint     Bağlantı parmak izi (nullable)
     */
    record DDoSCheckContext(
            String ip,
            boolean isVerified,
            boolean isAttackMode,
            AttackLevelManager.AttackLevel currentLevel,
            String hostname,
            int port,
            int protocolVersion,
            String fingerprint
    ) {
        /** Ping/bağlantı kontrolü için minimal bağlam oluştur. */
        public static DDoSCheckContext forConnection(
                String ip, boolean isVerified, boolean isAttackMode,
                AttackLevelManager.AttackLevel level) {
            return new DDoSCheckContext(ip, isVerified, isAttackMode, level, null, 0, 0, null);
        }
    }

    // ────────────────────────────────────────────────────────
    // Sonuç
    // ────────────────────────────────────────────────────────

    /**
     * Kontrol sonucu.
     *
     * @param allowed       Bağlantıya izin verildi mi?
     * @param reason        İnsan okunabilir sebep (log için)
     * @param kickMessageKey messages.yml anahtar (null ise kick yok)
     * @param checkName     Bu sonucu üreten kontrol adı
     */
    record DDoSCheckResult(
            boolean allowed,
            String reason,
            String kickMessageKey,
            String checkName
    ) {
        /** İzin ver. */
        public static DDoSCheckResult allow(String checkName) {
            return new DDoSCheckResult(true, "ok", null, checkName);
        }

        /** Reddet. */
        public static DDoSCheckResult deny(String reason, String kickKey, String checkName) {
            return new DDoSCheckResult(false, reason, kickKey, checkName);
        }
    }
}
