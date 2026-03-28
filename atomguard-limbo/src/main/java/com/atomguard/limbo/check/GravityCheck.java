package com.atomguard.limbo.check;

/**
 * Minecraft yerçekimi fiziği kontrolü.
 *
 * <p>Minecraft'ta her tick (50ms) yerçekimi şu formülle hesaplanır:
 * <pre>
 *   velocity_y = (velocity_y - 0.08) * 0.98
 *   position_y += velocity_y
 * </pre>
 *
 * <p>Beklenen düşüş dizisi (y=65'ten başlayarak):
 * <pre>
 *   Tick 0: y = 65.0000  (spawn)
 *   Tick 1: y = 64.9216  (Δ = -0.0784)
 *   Tick 2: y = 64.7665  (Δ = -0.1551)
 *   Tick 3: y = 64.5360  (Δ = -0.2305)
 *   Tick 4: y = 64.2312  (Δ = -0.3048)
 *   ...
 * </pre>
 *
 * <p>TCP garantisi: Paketler sırasız gelemez. Lag sadece geciktirir, fiziği bozmaz.
 * Bu yüzden yüksek ping'li oyuncular güvenle doğrulanabilir.
 *
 * <p>Bot yazılımları bu hesaplamayı ya hiç yapmaz ya da yanlış yapar → tespit edilir.
 */
public class GravityCheck {

    private static final double GRAVITY = 0.08;
    private static final double DRAG    = 0.98;

    /** Tolerans: ±0.04 blok (ping kaynaklı gecikme + kayan nokta hatası) */
    private static final double TOLERANCE = 0.04;

    /** En az bu kadar tick pozisyon verisi toplanmadan karar verilmez */
    private static final int MIN_TICKS = 5;

    /** Bu kadar tick sonra hâlâ belirsizse → belirsiz sonuç */
    private static final int MAX_TICKS = 25;

    private double expectedY;
    private double velocity = 0.0;
    private int tickCount = 0;
    private int correctTicks = 0;

    public GravityCheck(double spawnY) {
        this.expectedY = spawnY;
    }

    /**
     * Her position update'te çağrılır.
     *
     * @param playerY Oyuncunun bildirdiği Y koordinatı
     * @return {@code Boolean.TRUE} → geçti, {@code Boolean.FALSE} → başarısız,
     *         {@code null} → henüz karar yok (daha fazla veri gerekli)
     */
    public Boolean onPositionUpdate(double playerY) {
        // Beklenen Y'yi bir sonraki tick için hesapla
        velocity = (velocity - GRAVITY) * DRAG;
        expectedY += velocity;
        tickCount++;

        double diff = Math.abs(playerY - expectedY);
        if (diff <= TOLERANCE) {
            correctTicks++;
        }

        if (tickCount >= MIN_TICKS) {
            double accuracy = (double) correctTicks / tickCount;
            if (accuracy >= 0.80) return Boolean.TRUE;  // %80+ doğru = gerçek client
            if (accuracy <= 0.25) return Boolean.FALSE; // %25- doğru = bot şüphesi
        }

        // Yeterince veri toplandı ama hâlâ belirsiz → timeout'a bırak
        if (tickCount >= MAX_TICKS) {
            return correctTicks >= MIN_TICKS ? Boolean.TRUE : Boolean.FALSE;
        }

        return null; // Henüz karar yok
    }

    public int getTickCount()    { return tickCount; }
    public int getCorrectTicks() { return correctTicks; }

    public double getAccuracy() {
        return tickCount == 0 ? 0.0 : (double) correctTicks / tickCount;
    }
}
