package com.getian.netty.example.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

/**
 * @Author: sonicge
 * @CreateTime: 2026-03-14
 */

public class NioSelectorDemo {
    /**
     * 演示Selector的基本用法
     */
    public static void demonstrateSelector() throws IOException {
        System.out.println("=== Selector 基本用法演示 ===\n");

        //1.创建Selector
        Selector selector = Selector.open();
        System.out.println("创建 Selector: " + selector);

        // 2. 创建 ServerSocketChannel
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);//必须设置为非阻塞，selector要同时监听多个channel，如果设置成阻塞的话，就不对了
        serverChannel.bind(new InetSocketAddress(0));
        int port = serverChannel.socket().getLocalPort();
        System.out.println("ServerSocketChannel 绑定端口: " + port);

        //3.注册channel到selector
        SelectionKey key = serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("注册 ServerSocketChannel，关注 OP_ACCEPT 事件");
        System.out.println("SelectionKey: " + key);
        System.out.println("  interestOps: " + key.interestOps() + " (OP_ACCEPT=" + SelectionKey.OP_ACCEPT + ")");
        System.out.println("  isValid: " + key.isValid());

        //4.演示SelectionKey操作
        System.out.println("\n--- SelectionKey 事件类型 ---");
        System.out.println("OP_ACCEPT  = " + SelectionKey.OP_ACCEPT + " (0b" + Integer.toBinaryString(SelectionKey.OP_ACCEPT) + ")");
        System.out.println("OP_CONNECT = " + SelectionKey.OP_CONNECT + " (0b" + Integer.toBinaryString(SelectionKey.OP_CONNECT) + ")");
        System.out.println("OP_READ    = " + SelectionKey.OP_READ + " (0b" + Integer.toBinaryString(SelectionKey.OP_READ) + ")");
        System.out.println("OP_WRITE   = " + SelectionKey.OP_WRITE + " (0b" + Integer.toBinaryString(SelectionKey.OP_WRITE) + ")");
        // 5. 清理
        serverChannel.close();
        selector.close();
        System.out.println("\n资源已关闭");
    }

    /**
     * 演示多个Channel 绑定到同一个 Selector上
     */
    public static void demonstrateMultipleChannels() throws IOException {
        System.out.println("\n=== 多 Channel 注册演示 ===\n");
        //创建Selector
        Selector selector = Selector.open();
        //创建多个ServerSocketChannel
        ServerSocketChannel[] channels = new ServerSocketChannel[3];
        for(int i =0;i<channels.length;i++){
            channels[i] = ServerSocketChannel.open();
            channels[i].configureBlocking(false);
            channels[i].bind(new InetSocketAddress(0));

            //将ServerSocketChannel注册到selector
            SelectionKey key = channels[i].register(selector, SelectionKey.OP_ACCEPT);
            key.attach("Server-" + i); //附加自定义数据

            System.out.println("注册 " + key.attachment() + "，端口: " + channels[i].socket().getLocalPort());
        }
        System.out.println("\n已注册 Channel 数量: " + selector.keys().size());

        //清理
        for(ServerSocketChannel channel : channels){
            channel.close();
        }
        selector.close();
    }


    /**
     * 演示完整的Selector事件循环（简化版）
     */
    public static void demonstrateEventLoop() throws IOException {
        System.out.println("\n=== Selector 事件循环演示 ===\n");

        //第一部分

        //1.创建一个selector，select方法用来监察所有绑定的channel
        Selector selector =Selector.open();
        //2.创建channel ServerSocketChannel类
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false); //非阻塞
        channel.bind(new InetSocketAddress(0)); //监听某一个端口号
        int port = channel.socket().getLocalPort();
        System.out.println("服务端启动，监听的端口: " + port);
        System.out.println("等待连接...");

        //3.将channel注册到selector中
        channel.register(selector,SelectionKey.OP_ACCEPT);

        //第二部分
        //4.模拟客户端连接
        Thread clientThread = new Thread(() -> {
            try {
                Thread.sleep(500);
                //4.1 创建channel管道
                SocketChannel socketChannel = SocketChannel.open();
                //4.2 连接ServerSocketChannel监听的端口号 此时会有一个accept的
                socketChannel.connect(new InetSocketAddress("localhost",port));

                //4.3 创建ByteBuffer，然后将数据放到通道里面，传送到remoteAddress中
                ByteBuffer buffer = ByteBuffer.wrap("hello,selector".getBytes(StandardCharsets.UTF_8));
                socketChannel.write(buffer);

                Thread.sleep(100);
                socketChannel.close();
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        //线程启动，但是线程会sleep 500ms
        clientThread.start();

        //事件循环
        int eventCount = 0;
        long startTime = System.currentTimeMillis();
        //5.如果时间循环超过2次 or 该while循环超过2s
        while(eventCount < 2 && (System.currentTimeMillis() - startTime) < 20000){
            //5.1 阻塞等待500ms,这个select也是会阻塞主线程的
            int readyChannels = selector.select(500);
            //5.2 没有准备好的channel
            if(readyChannels == 0)continue;

            System.out.println("\n就绪 Channel 数量: " + readyChannels);
            //5.3 获取到所有的SelectionKey
            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();
            while(iterator.hasNext()){
                SelectionKey key = iterator.next();
                eventCount++;

                //5.4 如果当前channel的类型是接收连接就绪的话
                if(key.isAcceptable()){
                    //处理Accept事件
                    System.out.println("事件:OP_ACCEPT");
                    //只有ServerSocketChannel适用Accept
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    //这是我们的客人 clientChannel
                    SocketChannel clientChannel = server.accept();
                    clientChannel.configureBlocking(false);

                    //注册客户端Channel，关注READ事件
                    clientChannel.register(selector,SelectionKey.OP_READ);
                    System.out.println("接受连接: " + clientChannel.getRemoteAddress());
                }else if(key.isReadable()){
                    //处理READ事件
                    SocketChannel clientChannel = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(256);
                    int bytesRead = clientChannel.read(buffer);

                    if(bytesRead>0){
                        buffer.flip();
                        byte[] data = new byte[buffer.remaining()];
                        buffer.get(data);
                        System.out.println("读取数据: " + new String(data, StandardCharsets.UTF_8));
                    }else if(bytesRead == -1){
                        System.out.println("客户端关闭连接");
                        clientChannel.close();
                    }
                }
            }
            // 必须手动移除已处理的 key
            iterator.remove();
        }

        // 清理
        channel.close();
        selector.close();
        System.out.println("\n演示完成");
    }


    public static void main(String[] args) throws IOException {
//        demonstrateMultipleChannels();
        demonstrateEventLoop();
    }
}
