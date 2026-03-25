package com.getian.netty.channel.nio;

import static org.assertj.core.api.Assertions.assertThat;

import com.getian.netty.channel.ChannelFuture;
import com.getian.netty.channel.ChannelHandlerContext;
import com.getian.netty.channel.ChannelId;
import com.getian.netty.channel.ChannelInboundHandler;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;


/**
 * NIO Channel 实现测试
 * 测试 NioServerSocketChannel 和 NioSocketChannel 的基本功能
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-17
 */

public class NioChannelTest {
    private NioEventLoop serverEventLoop;
    private NioEventLoop clientEventLoop;

    @BeforeEach
    void setup() {
        serverEventLoop = new NioEventLoop(null);
        clientEventLoop = new NioEventLoop(null);

        //先让selector起来，然后进行监听
        serverEventLoop.start();
        clientEventLoop.start();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (serverEventLoop != null) {
            serverEventLoop.shutdownGracefully();
        }
        if (clientEventLoop != null) {
            clientEventLoop.shutdownGracefully();
        }
        Thread.sleep(200);
    }

    /**
     * 主要是ServerSocketChannel的测试
     */

    @Nested
    @DisplayName("NioServerSocketChannel 测试")
    class ServerChannelTests {

        /**
         * new NioServerSocketChannel之后会执行哪些逻辑？
         * 1.首先会创建一个ServerSocketChannel，然后设置成非阻塞的，因为selector只支持非阻塞的channel
         * 2.还会给你分配一个channelId、pipeline、parent等。
         * 3.包括一些状态的变量：active open 等
         */
        @Test
        @DisplayName("创建 NioServerSocketChannel")
        void createsServerChannel() {
            NioServerSocketChannel serverChannel = new NioServerSocketChannel();

            assertThat(serverChannel).isNotNull();
            assertThat(serverChannel.isOpen()).isTrue();
            assertThat(serverChannel.isActive()).isFalse(); //未绑定
            assertThat(serverChannel.id()).isNotNull();
            assertThat(serverChannel.pipeline()).isNotNull();

            serverChannel.close();
        }

        /**
         * 绑定到端口之后 && open()了 serverSocketChannel的active就是true了  -> NioServerSocketChannel中的方法
         * 绑定成功是会有对应的事件发送到pipeline中的，也会有成功的响应返回。successFuture
         */
        @Test
        @DisplayName("绑定到端口")
        void bindToPost() {
            NioServerSocketChannel serverSocketChannel = new NioServerSocketChannel();
            //底层也是获取到对应的ServerSocketChannel类对象，然后调用bind()方法，绑定随机端口之后
            ChannelFuture future = serverSocketChannel.bind(0);
            assertThat(future.isSuccess()).isTrue();
            assertThat(serverSocketChannel.isActive()).isTrue();
            assertThat(serverSocketChannel.localAddress()).isNotNull();

            System.out.println(serverSocketChannel.localAddress());
        }

        @Test
        @DisplayName("关闭服务端")
        void closesServerChannel() {
            NioServerSocketChannel serverSocketChannel = new NioServerSocketChannel();
            //底层也是获取到对应的ServerSocketChannel类对象，然后调用bind()方法，绑定随机端口之后
            ChannelFuture future = serverSocketChannel.bind(0);


            assertThat(serverSocketChannel.isOpen()).isTrue();
            assertThat(serverSocketChannel.isActive()).isTrue();

            //关闭服务端
            serverSocketChannel.close();

            assertThat(serverSocketChannel.isOpen()).isFalse();
            assertThat(serverSocketChannel.isActive()).isFalse();
        }

