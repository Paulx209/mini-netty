package com.getian.netty.example.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 简单的 BIO（阻塞 I/O）服务端示例。
 * 单线程串行处理连接与请求，便于学习最基础的 Socket 通信流程。
 *  @Author: sonicge
 *  @CreateTime: 2026-02-25
 */
public class SimpleBioServer {
    /** 服务端监听端口。 */
    private final int port;

    /** 服务端 Socket，负责接收客户端连接。 */
    private ServerSocket serverSocket;

    /** 服务运行状态，供多线程可见。 */
    private volatile boolean running;

    /**
     * 创建一个服务实例。
     *
     * @param port 监听端口
     */
    public SimpleBioServer(int port) {
        this.port = port;
    }

    /**
     * 启动服务并阻塞监听客户端连接。
     *
     * @throws IOException 端口绑定失败或 I/O 异常
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("[SimpleBioServer] Server started on port: " + port);
        while (running) {
            try {
                // BIO 模型：accept 会阻塞当前线程直到有客户端连接。
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SimpleBioServer] Client connected: " + clientSocket.getRemoteSocketAddress());
                // 串行处理：当前连接处理完才会接收下一个连接。
                handleClient(clientSocket);
            } catch (IOException e) {
                if (running) {
                    System.err.println("[SimpleBioServer] Accept failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 处理单个客户端连接。
     *
     * @param clientSocket 客户端 Socket
     * @throws IOException 读写异常
     */
    private void handleClient(Socket clientSocket) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(clientSocket.getOutputStream())) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[SimpleBioServer] Received: " + line);
                String response = "hello,mini-netty";
                writer.println(response);
                writer.flush();
                System.out.println("[SimpleBioServer] Sent: " + response);
            }
        }
    }

    /**
     * 在后台线程启动服务。
     *
     * @return 启动服务的线程对象
     */
    public Thread startInBackGround() {
        Thread thread = new Thread(() -> {
            try {
                start();
            } catch (IOException e) {
                running = false;
                System.err.println("[SimpleBioServer] Server exception: " + e.getMessage());
            }
        }, "bio-server");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * 停止服务。
     * 通过关闭 ServerSocket 来中断可能阻塞在 accept 的线程。
     */
    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("[SimpleBioServer] Server stopped");
            } catch (IOException e) {
                System.err.println("[SimpleBioServer] Stop failed: " + e.getMessage());
            }
        }
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 启动入口。
     * 可选参数：第一个参数为端口号，默认 8080。
     */
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        SimpleBioServer server = new SimpleBioServer(port);
        server.start();
    }
}
