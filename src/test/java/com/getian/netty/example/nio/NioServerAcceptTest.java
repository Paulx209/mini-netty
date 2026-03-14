package com.getian.netty.example.nio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试 NioServer 处理客户端连接的能力
 * 服务端启动和停止
 * 接受客户端连接
 * 客户端 Channel 注册到 Selector
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-14
 */

public class NioServerAcceptTest {
    private static final int TEST_PORT = 9996;
    private NioServer server;

    @BeforeEach
    void setup() {
        server = new NioServer(TEST_PORT);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("服务器端可以正常启动和停止")
    void serverCanStartAndStop() throws InterruptedException, IOException {
        assertThat(server.isRunning()).isFalse();

        //然后server启动
        server.startInBackground();
        Thread.sleep(100);

        assertThat(server.isRunning()).isTrue();
        assertThat(server.getPort()).isEqualTo(TEST_PORT);
        assertThat(server.getSelector()).isNotNull();
        assertThat(server.getSelector().isOpen()).isTrue();

        //停止服务端
        server.stop();
        Thread.sleep(100);
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    @DisplayName("服务端可以接受客户端连接")
    void serverCanAcceptClientConnection() throws Exception {
        assertThat(server.isRunning()).isFalse();

        //1.server启动
        server.startInBackground();

        //2.客户端连接
        SocketChannel clientChannel = SocketChannel.open();
        clientChannel.connect(new InetSocketAddress("localhost", TEST_PORT));

        //3.连接成功
        assertThat(clientChannel.isConnected()).isTrue();

        Thread.sleep(100);
        assertThat(server.getSelector().keys().size()).isEqualTo(2);
        clientChannel.close();
    }

    @Test
    @DisplayName("服务端可以接受多个客户端连接")
    void serverCanAcceptMultipleConnections() throws Exception{
        assertThat(server.isRunning()).isFalse();
        //1.server启动
        server.startInBackground();

        //2.创建多个客户端
        SocketChannel clientChannel1 = SocketChannel.open();
        SocketChannel clientChannel2 = SocketChannel.open();
        SocketChannel clientChannel3 = SocketChannel.open();

        clientChannel1.connect(new InetSocketAddress("localhost",TEST_PORT));
        clientChannel2.connect(new InetSocketAddress("localhost",TEST_PORT));
        clientChannel3.connect(new InetSocketAddress("localhost",TEST_PORT));

        //3.判断是否连接成功
        assertThat(clientChannel1.isConnected()).isTrue();
        assertThat(clientChannel2.isConnected()).isTrue();
        assertThat(clientChannel3.isConnected()).isTrue();

        //4.判断selector的数量
        Thread.sleep(100);
        assertThat(server.getSelector().keys().size()).isEqualTo(4);
        clientChannel1.close();
        clientChannel2.close();
        clientChannel3.close();
    }

    @Test
    @DisplayName("客户端 Channel 注册了 READ 事件")
    void clientChannelRegisteredWithReadEvent() throws Exception{
        assertThat(server.isRunning()).isFalse();
        //1.server启动
        server.startInBackground();

        Thread.sleep(500);
        //2.客户端连接
        SocketChannel clientChannel = SocketChannel.open();
        clientChannel.connect(new InetSocketAddress("localhost", TEST_PORT));

        //3.等待服务器处理
        Thread.sleep(100);

        //4.验证情况
        boolean hasReadInterest = false;
        Set<SelectionKey> keys = server.getSelector().keys();
        for(SelectionKey key :keys){
            if(key.channel() instanceof SocketChannel){
                if((key.interestOps() & SelectionKey.OP_READ) != 0 ){
                    hasReadInterest = true;
                    break;
                }
            }
        }
        assertThat(hasReadInterest).isTrue();
        clientChannel.close();
    }

    @Test
    @DisplayName("EventLoop运行时，注册ServerSocketChannel能接收到ACCEPT事件")
    void acceptanceScenario() throws Exception{
        //1.运行
        server.startInBackground();//此时已经有一个SocketServerChannel绑定了
        Thread.sleep(100);
        assertThat(server.isRunning()).isTrue();

        //2.发起连接
        SocketChannel clientChannel = SocketChannel.open();
        clientChannel.connect(new InetSocketAddress("localhost",TEST_PORT));
        Thread.sleep(200); //等待处理

        //3.判断结果
        Set<SelectionKey> keys = server.getSelector().keys();
        int socketChannelCount = 0;
        for(SelectionKey key : keys){
            if(key.channel() instanceof SocketChannel){
                socketChannelCount++;
            }
        }
        assertThat(socketChannelCount).isEqualTo(1);
    }
}
