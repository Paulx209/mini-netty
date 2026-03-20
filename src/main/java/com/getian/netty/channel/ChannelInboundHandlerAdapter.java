package com.getian.netty.channel;

/**
 * ChannelInboundHandler 的适配器类 后面还会有出站的适配器类
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-20
 */

public class ChannelInboundHandlerAdapter implements ChannelInboundHandler {
    /**
     * pipeline中添加handler的时候 会触发该方法 需要handler处理
     * 默认不实现
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

    }

    /**
     * pipeline中删除handler的时候 会触发该方法 需要handler处理
     * 默认不实现
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

    }

    /**
     * channel中注册eventLoop的时候 会触发该方法 需要handler处理
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        //继续向下传播
        ctx.fireChannelRegistered();
    }

    /**
     * channel中取消绑定eventLoop的时候 会触发该方法 需要handler处理
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        //继续向下传播
        ctx.fireChannelUnregistered();

    }

    /**
     * channel中取消 bind 或者 取消 connect时候 会触发该方法
     * 如果是NioServerSocketChannel的话 就是与监听的端口号断开了
     * 如果是ServerSocketChannel的话 就是建立的TCP连接断开了
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //继续向下传播
        ctx.fireChannelInactive();
    }

    /**
     * channel中bind 和 connect 的时候 会触发该方法
     * bind:如果是NioServerSocketChannel的话 就是监听了对应的端口号
     * connect:如果是NioSocketChannel的话 就是和对应的地址创建了TCP连接
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //继续向下传播
        ctx.fireChannelActive();
    }

    /**
     * Channel读取数据
     *
     * @param ctx 上下文
     * @param msg 读取到的消息
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //继续向下传播
        ctx.fireChannelRead(msg);
    }

    /**
     * chanenl读取完成？
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        //继续向下传播
        ctx.fireChannelReadComplete();
    }

    /**
     * pipeline中抛出异常时 会交给各个handler处理
     *
     * @param ctx   上下文
     * @param cause 异常原因
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //继续向下传播
        ctx.fireExceptionCaught(cause);
    }

}
