package com.wonhoone.misterworld.application.sms;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SmsRetryPolicyTests {
    @Test void backoffDoublesUntilConfiguredMaximum() {
        var policy = new SmsRetryPolicy(30, 1800);
        assertThat(java.util.stream.LongStream.rangeClosed(1, 8).map(i -> policy.delayAfterFailure(i).toSeconds()))
                .containsExactly(30L, 60L, 120L, 240L, 480L, 960L, 1800L, 1800L);
    }
    @Test void enormousAttemptCountDoesNotOverflowOrLoopIndefinitely() {
        assertThat(new SmsRetryPolicy(30, 1800).delayAfterFailure(Long.MAX_VALUE)).isEqualTo(Duration.ofSeconds(1800));
    }
    @Test void doublingNearLongLimitCapsWithoutOverflow() {
        assertThat(new SmsRetryPolicy(Long.MAX_VALUE / 2 + 1, Long.MAX_VALUE).delayAfterFailure(2))
                .isEqualTo(Duration.ofSeconds(Long.MAX_VALUE));
    }
    @Test void invalidDelaysOrAttemptAreRejected() {
        assertThatThrownBy(() -> new SmsRetryPolicy(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmsRetryPolicy(10, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmsRetryPolicy(30, 1800).delayAfterFailure(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
