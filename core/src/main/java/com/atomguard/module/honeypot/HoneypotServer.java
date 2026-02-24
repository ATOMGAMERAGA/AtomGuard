package com.atomguard.module.honeypot;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tek bir honeypot TCP portu için sunucu döngüsü.
 *
 * <p>Her port için ayrı bir {@code HoneypotServer} örneği oluşturulur.
 * Bağlantı kabulü ayrı bir kabul iş parçacığında; bağlantı işleme ise
 * 2 iş parçacıklı bir havuzda gerçekleşir. Aktif bağlantı eşiği
 * aşılırsa yeni bağlantılar hemen kapatılır.</p>
 */
public class HoneypotServer implements Runnable {

    private final int port;
    private final HoneypotModule module;

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private final ExecutorService connectionPool;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    /** IP -> bu portla kaç kez bağlantı kurduğu */
    private final ConcurrentHashMap<String, AtomicInteger> connectionCounts = new ConcurrentHashMap<>();

    public HoneypotServer(int port, HoneypotModule module) {
        this.port = port;
        this.module = module;
        this.connectionPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AtomGuard-Honeypot-Worker-" + port);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Sunucu soketini açar ve kabul döngüsünü başlatır.
     * Port açılamazsa (örn. yetki yoksa) uyarı loglanır ve sessizce atlanır.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            serverSocket.setSoTimeout(100); // accept() zaman aşımı — çalışma durumu kontrolü için

            running = true;

            Thread acceptThread = new Thread(this, "AtomGuard-Honeypot-Accept-" + port);
            acceptThread.setDaemon(true);
            acceptThread.start();

            module.getPlugin().getLogger().info("[Honeypot] Port " + port + " dinleniyor.");

        } catch (IOException e) {
            module.getPlugin().getLogger().warning(
                    "[Honeypot] Port " + port + " açılamadı: " + e.getMessage()
                    + " (yetki veya port çakışması olabilir)");
        }
    }

    /**
     * Sunucuyu durdurur: bağlantı havuzunu kapatır ve soketi serbest bırakır.
     */
    public void stop() {
        running = false;
        connectionPool.shutdown();
        try {
            connectionPool.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        module.getPlugin().getLogger().info("[Honeypot] Port " + port + " kapatıldı.");
    }

    /**
     * Kabul döngüsü — bağlantıları kabul eder ve havuza gönderir.
     */
    @Override
    public void run() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();

                // Eşzamanlı bağlantı sınırını kontrol et
                if (activeConnections.get() >= module.getMaxConcurrentConnections()) {
                    try { socket.close(); } catch (IOException ignored) {}
                    continue;
                }

                connectionPool.submit(() -> handleConnection(socket));

            } catch (SocketTimeoutException ignored) {
                // Normal — running bayrağını kontrol etmek için
            } catch (IOException e) {
                if (running) {
                    module.getPlugin().getLogger().warning(
                            "[Honeypot] Port " + port + " accept hatası: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Tek bir bağlantıyı işler: protokol tespiti, sayaç artışı ve modüle bildirim.
     *
     * @param socket İşlenecek istemci soketi
     */
    private void handleConnection(Socket socket) {
        activeConnections.incrementAndGet();
        try {
            String ip = socket.getInetAddress().getHostAddress();
            socket.setSoTimeout(module.getConnectionTimeoutSeconds() * 1000);

            // Protokol tespiti: önce SLP dene
            String protocol = "TCP_RAW";
            if (module.isFakeMotdEnabled() && module.getFakeMotdHandler() != null) {
                try {
                    boolean handled = module.getFakeMotdHandler().handle(socket);
                    if (handled) protocol = "SLP";
                } catch (Exception ignored) {
                    // SLP başarısız, TCP_RAW olarak devam et
                }
            }

            // IP başına bağlantı sayacını artır
            int count = connectionCounts.computeIfAbsent(ip, k -> new AtomicInteger())
                                        .incrementAndGet();

            module.onHoneypotConnection(ip, port, protocol, count);

        } catch (Exception ignored) {
            // Bağlantı aniden kapanmış veya zaman aşımı — normal akış
        } finally {
            activeConnections.decrementAndGet();
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /** Bu sunucunun dinlediği port numarası */
    public int getPort() { return port; }

    /** Sunucu şu anda bağlantı kabul ediyorsa {@code true} */
    public boolean isRunning() { return running; }

    /** Anlık eşzamanlı bağlantı sayısı */
    public int getActiveConnections() { return activeConnections.get(); }
}
