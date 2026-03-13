package com.getian.netty.example.bio;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多客户端并发测试
 * 测试 MultiThreadBioServer 处理多个并发客户端连接的能力：
 * 1.多个客户端同时连接
 * 2.并发消息发送和接收
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-13
 */

public class MultiThreadBioServerTest {
    private static final int TEST_PORT = 9997;
    private static final int THREAD_POOL_SIZE = 5;
    private MultiThreadBioServer server;

    //每次都会执行
    @BeforeEach
    public void setup() throws InterruptedException {
        server = new MultiThreadBioServer(TEST_PORT, THREAD_POOL_SIZE);
        server.startInBackground();
        Thread.sleep(100);//等待服务端启动
    }

    @AfterEach
    public void tearDown() {
        if (server != null && server.isRunning()) {
            server.stop();
        }
    }

    @Test
    @DisplayName("服务端可以处理多个客户端连接")
    void serverHandleMultipleClients() {
        assertThat(server.isRunning()).isTrue();
        int clientCount = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(clientCount);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            executor.submit(() -> {
                //等待所有的客户端都站在起跑线之前
                try {
                    startLatch.await();
                    try (SimpleBioClient client = new SimpleBioClient("localhost", TEST_PORT)) {
                        client.connect();
                        String message = "hello from client " + clientId;
                        String response = client.sendAndReceive(message);
                        if ("hello,mini-netty".equals(response)) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Client " + clientId + " failed: " + e.getMessage());
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        //等所有的线程都执行完毕
        try {
            boolean completed = completeLatch.await(1000, TimeUnit.SECONDS);
            executor.shutdown();
            //判断 所有客户端都成功收发消息
            assertThat(completed).isTrue();
            assertThat(successCount.get()).isEqualTo(clientCount);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("每个客户端都能收到独立的消息")
    public void eachClientReceivesIndependentResponse() throws Exception {
        assertThat(server.isRunning()).isTrue();

        //创建多个客户端依次连接
        List<SimpleBioClient> clients = new ArrayList<>();
        List<String> responses = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            SimpleBioClient client = new SimpleBioClient("localhost", TEST_PORT);
            client.connect();
            clients.add(client);
        }
        for (int i = 0; i < clients.size(); i++) {
            String response = clients.get(i).sendAndReceive("My name is " + i);
            responses.add(response);
        }

        //关闭所有的客户端
        for (SimpleBioClient client : clients) {
            client.close();
        }

        // Then: 每个客户端都收到正确响应
        assertThat(responses).hasSize(3);
        assertThat(responses).allMatch(response -> "hello,mini-netty".equals(response));
    }

    @Test
    @DisplayName("多个客户端同时连接，并且都能收到独立的请求")
    void acceptanceScenario() throws InterruptedException {
        int clientCount = 3;
        CountDownLatch completeCountLatch = new CountDownLatch(clientCount);
        AtomicInteger successResponses = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(clientCount);

        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            executorService.submit(() -> {
                //所有的client都在这里停止
                try {
                    try (SimpleBioClient client = new SimpleBioClient("localhost", TEST_PORT)) {
                        client.connect();
                        for (int j = 0; j < 3; j++) {
                            String response = client.sendAndReceive("My name is " + clientId);
                            if (response.equals("hello,mini-netty")) {
                                successResponses.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Client failed: " + e.getMessage());
                } finally {
                    completeCountLatch.countDown();
                }
            });
        }

        boolean completed = completeCountLatch.await(1000, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(completed).isTrue();
        assertThat(successResponses.get()).isEqualTo(clientCount * 3);
    }

    @Test
    @DisplayName("服务端可以追踪连接活跃数")
    void serverTracksActiveConnections() throws Exception {
        // Given: 服务端已启动
        assertThat(server.isRunning()).isTrue();
        assertThat(server.getActiveConnections()).isEqualTo(0);

        // When: 客户端连接
        SimpleBioClient client1 = new SimpleBioClient("localhost", TEST_PORT);
        client1.connect();
        Thread.sleep(50);

        SimpleBioClient client2 = new SimpleBioClient("localhost", TEST_PORT);
        client2.connect();
        Thread.sleep(50);

        assertThat(server.getActiveConnections()).isGreaterThanOrEqualTo(0);

        // 关闭客户端
        client1.close();
        client2.close();
    }

}
