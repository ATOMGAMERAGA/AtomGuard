package com.atomguard.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bağlantı kontrol sonucu arayüzü.
 * Bir kontrolün izin veya red kararını temsil eder.
 *
 * @author AtomGuard Team
 * @since 2.0.0
 */
public interface ICheckResult {

    /**
     * Bağlantının reddedilip reddedilmediğini kontrol eder.
     *
     * @return Reddedildiyse true
     */
    boolean isDenied();

    /**
     * Red nedeni.
     *
     * @return Red nedeni veya null (izin verildiyse)
     */
    @Nullable
    String getReason();

    /**
     * İzin verilmiş sonuç oluşturur.
     *
     * @return İzin sonucu
     */
    static ICheckResult allowed() {
        return AllowedResult.INSTANCE;
    }

    /**
     * Reddedilmiş sonuç oluşturur.
     *
     * @param reason Red nedeni
     * @return Red sonucu
     */
    static ICheckResult denied(@NotNull String reason) {
        return new DeniedResult(reason);
    }
}

/**
 * İzin verilmiş sonuç implementasyonu.
 */
final class AllowedResult implements ICheckResult {

    static final AllowedResult INSTANCE = new AllowedResult();

    private AllowedResult() {
    }

    @Override
    public boolean isDenied() {
        return false;
    }

    @Override
    @Nullable
    public String getReason() {
        return null;
    }

    @Override
    public String toString() {
        return "CheckResult[ALLOWED]";
    }
}

/**
 * Reddedilmiş sonuç implementasyonu.
 */
final class DeniedResult implements ICheckResult {

    private final String reason;

    DeniedResult(@NotNull String reason) {
        this.reason = reason;
    }

    @Override
    public boolean isDenied() {
        return true;
    }

    @Override
    @NotNull
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "CheckResult[DENIED: " + reason + "]";
    }
}
