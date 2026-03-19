package com.getian.netty.channel.nio;


import com.getian.netty.channel.AbstractChannel;
import com.getian.netty.channel.Channel;
import com.getian.netty.channel.EventLoop;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * NIO Channel 的抽象基类
 * <p>
 * AbstractNioChannel 封装了 NIO SelectableChannel 的通用操作：
 * 1.管理层的selectableChannel
 * 2.处理与Selector的注册
 * 3.管理SelectionKey和感兴趣的操作
 * <p>
 * 学习要点：
 * 1.SelectableChannel 是 NIO 可选择通道的抽象
 * 2.每个通道只能注册到一个 Selector
 * 3.interestOps 控制关注哪些 I/O 事件
 *
 * @see SelectableChannel
 * @see SelectionKey
 */
public abstract class AbstractNioChannel extends AbstractChannel {

    /**
     * 底层的 NIO Channel
     */
    private final SelectableChannel ch;

    /**
     * 初始感兴趣的操作
     */
    private final int readInterestOp;

    /**
     * 注册到 Selector 的 SelectionKey
     */
    private volatile SelectionKey selectionKey;

    /**
     * 构造函数
     *
     * @param parent         父 Channel
     * @param ch             底层的 SelectableChannel
     * @param readInterestOp 读操作的 interest op（如 OP_ACCEPT, OP_READ）
     */
    protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent);
        this.ch = ch;
        this.readInterestOp = readInterestOp;

        try {
            // 设置为非阻塞模式
            ch.configureBlocking(false);
        } catch (IOException e) {
            try {
                ch.close();
            } catch (IOException e2) {
                // ignore
            }
            throw new RuntimeException("无法设置非阻塞模式", e);
        }
    }

    /**
     * 获取底层的 Java NIO Channel
     *
     * @return SelectableChannel
     */
    protected SelectableChannel javaChannel() {
        return ch;
    }

    /**
     * 获取 SelectionKey
     *
     * @return 注册的 SelectionKey
     */
    protected SelectionKey selectionKey() {
        return selectionKey;
    }

    /**
     * 获取读操作的 interest op
     *
     * @return interest op (如 OP_ACCEPT, OP_READ)
     */
    protected int readInterestOp() {
        return readInterestOp;
    }

    @Override
    public boolean isOpen() {
        return ch.isOpen();
    }

    @Override
    protected void doRegister() throws Exception {
        // 获取 NioEventLoop 的 Selector
        EventLoop eventLoop = eventLoop();
        if (!(eventLoop instanceof NioEventLoop)) {
            throw new IllegalStateException("必须注册到 NioEventLoop");
        }

        NioEventLoop nioEventLoop = (NioEventLoop) eventLoop;

        // 注册到 Selector，初始时不关注任何事件
        selectionKey = javaChannel().register(nioEventLoop.selector(), 0, this);
    }

    @Override
    protected void doClose() throws Exception {
        // 取消 SelectionKey
        if (selectionKey != null) {
            selectionKey.cancel();
        }
        // 关闭底层通道
        javaChannel().close();
    }

    /**
     * 开始读取数据
     *
     * <p>设置读操作的 interest op，让 Selector 通知数据可读。
     */
    protected void doBeginRead() throws Exception {
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            return;
        }

        int interestOps = selectionKey.interestOps();
        if ((interestOps & readInterestOp) == 0) {
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }

    /**
     * 执行实际的读操作
     *
     * <p>子类实现具体的读取逻辑。
     */
    protected abstract void doRead();


    /**
     * 执行实际的写操作
     *
     * @param msg 要写入的消息
     * @throws Exception 如果写入失败
     */
    protected abstract void doWrite(Object msg) throws Exception;

    @Override
    protected void doBind(java.net.SocketAddress localAddress) throws Exception {
        // 子类实现具体的绑定逻辑
        throw new UnsupportedOperationException("绑定操作需要子类实现");
    }

    @Override
    protected abstract UnSafe newUnsafe();

    protected abstract class AbstractNioUnsafe extends AbstractUnsafe {
        @Override
        protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
            // 子类实现具体的连接逻辑
            throw new UnsupportedOperationException("连接操作需要子类实现");
        }
    }
}
