package org.example.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.config.AppConfig;

import java.util.concurrent.TimeUnit;

public class ReputationManager {

    // Кэш для счетчика нарушений. Если IP не "хулиганит" 1 час — счетчик сбрасывается.
    private static final Cache<String, Integer> violations = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(10_000) // Защита от переполнения памяти
            .build();

    // Кэш для забаненных. Запись сама удалится через время бана.
    private static final Cache<String, Long> blacklist = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS) // Максимальный срок жизни записи в кэше
            .maximumSize(10_000)
            .build();

    public static boolean isBanned(String ip) {
        Long banUntil = blacklist.getIfPresent(ip);
        if (banUntil != null) {
            if (System.currentTimeMillis() < banUntil) {
                return true;
            }
            blacklist.invalidate(ip); // Снимаем бан вручную, если время вышло
        }
        return false;
    }

    public static void reportViolation(String ip) {
        var security = AppConfig.getConfig().getSecurity();

        // Атомарно увеличиваем счетчик
        Integer count = violations.get(ip, k -> 0) + 1;
        violations.put(ip, count);

        if (count >= security.getMaxViolations()) {
            long banDuration = TimeUnit.MINUTES.toMillis(security.getBanTimeMinutes());
            System.out.println("!!! [BAN] IP: " + ip + " на " + security.getBanTimeMinutes() + " мин.");
            blacklist.put(ip, System.currentTimeMillis() + banDuration);
        }
    }

    public static void clear() {
        violations.invalidateAll();
        blacklist.invalidateAll();
    }
}
