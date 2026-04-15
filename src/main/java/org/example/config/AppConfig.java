package org.example.config;

import org.example.models.*;
import org.example.network.BackendPoolManager;
import org.example.service.RateLimiterService;
import org.yaml.snakeyaml.Yaml;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.InputStream;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

public class AppConfig {

    private static final AtomicReference<AppConfig> instance = new AtomicReference<>(new AppConfig());

    // --- ПЕРЕИСПОЛЬЗУЕМЫЕ РЕСУРСЫ ДЛЯ БЭКЕНДА (Защита от утечек) ---
    private static final EventLoopGroup backendGroup = new NioEventLoopGroup(1);
    private static final Bootstrap sharedBootstrap = new Bootstrap()
            .group(backendGroup)
            .channel(NioSocketChannel.class);

    private ServerConfig server = new ServerConfig();
    private BackendConfig backend = new BackendConfig();
    private SecurityConfig security = new SecurityConfig();
    private RateLimitConfig ratelimit = new RateLimitConfig();

    public static void load() {
        Yaml yaml = new Yaml();
        try (InputStream inputStream = AppConfig.class.getClassLoader().getResourceAsStream("config.yaml")) {
            if (inputStream == null) {
                System.err.println("[ERROR] Файл config.yaml не найден в папке resources!");
                return;
            }

            AppConfig tempConfig = yaml.loadAs(inputStream, AppConfig.class);

            if (tempConfig != null) {
                instance.set(tempConfig);
                printCurrentConfig();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Критическая ошибка при загрузке конфигурации: " + e.getMessage());
        }
    }

    public static void watchForChanges() {
        Thread watchThread = new Thread(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                Path path = Paths.get("src/main/resources");
                path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.context().toString().equals("config.yaml")) {
                            Thread.sleep(500); // Даем время на сохранение файла
                            load();

                            // ГОРЯЧЕЕ ОБНОВЛЕНИЕ БЕЗ УТЕЧЕК
                            RateLimiterService.clear();

                            // Переинициализируем пул, используя существующий Bootstrap
                            BackendPoolManager.init(sharedBootstrap);

                            System.out.println("🔄 Конфигурация и пул бэкендов обновлены.");
                        }
                    }
                    if (!key.reset()) break;
                }
            } catch (Exception e) {
                System.err.println("[CRITICAL] WatchService сбой: " + e.getMessage());
            }
        });
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private static void printCurrentConfig() {
        AppConfig config = instance.get();
        System.out.println("\n" + "=".repeat(40));
        System.out.println("   🛡️  SHIELD GATE: CONFIG LOADED");
        System.out.println("=".repeat(40));
        System.out.printf(" 🌐 Port: %d\n", config.getServer().getPort());
        System.out.printf(" 🎯 Backend: %s:%d (Max Connections: %d)\n",
                config.getBackend().getHost(),
                config.getBackend().getPort(),
                config.getBackend().getMaxConnections());
        System.out.printf(" ⚡ Rate Limit: %d req / %d tokens\n",
                config.getRatelimit().getCapacity(),
                config.getRatelimit().getRefillTokens());
        System.out.println("=".repeat(40) + "\n");
    }

    public static AppConfig getConfig() {
        return instance.get();
    }

    // Очистка при выключении
    public static void shutdown() {
        backendGroup.shutdownGracefully();
    }

    // Геттеры и сеттеры для SnakeYAML
    public ServerConfig getServer() { return server; }
    public void setServer(ServerConfig server) { this.server = server; }
    public BackendConfig getBackend() { return backend; }
    public void setBackend(BackendConfig backend) { this.backend = backend; }
    public SecurityConfig getSecurity() { return security; }
    public void setSecurity(SecurityConfig security) { this.security = security; }
    public RateLimitConfig getRatelimit() { return ratelimit; }
    public void setRatelimit(RateLimitConfig ratelimit) { this.ratelimit = ratelimit; }
}
