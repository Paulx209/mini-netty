package com.getian.netty.handler.codec;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.buffer.HeapByteBuf;
import com.getian.netty.channel.ChannelHandlerContext;
import com.getian.netty.channel.ChannelInboundHandlerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 字节到消息解码器 (解决粘包拆包问题的核心类)
 * 接收字节 -> 缓冲区 -> 解码成消息对象
 * <p>
 * 粘包问题：多条消息合并成一个TCP包发送  TCP是流式协议，不保证边界
 * 拆包问题：一条消息被拆分成多个TCP包发送
 * <p>
 * 为啥继承入站的？ 入站一般是消息从外部进入 所以需要用到解码器
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-22
 */

public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {
    /**
     * 累计缓冲区
     */
    private ByteBuf cumulation;

    /**
     * 默认初始容量
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 256;

    /**
     * 默认最大容量
     */
    private static final int DEFAULT_MAX_CAPACITY = 65536;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf data = (ByteBuf) msg;
            boolean wasNull = cumulation == null;
            if (wasNull) {
                cumulation = data;
            } else {
                //累计缓冲区 最大为65536
                cumulation = cumulate(cumulation, data);
            }

            try {
                callDecode(ctx, cumulation);
            } finally {
                if (cumulation != null && !cumulation.isReadable()) {
                    cumulation.release();
                    cumulation = null;
                }
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * 解码驱动器 反复调用decode()方法
     *
     * @param ctx        上下文
     * @param cumulation 累积缓冲区
     */
    private void callDecode(ChannelHandlerContext ctx, ByteBuf cumulation) throws Exception {
        //1.存放解码之后的结果
        List<Object> out = new ArrayList<>();
        //2.如果缓冲区中有数据读的话
        while (cumulation.isReadable()) {
            int oldReaderIndex = cumulation.readerIndex();

            decode(ctx, cumulation, out);

            if (out.isEmpty()) {
                //没有解码出消息，并且oldReaderIndex也没有发生变化
                if (oldReaderIndex == cumulation.readerIndex()) {
                    break;
                }
            } else {
                //解码出了数据
                if (oldReaderIndex == cumulation.readerIndex()) {
                    throw new DecoderException(
                            getClass() + ".decode() did not read anything but decoded a message.");
                }
                //将解码出的消息传递给下一个handler
                for (Object msg : out) {
                    ctx.fireChannelRead(msg);
                }
                out.clear();
            }
        }
    }

    /**
     * 解码方法 - 子类必须实现
     *
     * <p>从输入缓冲区读取数据并解码成消息，添加到 out 列表。
     * 如果数据不足以解码一条完整消息，应该直接返回不添加任何内容。
     *
     * @param ctx 上下文
     * @param in  输入缓冲区
     * @param out 解码后的消息列表
     * @throws Exception 如果解码过程中发生异常
     */
    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;

    /**
     * 累计数据到缓冲区
     *
     * @param cumulation 现有累计缓冲区
     * @param in         新到达的数据
     * @return 累计后的缓冲区
     */
    protected ByteBuf cumulate(ByteBuf cumulation, ByteBuf in) {
        try {
            //1.判断新的数据加过来之后，是否需要扩容
            int required = in.readableBytes();
            //如果当前缓存区可写的空间 < 需要的空间的话 -> 需要扩容
            if (cumulation.writableBytes() < required) {
                //2.1 创建一个新的缓冲区
                HeapByteBuf newByteBuf = new HeapByteBuf(cumulation.readableBytes() + required, DEFAULT_MAX_CAPACITY);
                //2.2 复制现有的数据 然后移花接木
                byte[] oldData = new byte[cumulation.readableBytes()];
                cumulation.readBytes(oldData);
                newByteBuf.writeBytes(oldData);

                cumulation.release();
                cumulation = newByteBuf;
            }
            //2.3 写入现有的数据
            byte[] newData = new byte[in.readableBytes()];
            in.readBytes(newData);
            cumulation.writeBytes(newData);
            return cumulation;
        } finally {
            in.release();
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //cumulation怎么可能为null呢？
        if (cumulation != null) {
            cumulation.release();
            cumulation = null;
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (cumulation != null) {
            cumulation.release();
            cumulation = null;
        }
    }
}
