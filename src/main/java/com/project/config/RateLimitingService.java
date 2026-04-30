package com.project.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingService {

    private final Map<String, Bucket> loginIpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginAccountBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerIpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> forgotIpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> resetIpBuckets = new ConcurrentHashMap<>();

    public boolean allowLoginFromIp(String ipAddress) {
        return loginIpBuckets.computeIfAbsent("login-ip:" + ipAddress, key -> newLoginIpBucket())
                .tryConsume(1);
    }

    public boolean allowLoginForAccount(String email) {
        return loginAccountBuckets.computeIfAbsent("login-account:" + email, key -> newLoginAccountBucket())
                .tryConsume(1);
    }

    public boolean allowRegistrationFromIp(String ipAddress) {
        return registerIpBuckets.computeIfAbsent("register-ip:" + ipAddress, key -> newRegistrationBucket())
                .tryConsume(1);
    }

    public boolean allowForgotPasswordFromIp(String ipAddress) {
        return forgotIpBuckets.computeIfAbsent("forgot-ip:" + ipAddress, key -> newForgotBucket())
                .tryConsume(1);
    }

    public boolean allowResetFromIp(String ipAddress) {
        return resetIpBuckets.computeIfAbsent("reset-ip:" + ipAddress, key -> newResetBucket())
                .tryConsume(1);
    }

    public void clearAll() {
        loginIpBuckets.clear();
        loginAccountBuckets.clear();
        registerIpBuckets.clear();
        forgotIpBuckets.clear();
        resetIpBuckets.clear();
    }

    private Bucket newLoginIpBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1))))
                .addLimit(Bandwidth.classic(50, Refill.intervally(50, Duration.ofHours(1))))
                .build();
    }

    private Bucket newLoginAccountBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(15))))
                .build();
    }

    private Bucket newRegistrationBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofHours(1))))
                .build();
    }

    private Bucket newForgotBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.greedy(5, Duration.ofHours(1))))
                .build();
    }

    private Bucket newResetBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(10, Refill.greedy(10, Duration.ofHours(1))))
                .build();
    }
}
