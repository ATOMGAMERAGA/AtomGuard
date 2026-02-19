package com.atomguard.velocity.module.antivpn;

import java.util.List;

/**
 * VPN/Proxy tespit sonucu - konsensüs tabanlı çok sağlayıcı sistemi.
 */
public class VPNCheckResult {

    private final boolean isVPN;
    private final int confidenceScore; // 0-100
    private final List<String> detectedBy;
    private final String method;

    public VPNCheckResult(boolean isVPN, int confidenceScore, List<String> detectedBy, String method) {
        this.isVPN = isVPN;
        this.confidenceScore = confidenceScore;
        this.detectedBy = List.copyOf(detectedBy);
        this.method = method;
    }

    public boolean isVPN() { return isVPN; }
    public int getConfidenceScore() { return confidenceScore; }
    public List<String> getDetectedBy() { return detectedBy; }
    public String getMethod() { return method; }

    @Override
    public String toString() {
        return "VPNCheckResult{isVPN=" + isVPN + ", confidence=" + confidenceScore
                + ", detectedBy=" + detectedBy + ", method='" + method + "'}";
    }
}
