package com.getian.netty.example.nio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NIO Selector 测试
 * @Author: sonicge
 * @CreateTime: 2026-03-14
 */

@DisplayName("NIO Selector 测试")
public class NioSelectorTest {
    private Selector selector;
    private ServerSocketChannel serverChannel;

    @BeforeEach
    void setup() throws IOException {
        selector = Selector.open();
    }

    @AfterEach
    void tearDown() throws IOException {
        if(serverChannel !=null && serverChannel.isOpen()){
            serverChannel.close();
        }
        if(selector!=null && selector.isOpen()){
            selector.close();
        }
    }

    @Test
    @DisplayName("可以创建和关闭 Selector")
    void canCreateAndCloseSelector() throws IOException {
        assertThat(selector.isOpen()).isTrue();

        Selector newSelector = Selector.open();

        assertThat(newSelector.isOpen()).isTrue();

        newSelector.close();
        assertThat(newSelector.isOpen()).isFalse();
    }

    @Test
    @DisplayName("Channel 必须是非阻塞模式才能注册到 Selector")
    void channelMustBeNonBlockingForRegistration() throws IOException {
        //1.创建非阻塞 ServerSocketChannel
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(0));

        //2.注册到 Selector
        SelectionKey key = serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        //3.断言
        assertThat(key).isNotNull();
        assertThat(key.isValid()).isTrue();
        assertThat(key.channel()).isEqualTo(serverChannel);
    }

    @Test
    @DisplayName("可以在 SelectionKey 上附加数据")
    void canAttachDataToSelectionKey() throws IOException{
        //1.创建并注册Channel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(0));

        SelectionKey key = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        //2.添加附加数据
        key.attach("测试111");

        //3.获取附加数据
        assertThat(key.attachment()).isEqualTo("测试111");
    }

    @Test
    @DisplayName("多个 Channel 可以注册到同一个 Selector")
    void multipleChannelsCanRegisterToSameSelector() throws IOException {
        //1.创建多个 Channel
        ServerSocketChannel channel1 = ServerSocketChannel.open();
        channel1.configureBlocking(false);
        channel1.bind(new InetSocketAddress(0));

        ServerSocketChannel channel2 = ServerSocketChannel.open();
        channel2.configureBlocking(false);
        channel2.bind(new InetSocketAddress(0));

        //2.注册到同一个 Selector
        channel1.register(selector, SelectionKey.OP_ACCEPT);
        channel2.register(selector, SelectionKey.OP_ACCEPT);

        //3.Selector 包含两个 key
        assertThat(selector.keys()).hasSize(2);

        //4.关闭
        channel1.close();
        channel2.close();
    }

    @Test
    @DisplayName("select() 返回就绪的 Channel 数量")
    void selectReturnsReadyChannelCount() throws Exception{
        //1.创建对应的Channel
        ServerSocketChannel serverChannel =ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(0));
        int port = serverChannel.socket().getLocalPort();
        serverChannel.register(selector,SelectionKey.OP_ACCEPT);

        //2.然后开启一个线程
        Thread connectThread = new Thread(() -> {
            try {
                Thread.sleep(100);
                SocketChannel clientChannel = SocketChannel.open();
                clientChannel.connect(new InetSocketAddress("localhost",port));
                Thread.sleep(100);
                clientChannel.close();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        connectThread.start();

        //3.selector的select()方法返回了多少个Channel数量？
        int selectKeys = selector.select(1000);
        assertThat(selectKeys).isGreaterThan(0);

        connectThread.join(2000);
    }


    @Test
    @DisplayName("selectNow() 不阻塞立即返回")
    void selectNowReturnsImmediately() throws IOException{
        //1.创建ServerChannel
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(0));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        //2.调用selectNow
        long startTime = System.currentTimeMillis();
        int readyCount = selector.selectNow();
        long elapsed = System.currentTimeMillis() - startTime;

        //3.断言
        assertThat(elapsed).isLessThan(100);
        assertThat(readyCount).isEqualTo(0);
    }

    @Test
    @DisplayName("处理 ACCEPT 事件后可以注册 READ 事件")
    void canRegisterReadEventAfterAccept() throws Exception{
        //1.创建Channel连接
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(0));
        int port = serverChannel.socket().getLocalPort();
        serverChannel.register(selector,SelectionKey.OP_ACCEPT);

        //2.开启一个线程 充当客户端连接
        Thread clientThread = new Thread(() -> {
            try {
                Thread.sleep(100);
                SocketChannel clientChannel = SocketChannel.open();
                //创建连接
                clientChannel.connect(new InetSocketAddress("localhost",port));
                ByteBuffer buffer = ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8));
                clientChannel.write(buffer);
                Thread.sleep(100);
                clientChannel.close();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        clientThread.start();
        //3.处理accept事件
        int readyCount = selector.select(1000);
        assertThat(readyCount).isGreaterThan(0);

        SocketChannel clientChannel = null ;
        //4.处理 ACCEPT 事件后可以注册 READ 事件
        Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
        while(iterator.hasNext()){
            SelectionKey key = iterator.next();
            if(key.isAcceptable()){
                ServerSocketChannel server = (ServerSocketChannel) key.channel();
                clientChannel = server.accept();
                clientChannel.configureBlocking(false);
                clientChannel.register(selector,SelectionKey.OP_READ);
            }
            iterator.remove();
        }

        // Then: 客户端 Channel 已注册 READ 事件
        assertThat(clientChannel).isNotNull();
        assertThat(selector.keys()).hasSize(2);

        readyCount = selector.select(1000);
        if(readyCount > 0){
            for(SelectionKey key : selector.keys()){
                if(key.isReadable()){
                    //如果是读取数据的
                    SocketChannel channel = (SocketChannel)key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(256);
                    int bytesRead = channel.read(buffer);
                    buffer.flip();
                    byte[] data = new byte[bytesRead];
                    buffer.get(data);
                    System.out.println("读取到的数据为:" + new String(data));
                }
            }
        }

        clientThread.join(2000);
        if (clientChannel != null) {
            clientChannel.close();
        }
    }

    @Test
    @DisplayName("wakeup() 可以唤醒阻塞的 select()")
    void wakeupCanUnblockSelect() throws Exception{
        //1.创建对应的Channel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(0));
        serverChannel.register(selector,SelectionKey.OP_ACCEPT);

        //2.然后开启一个异步线程进行唤醒
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(200);
                selector.wakeup();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();

        //3.selector开始进行select
        long startTime = System.currentTimeMillis();
        selector.select(500);
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println(elapsed);

        assertThat(elapsed).isLessThan(500);
    }

}
