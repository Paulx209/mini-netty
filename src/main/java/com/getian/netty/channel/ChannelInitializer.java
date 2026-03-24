package com.getian.netty.channel;

/**
 * Channel 初始化器
 * 用于在 Channel 注册到 EventLoop 后初始化其 ChannelPipeline。
 * 通常与 Bootstrap 配合使用，配置新连接的处理器链。
 * <p>
 * ChannelInitializer 自身会在 initChannel 完成后自动从 Pipeline 中移除， （有点像BeanPostxxx）
 * 因此它不会占用 Pipeline 的位置。
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-23
 */

public abstract class ChannelInitializer<C extends Channel> extends ChannelInboundHandlerAdapter {

    /**
     * 当channel注册到eventLoop后调用此方法
     *
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (initChannel(ctx)) {
            //初始化成功 把这个handler删除掉
            ctx.pipeline().remove(this);
            ctx.fireChannelRegistered();
        } else {
            //初始化失败 关闭Channel
            ctx.close();
        }
    }

    private boolean initChannel(ChannelHandlerContext ctx) {
        try {
            C channel = (C) ctx.channel();
            initChannel(channel);
            return true;
        } catch (Throwable cause) {
            exceptionCaught(ctx, cause);
            return false;
        }
    }

    /**
     * 初始化 Channel
     *
     * <p>子类需要实现此方法，在 Channel 的 Pipeline 上添加所需的 Handler。
     *
     * @param ch 要初始化的 Channel
     * @throws Exception 初始化异常
     */
    protected abstract void initChannel(C ch) throws Exception;


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[ChannelInitializer] 初始化失败: " + cause.getMessage());
        ctx.close();
    }


}
