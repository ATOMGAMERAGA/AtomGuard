package com.atomguard.web.handler;

import com.atomguard.AtomGuard;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;

/**
 * /api/events/stream endpoint — Server-Sent Events ile gercek zamanli bildirimler.
 */
public class SSEHandler implements HttpHandler {

    private final AtomGuard plugin;
    private final List<OutputStream> clients = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatExecutor;
    private ScheduledFuture<?> heartbeatTask;

    public SSEHandler(AtomGuard plugin) {
        this.plugin = plugin;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AtomGuard-SSE-Heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        int heartbeatSec = plugin.getConfig().getInt("web-panel.sse.heartbeat-saniye", 30);
        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(this::sendHeartbeat,
                heartbeatSec, heartbeatSec, TimeUnit.SECONDS);
    }

    public void stop() {
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        heartbeatExecutor.shutdown();
        for (OutputStream os : clients) {
            try { os.close(); } catch (IOException ignored) {}
        }
        clients.clear();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        OutputStream os = exchange.getResponseBody();
        clients.add(os);

        // Send initial connection event
        try {
            os.write("event: connected\ndata: {\"status\":\"ok\"}\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            clients.remove(os);
        }
    }

    /**
     * Tum bagli istemcilere olay gonderir.
     */
    public void pushEvent(String eventType, String jsonData) {
        String message = "event: " + eventType + "\ndata: " + jsonData + "\n\n";
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);

        for (OutputStream os : clients) {
            try {
                os.write(bytes);
                os.flush();
            } catch (IOException e) {
                clients.remove(os);
            }
        }
    }

    private void sendHeartbeat() {
        byte[] heartbeat = ": heartbeat\n\n".getBytes(StandardCharsets.UTF_8);
        for (OutputStream os : clients) {
            try {
                os.write(heartbeat);
                os.flush();
            } catch (IOException e) {
                clients.remove(os);
            }
        }
    }

    public int getConnectedClients() {
        return clients.size();
    }
}
