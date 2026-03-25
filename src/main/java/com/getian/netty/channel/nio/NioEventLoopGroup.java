package com.getian.netty.channel.nio;

import com.getian.netty.channel.*;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NIO EventLoop 组实现
 * NioEventLoopGroup 管理一组 NioEventLoop，是 Netty NIO 传输的核心组件。
 *
 * <p>学习要点：
 * <ul>
 *   <li>EventLoopGroup 是 EventLoop 的容器和管理者</li>
 *   <li>轮询策略（Round-Robin）保证负载均衡</li>
 *   <li>Channel 一旦分配到 EventLoop，整个生命周期内不会改变</li>
 * </ul>
 * @Author: sonicge
 * @CreateTime: 2026-03-25
 */

public class NioEventLoopGroup implements EventLoopGroup {

    /**
     * 默认线程数：CPU 核心数 * 2
     */
    private static final int DEFAULT_EVENT_LOOP_THREADS =
            Math.max(1, Runtime.getRuntime().availableProcessors() * 2);


    /**
     * EventLoop 数组
     */
    private final NioEventLoop[] eventLoops;

    /**
     * 轮询索引计数器
     */
    private final AtomicInteger idx = new AtomicInteger();

    /**
     * 是否已关闭
     */
    private volatile boolean shutdown = false;

    /**
     * 是否已终止
     */
    private volatile boolean terminated = false;

    /**
     * 使用默认线程数创建 NioEventLoopGroup
     *
     * <p>默认线程数为 CPU 核心数 * 2
     */
    public NioEventLoopGroup() {
        this(0);
    }

    /**
     * 构造函数
     * @param nThreads 线程个数
     */
    public NioEventLoopGroup(int nThreads) {
        if (nThreads < 0) {
            throw new IllegalArgumentException("nThreads: " + nThreads + " (expected: >= 0)");
        }
        //如果传入的值为0的话 就是默认的2倍cpu核数;否则就是传入的值
        int threads = nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads;
        this.eventLoops = new NioEventLoop[threads];

        for (int i = 0; i < threads; i++) {
            this.eventLoops[i] = newEventLoop();
        }
        System.out.println("[NioEventLoopGroup] 创建 " + threads + " 个 NioEventLoop");
    }


    /**
     * 创建新的 NioEventLoop
     *
     * <p>子类可以重写此方法创建自定义的 EventLoop
     *
     * @return 新创建的 NioEventLoop
     */
    private NioEventLoop newEventLoop() {
        return new NioEventLoop(this);
    }

    /**
     * 启动所有的EventLoop
     */
    public void start() {
        for (NioEventLoop eventLoop : eventLoops) {
            eventLoop.start();
        }
        //等待所有的eventLoop完成
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 返回下一个要使用的NioEventLoop
     * 使用轮询（Round-Robin）策略选择 EventLoop，  保证每个 EventLoop 被均匀地分配到新 Channel。
     * @return NioEventLoop
     */
    @Override
    public EventLoop next() {
        //使用取模运算实现轮询
        int index = Math.abs(idx.getAndIncrement() % eventLoops.length);
        return eventLoops[index];
    }

    /**
     * 注册一个Channel到EventLoopGroup，
     * @param channel 要注册的 Channel
     * @return
     */
    @Override
    public ChannelFuture register(Channel channel) {
        EventLoop eventLoop = next();
        DefaultChannelPromise promise = new DefaultChannelPromise(channel);
        //内部会判断的
        try {
            channel.unsafe().register(eventLoop, promise);
        } catch (Exception e) {
            promise.setFailure(e);
        }
        return promise;
    }

    /**
     * 优雅关闭所有的eventLoop 按顺序关闭每个 EventLoop，等待所有任务完成。
     * @return 关闭结果的 Future
     */
    @Override
    public Future<?> shutdownGracefully() {
        shutdown = true;
        for (NioEventLoop eventLoop : eventLoops) {
            //1.先关闭没有启动的
            if (!eventLoop.isRunning()) {
                try {
                    if (eventLoop.selector().isOpen()) {
                        eventLoop.selector().close();
                    }
                } catch (IOException e) {
                    //ignore
                }
            } else {
                // 对于运行中的 EventLoop，发送关闭信号并唤醒
                eventLoop.shutdownGracefully();
                try {
                    eventLoop.selector().wakeup();
                } catch (Exception e) {
                    // ignore if selector is already closed
                }
            }
        }

        // 等待正在运行的 EventLoop 终止（最多等待 500ms）
        try {
            for (NioEventLoop eventLoop : eventLoops) {
                if (eventLoop.isRunning()) {
                    for (int i = 0; i < 50 && !eventLoop.isTerminated(); i++) {
                        Thread.sleep(10);
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        terminated = true;
        return null; // 简化实现，不返回实际的 Future
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * 判断所有 EventLoop 是否已终止
     *
     * @return 如果所有 EventLoop 都已终止返回 true
     */
    @Override
    public boolean isTerminated() {
        if (!shutdown) {
            return false;
        }

        for (NioEventLoop eventLoop : eventLoops) {
            if (!eventLoop.isTerminated()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取 EventLoop 数量
     *
     * @return EventLoop 数量
     */
    public int executorCount() {
        return eventLoops.length;
    }

    /**
     * 获取指定索引的 EventLoop
     *
     * @param index 索引
     * @return EventLoop
     */
    public NioEventLoop eventLoop(int index) {
        if (index < 0 || index >= eventLoops.length) {
            throw new IndexOutOfBoundsException("index: " + index);
        }
        return eventLoops[index];
    }
}
