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
        double tps = Bukkit.getTPS()[0];
        if (tps < 16.0) return 0; // Ağır lag — tamamen devre dışı

        List<Double> yPositions = profile.getRecentYPositions();
        int minData = module.getConfigInt("checks.gravity.min-data-count", 10);

        if (yPositions.size() < minData) return 0;

        int violations = 0;
        int totalChecks = 0;
        double baseTolerance = module.getConfigDouble("checks.gravity.tolerance", 0.08);

        // KADEMELİ tolerans: TPS düştükçe lineer artış (19-20 arası da kapsanıyor)
        double tolerance = baseTolerance;
        if (tps < 20.0) {
            double lagFactor = (20.0 - tps) / 4.0; // TPS=20→0, TPS=19→0.25, TPS=18→0.5, TPS=16→1.0
            tolerance = baseTolerance * (1.0 + lagFactor * 5.0);
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

        if (totalChecks < 5) return 0;

        double violationRate = (double) violations / totalChecks;

        if (violationRate > 0.85) return 30;
        if (violationRate > 0.70) return 15;
        if (violationRate > 0.55) return 5;

        return 0;
    }
}
