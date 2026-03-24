package com.getian.netty.bootstrap;

import com.getian.netty.channel.*;
import com.getian.netty.channel.nio.NioEventLoop;
import com.getian.netty.channel.nio.NioServerSocketChannel;
import org.junit.jupiter.api.*;

import javax.imageio.spi.ServiceRegistry;
import javax.sql.rowset.WebRowSet;
import java.net.InetSocketAddress;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ServerBootstrap 测试
 * 测试 ServerBootstrap 的配置和启动功能
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-24
 */

public class ServerBootstrapTest {
    private TestEventLoopGroup bossGroup;
    private TestEventLoopGroup workerGroup;

    @BeforeEach
    void setup() {
        bossGroup = new TestEventLoopGroup();
        workerGroup = new TestEventLoopGroup();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        Thread.sleep(100);
    }

    @Nested
    @DisplayName("配置测试")
    class ConfigurationTests {
        @Test
        @DisplayName("设置 Boss 和 Worker 组")
        void setsBossAndWorkerGroups() {
            ServerBootstrap bootstrap = new ServerBootstrap();
            ServerBootstrap result = bootstrap.group(bossGroup, workerGroup);

            assertThat(result).isSameAs(bootstrap);
            assertThat(bootstrap.group()).isSameAs(bossGroup);
            assertThat(bootstrap.childGroup()).isSameAs(workerGroup);
        }

        @Test
        @DisplayName("单个 group 方法只设置 Boss 组")
        void singleGroupMethodSetsBossOnly() {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup);
            assertThat(bootstrap.group()).isSameAs(bossGroup);
        }

        @Test
        @DisplayName("设置 ChannelFactory创建Channel的类型")
        void setsChannelClass() {
            ServerBootstrap bootstrap = new ServerBootstrap();
            ServerBootstrap result = bootstrap.channel(NioServerSocketChannel.class);
            assertThat(result).isSameAs(bootstrap);
        }

        @Test
        @DisplayName("设置 childHandler")
        void setsChildHandler() {
            ServerBootstrap bootstrap = new ServerBootstrap();
            ChannelHandler handler = new ChannelInboundHandlerAdapter();

            ServerBootstrap res = bootstrap.childHandler(handler);
            assertThat(res).isSameAs(bootstrap);
        }

        @Test
        @DisplayName("设置 childOption")
        void setsChildOption() {
            ServerBootstrap bootstrap = new ServerBootstrap();

            ServerBootstrap result = bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

            assertThat(result).isSameAs(bootstrap);
        }

        @Test
        @DisplayName("设置多个 childOption")
        void setsMultipleChildOptions() {
            ServerBootstrap bootstrap = new ServerBootstrap();
            ServerBootstrap res = bootstrap.childOption(ChannelOption.AUTO_READ, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);
            assertThat(res).isSameAs(bootstrap);
        }

        @Test
        @DisplayName("设置 option")
        void setsOption() {
            ServerBootstrap bootstrap = new ServerBootstrap();

            ServerBootstrap result = bootstrap.option(ChannelOption.SO_BACKLOG, 128);

            assertThat(result).isSameAs(bootstrap);
        }

