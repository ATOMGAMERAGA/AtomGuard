package com.atomguard.velocity.module.antiddos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        // capacity=5, refill=1 token/second
        limiter = new RateLimiter(5, 1);
    }

    // ── Under limit ─────────────────────────────────────────────

    @Nested
    class UnderLimit {

        @Test
        void acquireUpToCapacity_allSucceed() {
            for (int i = 0; i < 5; i++) {
                assertThat(limiter.tryAcquire("10.0.0.1"))
                        .as("acquire #%d should succeed", i + 1)
                        .isTrue();
            }
        }

        @Test
        void getTokens_decreasesAfterAcquire() {
            assertThat(limiter.getTokens("10.0.0.1")).isEqualTo(5);

            limiter.tryAcquire("10.0.0.1");

            assertThat(limiter.getTokens("10.0.0.1")).isEqualTo(4);
        }
    }

    // ── Over limit ──────────────────────────────────────────────

    @Nested
    class OverLimit {

        @Test
        void exhaustTokens_nextAcquireFails() {
            for (int i = 0; i < 5; i++) {
                limiter.tryAcquire("10.0.0.1");
            }

            assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
        }

        @Test
        void getTokens_returnsZeroWhenExhausted() {
            for (int i = 0; i < 5; i++) {
                limiter.tryAcquire("10.0.0.1");
            }

            assertThat(limiter.getTokens("10.0.0.1")).isZero();
        }
    }

    // ── Window expiry / refill ──────────────────────────────────

    @Nested
    class WindowExpiry {

        @Test
        void tokensRefillAfterWaiting() throws InterruptedException {
            // Use a high refill rate so we don't wait long
            RateLimiter fastRefill = new RateLimiter(5, 100); // 100 tokens/sec

            // Exhaust all tokens
            for (int i = 0; i < 5; i++) {
                fastRefill.tryAcquire("10.0.0.1");
            }
            assertThat(fastRefill.tryAcquire("10.0.0.1")).isFalse();

            // Wait for refill (100 tokens/sec = 1 token per 10ms)
            Thread.sleep(60);

            assertThat(fastRefill.tryAcquire("10.0.0.1")).isTrue();
        }

        @Test
        void tokensDoNotExceedCapacity() throws InterruptedException {
            RateLimiter fastRefill = new RateLimiter(5, 1000);

            // Wait to allow many refills
            Thread.sleep(50);

            // Tokens should be capped at capacity
            assertThat(fastRefill.getTokens("10.0.0.1")).isEqualTo(5);
        }
    }

    // ── Per-IP isolation ────────────────────────────────────────

    @Nested
    class PerIpIsolation {

        @Test
        void differentIps_haveIndependentBuckets() {
            // Exhaust tokens for IP-1
            for (int i = 0; i < 5; i++) {
                limiter.tryAcquire("10.0.0.1");
            }
            assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

            // IP-2 should still have full capacity
            assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();
            assertThat(limiter.getTokens("10.0.0.2")).isEqualTo(4);
        }

        @Test
        void getTokens_unknownKey_returnsCapacity() {
            assertThat(limiter.getTokens("never-seen")).isEqualTo(5);
        }
    }

    // ── Reset ───────────────────────────────────────────────────

    @Nested
    class Reset {

        @Test
        void reset_restoresFullCapacity() {
            for (int i = 0; i < 5; i++) {
                limiter.tryAcquire("10.0.0.1");
            }
            assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();

            limiter.reset("10.0.0.1");

            assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
            assertThat(limiter.getTokens("10.0.0.1")).isEqualTo(4);
        }

        @Test
        void reset_doesNotAffectOtherKeys() {
            limiter.tryAcquire("10.0.0.1");
            limiter.tryAcquire("10.0.0.2");

            limiter.reset("10.0.0.1");

            // IP-1 reset to full capacity
            assertThat(limiter.getTokens("10.0.0.1")).isEqualTo(5);
            // IP-2 still has consumed 1 token
            assertThat(limiter.getTokens("10.0.0.2")).isEqualTo(4);
        }
    }

    // ── Multiple token acquire ──────────────────────────────────

    @Nested
    class MultipleTokenAcquire {

        @Test
        void acquireMultipleTokens_succeeds() {
            assertThat(limiter.tryAcquire("10.0.0.1", 3)).isTrue();
            assertThat(limiter.getTokens("10.0.0.1")).isEqualTo(2);
        }

        @Test
        void acquireMoreThanAvailable_fails() {
            limiter.tryAcquire("10.0.0.1", 3);

            assertThat(limiter.tryAcquire("10.0.0.1", 3)).isFalse();
            // Tokens should not be consumed on failure
            assertThat(limiter.getTokens("10.0.0.1")).isEqualTo(2);
        }

        @Test
        void acquireExactCapacity_succeeds() {
            assertThat(limiter.tryAcquire("10.0.0.1", 5)).isTrue();
            assertThat(limiter.getTokens("10.0.0.1")).isZero();
        }

        @Test
        void acquireMoreThanCapacity_fails() {
            assertThat(limiter.tryAcquire("10.0.0.1", 6)).isFalse();
        }
    }

    // ── Concurrent access ───────────────────────────────────────

    @Nested
    class ConcurrentAccess {

        @Test
        void multipleThreads_totalAcquiresDoNotExceedCapacity() throws Exception {
            RateLimiter concurrentLimiter = new RateLimiter(100, 0);
            int threadCount = 10;
            int acquiresPerThread = 20;

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);

            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < acquiresPerThread; i++) {
                        if (concurrentLimiter.tryAcquire("shared-key")) {
                            successCount.incrementAndGet();
                        }
                    }
                }));
            }

            // Release all threads at once
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get();
            }
            executor.shutdown();

            // Total successful acquires must equal capacity (100)
            // since refill=0 and total attempts (200) > capacity
            assertThat(successCount.get()).isEqualTo(100);
        }
    }

    // ── Cleanup ─────────────────────────────────────────────────

    @Nested
    class Cleanup {

        @Test
        void cleanup_doesNotThrow() {
            limiter.tryAcquire("10.0.0.1");
            limiter.tryAcquire("10.0.0.2");

            // Should not throw
            limiter.cleanup();
        }
    }
}
