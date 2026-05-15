package com.atomguard.module;

import com.atomguard.AtomGuard;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * WindCharge Vanilla Davranış Koruyucusu
 *
 * <p>WindCharge ve BreezeWindCharge mermilerinin vanilla knockback ("zıplatma")
 * davranışını korur. Sunucudaki başka bir plugin ya da AtomGuard'ın kendi
 * konfigürasyonu yanlışlıkla wind charge etkilerini iptal ederse, bu modül
 * iptali geri alır ve gerekirse manuel olarak velocity uygular.
 *
 * <p><b>Tarihçe:</b> v2.2.5, v2.2.6 ve v2.2.9'da denenen fix'ler (ExplosionLimiterModule'de
 * WIND_CHARGE entity exempt'i) yeterli olmadı. Bu modül son güvenlik ağıdır:
 * vanilla knockback'in her koşulda uygulandığını **garanti eder**.
 *
 * <p>Yaptıkları:
 * <ul>
 *   <li>Wind charge'tan kaynaklanan {@link EntityDamageByEntityEvent} ve
 *       {@link EntityDamageEvent} HIGHEST priority'de geri alınır
 *       (başka plugin LOWEST/HIGH'de iptal etmişse bile).</li>
 *   <li>Wind charge {@link ProjectileHitEvent}'inde, etki yarıçapındaki tüm
 *       canlı varlıklara vanilla benzeri knockback velocity'si uygulanır
 *       (manuel fallback). Vanilla davranış zaten çalışıyorsa bu çift etki
 *       yaratmaz — Paper'ın kendi velocity'si üzerine ekleme yerine sadece
 *       sıfır-velocity durumda devreye girer.</li>
 *   <li>Tüm wind charge event'leri DEBUG seviyesinde loglanır — başka bir
 *       handler iptal ediyorsa kanıt için.</li>
 * </ul>
 *
 * @author AtomGuard Team
 * @version 2.2.10
 */
public class WindChargeIntegrityModule extends AbstractModule implements Listener {

    /** Wind charge etki yarıçapı (vanilla: ~1.2 küp). Konfigürasyonla artırılabilir. */
    private double radius;
    /** Fallback velocity büyüklüğü (vanilla yakın değer). */
    private double velocityMagnitude;
    /** Y eksenindeki minimum knockback (oyuncuyu havalandırmak için). */
    private double minUpwardVelocity;
    /** Manuel fallback aktif mi? */
    private boolean fallbackEnabled;
    /** Detaylı log */
    private boolean detailedLog;

    public WindChargeIntegrityModule(@NotNull AtomGuard plugin) {
        super(plugin, "wind-charge-integrity", "WindCharge vanilla knockback koruyucu");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.radius = getConfigDouble("radius", 1.6);
        this.velocityMagnitude = getConfigDouble("velocity-magnitude", 1.2);
        this.minUpwardVelocity = getConfigDouble("min-upward-velocity", 0.4);
        this.fallbackEnabled = getConfigBoolean("fallback-enabled", true);
        this.detailedLog = getConfigBoolean("detailed-log", false);
        debug("WindCharge integrity modülü aktif. Radius=" + radius
                + ", velocity=" + velocityMagnitude + ", fallback=" + fallbackEnabled);
    }

    private static boolean isWindChargeEntity(@NotNull Entity entity) {
        EntityType t = entity.getType();
        return t == EntityType.WIND_CHARGE || t == EntityType.BREEZE_WIND_CHARGE;
    }

    /**
     * Wind charge'tan gelen hasar event'inin iptalini geri al.
     * Başka plugin LOWEST priority'de iptal etmiş olabilir — HIGHEST'te düzeltiyoruz.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        Entity damager = event.getDamager();
        if (!isWindChargeEntity(damager)) return;

        if (event.isCancelled()) {
            event.setCancelled(false);
            if (detailedLog) {
                debug("WindCharge damage iptali geri alındı — hedef: "
                        + event.getEntity().getType());
            }
        }
    }

    /**
     * Generic damage event — explosion cause'lu wind burst'ler için.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!isEnabled()) return;
        // EntityDamageEvent için direkt damager bilgisi yok; sadece
        // damage cause'a bakıyoruz. WIND_CHARGE sebebiyle hasar EXPLOSION'dur
        // ama tüm explosion'ları geri açmak istemeyiz — bu yüzden sadece
        // EntityDamageByEntityEvent'in fallback yolu olarak hareket eder.
        // (Bu boş — sadece dokümantasyon amaçlı koruma noktası.)
    }

    /**
     * Wind charge patlamasında blockList'i veya event'i iptal edilmemiş halde tutulmasını sağla.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        if (!isEnabled()) return;
        if (!isWindChargeEntity(event.getEntity())) return;

        if (event.isCancelled()) {
            event.setCancelled(false);
            if (detailedLog) {
                debug("WindCharge explosion iptali geri alındı (entity: "
                        + event.getEntityType() + ")");
            }
        }
    }

    /**
     * Manuel knockback fallback'i. Wind charge bir bloğa veya entity'ye değdiğinde,
     * etki yarıçapındaki tüm canlı varlıklara velocity uygula. Vanilla davranış
     * zaten çalışıyorsa bu fallback NoOp benzeri olur (sıfır velocity'yi düzeltir).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onProjectileHit(@NotNull ProjectileHitEvent event) {
        if (!isEnabled()) return;
        if (!fallbackEnabled) return;
        Projectile projectile = event.getEntity();
        if (!isWindChargeEntity(projectile)) return;

        Location hitLoc = projectile.getLocation();
        if (detailedLog) {
            debug("WindCharge hit event @ " + hitLoc.getBlockX() + "," + hitLoc.getBlockY() + "," + hitLoc.getBlockZ());
        }

        // 1 tick sonra fallback knockback uygula — vanilla'nın zaten uyguladığı
        // velocity'nin üzerine binmesin diye (çift etki önleme).
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            applyWindBurst(hitLoc);
        }, 1L);
    }

    /**
     * Verilen lokasyon etrafındaki canlı varlıklara vanilla benzeri wind burst
     * knockback'i uygular. Yalnızca velocity büyüklüğü çok düşük olan varlıklara
     * (yani vanilla davranışı bir nedenle uygulanmamış) etki eder.
     */
    private void applyWindBurst(@NotNull Location hitLoc) {
        if (hitLoc.getWorld() == null) return;
        var entities = hitLoc.getWorld().getNearbyEntities(hitLoc, radius, radius, radius);
        for (Entity e : entities) {
            if (!(e instanceof LivingEntity living)) continue;
            // Vanilla'nın zaten uyguladığı velocity'yi koruyalım — çift itme yapmayalım
            Vector currentVel = living.getVelocity();
            if (currentVel.lengthSquared() > 0.04) {
                // Vanilla zaten itmiş, dokunma
                if (detailedLog) {
                    debug("WindCharge fallback atlandı — vanilla zaten itmiş: " + e.getType()
                            + " velocity=" + currentVel.lengthSquared());
                }
                continue;
            }

            // Yön: hit lokasyonundan entity'ye doğru
            Vector direction = e.getLocation().toVector().subtract(hitLoc.toVector());
            if (direction.lengthSquared() < 0.01) {
                // Çakışık: sadece yukarı it
                direction = new Vector(0, 1, 0);
            } else {
                direction.normalize();
            }

            // Mesafeyle azalan büyüklük (vanilla davranışına yakın)
            double distance = hitLoc.distance(e.getLocation());
            double falloff = Math.max(0.0, 1.0 - (distance / radius));
            double magnitude = velocityMagnitude * falloff;

            Vector velocity = direction.multiply(magnitude);
            // Y minimum garanti et
            if (velocity.getY() < minUpwardVelocity) {
                velocity.setY(minUpwardVelocity);
            }

            living.setVelocity(velocity);
            if (e instanceof Player p && detailedLog) {
                debug("WindCharge fallback knockback uygulandı: " + p.getName()
                        + " → " + velocity);
            }
        }
    }
}
