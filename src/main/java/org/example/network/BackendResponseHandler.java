package org.example.network;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackendResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private static final Logger logger = LoggerFactory.getLogger(BackendResponseHandler.class);
    private final ChannelHandlerContext clientCtx;

    public BackendResponseHandler(ChannelHandlerContext clientCtx) {
        this.clientCtx = clientCtx;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) {
        // --- ДОСТАЕМ CORRELATION ID ---
        String requestId = clientCtx.channel().attr(ShieldHandler.RID).get();

        var responseToClient = response.retainedDuplicate();
        boolean keepAlive = HttpUtil.isKeepAlive(response);
        var future = clientCtx.writeAndFlush(responseToClient);

        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }

        ctx.pipeline().remove(this);
        BackendPoolManager.release(ctx.channel());

        // Логируем успех с использованием ID
        logger.info("[{}] Ответ получен и отправлен клиенту (Status: {})", requestId, response.status());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // --- ДОСТАЕМ CORRELATION ID ПРИ ОШИБКЕ ---
        String requestId = clientCtx.channel().attr(ShieldHandler.RID).get();

        logger.error("[{}] Критическая ошибка связи с бэкендом: {}", requestId, cause.getMessage());

        if (ctx.pipeline().context(this) != null) {
            ctx.pipeline().remove(this);
        }

        BackendPoolManager.release(ctx.channel());

        if (clientCtx.channel().isActive()) {
            clientCtx.close();
        }
    }
}
