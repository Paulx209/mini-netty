package com.getian.netty.channel;

/**
 *  事件处理器基接口
 *  ChannelHandler 是处理 I/O 事件的核心接口。它有两个主要子接口：
 *      ChannelInboundHandler   处理入站事件（数据读取)
 *      ChannelOutboundHandler  处理出站事件（数据写入）
 *
 *      生命周期方法： handlerAdded - Handler 被添加到 Pipeline 时调用
 *      handlerRemoved - Handler 从 Pipeline 移除时调用
 *
 */
public interface ChannelHandler {

    /**
     * Handler 被添加到 Pipeline 时调用
     *
     * @param ctx Handler 上下文
     * @throws Exception 如果处理过程中发生异常
     */
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * Handler 从 Pipeline 移除时调用
     *
     * @param ctx Handler 上下文
     * @throws Exception 如果处理过程中发生异常
     */
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;
}
