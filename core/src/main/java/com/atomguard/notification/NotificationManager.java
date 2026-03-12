package com.atomguard.notification;

import com.atomguard.AtomGuard;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * Multi-provider notification dispatcher with type-based filtering and exploit batching.
 *
 * <p>This manager routes {@link NotificationMessage} instances to one or more registered
 * {@link NotificationProvider} implementations (e.g., Discord, Telegram, Slack). Each
 * provider is registered with a set of {@link NotificationType} values it should receive,
 * enabling fine-grained control over which channels get which notifications.
 *
 * <p><b>Exploit batching:</b> Notifications of type {@link NotificationType#EXPLOIT_BLOCKED}
 * are buffered and flushed at a configurable interval (default 30 seconds) to prevent
 * notification spam during high-volume attacks. All other notification types are dispatched
 * immediately.
 *
 * <p><b>Thread safety:</b> Provider and filter maps use {@link ConcurrentHashMap}; the
 * exploit buffer uses a {@link ConcurrentLinkedQueue}. Batch flushing runs on a dedicated
 * daemon thread ({@code AtomGuard-Notification}).
 *
 * @see NotificationProvider
 * @see NotificationType
 */
public class NotificationManager {

    private final AtomGuard plugin;
    private final Map<String, NotificationProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, Set<NotificationType>> providerFilters = new ConcurrentHashMap<>();

    // Exploit bildirimleri icin toplama (batching) destegi
    private final ConcurrentLinkedQueue<NotificationMessage> exploitBuffer = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService batchExecutor;
    private ScheduledFuture<?> batchTask;
    private volatile int batchIntervalSeconds;

    public NotificationManager(AtomGuard plugin) {
        this.plugin = plugin;
        this.batchExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AtomGuard-Notification");
            t.setDaemon(true);
            return t;
        });
        this.batchIntervalSeconds = plugin.getConfig().getInt("bildirimler.toplama-suresi", 30);
    }

    /**
     * Bir bildirim saglayicisini, izin verilen bildirim turleriyle birlikte kaydeder.
     *
     * @param provider     bildirim saglayicisi
     * @param allowedTypes bu saglayicinin alacagi bildirim turleri
     */
    public void registerProvider(NotificationProvider provider, Set<NotificationType> allowedTypes) {
        providers.put(provider.getName(), provider);
        providerFilters.put(provider.getName(), Collections.unmodifiableSet(new HashSet<>(allowedTypes)));
    }

    /**
     * Bir bildirim saglayicisini kaldirir.
     *
     * @param name saglayici adi
     */
    public void unregisterProvider(String name) {
        NotificationProvider removed = providers.remove(name);
        providerFilters.remove(name);
        if (removed != null) {
            removed.stop();
        }
    }

    /**
     * Bildirim gonderir. Exploit bildirimleri toplama buffer'ina eklenir,
     * diger bildirimler hemen dagitilir.
     *
     * @param message gonderilecek bildirim
     */
    public void notify(NotificationMessage message) {
        if (message.type() == NotificationType.EXPLOIT_BLOCKED) {
            exploitBuffer.add(message);
            return;
        }
        dispatch(message);
    }

    /**
     * Mesaji filtreye uyan tum saglayicilara dagitir.
     */
    private void dispatch(NotificationMessage message) {
        for (var entry : providers.entrySet()) {
            NotificationProvider provider = entry.getValue();
            Set<NotificationType> allowed = providerFilters.get(entry.getKey());
            if (provider.isEnabled() && allowed != null && allowed.contains(message.type())) {
                try {
                    provider.sendAsync(message);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Bildirim gonderilemedi (" + provider.getName() + "): " + e.getMessage());
                }
            }
        }
    }

    /**
     * Biriktirilmis exploit bildirimlerini toplu olarak gonderir.
     */
    private void flushExploitBuffer() {
        if (exploitBuffer.isEmpty()) return;

        List<NotificationMessage> batch = new ArrayList<>();
        NotificationMessage msg;
        while ((msg = exploitBuffer.poll()) != null && batch.size() < 25) {
            batch.add(msg);
        }
        if (batch.isEmpty()) return;

        // Toplu mesaj olustur
        StringBuilder desc = new StringBuilder();
        for (NotificationMessage m : batch) {
            String line = m.title() + ": " + m.description();
            if (desc.length() + line.length() > 2000) break;
            desc.append(line).append("\n");
        }

        NotificationMessage batchMessage = NotificationMessage.of(
                NotificationType.EXPLOIT_BLOCKED,
                "Exploit Engelleme Raporu",
                desc.toString().trim() + "\n\nToplam: " + batch.size() + " engelleme",
                NotificationMessage.Severity.WARNING
        );

        dispatch(batchMessage);
    }

    /**
     * Tum saglayicilari baslatir ve toplama gorevini zamanlar.
     */
    public void start() {
        providers.values().forEach(provider -> {
            try {
                provider.start();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Bildirim saglayicisi baslatilamadi (" + provider.getName() + "): " + e.getMessage());
            }
        });

        // Exploit batching task
        if (batchTask != null) batchTask.cancel(false);
        batchTask = batchExecutor.scheduleAtFixedRate(this::flushExploitBuffer,
                batchIntervalSeconds, batchIntervalSeconds, TimeUnit.SECONDS);

        plugin.getLogger().info("NotificationManager baslatildi (" + providers.size() + " saglayici).");
    }

    /**
     * Tum saglayicilari durdurur ve kalan exploit bildirimlerini gonderir.
     */
    public void stop() {
        // Kalan exploit bildirimlerini gonder
        flushExploitBuffer();

        providers.values().forEach(provider -> {
            try {
                provider.stop();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Bildirim saglayicisi durdurulamadi (" + provider.getName() + "): " + e.getMessage());
            }
        });

        if (batchTask != null) {
            batchTask.cancel(false);
        }
        batchExecutor.shutdown();
        try {
            if (!batchExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            batchExecutor.shutdownNow();
        }
    }

    /**
     * Belirli bir saglayiciyi adi ile getirir.
     */
    public Optional<NotificationProvider> getProvider(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    /**
     * Kayitli tum saglayici adlarini dondurur.
     */
    public Set<String> getProviderNames() {
        return Collections.unmodifiableSet(providers.keySet());
    }

    /**
     * Yapistirmayi (config) yeniden yukler.
     */
    public void reload() {
        this.batchIntervalSeconds = plugin.getConfig().getInt("bildirimler.toplama-suresi", 30);
    }
}
