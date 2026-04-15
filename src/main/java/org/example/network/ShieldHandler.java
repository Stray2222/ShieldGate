package org.example.network;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import org.example.config.AppConfig;
import org.example.core.JsonInspector;
import org.example.util.HttpResponder;
import org.example.service.MetricsService;
import org.example.service.RateLimiterService;
import org.example.service.ReputationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ShieldHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(ShieldHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    public static final AttributeKey<String> RID = AttributeKey.valueOf("req_id");

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        ctx.channel().attr(RID).set(requestId);

        String ip = getIp(ctx);
        var security = AppConfig.getConfig().getSecurity();

        // --- 1. METRICS (Доступны без авторизации) ---
        if ("/metrics".equals(msg.uri())) {
            String stats = MetricsService.scrape();
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.copiedBuffer(stats, StandardCharsets.UTF_8)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; version=0.0.4; charset=utf-8");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        // --- 2. IP WHITE-LIST ---
        if (!security.getWhiteListIps().isEmpty() && !security.getWhiteListIps().contains(ip)) {
            logger.warn("[{}] BLOCK | IP {} не в белом списке", requestId, ip);
            HttpResponder.send(ctx, "Access Denied: IP not whitelisted", HttpResponseStatus.FORBIDDEN);
            return;
        }

        // --- 3. API KEY AUTH (Возвращаем строгую проверку) ---
        String expectedKey = security.getApiSecretKey();
        if (expectedKey != null) {
            String clientKey = msg.headers().get("X-API-KEY");
            if (clientKey == null) clientKey = msg.headers().get("x-api-key");

            if (clientKey == null || !expectedKey.equals(clientKey)) {
                logger.warn("[{}] BLOCK | Неверный API Key от IP: {}", requestId, ip);
                MetricsService.incBlocked();
                HttpResponder.send(ctx, "Unauthorized: Invalid API Key", HttpResponseStatus.UNAUTHORIZED);
                return;
            }
        }

        // --- 4. ПРОВЕРКА РЕПУТАЦИИ (Ban-list) ---
        if (ReputationManager.isBanned(ip)) {
            MetricsService.incBlocked();
            logger.warn("[{}] BLOCK | IP {} находится в бане", requestId, ip);
            HttpResponder.send(ctx, "Your IP is temporarily banned", HttpResponseStatus.FORBIDDEN);
            return;
        }

        // --- 5. RATE LIMITER ---
        if (!RateLimiterService.tryConsume(ip)) {
            MetricsService.incBlocked();
            logger.warn("[{}] RATE_LIMIT | IP: {}", requestId, ip);
            HttpResponder.send(ctx, "Too Many Requests", HttpResponseStatus.TOO_MANY_REQUESTS);
            return;
        }

        MetricsService.incTotal();

        // --- 6. ВАЛИДАЦИЯ МЕТОДА ---
        if (msg.method() != HttpMethod.POST) {
            HttpResponder.send(ctx, "Only POST allowed", HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        // --- 7. ИНСПЕКЦИЯ JSON (Streaming WAF) ---
        try (ByteBufInputStream is = new ByteBufInputStream(msg.content())) {
            JsonParser parser = mapper.getFactory().createParser((InputStream) is);
            String securityError = JsonInspector.checkSafety(parser, 0);

            if (securityError != null) {
                failSecurity(ctx, ip, requestId, securityError);
                return;
            }
        } catch (Exception e) {
            MetricsService.incBlocked();
            ReputationManager.reportViolation(ip);
            logger.error("[{}] JSON_ERROR | IP: {} | Msg: {}", requestId, ip, e.getMessage());
            HttpResponder.send(ctx, "Invalid JSON or Security Violation", HttpResponseStatus.BAD_REQUEST);
            return;
        }

        // --- 8. ПРОКСИРОВАНИЕ ---
        forwardToBackend(ctx, msg);
    }

    private void failSecurity(ChannelHandlerContext ctx, String ip, String requestId, String reason) {
        MetricsService.incViolation();
        MetricsService.incBlocked();
        logger.error("[{}] SECURITY_ALERT | IP: {} | Reason: {}", requestId, ip, reason);
        ReputationManager.reportViolation(ip);
        HttpResponder.send(ctx, "Security Alert: " + reason, HttpResponseStatus.BAD_REQUEST);
    }

    private void forwardToBackend(ChannelHandlerContext clientCtx, FullHttpRequest request) {
        String requestId = clientCtx.channel().attr(RID).get();
        request.retain();

        BackendPoolManager.acquire().addListener((Future<Channel> future) -> {
            if (future.isSuccess()) {
                Channel ch = future.getNow();
                ch.pipeline().addLast(new BackendResponseHandler(clientCtx));

                var backendCfg = AppConfig.getConfig().getBackend();
                request.headers().set(HttpHeaderNames.HOST, backendCfg.getHost());
                request.headers().set("X-Forwarded-For", getIp(clientCtx));
                request.headers().set("X-Request-ID", requestId);

                ch.writeAndFlush(request).addListener(f -> {
                    if (!f.isSuccess()) {
                        logger.error("[{}] PROXY_ERROR | Write failed", requestId);
                        BackendPoolManager.release(ch);
                        if (request.refCnt() > 0) request.release();
                    }
                });
            } else {
                logger.error("[{}] POOL_ERROR | No connection available", requestId);
                if (request.refCnt() > 0) request.release();
                HttpResponder.send(clientCtx, "Backend Service Busy", HttpResponseStatus.SERVICE_UNAVAILABLE);
            }
        });
    }

    // --- ФИНАЛЬНЫЙ ШТРИХ: ОБРАБОТКА ОШИБОК КЛИЕНТА ---
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String requestId = ctx.channel().attr(RID).get();
        if (cause instanceof ReadTimeoutException) {
            logger.warn("[{}] Client connection timed out (Slowloris protection)", requestId);
        } else {
            logger.error("[{}] Unexpected error: {}", requestId, cause.getMessage());
        }
        ctx.close();
    }

    private String getIp(ChannelHandlerContext ctx) {
        var address = ctx.channel().remoteAddress();
        return (address instanceof java.net.InetSocketAddress) ?
                ((java.net.InetSocketAddress) address).getAddress().getHostAddress() : "127.0.0.1";
    }
}
