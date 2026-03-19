package com.getian.netty.channel;

import java.net.SocketAddress;

/**
 * 网络通道接口，代表一个可进行 I/O 操作的通道
 * Channel 是 Netty 网络抽象的核心接口，它代表了一个打开的连接
 * <p>
 * Channel 提供了以下核心功能：
 * 状态查询（isOpen, isActive, isRegistered）
 * 配置（config）
 * I/O 操作（read, write, flush）
 * 获取关联的 EventLoop 和 Pipeline<
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-15
 */

public interface Channel {

    /**
     * 返回Channel 的唯一标识
     *
     * @return
     */
    ChannelId id();

    /**
     * EventLoop可以理解成对Selector封装了一层，用来监控eventLoop的
     * <p>
     * Channel 的所有 I/O 操作都在此 EventLoop 的线程中执行。
     *
     * @return 关联的 EventLoop，如果未注册返回 null
     */
    EventLoop eventLoop();


    /**
     * 对于服务器创建的监听连接，返回ServerSocketChannel
     * 对于客户端创建的请求连接，返回SocketChannel
     *
     * @return
     */
    Channel parent();

    /**
     * 返回 Channel 的配置
     *
     * @return Channel 配置
     */
    ChannelConfig config();


    /**
     * 返回 ChannelPipeline
     * Pipeline 包含了处理入站和出站事件的 Handler 链。
     *
     * @return
     */
    ChannelPipeline pipeline();


    /**
     * 判断 Channel 是否打开
     *
     * @return 如果 Channel 打开返回 true
     */
    boolean isOpen();


    /**
     * 判断 Channel 是否已注册到 EventLoop
     *
     * @return 如果已注册返回 true
     */
    boolean isRegistered();


    /**
     * 判断 Channel 是否处于活动状态
     *
     * <p>对于 TCP 连接，活动状态意味着连接已建立。
     *
     * @return 如果处于活动状态返回 true
     */
    boolean isActive();

    /**
     * 关闭 Channel
     *
     * @return 关闭操作的 Future
     */
    ChannelFuture close();

    /**
     * 请求从 Channel 读取数据
     * <p>
     * 此方法触发一次读取操作，读取到的数据会通过 Pipeline 中的
     * ChannelInboundHandler.channelRead() 方法传递。
     *
     * @return this
     */
    Channel read();


    /**
     * 提供对外暴露的方法接口
     *
     * @return UnSafe
     */
    UnSafe unsafe();

    /**
     * 底层 I/O 操作接口（内部使用）
     * Unsafe 封装了不应该直接暴露给用户的底层操作。 这些操作会被 Pipeline 中的 HeadContext 调用。
     */
    interface UnSafe {
        /**
         * 注册 Channel 到 EventLoop
         *
         * @param eventLoop      要注册的 EventLoop
         * @param channelPromise 注册结果通知
         */
        void register(EventLoop eventLoop, ChannelPromise channelPromise);


        /**
         * 绑定到本地地址(一般是ServerSocketChannel)
         *
         * @param socketAddress  本地地址
         * @param channelPromise 绑定结果通知
         */
        void bind(SocketAddress socketAddress, ChannelPromise channelPromise);

        /**
         * 向远程地址发起连接(一般是SocketChannel)
         *
         * @param remoteAddress  远程地址
         * @param localAddress   本地地址
         * @param channelPromise 连接结果通知
         */
        void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise channelPromise);

        /**
         * 断开连接
         *
         * @param promise 断开连接通知
         */
        void disConnect(ChannelPromise promise);

        /**
         * 关闭 Channel
         *
         * @param promise 操作结果通知
         */
        void close(ChannelPromise promise);

        /**
         * 读取数据
         */
        void beginRead();

        /**
         * 写数据
         *
         * @param msg
         * @param promise
         */
        void write(Object msg, ChannelPromise promise);

        /**
         * 刷新所有写入信息
         */
        void flush();
    }
}
