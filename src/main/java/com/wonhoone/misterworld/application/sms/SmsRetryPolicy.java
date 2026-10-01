package com.wonhoone.misterworld.application.sms;

import java.time.Duration;

public final class SmsRetryPolicy {
    private final long initialSeconds;
    private final long maxSeconds;
    public SmsRetryPolicy(long initialSeconds, long maxSeconds) {
        if (initialSeconds <= 0 || maxSeconds < initialSeconds) throw new IllegalArgumentException("Invalid retry delays");
        this.initialSeconds = initialSeconds; this.maxSeconds = maxSeconds;
    }
    public Duration delayAfterFailure(long attemptCount) {
        if (attemptCount < 1) throw new IllegalArgumentException("Failure attempt must be positive");
        long delay = initialSeconds;
        // At most 63 iterations, regardless of attempt count; never overflow before applying the cap.
        for (long attempt = 1; attempt < attemptCount && delay < maxSeconds; attempt++) {
            delay = delay > maxSeconds / 2 ? maxSeconds : Math.min(maxSeconds, delay * 2);
        }
        return Duration.ofSeconds(delay);
    }
}
