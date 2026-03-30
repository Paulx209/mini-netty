package com.getian.netty.integration;

import com.getian.netty.bootstrap.AbstractBootstrap;
import com.getian.netty.bootstrap.Bootstrap;
import com.getian.netty.bootstrap.ServerBootstrap;
import com.getian.netty.channel.*;
import com.getian.netty.channel.nio.NioEventLoop;
import com.getian.netty.channel.nio.NioServerSocketChannel;
import com.getian.netty.channel.nio.NioSocketChannel;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *  Bootstrap 集成测试
 * @Author: sonicge
 * @CreateTime: 2026-03-24
 **/

public class BootstrapIntegrationTest {
    private TestEventLoopGroup bossGroup;
    private TestEventLoopGroup workerGroup;
    private TestEventLoopGroup clientGroup;

    @BeforeEach
    void setup() {
        bossGroup = new TestEventLoopGroup();
        workerGroup = new TestEventLoopGroup();
        clientGroup = new TestEventLoopGroup();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (clientGroup != null) {
            clientGroup.shutdownGracefully();
        }
        Thread.sleep(100);
    }

    @Nested
    @DisplayName("Bootstrap 配置测试")
    class BootstrapConfigurationTests {
        @Test
        @DisplayName("设置 EventLoopGroup")
        void setsEventLoopGroup() {
            Bootstrap bootstrap = new Bootstrap();
            AbstractBootstrap group = bootstrap.group(clientGroup);
            assertThat(bootstrap).isSameAs(group);
            assertThat(bootstrap.group()).isSameAs(clientGroup);
        }

        @Test
        @DisplayName("设置 Channel 类型")
        void setsChannelClass() {
            Bootstrap bootstrap = new Bootstrap();

            Bootstrap result = bootstrap.channel(NioSocketChannel.class);

            assertThat(result).isSameAs(bootstrap);
        }

        @Test
        @DisplayName("设置 handler")
        void setsHandler() {
            Bootstrap bootstrap = new Bootstrap();
            ChannelHandler handler = new ChannelInboundHandlerAdapter();
            Bootstrap result = bootstrap.handler(handler);
            assertThat(result).isSameAs(handler);
        }

        @Test
        @DisplayName("设置 option")
        void setsOption() {
            Bootstrap bootstrap = new Bootstrap();
            Bootstrap res = bootstrap.option(ChannelOption.TCP_NODELAY, true);
            assertThat(res).isSameAs(bootstrap);
        }

        @Test
        @DisplayName("设置远程地址")
        void setsRemoteAddress() {
            Bootstrap bootstrap = new Bootstrap();
            Bootstrap res = bootstrap.remoteAddress("localhost", 8080);
            assertThat(res).isSameAs(bootstrap);
        }

        @Test
        @DisplayName("链式配置")
        void fluentConfiguration() {
            Bootstrap res = new Bootstrap()
                    .group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.AUTO_READ, true)
                    .remoteAddress(8080);
            assertThat(res).isSameAs(res);
        }

