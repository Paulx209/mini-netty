package com.getian.netty.channel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChannelConfig 的默认实现。
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-19
 */
public class DefaultChannelConfig implements ChannelConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 30000; //30s
    private static final int DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK = 64 * 1024;
    private static final int DEFAULT_WRITE_BUFFER_LOW_WATER_MARK = 32 * 1024;
    private static final boolean DEFAULT_AUTO_READ = true;
    private static final boolean DEFAULT_AUTO_CLOSE = true;

    protected final Channel channel;

    private final Map<ChannelOption<?>, Object> options = new ConcurrentHashMap<>();

    // 常用配置的直接字段（性能优化）
    private volatile int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;
    private volatile int writeBufferHighWaterMark = DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK;
    private volatile int writeBufferLowWaterMark = DEFAULT_WRITE_BUFFER_LOW_WATER_MARK;
    private volatile boolean autoRead = DEFAULT_AUTO_READ;
    private volatile boolean autoClose = DEFAULT_AUTO_CLOSE;

    public DefaultChannelConfig(Channel channel) {
        this.channel = Objects.requireNonNull(channel, "channel");
    }


    /**
     * 返回默认的配置项？
     *
     * @return
     */
    @Override
    public Map<ChannelOption<?>, Object> getOptions() {
        Map<ChannelOption<?>, Object> result = new HashMap<>();

        result.put(ChannelOption.CONNECT_TIMEOUT_MILLIS, DEFAULT_CONNECT_TIMEOUT_MILLIS);
        result.put(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK);
        result.put(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, DEFAULT_WRITE_BUFFER_LOW_WATER_MARK);
        result.put(ChannelOption.AUTO_READ, DEFAULT_AUTO_READ);
        result.put(ChannelOption.AUTO_CLOSE, DEFAULT_AUTO_CLOSE);

        result.putAll(options);

        return result;
    }

    @Override
    public boolean setOptions(Map<ChannelOption<?>, ?> options) {
        if (options == null) return false;

        boolean success = true;

        Set<? extends Map.Entry<ChannelOption<?>, ?>> entries = options.entrySet();
        Iterator<? extends Map.Entry<ChannelOption<?>, ?>> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Map.Entry<ChannelOption<?>, ?> entry = iterator.next();
            ChannelOption<Object> option = (ChannelOption<Object>) entry.getKey();
            Object value = entry.getValue();
            if (!setOption(option, value)) {
                success = false;
            }
        }
        return success;
    }

    @Override
    public <T> T getOption(ChannelOption<T> option) {
        Objects.requireNonNull(option, "option");

        // 优先检查常用选项
        if (option == ChannelOption.CONNECT_TIMEOUT_MILLIS) {
            return (T) Integer.valueOf(getConnectTimeoutMillis());
        } else if (option == ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK) {
            return (T) Integer.valueOf(getWriteBufferHighWaterMark());
        } else if (option == ChannelOption.WRITE_BUFFER_LOW_WATER_MARK) {
            return (T) Integer.valueOf(getWriteBufferLowWaterMark());
        } else if (option == ChannelOption.AUTO_READ) {
            return (T) Boolean.valueOf(isAutoRead());
        } else if (option == ChannelOption.AUTO_CLOSE) {
            return (T) Boolean.valueOf(isAutoClose());
        }
        // 如果常用选项中没有的话 从通用选项存储中获取
        return (T) options.get(option);
    }

    @Override
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        //1.如果option为null的话，直接抛出异常
        Objects.requireNonNull(option, "option");

        //2.校验value是否为null，为空抛出异常
        try {
            option.validate(value);
        } catch (Exception e) {
            return false;
        }

        if (option == ChannelOption.CONNECT_TIMEOUT_MILLIS) {
            setConnectTimeoutMillis((Integer) value);
            return true;
        } else if (option == ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK) {
            setWriteBufferHighWaterMark((Integer) value);
            return true;
        } else if (option == ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK) {
            setWriteBufferLowWaterMark((Integer) value);
            return true;
        } else if (option == ChannelOption.AUTO_READ) {
            setAutoRead((Boolean) value);
            return true;
        } else if (option == ChannelOption.AUTO_CLOSE) {
            setAutoClose((Boolean) value);
            return true;
        }

        //存储到通用选项存储
        options.put(option, value);
        return true;
    }

    @Override
    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    @Override
    public ChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        if (connectTimeoutMillis < 0) {
            throw new IllegalArgumentException(
                    "connectTimeoutMillis must be >= 0: " + connectTimeoutMillis);
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        return this;
    }

    @Override
    public int getWriteBufferHighWaterMark() {
        return writeBufferHighWaterMark;
    }

    @Override
    public ChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
        if (writeBufferHighWaterMark < 0) {
            throw new IllegalArgumentException(
                    "writeBufferHighWaterMark must be >= 0: " + writeBufferHighWaterMark);
        }
        if (writeBufferHighWaterMark < writeBufferLowWaterMark) {
            throw new IllegalArgumentException(
                    "writeBufferHighWaterMark must be >= writeBufferLowWaterMark: "
                            + writeBufferHighWaterMark + " < " + writeBufferLowWaterMark);
        }
        this.writeBufferHighWaterMark = writeBufferHighWaterMark;
        return this;
    }

    @Override
    public int getWriteBufferLowWaterMark() {
        return this.writeBufferLowWaterMark;
    }

    @Override
    public ChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
        if (writeBufferLowWaterMark < 0) {
            throw new IllegalArgumentException(
                    "writeBufferLowWaterMark must be >= 0: " + writeBufferLowWaterMark);
        }
        if (writeBufferLowWaterMark > writeBufferHighWaterMark) {
            throw new IllegalArgumentException(
                    "writeBufferLowWaterMark must be <= writeBufferHighWaterMark: "
                            + writeBufferLowWaterMark + " > " + writeBufferHighWaterMark);
        }
        this.writeBufferLowWaterMark = writeBufferLowWaterMark;
        return this;
    }

    @Override
    public boolean isAutoRead() {
        return autoRead;
    }

    @Override
    public ChannelConfig setAutoRead(boolean autoRead) {
        boolean oldAutoRead = this.autoRead;
        this.autoRead = autoRead;

        // 如果从禁用变为启用，触发读取
        if (autoRead && !oldAutoRead) {
            channel.read();
        }
        return this;
    }

    @Override
    public boolean isAutoClose() {
        return autoClose;
    }

    @Override
    public ChannelConfig setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
        return this;
    }
}
