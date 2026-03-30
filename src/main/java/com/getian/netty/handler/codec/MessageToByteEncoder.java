package com.getian.netty.handler.codec;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.buffer.HeapByteBuf;
import com.getian.netty.channel.ChannelHandlerContext;
import com.getian.netty.channel.ChannelOutboundHandlerAdapter;
import com.getian.netty.channel.ChannelPromise;

/**
 * 负责出站 消息到字节的编码器基类
 * 将出站消息对象编码为 ByteBuf。这是编写协议编码器的基类。抽象类，具体encode方法交给子类实现
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-23
 */

public abstract class MessageToByteEncoder<I> extends ChannelOutboundHandlerAdapter {
    /**
     * 支持的消息类型
     */
    private final Class<? extends I> outboundMessageType;

    /**
     * 默认初始容量
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 256;

    /**
     * 默认最大容量
     */
    private static final int DEFAULT_MAX_CAPACITY = 65536;

    /**
     * 使用 Object 作为默认匹配类型
     */
    protected MessageToByteEncoder() {
        this.outboundMessageType = (Class<? extends I>) Object.class;
    }


    /**
     * 指定支持的消息类型
     *
     * @param outboundMessageType 支持的消息类型
     */
    protected MessageToByteEncoder(Class<? extends I> outboundMessageType) {
        this.outboundMessageType = outboundMessageType;
    }


    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = null;
        //1.如果该消息类型可以被处理
        try {
            if (acceptOutboundMessage(msg)) {
                //因为我们outboundMessageType的类型为I，可以被处理的话，说明可以转换成I类型的变量
                I cast = (I) msg;
                //分配输出缓冲区
                buf = allocateBuffer(ctx);
                try {
                    //编码
                    encode(ctx, cast, buf);
                } finally {
                    //如果消息的类型为ByteBuf的话 就需要释放array
                    if (msg instanceof ByteBuf) {
                        ((ByteBuf) msg).release();
                    }
                }
                //如果encode操作编码出来了内容 也就是可读
                if (buf.isReadable()) {
                    ctx.write(buf, promise);
                } else {
                    buf.release();
                    ctx.write(new HeapByteBuf(0, 0), promise);
                }
                buf = null;
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable e) {
            throw new EncoderException(e);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    /**
     * 检查消息是否可以被编码
     *
     * @param msg 消息
     * @return boolean
     */
    protected boolean acceptOutboundMessage(Object msg) {
        // msg instanceOf outboundMessageType
        return outboundMessageType.isInstance(msg);
    }


    /**
     * 分配输出缓冲区
     *
     * @param ctx 上下文
     * @return 新的 ByteBuf
     */
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx) {
        return new HeapByteBuf(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY);
    }

    /**
     * 编码消息到ByteBuf
     *
     * @param ctx 上下文
     * @param msg 消息
     * @param out 输出缓冲区
     * @throws Exception 编码异常
     */
    protected abstract void encode(ChannelHandlerContext ctx, I msg, ByteBuf out);
}
