package com.getian.netty.channel;

import java.util.concurrent.Future;

/**
 * 事件循环组接口，管理一组 EventLoop
 *      负载均衡地选择 EventLoop
 *      提供 Channel 注册功能
 *      管理一组 EventLoop 实例
 *
 */
public interface EventLoopGroup {


    /**
     * 返回下一个EventLoop
     *  使用轮询或其他策略选择一个 EventLoop
     * @return EventLoop
     */
    EventLoop next();

    /**
     * 注册一个Channel到EventLoopGroup，
     * Channel 会被分配到一个 EventLoop，
     * 之后该 Channel 的所有 I/O 操作都在该 EventLoop 线程中执行。
     * @param channel 要注册的 Channel
     * @return 注册结果的 Future
     */
    ChannelFuture register(Channel channel);

    /**
     * 优雅关闭所有的EventLoop
     *  优雅关闭意味着：
     *   拒绝接受新任务
     *   等待已提交的任务完成
     *   释放所有资源
     *
     * @return
     */
    Future<?> shutdownGracefully();

    /**
     * 判断 EventLoopGroup 是否已关闭
     *
     * @return 如果已关闭返回 true
     */
    boolean isShutdown();

    /**
     * 判断所有 EventLoop 是否已终止
     *
     * @return 如果所有 EventLoop 都已终止返回 true
     */
    boolean isTerminated();
}
