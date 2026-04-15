package org.example.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.example.util.HttpResponder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalErrorHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GlobalErrorHandler.class);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("🛑 Необработанное исключение: {}", cause.getMessage());

        // Отправляем 500 ошибку клиенту через твой утилитный класс
        if (ctx.channel().isActive()) {
            HttpResponder.send(ctx, "Internal ShieldGate Error", HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
