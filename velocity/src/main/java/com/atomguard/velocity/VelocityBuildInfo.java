package com.atomguard.velocity;

import org.slf4j.Logger;

public final class VelocityBuildInfo {

    public static final String VERSION = "1.0.0";
    public static final String BUILD_DATE = "2026-02-17";
    public static final String AUTHOR = "AtomGuard Team";

    private VelocityBuildInfo() {}

    public static void printBanner(Logger logger, int moduleCount) {
        logger.info("╔══════════════════════════════════════╗");
        logger.info("║        AtomGuard Velocity            ║");
        logger.info("║  Kurumsal Proxy Güvenlik Sistemi     ║");
        logger.info("╠══════════════════════════════════════╣");
        logger.info("║  Sürüm   : v{}                  ║", VERSION);
        logger.info("║  Modüller: {}                      ║", moduleCount);
        logger.info("╚══════════════════════════════════════╝");
    }

    public static String getStartupInfo() {
        return "AtomGuard Velocity v" + VERSION + " (" + BUILD_DATE + ") - " + AUTHOR;
    }
}
