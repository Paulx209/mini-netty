package com.getian.netty.channel;

import java.net.SocketAddress;

/**
 * 双向 Handler 适配器类  同时实现ChannelInboundHandler 和 ChannelOutboundHandler
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-21
 */

public class ChannelDuplexHandler implements ChannelInboundHandler, ChannelOutboundHandler {

    // =====================
    // ChannelHandler 生命周期
    // =====================

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        //默认不实现
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        //默认不实现
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        if (promise != null) {
            promise.setSuccess();
        }
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        if (promise != null) {
            promise.setSuccess();
        }
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (promise != null) {
            promise.setSuccess();
        }
    }


    // =====================
    // 入站事件处理
    // =====================

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        //传递给下一个handler
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        //传递给下一个handler
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //传递给下一个handler
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //传递给下一个handler
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //传递给下一个handler
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        //传递给下一个handler
        ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //传递给下一个handler
        ctx.fireExceptionCaught(cause);
    }


    // =====================
    // 出站事件处理
    // =====================

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ctx.write(msg);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }
}
