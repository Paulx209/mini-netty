package com.getian.netty.channel;

/**
 * 网络通道接口，代表一个可进行 I/O 操作的通道
 * Channel 是 Netty 网络抽象的核心接口，它代表了一个打开的连接
 *
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
     * @return
     */
    ChannelId id();

    /**
     * EventLoop可以理解成对Selector封装了一层，用来监控eventLoop的
     *
     * Channel 的所有 I/O 操作都在此 EventLoop 的线程中执行。
     * @return 关联的 EventLoop，如果未注册返回 null
     */
    EventLoop eventLoop();


    /**
     * 对于服务器创建的监听连接，返回ServerSocketChannel
     * 对于客户端创建的请求连接，返回SocketChannel
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
     *  返回 ChannelPipeline
     *  Pipeline 包含了处理入站和出站事件的 Handler 链。
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
     *
     * 此方法触发一次读取操作，读取到的数据会通过 Pipeline 中的
     * ChannelInboundHandler.channelRead() 方法传递。
     *
     * @return this
     */
    Channel read();
}
