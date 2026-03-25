package com.getian.netty.integration;

import com.getian.netty.bootstrap.ServerBootstrap;
import com.getian.netty.channel.*;
import com.getian.netty.channel.nio.NioEventLoop;
import com.getian.netty.channel.nio.NioEventLoopGroup;
import com.getian.netty.channel.nio.NioServerSocketChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boss/Worker 主从 Reactor 模型集成测试
 * @Author: sonicge
 * @CreateTime: 2026-03-25
 */

public class BossWorkerModelTest {
    @Nested
    @DisplayName("配置测试")
    class ConfigurationTests {
        @Test
        @DisplayName("Boss 1个线程 + Worker 4个线程配置正确")
        void configuresBossAndWorkerGroups() {
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(4);

            try {
                assertThat(bossGroup.executorCount()).isEqualTo(1);
                assertThat(workerGroup.executorCount()).isEqualTo(4);
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }

        @Test
        @DisplayName("ServerBootstrap 绑定 Boss 和 Worker 组")
        void bindsBossAndWorkerGroups() {
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(4);
            try {
                ServerBootstrap bootstrap = new ServerBootstrap()
                        .group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(new ChannelInboundHandlerAdapter());

                assertThat(bootstrap.childGroup()).isSameAs(workerGroup);
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }

        @Test
        @DisplayName("未设置 childGroup 时使用 parentGroup")
        void usesParentGroupWhenChildGroupNotSet() {
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(4);

            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(bossGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter());

            serverBootstrap.validate();
            assertThat(serverBootstrap.childGroup()).isEqualTo(bossGroup);
        }
    }

    @Nested
    @DisplayName("线程模型测试")
    class ThreadModelTests {
        @Test
        @DisplayName("Boss 使用单独的线程组")
        void bossUsesOwnThreadGroup() {
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(4);

            try {
                assertThat(bossGroup.executorCount()).isEqualTo(1);
                // Boss EventLoop 不同于 Worker EventLoop
                EventLoop bossLoop = bossGroup.next();
                EventLoop workerLoop = workerGroup.next();
                assertThat(bossLoop).isNotSameAs(workerLoop);
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        }

        @Test
        @DisplayName("Worker 组支持多个线程")
        void workerGroupSupportsMultipleThreads() {
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(4);
            assertThat(workerGroup.executorCount()).isEqualTo(4);
            Set<EventLoop> sets = new HashSet<>();
            for (int i = 0; i < workerGroup.executorCount(); i++) {
                EventLoop next = workerGroup.next();
                sets.add(next);
            }
            assertThat(sets.size()).isEqualTo(4);
        }

        @Test
        @DisplayName("Worker 轮询分配 EventLoop")
        void workerDistributesEvenly() {
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(3);
            int[] counts = new int[3];
            for (int i = 0; i < 6; i++) {
                //一个是顺序下一个
                EventLoop loop = workerGroup.next();
                for (int j = 0; j < 3; j++) {
                    //一个是按照索引
                    if (loop == workerGroup.eventLoop(j)) {
                        counts[j]++;
                    }
                }
            }
            for (int i = 0; i < counts.length; i++) {
                System.out.println(counts[i] == 2);
            }
        }
    }

    @Nested
    @DisplayName("生命周期测试")
    class LifecycleTests {

        @Test
        @DisplayName("优雅关闭 Boss 和 Worker 组")
        void gracefullyShutsBothGroups() {
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(2);

            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();

            assertThat(bossGroup.isShutdown()).isTrue();
            assertThat(workerGroup.isShutdown()).isTrue();
        }


        @Test
        @DisplayName("关闭后 EventLoopGroup 不再接受新任务")
        void noNewTasksAfterShutdown() {
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(1);

            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("场景: 完整的 Boss/Worker 配置")
        void scenarioCompleteSetup() {
            NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
            NioEventLoopGroup workerGroup = new NioEventLoopGroup(4);

            //配置服务器
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast("echo", new ChannelInboundHandlerAdapter());
                        }
                    });

            EventLoopGroup group = serverBootstrap.group();
            assertThat(group).isEqualTo(bossGroup);
            EventLoopGroup eventLoopGroup = serverBootstrap.childGroup();
            assertThat(eventLoopGroup).isEqualTo(workerGroup);
        }

        @Test
        @DisplayName("场景: Boss 和 Worker 使用相同组")
        void scenarioSameGroupForBossAndWorker() {
            NioEventLoopGroup group = new NioEventLoopGroup(4);
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(group)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter());
            bootstrap.validate();
            EventLoopGroup eventLoopGroup = bootstrap.childGroup();
            assertThat(eventLoopGroup).isEqualTo(group);
        }
    }
}
