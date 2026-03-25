package com.getian.netty.channel;

/**
 * EventLoopChooser的工厂类 用于根据不同的情况来创建负载均衡策略的
 *@Author: sonicge
 *@CreateTime: 2026-03-25
 */

public class DefaultEventLoopChooserFactory implements EventLoopChooserFactory {
    public static final DefaultEventLoopChooserFactory INSTANCE = new DefaultEventLoopChooserFactory();

    /**
     * 私有构造函数 防止外部实例化
     */
    private DefaultEventLoopChooserFactory() {

    }

    @Override
    public EventLoopChooser newChooser(EventLoop[] eventLoops) {
        if (eventLoops == null || eventLoops.length == 0) {
            throw new NullPointerException("eventLoops");
        }
        boolean isPowerOfTwo = PowerOfTwoEventLoopChooser.isPowerOfTwo(eventLoops.length);
        if (isPowerOfTwo) {
            return new PowerOfTwoEventLoopChooser(eventLoops);
        } else {
            return new RoundRobinEventLoopChooser(eventLoops);
        }
    }
}
