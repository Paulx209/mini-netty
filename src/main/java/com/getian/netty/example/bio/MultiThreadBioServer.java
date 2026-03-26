package com.getian.netty.example.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多线程BIO(Blocking I/O)客户端
 * 使用线程池处理多个客户端连接的 BIO 服务端实现
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-13
 */

public class MultiThreadBioServer {
    //SimpleBioServer原有的属性
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running;
    //MultiThreadBioServer新增的属性
    private final int threadPoolSize; //线程池大小
    private ExecutorService executorService; //线程池管理器
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    /**
     * @param port           监听的端口号
     * @param threadPoolSize 线程池大小(最大并发连接数)
     */
    public MultiThreadBioServer(int port, int threadPoolSize) {
        this.port = port;
        this.threadPoolSize = threadPoolSize;
    }

    public MultiThreadBioServer(int port) {
        //默认的线程数量为10
        this(port, 10);
    }

    /**
     * 监听端口并且处理连接
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        executorService = Executors.newFixedThreadPool(threadPoolSize);
        running = true;
        System.out.println("[MultiThreadBioServer] 服务端启动，监听端口: " + port +
                "，线程池大小: " + threadPoolSize);

        while (running) {
            try {
                //阻塞线程
                Socket clientSocket = serverSocket.accept();
                activeConnections.incrementAndGet();
                System.out.println("[MultiThreadBioServer] 客户端连接: " +
                        clientSocket.getRemoteSocketAddress());
                //将客户端处理任务提交到线程池
                executorService.submit(() -> handleClientSocket(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("[MultiThreadBioServer] accept()失败: " + e.getMessage());
                }
            }
        }
    }

    public Thread startInBackground() {
        Thread serverThread = new Thread(() -> {
            try {
                start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("[MultiThreadBioServer] accept()异常: " + e.getMessage());
                }
            }
        }, "multi-thread-bio-server");
        serverThread.setDaemon(true);
        serverThread.start();

        while (serverSocket == null && running) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return serverThread;
    }

    /**
     * 处理单个clientSocket连接
     *
     * @param clientSocket
     */
    public void handleClientSocket(Socket clientSocket) {
        String remoteAddress = clientSocket.getRemoteSocketAddress().toString();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[MultiThreadBioServer] [" + Thread.currentThread().getName() +
                        "] 收到消息: " + line);
                //发送响应
                String response = "hello,mini-netty";
                writer.println(response);
                System.out.println("[MultiThreadBioServer] [" + Thread.currentThread().getName() +
                        "] 发送响应: " + response);
            }
        } catch (IOException e) {
            System.err.println("[MultiThreadBioServer] 处理客户端失败: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("[MultiThreadBioServer] 客户端断开连接: " + remoteAddress);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            activeConnections.decrementAndGet();
        }
    }

    /**
     * 停止服务端
     */
    public void stop() {
        running = false;
        //关闭线程池
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    //如果超过5s还没有执行完毕的话，直接关掉
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        //关闭socket连接
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("[MultiThreadBioServer] 服务端已停止");
            } catch (IOException e) {
                System.out.println("[MultiThreadBioServer] 服务端停止失败");
            }
        }
    }


    public int port() {
        return this.port;
    }

    public boolean isRunning() {
        return this.running && serverSocket != null && !serverSocket.isClosed();
    }

    public int getActiveConnections() {
        return this.activeConnections.get();
    }


    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        int threadPoolSize = args.length > 1 ? Integer.parseInt(args[1]) : 10;
        MultiThreadBioServer server = new MultiThreadBioServer(port, threadPoolSize);
        server.start();
    }
}
