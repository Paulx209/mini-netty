package com.getian.netty.bootstrap;

import com.getian.netty.channel.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 服务端启动器
 * ServerBootstrap 是用于引导服务端的辅助类
 * 它使用流式 API 配置服务端 Channel，并启动服务监听
 * ServerBootstrap 支持主从 Reactor 模型
 * 1.parentGroup (bossGroup) - 负责接受新连接
 * 2.childGroup (workerGroup) - 负责处理已建立连接的 I/O 操作
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-24
 */

public class ServerBootstrap extends AbstractBootstrap<ServerBootstrap, Channel> {
    /**
     * 子EventLoopGroup （用于处理已接受的连接）
     */
    private volatile EventLoopGroup childGroup;

    /**
     * 子Channel选项
     */
    private final Map<ChannelOption<?>, Object> childOptions = new LinkedHashMap<>();

    /**
     * 子 Channel 属性
     */
    private final Map<Object, Object> childAttrs = new LinkedHashMap<>();

    /**
     * 子 channel 处理器
     */
    private volatile ChannelHandler childHandler;

    public ServerBootstrap() {
    }

    /**
     * 复制构造函数
     *
     * @param bootstrap
     */
    private ServerBootstrap(ServerBootstrap bootstrap) {
        super(bootstrap);
        this.childGroup = bootstrap.childGroup;
        this.childHandler = bootstrap.childHandler;
        synchronized (bootstrap.childOptions) {
            this.childOptions.putAll(bootstrap.childOptions);
        }
        synchronized (bootstrap.childAttrs) {
            this.childAttrs.putAll(bootstrap.childAttrs);
        }
    }

    /**
     * 设置Boss 和 Worker 的 EventLoopGroup
     *
     * @param parentGroup EventLoopGroup 属于Boss线程组 一般有1个线程 负责接收新的连接
     * @param childGroup  EventLoopGroup 属于Worker线程组 一般有多个线程 负责处理I/O操作
     * @return this
     */
    public ServerBootstrap group(EventLoopGroup parentGroup, EventLoopGroup childGroup) {
        super.group(parentGroup);
        if (childGroup == null) {
            throw new NullPointerException("childGroup");
        }
        if (this.childGroup != null) {
            throw new IllegalStateException("childGroup set already");
        }
        this.childGroup = childGroup;
        return this;
    }

    /**
     * 设置子Channel的选项
     * 子 Channel 选项会应用到每个接受的客户端连接。
     * 1.TCP保活
     * 2.禁用Nagle算法
     * 3.接收缓冲区大小
     *
     * @param option 选项
     * @param value  值
     * @param <T>    值类型
     * @return
     */
    public <T> ServerBootstrap childOption(ChannelOption<T> option, T value) {
        if (option == null) {
            throw new NullPointerException("childOption");
        }
        synchronized (childOptions) {
            if (value == null) {
                childOptions.remove(option);
            } else {
                childOptions.put(option, value);
            }
        }
        return this;
    }

    /**
     * 设置子 Channel 的属性
     *
     * @param key   属性键
     * @param value 属性值
     * @return this
     */
    public ServerBootstrap childAttr(Object key, Object value) {
        if (key == null) {
            throw new NullPointerException("childAttr key");
        }
        synchronized (childAttrs) {
            if (value == null) {
                childAttrs.remove(key);
            } else {
                childAttrs.put(key, value);
            }
        }
        return this;
    }

    public ServerBootstrap childHandler(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.childHandler = handler;
        return this;
    }

    /**
     * 初始化服务端 Channel
     * 为服务端 Channel 配置处理器，该处理器会在接受新连接时：
     * 1.为子 Channel 设置选项和属性
     * 2.将子 Channel 注册到 childGroup
     * 3.为子 Channel 添加 childHandler
     *
     * @param channel
     */
    void init(Channel channel) {
        // 设置服务端 Channel 的选项
        setChannelOptions(channel, options());

        //获取pipeline
        ChannelPipeline pipeline = channel.pipeline();

        //保存子Channel配置到局部变量
        final EventLoopGroup currentChildGroup = childGroup;
        final ChannelHandler currentChildHandler = childHandler;
        final Map<ChannelOption<?>, Object> currentChildOptions;
        final Map<Object, Object> currentChildAttrs;
        synchronized (childOptions) {
            currentChildOptions = new LinkedHashMap<>(childOptions);
        }
        synchronized (childAttrs) {
            currentChildAttrs = new LinkedHashMap<>(childAttrs);
        }

        // 添加 ServerBootstrapAcceptor 处理器
        // 该处理器负责接受新连接并配置子 Channel
        pipeline.addLast("ServerBootstrapAcceptor",
                new ServerBootstrapAcceptor(childGroup, childHandler, childOptions, childAttrs));

        //如果设置了handler 也添加到服务端 Pipeline
        ChannelHandler handler = handler();
        if (handler != null) {
            pipeline.addLast("handler", handler);
        }
    }

