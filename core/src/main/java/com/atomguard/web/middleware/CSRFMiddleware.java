package com.atomguard.web.middleware;

import com.sun.net.httpserver.HttpExchange;

/**
 * CSRF (Cross-Site Request Forgery) koruma middleware'i.
 * POST isteklerinde Origin/Referer header dogrulamasi yapar.
 *
 * @author AtomGuard Team
 * @version 2.0.0
 */
public class CSRFMiddleware {

    /**
     * POST isteklerinde CSRF korumasini kontrol eder.
     *
     * @param exchange HTTP exchange
     * @return Gecerli origin/referrer varsa veya POST degilse true
     */
    public boolean checkCSRF(HttpExchange exchange) {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }

        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String referer = exchange.getRequestHeaders().getFirst("Referer");
        String host = exchange.getRequestHeaders().getFirst("Host");

        boolean originOk = origin != null && isOriginAllowed(origin, host);
        boolean refererOk = referer != null && isRefererAllowed(referer, host);

        return originOk || refererOk;
    }

    /**
     * Origin header'in izin verilen kaynaklardan gelip gelmedigini kontrol eder.
     */
    private boolean isOriginAllowed(String origin, String host) {
        if (origin == null) return false;
        if (host != null && (origin.equals("http://" + host) || origin.equals("https://" + host))) return true;
        return origin.equals("http://localhost") || origin.equals("https://localhost")
            || origin.equals("http://127.0.0.1") || origin.equals("https://127.0.0.1")
            || origin.startsWith("http://localhost:") || origin.startsWith("https://localhost:")
            || origin.startsWith("http://127.0.0.1:") || origin.startsWith("https://127.0.0.1:");
    }

    /**
     * Referer header'inin izin verilen host ile eslesip eslesmedigini kontrol eder.
     */
    private boolean isRefererAllowed(String referer, String host) {
        if (referer == null) return false;
        if (host != null && (referer.startsWith("http://" + host + "/") || referer.startsWith("https://" + host + "/"))) return true;
        return referer.startsWith("http://localhost") || referer.startsWith("https://localhost")
            || referer.startsWith("http://127.0.0.1") || referer.startsWith("https://127.0.0.1");
    }
}
