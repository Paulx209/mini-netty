package com.getian.netty.handler.logging;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.channel.*;

import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 日志记录器 记录所有入站和出站事件的日志信息，用于调试和监控。
 *@Author: sonicge
 *@CreateTime: 2026-03-26
 */

public class LoggingHandler extends ChannelDuplexHandler {
    /**
     * 默认级别
     */
    private static final LogLevel DEFAULT_LEVEL = LogLevel.DEBUG;

    /**
     * 日志输出名称
     */
    private final String name;

    /**
     * 日志级别
     */
    private final LogLevel level;

    /**
     * name默认，level默认
     */
    public LoggingHandler() {
        this(DEFAULT_LEVEL);
    }

    /**
     * name默认，level不默认
     * @param level
     */
    public LoggingHandler(LogLevel level) {
        this(LoggingHandler.class.getSimpleName(), level);
    }

    /**
     * name不默认，level默认
     * @param name
     */
    public LoggingHandler(String name) {
        this(name, DEFAULT_LEVEL);
    }

    /**
     * name不默认，level不默认
     * @param name
     * @param level
     */
    public LoggingHandler(String name, LogLevel level) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (level == null) {
            throw new NullPointerException("level");
        }
        this.name = name;
        this.level = level;
    }


    /**
     * 返回日志级别
     *
     * @return 日志级别
     */
    public LogLevel level() {
        return level;
    }

    /**
     * 返回日志名称
     * @return 日志名称
     */
    public String name() {
        return name;
    }

    // ========== 入站事件日志 ==========

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        log(ctx, "REGISTERED");
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        log(ctx, "UNREGISTERED");
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log(ctx, "ACTIVE");
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log(ctx, "INACTIVE");
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log(ctx, "READ", msg);
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        log(ctx, "READ_COMPLETE");
        ctx.fireChannelReadComplete();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log(ctx, "EXCEPTION", cause);
        ctx.fireExceptionCaught(cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)  {
        log(ctx, "USER_EVENT", evt);
        ctx.fireUserEventTriggered(evt);
    }

    // ========== 出站操作日志 ==========

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        log(ctx, "BIND", localAddress);
        super.bind(ctx, localAddress, promise);
        ;
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        log(ctx, "CONNECT", remoteAddress, localAddress);
        super.connect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        log(ctx, "DISCONNECT");
        super.disconnect(ctx, promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        log(ctx, "CLOSE");
        super.close(ctx, promise);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        log(ctx, "WRITE", msg);
        super.write(ctx, msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        log(ctx, "FLUSH");
        super.flush(ctx);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        log(ctx, "READ_REQUEST");
        super.read(ctx);
    }

    /**
     * 格式化日志消息（无数据）
     * @param ctx 上下文
     * @param event 事件名称
     */
    protected void log(ChannelHandlerContext ctx, String event) {
        log(ctx, event, null);
    }

    /**
     *
     * @param ctx 上下文
     * @param event 事件名称
     * @param data 数据对象
     */
    protected void log(ChannelHandlerContext ctx, String event, Object data) {
        String channelId = ctx.channel().id().asShortText();
        String message = formatMessage(event, channelId, data);
        doLog(message);
    }

    /**
     * 格式化日志消息（连接事件，有远程和本地地址）
     *
     * @param ctx 上下文
     * @param event 事件
     * @param remoteAddress 远程地址
     * @param localAddress 本地地址
     */
    protected void log(ChannelHandlerContext ctx, String event, SocketAddress remoteAddress, SocketAddress localAddress) {
        String channelId = ctx.channel().id().asShortText();
        String data = remoteAddress + (localAddress != null ? "->" + localAddress : "");
        String message = formatMessage(event, channelId, data);
        doLog(message);
    }


    /**
     * 格式化消息字符串
     * @param event 事件
     * @param channelId channelId
     * @param data 数据
     * @return 格式化后的字符串  [name][channelId] event:data
     */
    private String formatMessage(String event, String channelId, Object data) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(name).append("]");
        sb.append("[").append(channelId).append("]");
        sb.append(" ").append(event);
        if (data != null) {
            sb.append(": ");
            sb.append(formatData(data));
        }
        return sb.toString();
    }

    /**
     * 1.如果数据对象的类型是ByteBuf的话，转换为String类型
     * 2.如果数据对象的类型是异常的话，返回异常信息
     * 3.其他类型的话，转换为字符串类型
     * @param data 数据
     * @return 格式化数据对象
     */
    protected String formatData(Object data) {
        if (data instanceof ByteBuf) {
            return formatByteBuf((ByteBuf) data);
        } else if (data instanceof Throwable) {
            Throwable t = (Throwable) data;
            return t.getClass().getSimpleName() + " " + t.getMessage();
        } else {
            return String.valueOf(data);
        }
    }

    /**
     * 1.判断buf的长度是否为0，为0的话直接return
     * 2.创建StringBuilder类，首先拼接长度信息
     * 3.读取可读的字节（不移动对应的读指针）
     * 4.显示十六进制内容
     * 5.显示字符串内容
     * @param buf
     * @return
     */
    private String formatByteBuf(ByteBuf buf) {
        //1.判断buf的长度是否为0，为0的话直接return
        int length = buf.readableBytes();
        if (length == 0) {
            return "ByteBuf(0B)";
        }

        //2.创建StringBuilder类，首先拼接长度信息
        StringBuilder sb = new StringBuilder();
        sb.append("ByteBuf(").append(length).append("B");

        //3.读取可读的字节（不移动对应的读指针）
        int maxShow = Math.min(length, 64);
        byte[] data = new byte[maxShow];
        buf.getBytes(buf.readerIndex(), data);

        //4.显示十六进制内容
        sb.append(",hex=");
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02x", data[i] & 0xFF));
            if (i < maxShow - 1) {
                sb.append(" ");
            }
            if (i > maxShow) {
                //如果数据超过64字节的话 后面的数据默认不显示 使用...代替
                sb.append("...");
            }
        }
        //5.显示字符串内容
        if (isPrintableAscii(data)) {
            sb.append(",str=\"");
            sb.append(new String(data, StandardCharsets.US_ASCII));
            if (length > maxShow) {
                sb.append("...");
            }
            sb.append("\"");
        }
        sb.append(")");
        return sb.toString();
    }


    /**
     * 判断字节数组是否是可打印 ASCII
     *
     * @param bytes 字节数组
     * @return 是否可打印
     */
    private boolean isPrintableAscii(byte[] bytes) {
        for (byte b : bytes) {
            if (b < 0x20 || b > 0x7E) {
                return false;
            }
        }
        return bytes.length > 0;
    }

    /**
     * 实际输出日志
     * 前三个一个级别；后两个一个级别
     * @param message
     */
    protected void doLog(String message) {
        switch (level) {
            case TRACE:
            case DEBUG:
            case INFO:
                System.out.println("[" + level + "] " + message);
                break;
            case WARN:
            case ERROR:
                System.err.println("[" + level + "] " + message);
                break;
        }
    }

    @Override
    public String toString() {
        return "LoggingHandler{name='" + name + "', level=" + level + "}";
    }


}
