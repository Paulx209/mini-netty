package com.getian.netty.channel;

import com.getian.netty.channel.nio.NioEventLoop;
import com.getian.netty.channel.nio.NioServerSocketChannel;
import com.getian.netty.channel.nio.NioSocketChannel;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: sonicge
 * @CreateTime: 2026-03-19
 */

public class ChannelUnsafeTest {
    private NioEventLoop bossLoop;
    private NioEventLoop workerLoop;

    @BeforeEach
    void setUp() {
        bossLoop = new NioEventLoop(null);
        workerLoop = new NioEventLoop(null);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (bossLoop != null) {
            bossLoop.shutdownGracefully();
        }
        if (workerLoop != null) {
            workerLoop.shutdownGracefully();
        }
        Thread.sleep(100);
    }

    @Nested
    @DisplayName("Unsafe 接口存在性测试")
    class UnsafeInterfaceTests {
        @Test
        @DisplayName("Unsafe 接口定义在 Channel 内部")
        void unsafeInterfaceExistsInChannel() {
            Class<?>[] innerClasses = Channel.class.getDeclaredClasses();
            boolean found = false;
            for (Class<?> clazz : innerClasses) {
                boolean exist = clazz.getSimpleName().equals("UnSafe");
                if (exist) {
                    found = true;
                    break;
                }
            }
            assertThat(found).isTrue();
        }

        @Test
        @DisplayName("Channel 提供 unsafe() 方法")
        void channelProvidesUnsafeMethod() throws NoSuchMethodException {
            Method method = Channel.class.getMethod("unsafe");
            assertThat(method).isNotNull();
        }

        @Test
        @DisplayName("Unsafe 接口包含必要的方法")
        void unsafeHasRequiredMethods() throws NoSuchMethodException {
            Class<?> unsafeClass = Channel.UnSafe.class;

            // 验证所有必要的方法
            assertThat(unsafeClass.getMethod("register", EventLoop.class, ChannelPromise.class)).isNotNull();
            assertThat(unsafeClass.getMethod("bind", java.net.SocketAddress.class, ChannelPromise.class)).isNotNull();
            assertThat(unsafeClass.getMethod("connect", java.net.SocketAddress.class,
                    java.net.SocketAddress.class, ChannelPromise.class)).isNotNull();
            assertThat(unsafeClass.getMethod("disconnect", ChannelPromise.class)).isNotNull();
            assertThat(unsafeClass.getMethod("close", ChannelPromise.class)).isNotNull();
            assertThat(unsafeClass.getMethod("beginRead")).isNotNull();
            assertThat(unsafeClass.getMethod("write", Object.class, ChannelPromise.class)).isNotNull();
            assertThat(unsafeClass.getMethod("flush")).isNotNull();
        }
    }

    @Nested
    @DisplayName("ServerSocketChannel Unsafe 测试")
    class ServerChannelUnsafeTests {
        @Test
        @DisplayName("ServerSocketChannel 提供 Unsafe 实例")
        void serverChannelProvidesUnsafe() {
            NioServerSocketChannel serverSocketChannel = new NioServerSocketChannel();
            Channel.UnSafe unsafe = serverSocketChannel.unsafe();
            assertThat(unsafe).isNotNull();
            assertThat(unsafe).isInstanceOf(Channel.UnSafe.class);
        }

        @Test
        @DisplayName("通过 Unsafe 注册到 EventLoop")
        void registerViaUnsafe() throws InterruptedException {
            //1.创建ServerSocketChannel
            NioServerSocketChannel serverChannel = new NioServerSocketChannel();
            //2.创建promise
            DefaultChannelPromise promise = new DefaultChannelPromise(serverChannel);
            //3.执行unsafe中的register方法
            serverChannel.unsafe().register(bossLoop, promise);
            //4.等待
            Thread.sleep(200);
            //5.assertThat
            assertThat(serverChannel.isRegistered()).isTrue();
            assertThat(serverChannel.eventLoop()).isEqualTo(bossLoop);
        }

        @Test
        @DisplayName("通过 Unsafe 绑定地址")
        void bindViaUnsafe() throws InterruptedException {
            NioServerSocketChannel serverChannel = new NioServerSocketChannel();
            DefaultChannelPromise registerPromise = new DefaultChannelPromise(serverChannel);

            serverChannel.unsafe().register(bossLoop, registerPromise);

            Thread.sleep(200);

            assertThat(serverChannel.isRegistered()).isTrue();

            DefaultChannelPromise bindPromise = new DefaultChannelPromise(serverChannel);
            bossLoop.execute(() -> {
                serverChannel.unsafe().bind(new InetSocketAddress(0), bindPromise);
            });

            Thread.sleep(200);
            assertThat(serverChannel.isActive()).isTrue();
        }

