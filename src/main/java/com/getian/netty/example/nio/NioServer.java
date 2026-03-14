package com.getian.netty.example.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

/**
 * @Author: sonicge
 * @CreateTime: 2026-03-14
 */

public class NioServer {
    private final int port;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private volatile boolean running;

    /**
     * 创建NIO服务器端
     *
     * @param port 端口号
     */
    public NioServer(int port) {
        this.port = port;
    }

    /**
     * 启动服务端并开始处理连接
     */
    public void start() throws IOException {
        //1.初始化selector 绑定channel
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress("localhost", port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        running = true;
        System.out.println("[NioServer] 服务端启动，监听端口: " + port);

        //2.事件循环
        while (running) {
            //2.1 检查是否有就绪的channel
            int select = selector.select(1000);
            if (select == 0) continue;

            //2.2 如果有的话
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                try {
                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                } catch (Exception e) {
                    System.err.println("[NioServer] 处理事件失败: " + e.getMessage());
                    key.cancel();
                }
            }
            iterator.remove();
        }
    }

    /**
     * 处理Accept事件 允许客户端连接
     *
     * @param key
     */
    public void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel != null) {
            //配置客户端Channel为非阻塞 其实这个对象和我们定义clientChannel对象申请连接port的客户端channel是同一个。
            //之前ServerSocket.accept()返回的对象也是clientSocket，也是申请连接的客户端对象。
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);

            System.out.println("[NioServer] 接受连接: " + clientChannel.getRemoteAddress());
        }
    }

    /**
     * 处理Read事件 读取channel管道里面发送的数据（可以使用buffer来读取）
     *
     * @param key
     */
    public void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientReadChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = clientReadChannel.read(buffer);

        if (bytesRead == -1) {
            //客户端关闭连接
            System.out.println("[NioServer] 客户端关闭连接: " + clientReadChannel.getRemoteAddress());
            key.cancel();
            clientReadChannel.close();
            return;
        }

        if (bytesRead > 0) {
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            String message = new String(data, StandardCharsets.UTF_8);
            System.out.println("[NioServer] 收到消息: " + message);

            //通过key的附加信息 来作为响应数据
            String response = "hello,mini-netty\n";
            key.attach(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));

            //将key的事件类型由read 修改为 write
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    /**
     * 处理 WRITE 事件 - 发送响应数据
     *
     * @param key SelectionKey
     */
    public void handleWrite(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        if (buffer != null) {
            clientChannel.write(buffer);
            System.out.println("[NioServer] 发送响应: hello, mini-netty");

            //如果buffer没有剩余内容
            if (!buffer.hasRemaining()) {
                //写入完成 切换为Read事件
                key.attach(null);
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }


    /**
     * 在后台线程启动服务端
     *
     * @return 启动服务端的线程
     */
    public Thread startInBackground() {
        Thread thread = new Thread(() -> {
            try {
                start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("[NioServer] 服务端异常: " + e.getMessage());
                }
            }
        }, "nio-server");
        //守护线程
        thread.setDaemon(true);
        thread.start();

        while (selector == null && running) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return thread;
    }

    public void stop() {
        //1.将running置为false
        running = false;
        //2.selector可能还在select()中，需要唤醒
        if (selector != null) {
            selector.wakeup();
        }
        //3.判断serverChannel是否关闭
        if (serverChannel != null && serverChannel.isOpen()) {
            try {
                serverChannel.close();
            } catch (IOException e) {
                System.err.println("[NioServer] 关闭 ServerSocketChannel 失败: " + e.getMessage());
            }
        }
        //4.判断selector是否关闭
        if (selector != null && selector.isOpen()) {
            try {
                selector.close();
            } catch (IOException e) {
                System.err.println("[NioServer] 关闭 Selector 失败: " + e.getMessage());
            }
        }

        System.out.println("[NioServer] 服务端已停止");
    }


    /**
     * 获取服务端端口
     *
     * @return 监听端口号
     */
    public int getPort() {
        return port;
    }

    /**
     * 获取 Selector
     *
     * @return Selector 实例
     */
    public Selector getSelector() {
        return selector;
    }

    /**
     * 检查服务端是否正在运行
     *
     * @return 如果服务端正在运行返回 true
     */
    public boolean isRunning() {
        return running && serverChannel != null && serverChannel.isOpen();
    }


}