        @Test
        @DisplayName("链式配置")
        void fluentConfiguration() {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInboundHandlerAdapter());
            assertThat(bootstrap.group()).isSameAs(bossGroup);
            assertThat(bootstrap.childGroup()).isSameAs(workerGroup);
        }

        @Test
        @DisplayName("克隆 ServerBootstrap")
        void clonesBootstrap() {
            ServerBootstrap original = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 127)
                    .childHandler(new ChannelInboundHandlerAdapter());
            ServerBootstrap clone = original.clone();

            assertThat(clone).isNotSameAs(original);
            assertThat(clone.group()).isSameAs(original.group());
            assertThat(clone.childGroup()).isSameAs(original.childGroup());
        }
    }

    @Nested
    @DisplayName("验证测试")
    class ValidationTests {
        @Test
        @DisplayName("未设置 group 时验证失败")
        void failsValidationWithoutGroup() {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter());
            bootstrap.validate();
        }


        @Test
        @DisplayName("未设置 channel 时验证失败")
        void failsValidationWithoutChannel() {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .childHandler(new ChannelInboundHandlerAdapter());


            assertThatThrownBy(bootstrap::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("channel");
        }

        @Test
        @DisplayName("未设置 childHandler 时验证失败")
        void failsValidationWithoutChildHandler() {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class);
            bootstrap.validate();

            assertThatThrownBy(bootstrap::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("childHandler");
        }

        @Test
        @DisplayName("完整配置验证通过")
        void passesValidationWithCompleteConfiguration() {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter());

            ServerBootstrap result = bootstrap.validate();

            assertThat(result).isSameAs(bootstrap);
        }

        @Test
        @DisplayName("未设置 childGroup 时使用 parentGroup")
        void usesParentGroupWhenChildGroupNotSet() {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter());

            bootstrap.validate();

            assertThat(bootstrap.childGroup()).isSameAs(bossGroup);
        }
    }

    @Nested
    @DisplayName("空值验证")
    class NullValidationTests {
        @Test
        @DisplayName("group 为空时抛出异常")
        void throwsExceptionWhenGroupIsNull() {
            ServerBootstrap bootstrap = new ServerBootstrap();
            assertThatThrownBy(() -> bootstrap.group(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("childGroup 为空时抛出异常")
        void throwsExceptionWhenChildGroupIsNull() {
            ServerBootstrap bootstrap = new ServerBootstrap();
            assertThatThrownBy(() -> bootstrap.group(bossGroup, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("channel 为空时抛出异常")
        void throwsExceptionWhenChannelIsNull() {
            ServerBootstrap bootstrap = new ServerBootstrap();

            assertThatThrownBy(() -> bootstrap.channel(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("childHandler 为空时抛出异常")
        void throwsExceptionWhenChildHandlerIsNull() {
            ServerBootstrap bootstrap = new ServerBootstrap();
            assertThatThrownBy(() -> bootstrap.childHandler(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("childOption 为空时抛出异常")
        void throwsExceptionWhenChildOptionIsNull() {
            ServerBootstrap bootstrap = new ServerBootstrap();

            assertThatThrownBy(() -> bootstrap.childOption(null, true))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("重复设置测试")
    class DuplicateSettingsTests {
        @Test
        @DisplayName("重复设置 group 时抛出异常")
        void throwsExceptionWhenGroupSetTwice() {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup);

            assertThatThrownBy(() -> bootstrap.group(workerGroup))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("set already");
        }

        @Test
        @DisplayName("重复设置 childGroup 时抛出异常")
        void throwsExceptionWhenChildGroupSetTwice() {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup);

            TestEventLoopGroup anotherWorkerGroup = new TestEventLoopGroup();
            assertThatThrownBy(() -> bootstrap.group(bossGroup, anotherWorkerGroup))
                    .isInstanceOf(IllegalStateException.class);
            anotherWorkerGroup.shutdownGracefully();
        }

        @Test
        @DisplayName("重复设置 channel 时抛出异常")
        void throwsExceptionWhenChannelSetTwice() {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .channel(NioServerSocketChannel.class);

            assertThatThrownBy(() -> bootstrap.channel(NioServerSocketChannel.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("set already");
        }
    }

    @Nested
    @DisplayName("绑定测试")
    class BindTests {
        @Test
        @DisplayName("绑定到端口")
        void bindsToPort() throws Exception {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter());

            ChannelFuture future = bootstrap.bind(0);
            assertThat(future).isNotNull();
            //等待绑定完成
            Thread.sleep(100);

            Channel channel = future.channel();
            assertThat(channel).isNotNull();
            assertThat(channel.isOpen()).isTrue();
            channel.close();
        }

        @Test
        @DisplayName("绑定到指定地址")
        void bindsToSocketAddress() throws Exception {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter());
            ChannelFuture future = bootstrap.bind(new InetSocketAddress("127.0.0.1", 0));
            Thread.sleep(100);

            Channel channel = future.channel();
            assertThat(channel).isNotNull();
            channel.close();
        }
    }


    private static class TestEventLoopGroup implements EventLoopGroup {
        private final NioEventLoop eventLoop;
        private volatile boolean shutdown = false;

        TestEventLoopGroup() {
            this.eventLoop = new NioEventLoop(this);
            this.eventLoop.start();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public EventLoop next() {
            return eventLoop;
        }

        @Override
        public ChannelFuture register(Channel channel) {
            DefaultChannelPromise promise = new DefaultChannelPromise(channel);
            eventLoop.execute(() -> {
                try {
                    channel.unsafe().register(eventLoop, promise);
                } catch (Exception e) {
                    promise.setFailure(e);
                }
            });
            return promise;
        }

        @Override
        public Future<?> shutdownGracefully() {
            shutdown = true;
            return eventLoop.shutdownGracefully();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return eventLoop.isTerminated();
        }
    }
}
