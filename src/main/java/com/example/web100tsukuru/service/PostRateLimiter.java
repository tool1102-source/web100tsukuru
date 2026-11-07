package com.example.web100tsukuru.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 同じ接続元からの連続投稿を制限する（スパム対策）。
 * 例: 10分間に3件まで。アプリの再起動でリセットされる簡易版。
 */
@Component
public class PostRateLimiter {

    private final int maxPosts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> history = new HashMap<>();

    @Autowired
    public PostRateLimiter(@Value("${app.post-limit.max:3}") int maxPosts,
                           @Value("${app.post-limit.window:10m}") Duration window) {
        this(maxPosts, window, Clock.systemUTC());
    }

    PostRateLimiter(int maxPosts, Duration window, Clock clock) {
        this.maxPosts = maxPosts;
        this.window = window;
        this.clock = clock;
    }

    /** 投稿してよければ true を返し、その投稿を記録する */
    public synchronized boolean tryAcquire(String clientKey) {
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);

        // 古い記録を捨てる（メモリが増え続けないように）
        history.values().forEach(times -> {
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.pollFirst();
            }
        });
        history.values().removeIf(Deque::isEmpty);

        Deque<Instant> times = history.computeIfAbsent(clientKey, k -> new ArrayDeque<>());
        if (times.size() >= maxPosts) {
            return false;
        }
        times.addLast(now);
        return true;
    }
}
