package com.example.web100tsukuru.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PostRateLimiterTests {

    /** 時刻を自由に進められる時計 */
    static class MovableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void 時間がたてば再び投稿できる() {
        MovableClock clock = new MovableClock();
        PostRateLimiter limiter = new PostRateLimiter(2, Duration.ofMinutes(10), clock);

        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        // 別の人は影響を受けない
        assertThat(limiter.tryAcquire("2.2.2.2")).isTrue();

        clock.now = clock.now.plus(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
    }
}
