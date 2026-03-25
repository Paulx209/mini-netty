package com.getian.netty.channel;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询 EventLoop 选择器 使用简单的轮询（Round-Robin）算法选择 EventLoop。
 * 1.每次调用 next() 返回下一个 EventLoop，循环遍历所有 EventLoop。
 * @Author: sonicge
 * @CreateTime: 2026-03-25
 */

public class RoundRobinEventLoopChooser implements EventLoopChooser {
    /**
     * EventLoop数组
     *
     */
    private EventLoop[] eventLoops;

    /**
     * 轮询索引
     */
    private AtomicInteger idx = new AtomicInteger();

    public RoundRobinEventLoopChooser(EventLoop[] eventLoops) {
        if (eventLoops == null || eventLoops.length == 0) {
            throw new IllegalArgumentException("eventLoops must not be null or empty");
        }
        this.eventLoops = eventLoops;
    }

    @Override
    public EventLoop next() {
        int selectedIndex = Math.abs(idx.getAndIncrement() % (eventLoops.length));
        return eventLoops[selectedIndex];
    }
}
