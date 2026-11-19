package com.foodfight.net;

import com.foodfight.net.RateLimiter.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RateLimiterTest {

    @Test
    void allowsUpToTheLimitThenWarnsOnceThenDrops() {
        RateLimiter limiter = new RateLimiter(120);
        for (int i = 0; i < 120; i++) {
            assertEquals(Result.ALLOW, limiter.check(1000));
        }
        assertEquals(Result.WARN_AND_DROP, limiter.check(1500));
        assertEquals(Result.DROP, limiter.check(1600));
        assertEquals(Result.DROP, limiter.check(1999));
    }

    @Test
    void aNewSecondStartsFresh() {
        RateLimiter limiter = new RateLimiter(2);
        limiter.check(0);
        limiter.check(10);
        assertEquals(Result.WARN_AND_DROP, limiter.check(20));
        assertEquals(Result.ALLOW, limiter.check(1000));
    }
}
