package com.getian.netty.example.bio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * SimpleBioServer 单元测试
 * <p>
 * 测试 BIO 服务端的基本功能：
 * <p>
 * 服务端启动和停止
 * 客户端连接和消息收发
 * 服务端正确响应 "hello, mini-netty"
 */

class SimpleBioServerTest {
    private static final int TEST_PORT = 9999;
    private SimpleBioServer server;

    @BeforeEach
    void setUp() {
        server = new SimpleBioServer(TEST_PORT);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("测试服务端可以正常开始和停止")
    void serverCanStartAndStop() throws InterruptedException {
        //1.服务器暂未启动,判断running状态
        assertThat(server.isRunning()).isFalse();


        //2.启动服务器
        server.startInBackGround();
        Thread.sleep(100); // 等待服务器启动
        assertThat(server.isRunning()).isTrue();
        assertThat(server.getPort()).isEqualTo(TEST_PORT);

        //3.停止服务端
        server.stop();
        Thread.sleep(100); //等待服务端停止
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    @DisplayName("客户端发送hello，服务端返回hello, mini-netty")
    public void serverRespondsWithHelloMiniNetty() throws InterruptedException, IOException {
        server.startInBackGround();
        Thread.sleep(100);
        assertThat(server.isRunning()).isTrue();

        //客户端连接并且发送消息
        try (Socket clientSocket = new Socket("localhost", TEST_PORT);
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            writer.println("hello");
            writer.flush();
            String response = reader.readLine();
            assertThat(response).isEqualTo("hello,mini-netty");
        }
    }

    @Test
    @DisplayName("客户端发送任意消息，服务端都返回hello,mini-netty")
    public void serverRespondsToAnyMessage() throws InterruptedException, IOException {
        server.startInBackGround();
        Thread.sleep(100);
        assertThat(server.isRunning()).isTrue();


        try (Socket clientSocket = new Socket("localhost", TEST_PORT);
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(clientSocket.getInputStream()))) {

            //发送第一条消息
            writer.println("test message 1");
            String response1 = reader.readLine();
            assertThat(response1).isEqualTo("hello,mini-netty");

            // 发送第二条消息
            writer.println("another message");
            String response2 = reader.readLine();
            assertThat(response2).isEqualTo("hello,mini-netty");
        }
    }

    @Test
    @DisplayName("服务器不会受到客户端的断开影响")
    public void serverHandlesClientDisconnection() throws InterruptedException, IOException {
        server.startInBackGround();
        Thread.sleep(100);
        assertThat(server.isRunning()).isTrue();

        Socket clientSocket = new Socket("localhost", TEST_PORT);
        clientSocket.close();

        //判断server是否还在运行
        Thread.sleep(100);
        assertThat(server.isRunning()).isTrue();
    }

}
