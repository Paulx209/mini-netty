package com.getian.netty.bootstrap;

import com.getian.netty.channel.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

/**
 *@Author: sonicge
 *@CreateTime: 2026-03-24
 */

public class Bootstrap extends AbstractBootstrap<Bootstrap, Channel> {
    /**
     * 远程地址
     */
    private volatile SocketAddress remoteAddress;

    /**
     * 默认构造函数
     */
    public Bootstrap() {

    }

    /**
     * 克隆构造函数
     * @param bootstrap
     */
    private Bootstrap(Bootstrap bootstrap) {
        super(bootstrap);
        this.remoteAddress = bootstrap.remoteAddress;
    }

    /**
     * 设置远程地址
     * @param remoteAddress
     */
    public Bootstrap remoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
        return this;
    }

    /**
     * 设置远程地址
     * @param host 主机地址
     * @param port 端口号
     */
    public Bootstrap remoteAddress(String host, int port) {
        return remoteAddress(new InetSocketAddress(host, port));
    }

    /**
     * 设置远程地址
     *
     * @param port 端口号（连接到本地）
     * @return this
     */
    public Bootstrap remoteAddress(int port) {
        return remoteAddress(new InetSocketAddress(port));
    }

    /**
     * 连接到预设的远程地址
     * @return ChannelFuture
     */
    public ChannelFuture connect() {
        validate();
        SocketAddress remoteAddress = this.remoteAddress;
        if (remoteAddress == null) {
            throw new IllegalStateException("remoteAddress not set");
        }
        return doConnect(remoteAddress, localAddress());
    }

    /**
     * 连接到指定远程地址
     * @param remoteAddress 远程地址
     * @return
     */
    public ChannelFuture connect(SocketAddress remoteAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validate();
        return doConnect(remoteAddress, localAddress());
    }

    /**
     * 连接到指定远程地址
     * @param host 主机号
     * @param port 端口号
     * @return
     */
    public ChannelFuture connect(String host, int port) {
        return connect(new InetSocketAddress(host, port));
    }

    /**
     * 连接到指定远程地址
     * @param remoteAddress 远程地址
     * @param localAddress  本机地址
     * @return
     */
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        if (remoteAddress == null) {
            throw new NullPointerException("remoteAddress");
        }
        validate();
        return doConnect(remoteAddress, localAddress);
    }

    /**
     * 执行连接操作
     * @param remoteAddress 远程的连接地址
     * @param localAddress  本地的连接地址
     * @return futrue
     */
    private ChannelFuture doConnect(SocketAddress remoteAddress, SocketAddress localAddress) {
        final Channel channel = initAndRegister();
        if (channel == null) return null;
        final DefaultChannelPromise promise = new DefaultChannelPromise(channel);
        doConnect0(channel, remoteAddress, localAddress, promise);
        return promise;
    }

    /**
     * 进行连接
     * @param channel NioSocketChannel 客户端channel
     * @param remoteAddress 远程地址
     * @param localAddress  本地地址
     * @param promise 异步执行结果
     */
    private void doConnect0(final Channel channel, final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        channel.eventLoop().execute(() -> {
            try {
                //1.如果设置了本地地址，先和本地地址进行连接  客户端这里 bind 的目的是“先确定我自己从哪个本地地址/端口出去 和服务端的bind作用不一致
                if (localAddress != null) {
                    channel.unsafe().bind(localAddress, new DefaultChannelPromise(channel));
                }
                channel.unsafe().connect(remoteAddress, localAddress, promise);
            } catch (Exception e) {
                promise.setFailure(e);
            }
        });
    }

    @Override
    void init(Channel channel) throws Exception {
        //给子channel设置options
        setChannelOptions(channel, options());

        //添加handler到pipeline中
        ChannelHandler handler = handler();
        if (handler != null) {
            channel.pipeline().addLast("handler", handler);
        }
    }

    private static void setChannelOptions(Channel channel, Map<ChannelOption<?>, Object> options) {
        for (Map.Entry<ChannelOption<?>, Object> entry : options.entrySet()) {
            setChannelOption(channel, entry.getKey(), entry.getValue());
        }
    }

    private static void setChannelOption(Channel channel, ChannelOption<?> option, Object value) {
        try {
            ChannelConfig config = channel.config();
            if (config != null) {
                config.setOption((ChannelOption<Object>) option, value);
            }
        } catch (Exception e) {
            System.err.println("[Bootstrap] 设置选项失败: " + option + " = " + value);
        }
    }

    public Bootstrap validate(){
        super.validate();
        if(handler() == null){
            throw new IllegalStateException("handler not set");
        }
        return this;
    }


    @Override
    public Bootstrap clone() {
        return new Bootstrap(this);
    }

    /**
     * 使用指定的EventLoopGroup克隆
     * @param group  新的 EventLoopGroup
     * @return  克隆的 Bootstrap
     */
    public Bootstrap clone(EventLoopGroup group){
        Bootstrap bootstrap = new Bootstrap(this);
        //简化处理

        return bootstrap;
    }
}
