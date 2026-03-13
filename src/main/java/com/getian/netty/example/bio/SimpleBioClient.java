package com.getian.netty.example.bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * @Author: sonicge
 * @CreateTime: 2026-03-13
 */

public class SimpleBioClient implements AutoCloseable {
    private final String host;
    private final int port;
    private Socket clientSocket;

    private BufferedReader reader;
    private PrintWriter writer;

    /**
     * 创建一个 BIO 客户端
     *
     * @param host 服务端主机地址
     * @param port 服务端端口号
     */
    public SimpleBioClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        clientSocket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        writer = new PrintWriter(clientSocket.getOutputStream(),true);
        System.out.println("[SimpleBioClient] 已连接到 " + host + ":" + port);
    }

    /**
     * 发送消息并接收响应 (此方法会阻塞，直到收到服务端响应。)
     *
     * @param message 要发送的信息
     * @return 服务端的响应
     */
    public String sendAndReceive(String message) throws IOException {
        ensureConnected();

        System.out.println("[SimpleBioClient] 发送消息: " + message);
        writer.println(message);

        String response = reader.readLine();
        System.out.println("[SimpleBioClient] 收到消息: " + response);

        return response;
    }

    /**
     * 发送信息
     *
     * @param message 要发送的信息
     * @throws IOException
     */
    public void send(String message) throws IOException {
        ensureConnected();
        writer.println(message);
        System.out.println("[SimpleBioClient] 发送消息: " + message);
    }

    public String receive() throws IOException {
        ensureConnected();
        String line;
        StringBuilder sb = new StringBuilder();
        while((line = reader.readLine())!=null){
            String response = reader.readLine();
            sb.append(response).append("\n");
            System.out.println("[SimpleBioClient] 收到消息: " + response);
        }
        return sb.toString();
    }

    /**
     * 确保已经建立连接
     */
    private void ensureConnected() throws IOException {
        if (clientSocket == null || clientSocket.isClosed()) {
            throw new IOException("客户端未连接");
        }
    }

    /**
     * 检查是否已经连接
     *
     * @return
     */
    public boolean isConnected() {
        return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
    }

    @Override
    public void close() throws Exception {
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                System.out.println("[SimpleBioClient] 连接已关闭");
            }
        } catch (IOException e) {
            System.err.println("[SimpleBioClient] 关闭连接失败: " + e.getMessage());
        }
    }

    /**
     * 启动客户端进行交互
     *
     * @param args
     */
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        try (
                SimpleBioClient client = new SimpleBioClient(host, port);
                BufferedReader console = new BufferedReader(new InputStreamReader(System.in))
        ) {
            client.connect();
            System.out.println("输入消息发送给服务端(输入'quit'退出)");

            String input;
            while ((input = console.readLine()) != null) {
                if ("quit".equalsIgnoreCase(input)) {
                    break;
                }
                String response = client.sendAndReceive(input);
                System.out.println("服务器端响应:" + response);
            }
        }
    }
}
