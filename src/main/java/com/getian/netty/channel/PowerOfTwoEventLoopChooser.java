package com.getian.netty.channel;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 优化的 2 的幂 EventLoop 选择器 类似八股：hashMap的底层长度为什么要是2的幂次方?
 * 如果length为2的幂次方的话：index % length -> index & (length - 1)
 * 位运算的效率更高 CPU 指令级别
 *@Author: sonicge
 *@CreateTime: 2026-03-25
 */

public class PowerOfTwoEventLoopChooser implements EventLoopChooser {
    /**
     * EventLoop数组
     */
    private final EventLoop[] eventLoops;

    private final AtomicInteger idx = new AtomicInteger();

    public PowerOfTwoEventLoopChooser(EventLoop[] eventLoops) {
        if (eventLoops == null || eventLoops.length == 0) {
            throw new IllegalArgumentException("eventLoops must not be null or empty");
        }
        if (!isPowerOfTwo(eventLoops.length)) {
            throw new IllegalArgumentException("eventLoops length must be a power of two");
        }
        this.eventLoops = eventLoops;
    }

    /**
     * 如果n是2的幂次方的话，那么n & (n-1) == 0
     * eg: 8:1000 7:0111 1000 & 0111 = 0000 = 0
     * @param n
     * @return
     */
    public static  boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    /**
     * 选择下一个EventLoop
     * @return
     */
    @Override
    public EventLoop next() {
        int selectedIndex = idx.getAndIncrement() & (eventLoops.length - 1);
        return eventLoops[selectedIndex];
    }
}
