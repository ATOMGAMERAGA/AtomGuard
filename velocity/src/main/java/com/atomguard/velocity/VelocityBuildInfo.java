package com.atomguard.velocity;

import org.slf4j.Logger;

public final class VelocityBuildInfo {

    public static final String VERSION = "1.2.3";
    public static final String BUILD_DATE = "2026-02-27";
    public static final String AUTHOR = "AtomGuard Team";

    private VelocityBuildInfo() {}

    public static void printBanner(Logger logger, int moduleCount) {
        logger.info("╔══════════════════════════════════════╗");
        logger.info("║        AtomGuard Velocity            ║");
        logger.info("║  Enterprise Proxy Security System    ║");
        logger.info("╠══════════════════════════════════════╣");
        logger.info("║  Version : v{}",   String.format("%-27s║", VERSION));
        logger.info("║  Modules : {}",    String.format("%-27s║", moduleCount));
        logger.info("╚══════════════════════════════════════╝");
    }

    public static String getStartupInfo() {
        return "AtomGuard Velocity v" + VERSION + " (" + BUILD_DATE + ") - " + AUTHOR;
    }
}
