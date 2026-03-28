package com.getian.netty.channel.nio;

import com.getian.netty.channel.Channel;
import com.getian.netty.channel.ChannelFuture;
import com.getian.netty.channel.EventLoopGroup;
import com.getian.netty.channel.SingleThreadEventLoop;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.Set;

/**
 * @Author: sonicge
 * @CreateTime: 2026-03-16
 */

public class NioEventLoop extends SingleThreadEventLoop {
    private final Selector selector;

    /**
     * 构造函数
     *
     * @param parent
     */
    public NioEventLoop(EventLoopGroup parent) {
        super(parent);
        try {
            //SelectorProvider 智能的后勤部长 会向操作系统中申请一个selector对象
            this.selector = SelectorProvider.provider().openSelector();
        } catch (IOException e) {
            throw new RuntimeException("无法创建 Selector", e);
        }
    }

    /**
     * 获取 Selector
     *
     * @return NIO Selector
     */
    public Selector selector() {
        return selector;
    }

    @Override
    public ChannelFuture register(Channel channel) {
        // TODO: 在后续迭代中实现 Channel 注册
        throw new UnsupportedOperationException("将在后续迭代实现 Channel 注册");
    }

    @Override
    protected void run() {
        System.out.println("[NioEventLoop] 事件循环启动");
        while (!isShutdown()) {
            try {
                //1.确认就绪的channel的数量
                int readyChannels = select();
                //2.开始处理
                //2.1 先处理selectionKey
                if (readyChannels > 0) {
                    processSelectedKeys();
                }
                //2.2 然后执行对应的task任务
                int taskCount = runAllTasks();
                System.out.println("该轮循环执行任务: " + taskCount + "次");
            } catch (IOException e) {
                System.err.println("[NioEventLoop] 事件循环异常: " + e.getMessage());
            }
        }
        try {
            selector.close();
        } catch (IOException e) {
            System.err.println("[NioEventLoop] 关闭 Selector 失败: " + e.getMessage());
        }

        terminated.set(true);
        running.set(false);
        System.out.println("[NioEventLoop] 事件循环已停止");
    }


    @Override
    protected void wakeup() {
        // 唤醒可能阻塞的 select()
        if (!inEventLoop()) {
            selector.wakeup();
        }
    }


    @Override
    protected String getThreadName() {
        return "nio-eventloop-" + Integer.toHexString(hashCode());
    }


    private int select() throws IOException {
        //如果有任务 使用selectNow()选择不阻塞 直接返回当前准备就绪的channel的个数
        if (hasTasks()) {
            return selector.selectNow();
        }
        //检查是否有定时任务
        long nextScheduledTaskDelayNanos = getNextScheduledTaskDelayNanos();
        if (nextScheduledTaskDelayNanos >= 0) {
            // 如果任务已到期或即将到期，不阻塞
            if (nextScheduledTaskDelayNanos == 0) {
                return selector.selectNow();
            }
            long timeoutMillis = Math.max(1, nextScheduledTaskDelayNanos / 1000000);
            return selector.select(timeoutMillis);
        }
        //否则使用超时选择，最多等待 1 秒
        return selector.select(1000);
    }

    private void processSelectedKeys() {
        Set<SelectionKey> selectionKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();

            if (!key.isValid()) {
                continue;
            }
            try {
                processSelectedKeys(key);
            } catch (Exception e) {
                System.err.println("[NioEventLoop] 处理事件失败: " + e.getMessage());
                //关闭出错的channel
                try {
                    key.channel().close();
                } catch (IOException ex) {

                }
            }
        }
    }

    /**
     * 处理单个SelectionKey
     * 子类或通过注册的 Channel 处理具体事件。
     *
     * @param key 就绪的 SelectionKey
     */
    protected void processSelectedKeys(SelectionKey key) {
        Object attachment = key.attachment();
        if (!(attachment instanceof AbstractNioChannel)) {
            return;
        }

        AbstractNioChannel channel = (AbstractNioChannel) attachment;
        if (key.isAcceptable()) {
            System.out.println("[NioEventLoop] ACCEPT 事件");
        }
        if (key.isConnectable()) {
            System.out.println("[NioEventLoop] CONNECT 事件");
        }
        if (key.isReadable()) {
            System.out.println("[NioEventLoop] READ 事件");
        }
        if (key.isWritable()) {
            System.out.println("[NioEventLoop] WRITE 事件");
        }
        channel.handleSelectedKey(key);
    }
}
