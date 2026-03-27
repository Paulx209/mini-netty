package com.getian.netty.example.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * NIO 客户端
 * 使用 NIO SocketChannel 实现客户端
 * 与 BIO 客户端相比，NIO 客户端使用 Channel 和 Buffer 进行数据传输
 * <p>
 * 学习要点
 * SocketChannel 用于建立 TCP 连接
 * 可配置为阻塞或非阻塞模式
 * 使用 ByteBuffer 读写数据
 * connect() 在非阻塞模式下可能不会立即完成
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-14
 */

public class NioClient implements AutoCloseable {
    private final String host;
    private final int port;
    private SocketChannel clientChannel;

    public NioClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 连接到服务器（阻塞模式）
     */
    public void connect() throws IOException {
        clientChannel = SocketChannel.open();
        clientChannel.configureBlocking(true);//使用阻塞模式连接到客户端
        clientChannel.connect(new InetSocketAddress(host, port));
        System.out.println("[NioClient] 已连接到 " + host + ":" + port);
    }

    /**
     * 发送消息并接收响应
     *
     * @param message 要发送的消息
     * @return 收到的消息
     */
    public String sendAndReceive(String message) throws IOException {
        ensureConnected();//发送消息
        ByteBuffer buffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));
        clientChannel.write(buffer);
        System.out.println("[NioClient] 发送消息: " + message);

        //读取响应的信息
        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        //通道将数据读到buffer中 所以readBuffer是写模式
        int bytesRead = clientChannel.read(readBuffer);
        if (bytesRead > 0) {
            readBuffer.flip();
            byte[] data = new byte[readBuffer.remaining()];
            readBuffer.get(data);
            String response = new String(data, StandardCharsets.UTF_8).trim();
            System.out.println("响应的数据为:" + response);

            return response;
        }
        return null;
    }

    /**
     * 只发送消息 不等待响应
     *
     * @param message
     */
    public void send(String message) throws IOException {
        ensureConnected();
        ByteBuffer buffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));
        int bytesWrite = clientChannel.write(buffer);
        System.out.println("[nioClient] 发送信息 " + message);
    }

    /**
     * 只接收响应信息
     *
     * @return 服务端响应
     */
    public String receive() throws IOException {
        ensureConnected();
        //1.创建ByteBuffer
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = clientChannel.read(buffer);
        if (bytesRead > 0) {
            buffer.flip();
            byte[] data = new byte[1024];
            buffer.get(data);
            String response = new String(data, StandardCharsets.UTF_8);
            System.out.println("[NioClient] 收到响应: " + response);
            return response;
        }
        return null;

    }

    /**
     * 确保clientChannel建立连接
     *
     * @throws IOException
     */
    private void ensureConnected() throws IOException {
        if (clientChannel == null || !clientChannel.isConnected()) {
            throw new IOException("客户端未连接");
        }
    }

    /**
     * 检查是否已连接
     *
     * @return 如果已连接返回 true
     */
    public boolean isConnected() {
        return clientChannel != null && clientChannel.isConnected();
    }

    /**
     * 关闭客户端连接
     */
    @Override
    public void close() {
        if (clientChannel != null && clientChannel.isOpen()) {
            try {
                clientChannel.close();
                System.out.println("[NioClient] 连接已关闭");
            } catch (IOException e) {
                System.err.println("[NioClient] 关闭连接失败: " + e.getMessage());
            }
        }
    }
}
