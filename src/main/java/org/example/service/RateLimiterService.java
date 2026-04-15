package org.example.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.example.config.AppConfig;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiterService {
    private static final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public static boolean tryConsume(String ip) {
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createNewBucket());
        return bucket.tryConsume(1);
    }

    private static Bucket createNewBucket() {
        // Берем значения из живого конфига
        var config = AppConfig.getConfig().getRatelimit();

        Refill refill = Refill.greedy(config.getRefillTokens(), Duration.ofSeconds(1));
        Bandwidth limit = Bandwidth.classic(config.getCapacity(), refill);
        return Bucket.builder().addLimit(limit).build();
    }

    // Метод для полной очистки лимитов (вызывай его при обновлении конфига в watchForChanges)
    public static void clear() {
        buckets.clear();
    }
}
