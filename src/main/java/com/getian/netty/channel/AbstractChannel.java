package com.getian.netty.channel;


import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Channel 的抽象基类，提供所有 Channel 实现的公共功能
 * Channel 生命周期管理
 * EventLoop 注册
 * Pipeline 创建和管理
 * 异步操作的 ChannelFuture 支持
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-17
 */

public abstract class AbstractChannel implements Channel {
    /**
     * 父 Channel（对于服务端接受的连接，指向 ServerSocketChannel）
     */
    private final Channel parent;

    /**
     * Channel 唯一标识
     */
    private final ChannelId id;

    /**
     * Channel 的处理管道
     */
    private final ChannelPipeline pipeline;

    /**
     * Channel配置
     */
    private final ChannelConfig config;

    /**
     * 关联的 EventLoop
     */
    private volatile EventLoop eventLoop;

    /**
     * 是否已注册到 EventLoop
     */
    private volatile boolean registered;

    /**
     * 是否已关闭
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 本地地址
     */
    private volatile SocketAddress localAddress;

    /**
     * 远程地址
     */
    private volatile SocketAddress remoteAddress;

    /**
     * Unsafe实例，封装底层I/O操作
     */
    private final UnSafe unsafe;

    /**
     * 构造函数
     *
     * @param parent 父 Channel，可以为 null
     */
    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        this.id = newId();
        this.pipeline = newChannelPipeline();
        this.config = newChannelConfig();
        this.unsafe = newUnsafe();
    }

    /**
     * 创建新的 Channel ID
     *
     * @return 新的 ChannelId
     */
    protected ChannelId newId() {
        return new DefaultChannelId();
    }

    /**
     * 创建新的 ChannelPipeline
     *
     * @return 新的 ChannelPipeline
     */
    protected ChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this);
    }

    /**
     * 创建新的ChannelConfig
     *
     * @return 新的ChannelConfig
     */
    protected ChannelConfig newChannelConfig() {
        return new DefaultChannelConfig(this);
    }


    /**
     * 创建新的Unsafe类
     *
     * @return 创建新的Unsafe类
     */
    protected abstract UnSafe newUnsafe();

    @Override
    public UnSafe unsafe() {
        return unsafe;
    }

    @Override
    public ChannelId id() {
        return id;
    }

    @Override
    public Channel parent() {
        return parent;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    public ChannelConfig config() {
        return config;
    }

    @Override
    public EventLoop eventLoop() {
        EventLoop loop = this.eventLoop;
        if (loop == null) {
            throw new IllegalStateException("Channel 未注册到 EventLoop");
        }
        return loop;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public boolean isOpen() {
        return !closed.get();
    }

    /**
     * 获取本地地址
     *
     * @return 本地 SocketAddress
     */
    public SocketAddress localAddress() {
        SocketAddress addr = this.localAddress;
        if (addr == null) {
            try {
                addr = localAddress0();
                this.localAddress = addr;
            } catch (Exception e) {
                // ignore
            }
        }
        return addr;
    }

    /**
     * 获取远程地址
     *
     * @return 远程 SocketAddress
     */
    public SocketAddress remoteAddress() {
        SocketAddress addr = this.remoteAddress;
        if (addr == null) {
            try {
                addr = remoteAddress0();
                this.remoteAddress = addr;
            } catch (Exception e) {
                // ignore
            }
        }
        return addr;
    }

    /**
     * 注册到 EventLoop
     *
     * @param eventLoop 要注册的 EventLoop
     * @return 注册操作的 Future
     */
    public ChannelFuture register(EventLoop eventLoop) {
        if (eventLoop == null) {
            throw new NullPointerException("eventLoop");
        }
        if (isRegistered()) {
            throw new IllegalStateException("已经注册到 EventLoop");
        }

        this.eventLoop = eventLoop;

        if (eventLoop.inEventLoop()) {
            register0();
        } else {
            eventLoop.execute(this::register0);
        }

        return newSucceededFuture();
    }

    /**
     * 实际的注册操作
     */
    private void register0() {
        try {
            doRegister();
            registered = true;
            // 触发 channelRegistered 事件
            pipeline.fireChannelRegistered();

            // 如果 Channel 已经是活动状态，触发 channelActive 事件
            if (isActive()) {
                pipeline.fireChannelActive();
            }
        } catch (Exception e) {
            System.err.println("[AbstractChannel] 注册失败: " + e.getMessage());
        }
    }

    @Override
    public Channel read() {
        pipeline.read();
        return this;
    }

    @Override
    public ChannelFuture close() {
        if (closed.compareAndSet(false, true)) {
            if (eventLoop != null && eventLoop.inEventLoop()) {
                close0();
            } else if (eventLoop != null) {
                eventLoop.execute(this::close0);
            } else {
                close0();
            }
        }
        return newSucceededFuture();
    }


    /**
     * 实际的关闭操作
     */
    private void close0() {
        try {
            doClose();
            // 触发 channelInactive 事件
            if (registered) {
                pipeline.fireChannelInactive();
                pipeline.fireChannelUnregistered();
            }
        } catch (Exception e) {
            System.err.println("[AbstractChannel] 关闭失败: " + e.getMessage());
        }
    }

    /**
     * 创建成功的 ChannelFuture
     *
     * @return 已完成的 ChannelFuture
     */
    protected ChannelFuture newSucceededFuture() {
        return new DefaultChannelFuture(this, true);
    }

    /**
     * 创建失败的 ChannelFuture
     *
     * @param cause 失败原因
     * @return 已失败的 ChannelFuture
     */
    protected ChannelFuture newFailedFuture(Throwable cause) {
        return new DefaultChannelFuture(this, cause);
    }

    // ========== 子类需要实现的抽象方法 ==========

    /**
     * 获取本地地址的实际实现
     *
     * @return 本地地址
     * @throws Exception 如果获取失败
     */
    protected abstract SocketAddress localAddress0() throws Exception;

    /**
     * 获取远程地址的实际实现
     *
     * @return 远程地址
     * @throws Exception 如果获取失败
     */
    protected abstract SocketAddress remoteAddress0() throws Exception;

    /**
     * 注册到底层传输的实际实现
     *
     * @throws Exception 如果注册失败
     */
    protected abstract void doRegister() throws Exception;

    /**
     * 关闭 Channel 的实际实现
     *
     * @throws Exception 如果关闭失败
     */
    protected abstract void doClose() throws Exception;


    // ========== AbstractUnsafe 内部类 ==========

    /**
     * Unsafe 的抽象基类实现
     * AbstractUnsafe 封装了 Channel 的底层 I/O 操作，这些操作
     * 不应该被用户代码直接调用。它们会被 Pipeline 中的 HeadContext 调用。
     * 要点：
     * 1.所有操作都会检查是否在 EventLoop 线程中执行
     * 2.使用 Promise 异步通知操作结果
     * 3.模板方法模式：调用 doXxx 方法完成实际操作
     */
    protected abstract class AbstractUnsafe implements UnSafe {
        //注册 Channel 到 EventLoop
        public void register(EventLoop eventLoop, ChannelPromise promise) {
            //1.校验参数
            if (eventLoop == null) {
                promise.setFailure(new NullPointerException("eventLoop is null"));
                return;
            }
            if (isRegistered()) {
                promise.setFailure(new IllegalStateException("已经注册到eventLoop中"));
                return;
            }
            //2.给当前的eventLoop属性赋值
            AbstractChannel.this.eventLoop = eventLoop;
            //3.判断当前执行的线程是否是eventLoop线程
            if (eventLoop.inEventLoop()) {
                register0(promise);
            } else {
                eventLoop.execute(() -> {
                    register0(promise);
                });
            }
        }

        private void register0(ChannelPromise promise) {
            try {
                //1.channel 和 selector 进行绑定
                doRegister();
                //2.registered赋值
                registered = true;
                promise.setSuccess();

                //3.触发channelRegistered事件
                pipeline.fireChannelRegistered();

                //4.如果 Channel 已经是活动状态，触发 channelActive 事件
                if (isActive()) {
                    pipeline.fireChannelActive();
                }
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }

        //绑定到本地地址(一般是ServerSocketChannel)
        @Override
        public void bind(SocketAddress socketAddress, ChannelPromise promise) {
            //1.检查channel开闭状态
            if (!isOpen()) {
                promise.setFailure(new IllegalStateException("Channel已经关闭"));
                return;
            }

            //2.判断当前的线程是否是eventLoop线程
            if (eventLoop().inEventLoop()) {
                bind0(socketAddress, promise);
            } else {
                eventLoop.execute(() -> bind0(socketAddress, promise));
            }
        }

        private void bind0(SocketAddress socketAddress, ChannelPromise promise) {
            try {

                doBind(socketAddress);

                promise.setSuccess();

                if (isActive()) {
                    pipeline.fireChannelActive();
                }
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }

        //向远程地址发起连接(一般是SocketChannel)
        @Override
        public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            //1.参数校验
            if (!isOpen()) {
                promise.setFailure(new IllegalStateException("Channel 已关闭"));
                return;
            }

            //2.执行
            if (eventLoop.inEventLoop()) {
                connect0(remoteAddress, localAddress, promise);
            } else {
                eventLoop.execute(() -> {
                    connect0(remoteAddress, localAddress, promise);
                });
            }
        }

        private void connect0(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            try {
                doConnect(remoteAddress, localAddress);

                promise.setSuccess();
                // 连接成功后触发 channelActive
                if (isActive()) {
                    pipeline.fireChannelActive();
                }
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }

        /**
         * 连接的实际实现，子类需要覆盖
         */
        protected void doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
            throw new UnsupportedOperationException("连接操作不支持");
        }

        //断开连接
        @Override
        public void disConnect(ChannelPromise promise) {
            if (!isOpen()) {
                promise.setFailure(new IllegalStateException("Channel 已关闭"));
                return;
            }

            if (eventLoop.inEventLoop()) {
                disconnect0(promise);
            } else {
                eventLoop.execute(() -> disconnect0(promise));
            }
        }

        private void disconnect0(ChannelPromise promise) {
            try {
                doDisconnect();
                promise.setSuccess();
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }

        /**
         * 断开连接的实际实现
         */
        protected void doDisconnect() throws Exception {
            doClose();
        }


        //关闭 Channel
        @Override
        public void close(ChannelPromise promise) {
            if (closed.getAndSet(true)) {
                promise.setSuccess();
                return;
            }
            if (eventLoop != null || eventLoop().inEventLoop()) {
                close0(promise);
            } else if (eventLoop != null) {
                eventLoop.execute(() -> {
                    close0(promise);
                });
            } else {
                close0(promise);
            }
        }

        private void close0(ChannelPromise promise) {
            try {
                doClose();
                promise.setSuccess();

                // 触发 channelInactive 事件
                if (registered) {
                    pipeline.fireChannelInactive();
                    pipeline.fireChannelUnregistered();
                }
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }


        //读取数据
        @Override
        public void beginRead() {
            if (!isRegistered()) {
                return;
            }

            if (eventLoop.inEventLoop()) {
                beginRead0();
            } else {
                eventLoop.execute(this::beginRead0);
            }
        }


        private void beginRead0() {
            try {
                doBeginRead();
            } catch (Exception e) {
                pipeline.fireExceptionCaught(e);
            }
        }

        // 写数据
        @Override
        public void write(Object msg, ChannelPromise promise) {
            if (!isOpen()) {
                promise.setFailure(new IllegalStateException("Channel 已关闭"));
                return;
            }

            if (eventLoop.inEventLoop()) {
                write0(msg, promise);
            } else {
                eventLoop.execute(() -> write0(msg, promise));
            }
        }

        private void write0(Object msg, ChannelPromise promise) {
            try {
                doWrite(msg);
                promise.setSuccess();
            } catch (Exception e) {
                promise.setFailure(e);
            }
        }

        @Override
        public void flush() {
            if (!isOpen()) {
                return;
            }

            if (eventLoop.inEventLoop()) {
                flush0();
            } else {
                eventLoop.execute(this::flush0);
            }
        }


        private void flush0() {
            try {
                doFlush();
            } catch (Exception e) {
                pipeline.fireExceptionCaught(e);
            }
        }

        /**
         * 刷新的实际实现
         */
        protected void doFlush() throws Exception {
            // 默认实现为空，子类可覆盖
        }
    }

    /**
     * 开始读取数据的实际实现
     *
     * @throws Exception 如果读取失败
     */
    protected abstract void doBeginRead() throws Exception;

    /**
     * 绑定地址的实际实现
     *
     * @param localAddress 本地地址
     * @throws Exception 如果绑定失败
     */
    protected abstract void doBind(SocketAddress localAddress) throws Exception;


    /**
     * 写入数据的实际实现
     *
     * @param msg 要写入的消息
     * @throws Exception 如果写入失败
     */
    protected abstract void doWrite(Object msg) throws Exception;


    /**
     * 默认的 ChannelId 实现
     */
    private static class DefaultChannelId implements ChannelId {
        private final String id;

        DefaultChannelId() {
            this.id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        @Override
        public String asShortText() {
            return id.substring(0, 8);
        }

        @Override
        public String asLongText() {
            return id;
        }

        @Override
        public int compareTo(ChannelId o) {
            return asLongText().compareTo(o.asLongText());
        }

        @Override
        public String toString() {
            return asShortText();
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChannelId)) return false;
            return asLongText().equals(((ChannelId) obj).asLongText());
        }
    }
}
