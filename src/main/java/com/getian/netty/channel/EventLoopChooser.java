package com.getian.netty.channel;

/**
 * EventLoop 选择器策略接口
 * EventLoopChooser 定义了如何从 EventLoopGroup 中选择下一个 EventLoop 的策略。
 * 不同的选择策略可以实现不同的负载均衡算法。
 * 策略模式
 *
 */
public interface EventLoopChooser {
    /**
     * 选择下一个 EventLoop
     *
     * @return 下一个要使用的 EventLoop
     */
    EventLoop next();
}
