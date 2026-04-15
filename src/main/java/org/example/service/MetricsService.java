package org.example.service;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.core.instrument.Counter;

public class MetricsService {
    private static final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    private static final Counter totalRequests = Counter.builder("shieldgate_requests_total")
            .description("Общее количество запросов")
            .register(registry);

    private static final Counter securityViolations = Counter.builder("shieldgate_security_violations")
            .description("Количество обнаруженных атак")
            .register(registry);

    // Добавляем счетчик заблокированных запросов
    private static final Counter blockedRequests = Counter.builder("shieldgate_requests_blocked")
            .description("Количество заблокированных запросов")
            .register(registry);

    public static void incTotal() { totalRequests.increment(); }
    public static void incViolation() { securityViolations.increment(); }
    public static void incBlocked() { blockedRequests.increment(); } // Добавили этот метод
    public static void printStats() { blockedRequests.increment(); }
    public static String scrape() {
        return registry.scrape();
    }
}

