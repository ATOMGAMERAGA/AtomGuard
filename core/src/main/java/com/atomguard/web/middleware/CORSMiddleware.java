package com.atomguard.web.middleware;

import com.sun.net.httpserver.HttpExchange;

/**
 * CORS (Cross-Origin Resource Sharing) header yonetimi middleware'i.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class CORSMiddleware {

    private final String allowedOrigin;

    public CORSMiddleware(String corsOrigin) {
        this.allowedOrigin = (corsOrigin == null || corsOrigin.isBlank()) ? "null" : corsOrigin;
    }

    /**
     * CORS header'larini ekler.
     *
     * @param exchange HTTP exchange
     */
    public void applyCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", allowedOrigin);
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    /**
     * Preflight OPTIONS istegini isler.
     *
     * @param exchange HTTP exchange
     * @return OPTIONS istegiyse true (caller 204 donmeli)
     */
    public boolean isPreflightRequest(HttpExchange exchange) {
        return "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod());
    }
}
