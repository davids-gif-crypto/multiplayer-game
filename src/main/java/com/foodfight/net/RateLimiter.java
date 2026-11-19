package com.foodfight.net;

/**
 * Stops one browser from flooding the server with messages.
 *
 * It counts messages in 1 second windows. The first {@code maxPerSecond}
 * messages in a window are allowed. After that, messages are dropped until
 * the next window starts. We only want to warn the browser once per window,
 * so the first dropped message says WARN and the rest say DROP.
 */
public class RateLimiter {

    /** What to do with one incoming message. */
    public enum Result { ALLOW, WARN_AND_DROP, DROP }

    private static final long WINDOW_MILLIS = 1000;

    private final int maxPerSecond;
    private long windowStart = Long.MIN_VALUE;
    private int count;

    public RateLimiter(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
    }

    /**
     * Records one message arriving at time {@code nowMillis} and says whether
     * to allow it. Synchronized in case two threads ever call it at once.
     */
    public synchronized Result check(long nowMillis) {
        if (windowStart == Long.MIN_VALUE || nowMillis - windowStart >= WINDOW_MILLIS) {
            // A new 1 second window starts now.
            windowStart = nowMillis;
            count = 0;
        }
        count++;
        if (count <= maxPerSecond) {
            return Result.ALLOW;
        }
        return (count == maxPerSecond + 1) ? Result.WARN_AND_DROP : Result.DROP;
    }
}
