package com.getian.netty.channel.nio;

import com.getian.netty.channel.EventLoop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 测试 NioEventLoopGroup 的创建、轮询和生命周期功能
 * @Author: sonicge
 * @CreateTime: 2026-03-25
 */

public class NioEventLoopGroupTest {
    @Test
    @DisplayName("使用默认线程数创建")
    void createsWithDefaultThreadCount() {
        NioEventLoopGroup nioEventLoopGroup = new NioEventLoopGroup();
        try {
            int expectedThreads = Math.max(1, Runtime.getRuntime().availableProcessors() * 2);
            assertThat(nioEventLoopGroup.executorCount()).isEqualTo(expectedThreads);
        } finally {
            nioEventLoopGroup.shutdownGracefully();
        }
    }

    @Test
    @DisplayName("使用指定线程数创建")
    void createsWithSpecifiedThreadCount() {
        NioEventLoopGroup group = new NioEventLoopGroup(4);
        int count = group.executorCount();
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("使用负数线程数创建时抛出异常")
    void throwsExceptionForNegativeThreadCount() {
        assertThatThrownBy(() -> new NioEventLoopGroup(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("next() 返回 EventLoop")
    void nextReturnsEventLoop() {
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            EventLoop first = group.next();
            EventLoop second = group.next();
            assertThat(first).isNotNull();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @DisplayName("轮询循环回到第一个")
    void roundRobinCyclesBack() {
        NioEventLoopGroup group = new NioEventLoopGroup(2);
        try {
            EventLoop first = group.next();
            EventLoop second = group.next();
            EventLoop third = group.next();

            assertThat(first).isEqualTo(third);
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @DisplayName("通过索引获取 EventLoop")
    void getsEventLoopByIndex() {
        NioEventLoopGroup group = new NioEventLoopGroup(3);
        EventLoop first = group.next();
        EventLoop second = group.next();
        NioEventLoop second2 = group.eventLoop(1);

        assertThat(second).isEqualTo(second2);
    }

    @Test
    @DisplayName("索引越界时抛出异常")
    void throwsExceptionForInvalidIndex() {
        NioEventLoopGroup group = new NioEventLoopGroup(3);
        assertThatThrownBy(() -> group.eventLoop(4)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("未关闭时 isShutdown 返回 false")
    void isNotShutdownInitially() {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        try {
            assertThat(group.isShutdown()).isFalse();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @DisplayName("关闭后 isShutdown 返回 true")
    void isShutdownAfterShutdown() {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        group.shutdownGracefully();

        assertThat(group.isShutdown()).isTrue();
    }

    @Test
    @DisplayName("场景: 创建主从 Reactor 线程组")
    void scenarioBossWorkerReactorGroups() {

        // Given: 创建 Boss 和 Worker 线程组
        NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
        NioEventLoopGroup workerGroup = new NioEventLoopGroup(4);

        try {
            // Then: 验证线程数正确
            assertThat(bossGroup.executorCount()).isEqualTo(1);
            assertThat(workerGroup.executorCount()).isEqualTo(4);

            // 验证 Boss 总是返回同一个 EventLoop
            EventLoop bossLoop1 = bossGroup.next();
            EventLoop bossLoop2 = bossGroup.next();
            assertThat(bossLoop1).isSameAs(bossLoop2);

            // 验证 Worker 轮询返回不同 EventLoop
            Set<EventLoop> workerLoops = new HashSet<>();
            for (int i = 0; i < 4; i++) {
                workerLoops.add(workerGroup.next());
            }
            assertThat(workerLoops).hasSize(4);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
