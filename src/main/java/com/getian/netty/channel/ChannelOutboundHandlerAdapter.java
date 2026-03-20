package com.getian.netty.channel;

import java.net.SocketAddress;

/**
 * ChannelOutBoundHandler的适配器类
 * 主打一个提供默认的实现方式，开发者只需要覆盖想写的方法即可，其他冗余的方法不要管
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-20
 */

public class ChannelOutboundHandlerAdapter implements ChannelOutboundHandler {
    /**
     * 默认实现：什么都不做
     *
     * @param ctx Handler 上下文
     * @throws Exception
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

    }

    /**
     * 默认实现：什么都不做
     *
     * @param ctx Handler 上下文
     * @throws Exception
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        //默认实现：直接完成，实际完成情况由具体的ctx判断
        if (promise != null) {
            promise.setSuccess();
        }
    }

    /**
     * 默认实现：设置 Promise 成功（connect 由 HeadContext 处理）
     *
     * @param ctx           上下文
     * @param remoteAddress 远程地址
     * @param localAddress  本地地址（可为 null）
     * @param promise       操作结果 Promise
     * @throws Exception
     */
    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        if (promise != null) {
            promise.setSuccess();
        }
    }


    /**
     * 默认实现：设置 Promise 成功（disconnect 由 HeadContext 处理）
     *
     * @param ctx     上下文
     * @param promise 操作结果 Promise
     * @throws Exception
     */
    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (promise != null) {
            promise.setSuccess();
        }
    }


    /**
     * 默认实现：传递给下一个 Handler
     *
     * @param ctx     上下文
     * @param promise 操作结果 Promise
     * @throws Exception
     */
    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    /**
     * 默认实现：传递给下一个 Handler
     *
     * @param ctx     上下文
     * @param msg     要写入的消息
     * @param promise 操作结果 Promise
     * @throws Exception
     */
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ctx.write(msg, promise);
    }

    /**
     * 默认实现：传递给下一个 Handler
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    /**
     * 默认实现：传递给下一个 Handler
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }
}