        /**
         * 这个register意味着什么来着？？我擦
         * 1.意味着将eventLoop挂载到serverSocketChannel的属性上
         * 2.还意味着将eventLoop中的selector和serverSocketChannel中的javaChannel()进行绑定
         * 3.绑定成功之后，registered变成true；然后还会触发对应的事件:channelRegistered、channelActive事件
         */
        @Test
        @DisplayName("注册到eventLoop")
        void registerToEventLoop() {
            try {
                NioServerSocketChannel serverSocketChannel = new NioServerSocketChannel();
                ChannelFuture future = serverSocketChannel.register(serverEventLoop);

                Thread.sleep(100);

                assertThat(serverSocketChannel.isRegistered()).isTrue();
                assertThat(serverSocketChannel.eventLoop()).isEqualTo(serverEventLoop);

                serverSocketChannel.close();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


    /**
     * 主要是SocketChannel的测试
     */

    /**
     * 来看一下NioSocketChannel中的构造函数中涉及到了哪些流程？
     * 1.会创建一个SocketChannel
     * 2.然后创建channelId，创建pipeline
     * 3.然后将socketChannel赋值给ch属性，设置为非阻塞模式
     */
    @Nested
    @DisplayName("NioSocketChannel 测试")
    class SocketChannelTests {
        @Test
        @DisplayName("创建 NioSocketChannel")
        void createsSocketChannel() {
            NioSocketChannel socketChannel = new NioSocketChannel();
            assertThat(socketChannel.isOpen()).isTrue();
            assertThat(socketChannel.isActive()).isFalse();
            assertThat(socketChannel.id()).isNotNull();
            assertThat(socketChannel.pipeline()).isNotNull();
            assertThat(socketChannel).isNotNull();
        }

        @Test
        @DisplayName("关闭 Socket Channel")
        void closesSocketChannel() {
            NioSocketChannel socketChannel = new NioSocketChannel();

            assertThat(socketChannel.isOpen()).isTrue();

            socketChannel.close();

            assertThat(socketChannel.isOpen()).isFalse();
        }

        /**
         * 将NioServerChannel注册到EventLoop上，具体的流程如下
         * 1.首先将eventLoop赋值给socketChannel的属性上。
         * 2.然后执行doRegister()方法，该方法会将eventLoop中的selector 和 NioSocketChannel中的channel进行绑定
         * 3.然后发送两个通知，一个是registered的，一个是active的
         *
         * @throws Exception
         */
        @Test
        @DisplayName("注册到 EventLoop")
        void registersToEventLoop() throws Exception {
            NioSocketChannel socketChannel = new NioSocketChannel();
            ChannelFuture future = socketChannel.register(clientEventLoop);

            Thread.sleep(100);

            assertThat(socketChannel.isRegistered()).isTrue();
            assertThat(socketChannel.eventLoop()).isEqualTo(clientEventLoop);
        }
    }

    //总结一下,selector其实和端口号没关系，只和监听的channel有关系，无论是服务端的channel还是客户端channel都可以被监听，所以register的方法是通用的

    @Nested
    @DisplayName("Channel ID 测试")
    class ChannelIdTests {
        @Test
        @DisplayName("每个 Channel 有唯一 ID")
        void channelHasUniqueId() {
            NioServerSocketChannel channel1 = new NioServerSocketChannel();
            NioServerSocketChannel channel2 = new NioServerSocketChannel();

            ChannelId id1 = channel1.id();
            ChannelId id2 = channel2.id();


            assertThat(id1).isNotNull();
            assertThat(id2).isNotNull();

            assertThat(id1.asLongText()).isNotEqualTo(id2.asLongText());

            channel1.close();
            channel2.close();
        }


        @Test
        @DisplayName("ChannelId 有短格式和长格式")
        void channelIdHasShortAndLongText() {
            NioSocketChannel channel = new NioSocketChannel();

            assertThat(channel.id().asShortText()).isNotEmpty();
            assertThat(channel.id().asLongText()).isNotEmpty();
            assertThat(channel.id().asShortText().length()).isLessThan(channel.id().asLongText().length());

            channel.close();
        }
    }

    @Nested
    @DisplayName("Pipeline 测试")
    class PipelineTests {
        @Test
        @DisplayName("Channel 创建时自动创建 Pipeline")
        void channelHasPipeline() {
            NioSocketChannel channel = new NioSocketChannel();

            assertThat(channel.pipeline()).isNotNull();
            assertThat(channel.pipeline().channel()).isEqualTo(channel);

            channel.close();
        }


        @Test
        @DisplayName("Pipeline 可以添加 Handler")
        void pipelineCanAddHandler() {
            NioSocketChannel channel = new NioSocketChannel();
            TestHandler lastHandler = new TestHandler();
            TestHandler firstHandler = new TestHandler();
            channel.pipeline().addLast("test", lastHandler);
            channel.pipeline().addFirst("first", firstHandler);

            channel.close();
        }
    }

    @Nested
    @DisplayName("验收场景")
    class AcceptanceScenarioTests {

        @Test
        @DisplayName("验收场景1: 创建服务端 Channel 并绑定端口")
        void acceptanceScenario1() {
            NioServerSocketChannel serverChannel = new NioServerSocketChannel();
            //随机绑定一个端口号
            ChannelFuture future = serverChannel.bind(0);

            assertThat(serverChannel.isActive()).isTrue();

            assertThat(future.isSuccess()).isTrue();

            assertThat(serverChannel.localAddress()).isInstanceOf(InetSocketAddress.class);

            assertThat(serverChannel.javaChannel()).isInstanceOf(ServerSocketChannel.class);

            serverChannel.close();
        }

        @Test
        @DisplayName("验收场景2: 创建客户端 Channel 并准备连接")
        void acceptanceScenario2() throws Exception {
            NioSocketChannel socketChannel = new NioSocketChannel();

            ChannelFuture future = socketChannel.register(clientEventLoop);
            Thread.sleep(100);

            assertThat(future.isSuccess()).isTrue();
            assertThat(socketChannel.isRegistered()).isTrue();

            socketChannel.close();
        }

        @Test
        @DisplayName("验收场景3: Channel Pipeline 添加多个 Handler")
        void acceptanceScenario3() {
            // Given: 创建 Channel
            NioSocketChannel channel = new NioSocketChannel();

            // When: 添加多个 Handler
            channel.pipeline().addLast("handler1", new TestHandler());
            channel.pipeline().addLast("handler2", new TestHandler());
            channel.pipeline().addFirst("handler0", new TestHandler());

            // Then: Handler 按正确顺序排列
            assertThat(channel.pipeline().names()).containsExactly("handler0", "handler1", "handler2");

            channel.close();
        }
    }

    private static class TestHandler implements ChannelInboundHandler {
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {

        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {

        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {

        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {

        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {

        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {

        }
    }
}