    @Override
    public ServerBootstrap validate() {
        //父类检查group \ channelFactory
        super.validate();

        if(childHandler == null){
            throw new IllegalStateException("childHandler not set");
        }
        if(childGroup == null){
            // 如果没有设置 childGroup，使用 parentGroup
            System.out.println("[ServerBootstrap] childGroup 未设置，使用 parentGroup");
            childGroup = group();
        }
        return this;
    }

    @Override
    public ServerBootstrap clone() {
        return new ServerBootstrap(this);
    }

    /**
     * 设置 Channel 选项
     *
     * @param channel
     * @param childOptions
     */
    private static void setChannelOptions(Channel channel, Map<ChannelOption<?>, Object> childOptions) {
        Set<Map.Entry<ChannelOption<?>, Object>> entries = childOptions.entrySet();
        for (Map.Entry<ChannelOption<?>, Object> entry : entries) {
            setChannelOption(channel, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 设置单个 Channel 选项
     */
    private static void setChannelOption(Channel channel, ChannelOption<?> option, Object value) {
        try {
            ChannelConfig config = channel.config();
            if (config != null) {
                config.setOption((ChannelOption<Object>) option, value);
            }
        } catch (Exception e) {
            System.err.println("[ServerBootstrap] 设置选项失败: " + option + " = " + value);
        }
    }

    /**
     * 获取子 EventLoopGroup
     */
    public final EventLoopGroup childGroup() {
        return childGroup;
    }

    /**
     * 服务端接受器
     *
     * <p>这是一个内部 Handler，负责处理服务端接受的新连接。
     * 当服务端 Channel 接受到新连接时，它会：
     * <ol>
     *   <li>接收 channelRead 事件中的子 Channel</li>
     *   <li>为子 Channel 配置选项和属性</li>
     *   <li>添加 childHandler 到子 Channel 的 Pipeline</li>
     *   <li>将子 Channel 注册到 childGroup</li>
     * </ol>
     */
    private static class ServerBootstrapAcceptor extends ChannelInboundHandlerAdapter {
        private final EventLoopGroup childGroup;
        private final ChannelHandler childHandler;
        private final Map<ChannelOption<?>, Object> childOptions;
        private final Map<Object, Object> childAttrs;

        ServerBootstrapAcceptor(
                EventLoopGroup childGroup,
                ChannelHandler childHandler,
                Map<ChannelOption<?>, Object> childOptions,
                Map<Object, Object> childAttrs) {
            this.childGroup = childGroup;
            this.childHandler = childHandler;
            this.childOptions = childOptions;
            this.childAttrs = childAttrs;

        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            final Channel child = (Channel) msg;
            //1.添加handler
            child.pipeline().addLast("childHandler", childHandler);

            //2.设置子channel的选项
            setChannelOptions(child, childOptions);

            // 设置子 Channel 的属性 (如果需要，可以扩展 Channel 接口支持属性)
            // 这里暂时不实现属性设置

            try {
                childGroup.register(child).addListener(future -> {
                    if (!future.isSuccess()) {
                        System.err.println("[ServerBootstrapAcceptor] 注册子 Channel 失败: " +
                                future.cause().getMessage());
                        child.close();
                    }
                });
            } catch (Exception e) {
                System.err.println("[ServerBootstrapAcceptor] 注册子 Channel 异常: " + e.getMessage());
                child.close();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.err.println("[ServerBootstrapAcceptor] 异常: " + cause.getMessage());
            ctx.fireExceptionCaught(cause);
        }
    }

}