        @Test
        @DisplayName("克隆 Bootstrap")
        void clonesBootstrap() {
            Bootstrap original = new Bootstrap()
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .option(ChannelOption.AUTO_READ, true)
                    .group(clientGroup);
            Bootstrap clone = original.clone();
            assertThat(clone).isNotSameAs(original);
            assertThat(clone.group()).isSameAs(original.group());
        }
    }

    @Nested
    @DisplayName("Bootstrap 验证测试")
    class BootstrapValidationTests {
        /**
         * validate参数会对：group channel类型 childHandler进行校验
         */
        @Test
        @DisplayName("未设置 group 时验证失败")
        void failsValidationWithoutGroup() {
            Bootstrap bootstrap = new Bootstrap()
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter());
            //缺少group
            assertThatThrownBy(bootstrap::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("group");
        }

        @Test
        @DisplayName("未设置 channel 时验证失败")
        void failsValidationWithoutChannel() {
            Bootstrap bootstrap = new Bootstrap()
                    .group(clientGroup)
                    .handler(new ChannelInboundHandlerAdapter());
            //缺少channel类型
            assertThatThrownBy(bootstrap::validate).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("未设置 handler 时验证失败")
        void failsValidationWithoutHandler() {
            Bootstrap bootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(NioSocketChannel.class);

            //缺少handler 对于一个clientBootstrap来说 没有handler的话 就是垃圾

            assertThatThrownBy(bootstrap::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("handler");
        }

        @Test
        @DisplayName("完整配置验证通过")
        void passesValidationWithCompleteConfiguration() {
            Bootstrap bootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter());

            Bootstrap res = bootstrap.validate();
            assertThat(res).isSameAs(bootstrap);
        }
    }

    @Nested
    @DisplayName("空值验证测试")
    class NullValidationTests {
        @Test
        @DisplayName("group 为空时抛出异常")
        void throwsExceptionWhenGroupIsNull() {
            Bootstrap bootstrap = new Bootstrap();
            assertThatThrownBy(() -> bootstrap.group(null))
                    .isInstanceOf(NullPointerException.class).hasMessageContaining("group");
        }

        @Test
        @DisplayName("channel 为空时抛出异常")
        void throwsExceptionWhenChannelIsNull() {
            Bootstrap bootstrap = new Bootstrap();
            assertThatThrownBy(() -> bootstrap.channel(null))
                    .isInstanceOf(NullPointerException.class).hasMessageContaining("channel");
        }

        @Test
        @DisplayName("handler 为空时抛出异常")
        void throwsExceptionWhenHandlerIsNull() {
            Bootstrap bootstrap = new Bootstrap();

            assertThatThrownBy(() -> bootstrap.handler(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("connect 远程地址为空时抛出异常")
        void throwsExceptionWhenRemoteAddressIsNull() {
            Bootstrap bootstrap = new Bootstrap();
            assertThatThrownBy(() -> bootstrap.connect(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("连接测试")
    class ConnectTests {
        /**
         * 全流程逻辑梳理，梳理不成功不下播：
         * 1.创建serverBootstrap的时候，可以给childHandler赋值了一个handler，这里先标记一下
         *      1.1 这个handler只有在channelActive事件触发的时候才会被调用。
         * 2.然后服务端启动，调用bind方法，这里的端口号是随机的，所以后面client进行连接的时候，需要获取到端口号。bind方法的流程
         *      2.1 首先对参数进行校验（group channel类型）, 然后创建和初始化channel，init这部分逻辑再看一下
         *      2.2
         * @throws Exception
         */
        @Test
        @DisplayName("连接到服务端")
        void connectsToServer() throws Exception {
            //1.创建服务端
            AtomicBoolean serverConnected = new AtomicBoolean(false);
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            serverConnected.set(true);
                            //这个方法能不能执行到？
                            System.out.println("[Server] 客户端已连接");
                        }
                    });
            //2.服务端启动
            ChannelFuture future = serverBootstrap.bind(0);
            Thread.sleep(200);

            //3.获取到连接信息
            Channel channel = future.channel();
            SocketAddress address = ((AbstractChannel) channel).localAddress();
            int port = ((InetSocketAddress) address).getPort();

            //4.创建客户端
            Bootstrap bootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter());
            ChannelFuture clientFuture = bootstrap.connect("127.0.0.1", port);
            Thread.sleep(200);

            //验证
            assertThat(clientFuture).isNotNull();
            assertThat(clientFuture.channel()).isNotNull();
        }

        @Test
        @DisplayName("使用预设远程地址连接")
        void connectsWithPresetRemoteAddress() throws Exception {
            //1.服务端启动
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter());

            ChannelFuture future = serverBootstrap.bind(0);
            Thread.sleep(200);

            int port = ((InetSocketAddress) ((AbstractChannel) future.channel()).localAddress()).getPort();

            //2.创建客户端
            Bootstrap bootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .remoteAddress(port);

            ChannelFuture clientFuture = bootstrap.connect();
            Thread.sleep(500);

            assertThat(clientFuture).isNotNull();
            assertThat(clientFuture.channel()).isNotNull();
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("场景: 完整的客户端服务端交互")
        void fullClientServerInteraction() throws Exception {
            AtomicReference<String> receivedMessage = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            //服务端：收到消息后回复
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            System.out.println("[Server] 客户端已连接");
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            System.out.println("[Server] 收到消息: " + msg);
                            //继续向下传递
                            ctx.fireChannelRead(msg);
                        }
                    });
            ChannelFuture future = serverBootstrap.bind(0);
            Thread.sleep(200);
            SocketAddress address = ((AbstractChannel) future.channel()).localAddress();
            int port = ((InetSocketAddress) address).getPort();
            //客户端
            Bootstrap bootstrap = new Bootstrap()
                    .group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            System.out.println("[Client] 已连接到服务端");
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            System.out.println("[Client] 收到响应: " + msg);
                            receivedMessage.set(msg.toString());
                            latch.countDown();
                        }
                    });
            ChannelFuture clientFuture = bootstrap.connect("127.0.0.1", port);
            Thread.sleep(300);

            //验证连接成功
            assertThat(clientFuture.channel()).isNotNull();
            assertThat(clientFuture.channel().isOpen()).isTrue();

            // 关闭
            clientFuture.channel().close();
            future.channel().close();
        }

        @Test
        @DisplayName("场景: Bootstrap 与 ServerBootstrap 配合使用")
        void bootstrapWithServerBootstrap() throws Exception {
            CountDownLatch connectionLatch = new CountDownLatch(1);
            //服务端
            ServerBootstrap server = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast("serverHandler", new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                    System.out.println("[Server] 新连接建立");
                                    connectionLatch.countDown();
                                }
                            });
                        }
                    });

            ChannelFuture serverFuture = server.bind(0);
            Thread.sleep(200);


            int port = ((InetSocketAddress) ((AbstractChannel) serverFuture.channel()).localAddress()).getPort();

            // 客户端
            Bootstrap client = new Bootstrap()
                    .group(clientGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast("clientHandler", new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    System.out.println("[Client] 已连接");
                                }
                            });
                        }
                    });

            ChannelFuture clientFuture = client.connect("127.0.0.1", port);
            Thread.sleep(500);


            // 验证
            assertThat(clientFuture.channel()).isNotNull();

            // 清理
            clientFuture.channel().close();
            serverFuture.channel().close();
        }
    }

    private class TestEventLoopGroup implements EventLoopGroup {
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

        /**
         * 将channel注册到EventLoopGroup中
         * @param channel 要注册的 Channel
         * @return
         */
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
