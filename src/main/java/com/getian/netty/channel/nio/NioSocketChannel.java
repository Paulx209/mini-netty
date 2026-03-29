package com.getian.netty.channel.nio;

import com.getian.netty.channel.Channel;
import com.getian.netty.channel.ChannelFuture;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * 客户端/连接 NIO Channel 实现
 * <p>
 * 一、NioSocketChannel 封装了 Java NIO 的 SocketChannel，
 * 用于 TCP 连接的读写操作。可以是：
 * 1.客户端主动创建的连接
 * 2.服务端接受的客户端连接
 * <p>
 * 二、主要功能：
 * 1.连接到远程服务器（客户端）
 * 2.读取和写入数据（OP_READ OP_WRITE）
 * 3.管理TCP连接的生命周期
 * <p>
 * 三、学习要点：
 * <p>
 * 1.客户端通道关注 OP_CONNECT 和 OP_READ 事件
 * 2.使用 ByteBuffer 进行数据读写
 * 3.非阻塞 connect() 可能需要等待 finishConnect()
 *
 * @see SocketChannel
 * @see NioServerSocketChannel
 */
public class NioSocketChannel extends AbstractNioChannel {
    private static final int READ_BUFFER_SIZE = 1024;

    /**
     * 构造函数 - 用于客户端创建新连接
     */
    public NioSocketChannel() {
        super(null, newSocket(), SelectionKey.OP_READ);
    }

    /**
     * 构造函数 - 用于服务端接受的连接
     *
     * @param parent       父 Channel（NioServerSocketChannel）
     * @param clientSocket 已接受的 SocketChannel
     */
    public NioSocketChannel(Channel parent, SocketChannel clientSocket) {
        super(parent, clientSocket, SelectionKey.OP_READ);
    }

    /**
     * 创建新的 SocketChannel
     *
     * @return 新的 SocketChannel
     */
    private static SocketChannel newSocket() {
        try {
            return SocketChannel.open();
        } catch (IOException e) {
            throw new RuntimeException("无法创建 SocketChannel", e);
        }
    }

    /**
     * 获取底层的 SocketChannel
     *
     * @return SocketChannel
     */
    @Override
    protected SocketChannel javaChannel() {
        return (SocketChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SocketChannel clientChannel = javaChannel();
        //channel开启 并且 channel底层对应的ServerSocket必须是connect了地址的
        return isOpen() && clientChannel.isConnected();
    }


    @Override
    protected SocketAddress localAddress0() throws Exception {
        return javaChannel().getLocalAddress();
    }

    @Override
    protected SocketAddress remoteAddress0() throws Exception {
        return javaChannel().getRemoteAddress();
    }

    /**
     * 连接到远程地址
     *
     * @param remoteAddress 远程地址
     * @return 连接操作的 Future
     */
    public ChannelFuture connect(SocketAddress remoteAddress) {
        try {
            SocketChannel clientChannel = javaChannel();
            boolean connected = clientChannel.connect(remoteAddress);
            if (connected) {
                System.out.println("[NioSocketChannel] 已连接到 " + remoteAddress);
                // 连接立即建立时，已注册的 Channel 直接进入 active/read 状态。
                if (isRegistered()) {
                    pipeline().fireChannelActive();
                    if (config().isAutoRead()) {
                        unsafe().beginRead();
                    }
                }
            } else {
                // 连接进行中，需要等待 OP_CONNECT 事件
                System.out.println("[NioSocketChannel] 正在连接 " + remoteAddress);
                SelectionKey key = selectionKey();
                if (key != null) {
                    //设置关注 OP_CONNECT 事件
                    key.interestOps(key.interestOps() | SelectionKey.OP_CONNECT);
                }
            }
            return newSucceededFuture();
        } catch (IOException e) {
            System.err.println("[NioSocketChannel] 连接失败: " + e.getMessage());
            return newFailedFuture(e);
        }
    }

    /**
     * 连接到远程地址
     *
     * @param host 主机名
     * @param port 端口号
     * @return 连接操作的 Future
     */
    public ChannelFuture connect(String host, int port) {
        return connect(new InetSocketAddress(host, port));
    }

    /**
     * 完成连接（用于非阻塞连接）
     *
     * @return 如果连接完成返回 true
     */
    public boolean finishConnect() {
        try {
            boolean finished = javaChannel().finishConnect();
            if (finished) {
                System.out.println("[NioSocketChannel] 连接完成");
                SelectionKey key = selectionKey();
                if (key != null) {
                    //取消connect 关注 read 事件
                    key.interestOps((key.interestOps() & ~SelectionKey.OP_CONNECT) | SelectionKey.OP_READ);
                }
                // 触发 channelActive
                pipeline().fireChannelActive();
                if (config().isAutoRead()) {
                    unsafe().beginRead();
                }
            }
            return finished;
        } catch (IOException e) {
            System.err.println("[NioSocketChannel] 完成连接失败: " + e.getMessage());
            pipeline().fireExceptionCaught(e);
            return false;
        }
    }


    @Override
    protected void doRead() {
        ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        try {
            //1.将channel中的数据读取到buffer中
            int bytesRead = javaChannel().read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                // 触发 channelRead 事件，传递 ByteBuffer 而非buffer.get(data)了
                pipeline().fireChannelRead(buffer);
                pipeline().fireChannelReadComplete();
            } else if (bytesRead < 0) {
                // 连接关闭
                System.out.println("[NioSocketChannel] 对端关闭连接");
                close();
            }
        } catch (IOException e) {
            System.err.println("[NioSocketChannel] 读取失败: " + e.getMessage());
            pipeline().fireExceptionCaught(e);
            close();
        }
    }

    @Override
    protected void doWrite(Object msg) throws Exception {
        //对msg区分三种类型 一种是buffer 一种是byte[] 另一种是string
        if (msg instanceof ByteBuffer) {
            ByteBuffer buffer = (ByteBuffer) msg;
            while (buffer.hasRemaining()) {
                javaChannel().write(buffer);
            }
        } else if (msg instanceof byte[]) {
            ByteBuffer buffer = ByteBuffer.wrap((byte[]) msg);
            while (buffer.hasRemaining()) {
                javaChannel().write(buffer);
            }
        } else if (msg instanceof String) {
            String str = (String) msg;
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                javaChannel().write(buffer);
            }
        } else {
            throw new IllegalArgumentException("不支持的消息类型: " + msg.getClass());
        }
    }

    /**
     * 写入消息
     *
     * @param msg 要写入的消息
     * @return 写入操作的 Future
     */
    public ChannelFuture writeAndFlush(Object msg) {
        try {
            doWrite(msg);
            return newSucceededFuture();
        } catch (Exception e) {
            return newFailedFuture(e);
        }
    }

    @Override
    protected void doClose() throws Exception {
        System.out.println("[NioSocketChannel] 关闭连接");
        super.doClose();
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress);
    }

    @Override
    protected UnSafe newUnsafe() {
        return new NioSocketChannelUnsafe();
    }

    private class NioSocketChannelUnsafe extends AbstractNioUnsafe {
        @Override
        protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
            if (localAddress != null) {
                javaChannel().bind(localAddress);
            }
            boolean connectd = javaChannel().connect(remoteAddress);
            if (!connectd) {
                //连接进行中，需要等待OP_CONNECT事件
                SelectionKey key = selectionKey();
                if (key != null) {
                    key.interestOps(key.interestOps() | SelectionKey.OP_CONNECT);
                }
            }
        }
    }

}
