package com.getian.netty.example.nio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NIO 客户端/服务端集成测试
 * 客户端连接到服务端
 * 消息发送和接收
 * 多客户端并发访问
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-14
 */

public class NioClientServerTest {
    private static final int TEST_PORT = 9995;
    private NioServer nioServer;

    @BeforeEach
    void setup() throws InterruptedException {
        nioServer = new NioServer(TEST_PORT);
        nioServer.startInBackground();
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        if (nioServer != null) {
            nioServer.stop();
        }
    }


    @Test
    @DisplayName("NIO客户端可以连接到NIO服务端")
    void clientCanConnectToServer() throws IOException {
        try (NioClient client = new NioClient("localhost", TEST_PORT);) {
            client.connect();
            assertThat(client.isConnected()).isTrue();
        }
    }

    /**
     * 整个流程为:server启动 -> ServerSocketChannel监听端口号 -> NioClient发起连接(SocketChannel) ->
     *
     * @throws IOException
     */
    @Test
    @DisplayName("客户端发送hello，收到hello, mini-netty响应")
    void clientReceivesCorrectResponse() throws IOException {
        try (NioClient client = new NioClient("localhost", TEST_PORT)) {
            client.connect();
            String response = client.sendAndReceive("hello");
            assertThat(response).isEqualTo("hello,mini-netty");
        }
    }

    /**
     * 客户端可以发送多条消息
     *
     * @throws IOException
     */
    @Test
    @DisplayName("客户端可以发送多条消息")
    void clientCanSendMultipleMessages() throws IOException {
        NioClient client = new NioClient("localhost", TEST_PORT);
        client.connect();
        String response1 = client.sendAndReceive("nihao");
        String response2 = client.sendAndReceive("hello");
        String response3 = client.sendAndReceive("sonicge");

        assertThat(response1).isEqualTo("hello,mini-netty");
        assertThat(response2).isEqualTo("hello,mini-netty");
        assertThat(response3).isEqualTo("hello,mini-netty");
    }

    @Test
    @DisplayName("NIO服务端可以同时处理多个客户端")
    void serverHandlesMultipleClients() throws Exception {
        // Given: 服务端已启动
        assertThat(nioServer.isRunning()).isTrue();

        int clientCount = 5;
        CountDownLatch latch = new CountDownLatch(clientCount);
        AtomicInteger successCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);

        // When: 多个客户端同时连接并发送消息
        for (int i = 0; i < clientCount; i++) {
            executor.submit(() -> {
                try (NioClient client = new NioClient("localhost", TEST_PORT)) {
                    client.connect();
                    String response = client.sendAndReceive("test");
                    if ("hello, mini-netty".equals(response)) {
                        successCount.incrementAndGet();
                    }
                } catch (IOException e) {
                    System.err.println("Client failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有客户端完成
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then: 所有客户端都成功
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(clientCount);
    }

    @Test
    @DisplayName("服务端已启动，客户端连接发送hello，收到hello, mini-netty")
    void acceptanceScenario1() throws IOException {
        NioClient client = new NioClient("localhost", TEST_PORT);
        client.connect();
        String response = client.sendAndReceive("hello");
        assertThat(response).isEqualTo("hello,mini-netty");
        client.close();
    }

    @Test
    @DisplayName("多个客户端同时连接，每个都能正常收发消息")
    void acceptanceScenario2() throws Exception {
        List<NioClient> clientList = new ArrayList<>();
        List<String> responseList = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            NioClient client = new NioClient("localhost", TEST_PORT);
            client.connect();
            clientList.add(client);
            String response = client.sendAndReceive("hello");
            responseList.add(response);
        }
        assertThat(responseList).allMatch(response -> "hello,mini-netty".equals(response));
    }

}
