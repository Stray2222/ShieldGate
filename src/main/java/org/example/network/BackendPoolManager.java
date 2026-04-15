package org.example.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.util.concurrent.Future;
import org.example.config.AppConfig;

import java.net.InetSocketAddress;

public class BackendPoolManager {
    // volatile гарантирует, что все потоки Netty увидят новый пул мгновенно
    private static volatile SimpleChannelPool pool;

    public static synchronized void init(Bootstrap b) {
        var cfg = AppConfig.getConfig().getBackend();
        InetSocketAddress address = new InetSocketAddress(cfg.getHost(), cfg.getPort());

        // 1. Сохраняем ссылку на старый пул
        SimpleChannelPool oldPool = pool;

        // 2. Создаем новый пул с актуальными настройками
        pool = new FixedChannelPool(
                b.remoteAddress(address),
                new PoolHandler(),
                cfg.getMaxConnections()
        );

        // 3. Если старый пул был — закрываем его асинхронно
        if (oldPool != null) {
            oldPool.closeAsync().addListener(f -> {
                System.out.println("♻️ Старый пул соединений успешно закрыт.");
            });
        }

        System.out.println("🚀 Пул инициализирован: " + cfg.getHost() + ":" + cfg.getPort());
    }

    public static Future<Channel> acquire() {
        return pool.acquire();
    }

    public static void release(Channel ch) {
        if (pool != null) {
            pool.release(ch);
        }
    }

    // Метод для полной остановки (вызывай при выключении сервера)
    public static void shutdown() {
        if (pool != null) {
            pool.close();
        }
    }
}
