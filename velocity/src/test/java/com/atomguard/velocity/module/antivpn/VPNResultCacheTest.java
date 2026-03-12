package com.atomguard.velocity.module.antivpn;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VPNResultCacheTest {

    private VPNResultCache cache;

    @BeforeEach
    void setUp() {
        // 1-hour TTL, default max size
        cache = new VPNResultCache(3_600_000);
    }

    // ── Put and retrieve ───────────────────────────────────────────

    @Test
    void putAndGet_returnsCachedResult() {
        cache.put("10.0.0.1", true, "proxycheck");

        VPNResultCache.CacheResult result = cache.get("10.0.0.1");

        assertThat(result).isNotNull();
        assertThat(result.isVPN()).isTrue();
        assertThat(result.provider()).isEqualTo("proxycheck");
    }

    @Test
    void putNonVPN_returnsCorrectFlag() {
        cache.put("10.0.0.2", false, "ip-api");

        VPNResultCache.CacheResult result = cache.get("10.0.0.2");

        assertThat(result).isNotNull();
        assertThat(result.isVPN()).isFalse();
        assertThat(result.provider()).isEqualTo("ip-api");
    }

    // ── Cache miss ─────────────────────────────────────────────────

    @Test
    void get_unknownIp_returnsNull() {
        VPNResultCache.CacheResult result = cache.get("10.0.0.99");

        assertThat(result).isNull();
    }

    @Test
    void contains_unknownIp_returnsFalse() {
        assertThat(cache.contains("10.0.0.99")).isFalse();
    }

    // ── Contains ───────────────────────────────────────────────────

    @Test
    void contains_afterPut_returnsTrue() {
        cache.put("10.0.0.5", false, "test");

        assertThat(cache.contains("10.0.0.5")).isTrue();
    }

    // ── Expiry ─────────────────────────────────────────────────────

    @Test
    void expiredEntry_get_returnsNull() {
        // Use a 1ms TTL so it expires almost immediately
        VPNResultCache shortLivedCache = new VPNResultCache(1);
        shortLivedCache.put("10.0.0.3", true, "test");

        // Wait for expiry
        sleepSafe(10);

        assertThat(shortLivedCache.get("10.0.0.3")).isNull();
    }

    @Test
    void expiredEntry_contains_returnsFalse() {
        VPNResultCache shortLivedCache = new VPNResultCache(1);
        shortLivedCache.put("10.0.0.4", true, "test");

        sleepSafe(10);

        assertThat(shortLivedCache.contains("10.0.0.4")).isFalse();
    }

    // ── Max size eviction ──────────────────────────────────────────

    @Test
    void maxSizeEviction_evictsOldestEntry() {
        VPNResultCache tinyCache = new VPNResultCache(60_000, 3);

        tinyCache.put("ip-1", false, "a");
        sleepSafe(5); // ensure distinct timestamps
        tinyCache.put("ip-2", false, "b");
        sleepSafe(5);
        tinyCache.put("ip-3", false, "c");

        assertThat(tinyCache.size()).isEqualTo(3);

        // Adding a 4th entry should evict the oldest (ip-1)
        tinyCache.put("ip-4", false, "d");

        assertThat(tinyCache.size()).isEqualTo(3);
        assertThat(tinyCache.get("ip-1")).isNull();
        assertThat(tinyCache.get("ip-4")).isNotNull();
    }

    @Test
    void maxSizeEviction_existingKeyDoesNotEvict() {
        VPNResultCache tinyCache = new VPNResultCache(60_000, 2);

        tinyCache.put("ip-1", false, "a");
        tinyCache.put("ip-2", false, "b");

        // Updating ip-1 (existing key) should NOT trigger eviction
        tinyCache.put("ip-1", true, "updated");

        assertThat(tinyCache.size()).isEqualTo(2);
        assertThat(tinyCache.get("ip-1").isVPN()).isTrue();
        assertThat(tinyCache.get("ip-2")).isNotNull();
    }

    // ── Cleanup ────────────────────────────────────────────────────

    @Test
    void cleanup_removesExpiredEntries() {
        VPNResultCache shortLivedCache = new VPNResultCache(1, 100);
        shortLivedCache.put("ip-a", false, "test");
        shortLivedCache.put("ip-b", true, "test");

        sleepSafe(10);

        shortLivedCache.cleanup();

        assertThat(shortLivedCache.size()).isZero();
    }

    @Test
    void cleanup_keepsNonExpiredEntries() {
        cache.put("ip-long", false, "test");

        cache.cleanup();

        assertThat(cache.size()).isEqualTo(1);
        assertThat(cache.get("ip-long")).isNotNull();
    }

    // ── Size ───────────────────────────────────────────────────────

    @Test
    void size_reflectsNumberOfEntries() {
        assertThat(cache.size()).isZero();

        cache.put("a", false, "p");
        cache.put("b", true, "p");

        assertThat(cache.size()).isEqualTo(2);
    }

    // ── Helpers ────────────────────────────────────────────────────

    private void sleepSafe(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
