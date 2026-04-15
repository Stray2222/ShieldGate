package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.ReadTimeoutHandler; // ДЛЯ ТАЙМАУТОВ
import org.example.config.AppConfig;
import org.example.network.BackendPoolManager;
import org.example.network.GlobalErrorHandler;
import org.example.service.MetricsService;
import org.example.network.ShieldHandler;

import java.util.concurrent.TimeUnit;

public class ShieldGate {
    public static void main(String[] args) throws Exception {
        // 1. Инициализация конфигурации
        AppConfig.load();
        AppConfig.watchForChanges();

        int port = AppConfig.getConfig().getServer().getPort();

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        // --- ГОРЯЧАЯ МОДЕРНИЗАЦИЯ: SHUTDOWN HOOK ---
        // Гарантирует, что при выключении (Ctrl+C) все ресурсы и пулы закроются чисто
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down ShieldGate...");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            BackendPoolManager.shutdown();
        }));

        try {
            // Инициализация пула бэкенда
            Bootstrap clientBootstrap = new Bootstrap()
                    .group(workerGroup)
                    .channel(NioSocketChannel.class);
            BackendPoolManager.init(clientBootstrap);

            // Поток для вывода статистики
            startMetricsReporter();

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(100 * 1024 * 1024));

                            p.addLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS));

                            // 4. Твой хендлер
                            p.addLast(new ShieldHandler());
                            p.addLast(new GlobalErrorHandler());
                        }
                    });

            System.out.println("\n" + "=".repeat(40));
            System.out.println("   🛡️  SHIELD GATE ENGINE IS ONLINE");
            System.out.println("   📍 Listening on port: " + port);
            System.out.println("=".repeat(40) + "\n");

            b.bind(port).sync().channel().closeFuture().sync();
        } catch (Exception e) {
            System.err.println("❌ Critical startup error: " + e.getMessage());
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            BackendPoolManager.shutdown();
        }
    }

    private static void startMetricsReporter() {
        Thread metricsThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30000);
                    MetricsService.printStats();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        metricsThread.setDaemon(true);
        metricsThread.setName("Metrics-Reporter");
        metricsThread.start();
    }
}
