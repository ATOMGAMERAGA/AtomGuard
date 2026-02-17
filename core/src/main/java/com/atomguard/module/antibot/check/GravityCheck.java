package com.atomguard.module.antibot.check;

import com.atomguard.module.antibot.AntiBotModule;
import com.atomguard.module.antibot.PlayerProfile;
import org.bukkit.Bukkit;
import java.util.List;

public class GravityCheck extends AbstractCheck {
    
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;

    public GravityCheck(AntiBotModule module) {
        super(module, "yercekimi");
    }

    @Override
    public int calculateThreatScore(PlayerProfile profile) {
        // FP-06: TPS Kontrolü
        double tps = Bukkit.getTPS()[0];
        if (tps < 15.0) return 0; // Çok yoğun lag varsa devre dışı bırak

        List<Double> yPositions = profile.getRecentYPositions();
        int minData = module.getConfigInt("kontroller.yercekimi.min-veri-sayisi", 8); // 5'ten 8'e çıkarıldı
        
        if (yPositions.size() < minData) return 0;

        int violations = 0;
        int totalChecks = 0;
        double tolerance = module.getConfigDouble("kontroller.yercekimi.tolerans", 0.08); // 0.03'ten 0.08'e çıkarıldı

        // FP-06: Düşük TPS durumunda toleransı artır
        if (tps < 18.0) {
            tolerance *= 2.0;
        }

        for (int i = 2; i < yPositions.size(); i++) {
            double deltaY1 = yPositions.get(i - 1) - yPositions.get(i - 2);
            double deltaY2 = yPositions.get(i) - yPositions.get(i - 1);

            if (Math.abs(deltaY1) < 0.001 && Math.abs(deltaY2) < 0.001) continue;

            totalChecks++;
            double expectedDeltaY = (deltaY1 - GRAVITY) * DRAG;
            double difference = Math.abs(deltaY2 - expectedDeltaY);

            if (difference > tolerance) {
                violations++;
            }
        }

        if (totalChecks < 5) return 0; // Minimum 5 gerçek kontrol

        double violationRate = (double) violations / totalChecks;

        // FP-06: Eşikleri yükselt (0.4, 0.6, 0.8)
        if (violationRate > 0.8) return 40;
        if (violationRate > 0.6) return 25;
        if (violationRate > 0.4) return 10;

        return 0;
    }
}