        @Test
        @DisplayName("通过 Unsafe 关闭 Channel")
        void closeViaUnsafe() throws InterruptedException {
            NioServerSocketChannel serverChannel = new NioServerSocketChannel();

            try {
                DefaultChannelPromise registerPromise = new DefaultChannelPromise(serverChannel);
                serverChannel.unsafe().register(bossLoop, registerPromise);
                Thread.sleep(200);

                DefaultChannelPromise closePromise = new DefaultChannelPromise(serverChannel);
                bossLoop.execute(() -> {
                    serverChannel.unsafe().close(closePromise);
                });
                Thread.sleep(200);

                assertThat(serverChannel.isOpen()).isFalse();
            } finally {
                serverChannel.close();
            }
        }
    }


    @Nested
    @DisplayName("SocketChannel Unsafe 测试")
    class SocketChannelUnsafeTests {

        @Test
        @DisplayName("SocketChannel 提供 Unsafe 实例")
        void socketChannelProvidesUnsafe() {
            NioSocketChannel socketChannel = new NioSocketChannel();
            Channel.UnSafe unsafe = socketChannel.unsafe();
            assertThat(unsafe).isNotNull();
            assertThat(unsafe).isInstanceOf(Channel.UnSafe.class);
        }

        @Test
        @DisplayName("通过 Unsafe 注册 SocketChannel")
        void registerSocketChannelViaUnsafe() throws InterruptedException {
            //1.创建socketChannel
            NioSocketChannel socketChannel = new NioSocketChannel();
            //2.创建promise
            DefaultChannelPromise promise = new DefaultChannelPromise(socketChannel);
            //3.创建unsafe
            Channel.UnSafe unsafe = socketChannel.unsafe();
            //4.register
            unsafe.register(workerLoop, promise);

            Thread.sleep(200);

            assertThat(socketChannel.isRegistered()).isTrue();
            assertThat(socketChannel.isOpen()).isTrue();

        }
    }

    @Nested
    @DisplayName("Promise 回调测试")
    class PromiseCallbackTests {
        @Test
        @DisplayName("注册成功时 Promise 设置成功")
        void promiseSuccessOnRegister() throws InterruptedException {
            NioServerSocketChannel serverChannel = new NioServerSocketChannel();

            DefaultChannelPromise promise = new DefaultChannelPromise(serverChannel);

            serverChannel.unsafe().register(bossLoop, promise);

            Thread.sleep(200);

            assertThat(promise.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("重复注册时 Promise 设置失败")
        void promiseFailureOnDuplicateRegister() throws InterruptedException {
            NioServerSocketChannel serverChannel = new NioServerSocketChannel();

            try {
                // 第一次注册
                DefaultChannelPromise promise1 = new DefaultChannelPromise(serverChannel);
                serverChannel.unsafe().register(bossLoop, promise1);
                Thread.sleep(100);

                // 第二次注册应该失败
                DefaultChannelPromise promise2 = new DefaultChannelPromise(serverChannel);
                serverChannel.unsafe().register(bossLoop, promise2);
                Thread.sleep(100);

                assertThat(promise2.isDone()).isTrue();
                assertThat(promise2.isSuccess()).isFalse();
                assertThat(promise2.cause()).isInstanceOf(IllegalStateException.class);
            } finally {
                serverChannel.close();
            }
        }
    }


    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("完整的服务端启动流程通过 Unsafe")
        void fullServerStartupViaUnsafe() throws InterruptedException {
            //1.注册
            NioServerSocketChannel serverChannel = new NioServerSocketChannel();

            DefaultChannelPromise registerPromise = new DefaultChannelPromise(serverChannel);

            serverChannel.unsafe().register(bossLoop, registerPromise);

            Thread.sleep(200);
            assertThat(serverChannel.isRegistered()).isTrue();


            //2.绑定
            DefaultChannelPromise bindPromise = new DefaultChannelPromise(serverChannel);
            bossLoop.execute(() -> {
                serverChannel.unsafe().bind(new InetSocketAddress(0), bindPromise);
            });
            Thread.sleep(200);
            assertThat(serverChannel.isActive()).isTrue();

            //3.close关闭
            DefaultChannelPromise closePromise = new DefaultChannelPromise(serverChannel);
            bossLoop.execute(() -> {
                serverChannel.unsafe().close(closePromise);
            });
            Thread.sleep(200);

            assertThat(serverChannel.isOpen()).isFalse();

        }

        @Test
        @DisplayName("客户端 Channel 通过 Unsafe 完成注册和关闭")
        void clientChannelViaUnsafe() throws InterruptedException {
            NioSocketChannel socketChannel = new NioSocketChannel();

            try {
                // 1. 注册
                DefaultChannelPromise registerPromise = new DefaultChannelPromise(socketChannel);
                socketChannel.unsafe().register(workerLoop, registerPromise);
                Thread.sleep(200);
                assertThat(socketChannel.isRegistered()).isTrue();

                // 2. 关闭
                DefaultChannelPromise closePromise = new DefaultChannelPromise(socketChannel);
                workerLoop.execute(() -> socketChannel.unsafe().close(closePromise));
                Thread.sleep(200);
                assertThat(socketChannel.isOpen()).isFalse();

            } finally {
                socketChannel.close();
            }
        }
    }


}
