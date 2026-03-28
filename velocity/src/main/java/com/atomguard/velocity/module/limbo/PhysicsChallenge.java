package com.atomguard.velocity.module.limbo;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft yerçekimi simülasyonu ile bot doğrulama.
 *
 * Minecraft gravity (Java Edition, her tick = 50ms):
 *   v_t = (v_{t-1} - 0.08) * 0.98
 *   y_t = y_{t-1} + v_t
 *
 * Başlangıç: Y=300.0, v=0
 * Beklenen Y değerleri: 299.9216, 299.7665, 299.5358, 299.2305, 298.8515
 *
 * Bot tespiti:
 *   - Y hiç değişmiyorsa (NoFall bot) → FAIL
 *   - Y beklenen fizikten çok sapıyorsa → FAIL
 *   - Yeterli paket gelmezse → TIMEOUT (config'e göre allow/deny)
 */
public class PhysicsChallenge {

    /** Başlangıç Y yüksekliği */
    public static final double START_Y = 300.0;
    /** Tolerans: beklenen Y'den bu kadar sapma kabul edilir (blok) */
    private static final double TOLERANCE = 0.15;
    /** Doğrulama için gereken minimum doğru paket sayısı */
    private static final int MIN_VALID_PACKETS = 3;
    /** Y değişimi olmaksızın bu kadar paket → NoFall tespiti */
    private static final int STATIC_Y_THRESHOLD = 3;

    /**
     * Toplanan Y pozisyonlarını doğrula.
     *
     * @param receivedYPositions Oyuncudan gelen Y değerleri (kronolojik sırada)
     * @return Doğrulama sonucu
     */
    public ValidationResult validate(List<Double> receivedYPositions) {
        if (receivedYPositions == null || receivedYPositions.isEmpty()) {
            return ValidationResult.fail("Pozisyon paketi alınmadı");
        }

        // NoFall tespiti: Y hiç değişmiyor
        if (receivedYPositions.size() >= STATIC_Y_THRESHOLD) {
            double first = receivedYPositions.get(0);
            boolean allSame = receivedYPositions.stream().allMatch(y -> Math.abs(y - first) < 0.001);
            if (allSame) {
                return ValidationResult.fail("Statik Y pozisyonu (NoFall bot)");
            }
        }

        // Beklenen fizik simülasyonuyla karşılaştır
        List<Double> expected = simulateGravity(START_Y, receivedYPositions.size());
        int validCount = 0;
        for (int i = 0; i < Math.min(receivedYPositions.size(), expected.size()); i++) {
            double diff = Math.abs(receivedYPositions.get(i) - expected.get(i));
            if (diff <= TOLERANCE) validCount++;
        }

        if (validCount >= MIN_VALID_PACKETS) {
            return ValidationResult.pass(validCount + "/" + receivedYPositions.size() + " paket doğrulandı");
        }

        return ValidationResult.fail("Fizik doğrulaması başarısız: " + validCount + "/" + receivedYPositions.size());
    }

    /**
     * n adım için beklenen Y pozisyonlarını simüle et.
     */
    public static List<Double> simulateGravity(double startY, int steps) {
        List<Double> positions = new ArrayList<>(steps);
        double y = startY;
        double vy = 0.0;
        for (int i = 0; i < steps; i++) {
            vy = (vy - 0.08) * 0.98;
            y += vy;
            positions.add(y);
        }
        return positions;
    }

    public record ValidationResult(boolean passed, String reason) {
        public static ValidationResult pass(String reason) { return new ValidationResult(true, reason); }
        public static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }
    }
}
