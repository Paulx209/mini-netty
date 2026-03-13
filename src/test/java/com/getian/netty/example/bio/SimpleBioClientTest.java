package com.getian.netty.example.bio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BIO 客户端/服务端集成测试
 * <p>
 * 客户端连接到服务端
 * 消息发送和接收
 * 多次消息交互
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-13
 */


@DisplayName("BIO 客户端/服务端集成测试")
public class SimpleBioClientTest {
    private static final int TEST_PORT = 9998;
    private SimpleBioServer server;
    private SimpleBioClient client;

    @BeforeEach
    void setup() throws InterruptedException {
        server = new SimpleBioServer(TEST_PORT);
        server.startInBackGround();
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("客户端可以连接到服务端")
    void clientCanConnectToServer() throws IOException {
        //判断服务器已经启动
        assertThat(server.isRunning()).isTrue();

        client = new SimpleBioClient("localhost", TEST_PORT);
        client.connect();

        //判断客户端是否已经连接
        assertThat(client.isConnected()).isTrue();
    }

    @Test
    @DisplayName("客户端发送hello，收到hello,mini-netty响应")
    void clientReceivesCorrectResponse() throws IOException {
        assertThat(server.isRunning()).isTrue();

        client = new SimpleBioClient("localhost", TEST_PORT);
        client.connect();

        //发送消息
        String response = client.sendAndReceive("hello");
        System.out.println(response);


        assertThat(response).isEqualTo("hello,mini-netty");
    }


    @Test
    @DisplayName("客户端可以发送多条消息")
    void clientCanSendMultipleMessages() throws IOException {
        //1.确认服务端是否存在
        assertThat(server.isRunning()).isTrue();
        //2.获取到client
        client = new SimpleBioClient("localhost", TEST_PORT);
        client.connect();

        //3.发送消息
        String response1 = client.sendAndReceive("haha");
        String response2 = client.sendAndReceive("nihao");
        String response3 = client.sendAndReceive("My name is sonicge");

        //4.接收信息
        assertThat(response1).isEqualTo("hello,mini-netty");
        assertThat(response2).isEqualTo("hello,mini-netty");
        assertThat(response3).isEqualTo("hello,mini-netty");
    }

    @Test
    @DisplayName("客户端关闭后无法发送消息")
    void clientCannotSendAfterClose() throws Exception {
        //1.判断服务器端的状态
        assertThat(server.isRunning()).isTrue();
        //2.创建客户端
        client = new SimpleBioClient("localhost", TEST_PORT);
        client.connect();
        client.close();
        //3.客户端关闭之后发生消息
        try {
            String response = client.sendAndReceive("hello");
        } catch (IOException e) {
            assertThat(e.getMessage()).isEqualTo("客户端未连接");
        }
    }

    @Test
    @DisplayName("完整的客户端-服务端通信流程")
    void fullCommunicationFlow() throws IOException {
        //1.判断Sevrer是否连接
        assertThat(server.isRunning()).isTrue();
        //2.创建客户端
        client = new SimpleBioClient("localhost",TEST_PORT);
        client.connect();

        //3.发送消息
        String response = client.sendAndReceive("hello");

        //4.判断消息是否正确
        assertThat(response).isEqualTo("hello, mini-netty");
    }
}
