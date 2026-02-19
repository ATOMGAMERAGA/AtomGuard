package com.atomguard.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TokenBucketTest {

    @Test
    void testInitialCapacity() {
        TokenBucket bucket = new TokenBucket(10, 2);
        assertEquals(10, bucket.getTokens());
        assertEquals(10, bucket.getCapacity());
        assertEquals(2, bucket.getRefillPerSecond());
    }

    @Test
    void testConsumption() {
        TokenBucket bucket = new TokenBucket(10, 2);
        long remaining = bucket.tryConsume();
        assertEquals(9, remaining);
        assertEquals(9, bucket.getTokens());
    }

    @Test
    void testExhaustion() {
        TokenBucket bucket = new TokenBucket(5, 1);
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryConsume() >= 0);
        }
        assertTrue(bucket.tryConsume() < 0);
    }

    @Test
    void testRefill() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(10, 5);
        
        // Consume all
        for (int i = 0; i < 10; i++) bucket.tryConsume();
        assertEquals(0, bucket.getTokens());

        // Wait for refill (at least 1 second)
        Thread.sleep(1100);

        // Should have refilled by at least 5 tokens
        assertTrue(bucket.getTokens() >= 5);
    }

    @Test
    void testReset() {
        TokenBucket bucket = new TokenBucket(10, 2);
        for (int i = 0; i < 5; i++) bucket.tryConsume();
        assertEquals(5, bucket.getTokens());
        
        bucket.reset();
        assertEquals(10, bucket.getTokens());
    }

    @Test
    void testInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(10, 0));
    }
}
