package com.getian.netty.bootstrap;

import com.getian.netty.channel.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bootstrap 抽象基类
 * 提供服务端和客户端 Bootstrap 的共同配置能力。
 * 使用流式 API (Builder 模式) 配置 Channel。
 * <p>
 * 配置项包括：
 * 1.EventLoopGroup - 处理 I/O 事件的线程池
 * 2.Channel 类型 - 使用的 Channel 实现类
 * 3.ChannelHandler - 处理 Channel 事件的处理器
 * 4.Channel 选项 - Channel 配置选项
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-23
 */

//递归泛型
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> {
    /**
     * EVentLoopGroup
     */
    private volatile EventLoopGroup group;

    /**
     * ChannelFactory 工厂类用于创建Channel
     */
    private volatile ChannelFactory<? extends C> channelFactory;

    /**
     * Channel 选项
     */
    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<>();

    /**
     * Channel 属性
     */
    private final Map<Object, Object> attrs = new LinkedHashMap<>();

    /**
     * 处理器
     */
    private volatile ChannelHandler handler;

    /**
     * 本机地址 bind用
     */
    private volatile SocketAddress localAddress;


    /**
     * 默认构造函数
     */
    protected AbstractBootstrap() {
    }

    /**
     * 复制构造函数
     *
     * @param bootstrap
     */
    protected AbstractBootstrap(AbstractBootstrap<B, C> bootstrap) {
        this.group = bootstrap.group;
        this.channelFactory = bootstrap.channelFactory;
        this.localAddress = bootstrap.localAddress;
        this.handler = bootstrap.handler;
        synchronized (bootstrap.options) {
            this.options.putAll(bootstrap.options);
        }
        synchronized (bootstrap.attrs) {
            this.attrs.putAll(bootstrap.attrs);
        }
    }

    /**
     * 设置group
     *
     * @param group EventLoopGroup
     * @return
     */
    public B group(EventLoopGroup group) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        if (this.group != null) {
            throw new IllegalStateException("group set already");
        }
        this.group = group;
        return self();
    }

    /**
     * 设置Channel类型
     *
     * @param channelClass Channel类
     * @return this
     */
    public B channel(Class<? extends C> channelClass) {
        if (channelClass == null) {
            throw new NullPointerException("channelClass");
        }
        return channelFactory(new ReflectiveChannelFactory<>(channelClass));
    }

    /**
     * 设置 Channel 工厂
     *
     * @param channelFactory Channel 工厂
     * @return this
     */
    public B channelFactory(ChannelFactory<? extends C> channelFactory) {
        if (channelFactory == null) {
            throw new NullPointerException("channelFactory");
        }
        if (this.channelFactory != null) {
            throw new IllegalStateException("channelFactory set already");
        }
        this.channelFactory = channelFactory;
        return self();
    }

    /**
     * 设置本地地址
     *
     * @param localAddress 本地地址
     * @return this
     */
    public B localAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
        return self();
    }

    /**
     * 设置本地端口
     *
     * @param port 端口号
     * @return this
     */
    public B localAddress(int port) {
        return localAddress(new InetSocketAddress(port));
    }

    /**
     * 设置 Channel 选项
     *
     * @param option 选项
     * @param value  值
     * @param <T>    值类型
     * @return this
     */
    public <T> B option(ChannelOption<T> option, T value) {
        if (option == null) {
            throw new NullPointerException("option");
        }
        synchronized (options) {
            if (value == null) {
                options.remove(option);
            } else {
                options.put(option, value);
            }
        }
        return self();
    }

    /**
     * 设置处理器
     *
     * @param handler 处理器
     * @return this
     */
    public B handler(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler is null");
        }
        this.handler = handler;
        return self();
    }

    /**
     * 绑定到本地地址
     * bind()方法一般是ServerSocketChannel执行的方法，一般连接的地址是本地的！
     *
     * @return ChannelFuture
     */
    public ChannelFuture bind() {
        validate();
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        return doBind(localAddress);
    }

    /**
     * 绑定到指定端口号
     *
     * @param port 端口号
     * @return
     */
    public ChannelFuture bind(int port) {
        validate();
        return doBind(new InetSocketAddress(port));
    }

    /**
     * 绑定到指定地址
     *
     * @param localAddress 本地地址
     * @return
     */
    public ChannelFuture bind(SocketAddress localAddress) {
        validate();
        if (localAddress == null) {
            throw new NullPointerException("localAddress");
        }
        return doBind(localAddress);
    }

    private ChannelFuture doBind(SocketAddress localAddress) {
        //1.初始化和注册channel
        final C channel = initAndRegister();
        if (channel == null) {
            return null;
        }
        DefaultChannelPromise promise = new DefaultChannelPromise(channel);
        //2.执行绑定操作
        doBind0(channel, localAddress, promise);

        return promise;
    }

    /**
     * 执行绑定
     */
    private void doBind0(final Channel channel, final SocketAddress localAddress, final ChannelPromise promise) {
        channel.eventLoop().execute(() -> {
            try {
                channel.unsafe().bind(localAddress, promise);
            } catch (Exception e) {
                promise.setFailure(e);
            }
        });
    }

    /**
     * 初始化并注册 Channel
     *
     * @return 创建的 Channel
     */
    private C initAndRegister() {
        C channel = null;
        try {
            //1.根据反射创建Channel
            channel = channelFactory.newChannel();
            //2.初始化channel
            init(channel);
        } catch (Throwable t) {
            if (channel != null) {
                channel.close();
            }
            throw new RuntimeException("Failed to initialize channel", t);
        }
        //3.将channel注册到eventLoopGroup中
        EventLoop eventLoop = group().next();

        //channel和eventLoop进行绑定
        channel.unsafe().register(eventLoop);

        return channel;
    }

    abstract void init(C channel) throws Exception;

    public B validate() {
        if (group == null) {
            throw new IllegalStateException("group not set");
        }
        if (channelFactory == null) {
            throw new IllegalStateException("channel or channelFactory not set");
        }
        return self();
    }

    private B self() {
        return (B) this;
    }

    /**
     * 获取 EventLoopGroup
     *
     * @return
     */
    public final EventLoopGroup group() {
        return group;
    }

    /**
     * 获取处理器
     */
    final ChannelHandler handler() {
        return handler;
    }

    /**
     * 获取本地地址
     */
    final SocketAddress localAddress() {
        return localAddress;
    }


    /**
     * 获取 Channel 选项
     */
    final Map<ChannelOption<?>, Object> options() {
        synchronized (options) {
            return new LinkedHashMap<>(options);
        }
    }


    /**
     * 获取 Channel 属性
     */
    final Map<Object, Object> attrs() {
        synchronized (attrs) {
            return new LinkedHashMap<>(attrs);
        }
    }

    /**
     * 复制 Bootstrap
     */
    public abstract B clone();


    public interface ChannelFactory<T extends Channel> {
        /**
         * 创建新的channel
         *
         * @return
         */
        T newChannel();
    }

    private static final class ReflectiveChannelFactory<T extends Channel> implements ChannelFactory<T> {
        private final Class<? extends T> clazz;


        ReflectiveChannelFactory(Class<? extends T> clazz) {
            this.clazz = clazz;
        }

        /**
         * 根据反射方法创建Channel
         *
         * @return Channel
         */
        @Override
        public T newChannel() {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                throw new RuntimeException("Unable to create Channel from class " + clazz, t);
            }
        }
    }
}
