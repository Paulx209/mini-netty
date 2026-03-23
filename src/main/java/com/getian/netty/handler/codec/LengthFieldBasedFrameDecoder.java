package com.getian.netty.handler.codec;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.buffer.HeapByteBuf;
import com.getian.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * 基于长度字段的帧解码器
 * 1.这是最通用的解决 TCP 粘包/拆包问题的解码器。
 * 2.消息格式：[长度字段][数据]，长度字段指示后续数据的字节数。
 * 参数说明：
 * 1.lengthFieldOffset 长度字段从消息的第几个字节开始
 * <p>
 * 2.lengthFieldLength  长度字段本身占几个字节
 * <p>
 * 3.lengthAdjustment 对长度字段里的值做修正（比如长度字段包括自己的话，这里是要取负数做校正的）
 * <p>
 * 4.initialBytesToStrip 解码成功后 前面要丢掉多少字节 不往下传
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-22
 */

public class LengthFieldBasedFrameDecoder extends ByteToMessageDecoder {
    //长度字段从小消息的第几个字节开始
    private final int lengthFieldOffset;

    //长度字段本身占几个字节
    private final int lengthFieldLength;

    //对长度字段的值做修正(比如长度字段包括自己的话，这里是要取负数做校正的)
    private final int lengthAdjustment;

    //解码成功后 前面要丢掉多少字节
    private final int initialBytesToStrip;

    //最大帧长度
    private final int maxFrameLength;

    //长度字段结束位置偏移量 = lengthFieldOffset + lengthFiledLength
    private final int lengthFieldEndOffset;

    public LengthFieldBasedFrameDecoder(int maxFrameLength, int lengthFieldOffset,
                                        int lengthFieldLength, int lengthAdjustment,
                                        int initialBytesToStrip) {
        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException("maxFrameLength must be positive: " + maxFrameLength);
        }
        if (lengthFieldOffset < 0) {
            throw new IllegalArgumentException("lengthFieldOffset must be non-negative: " + lengthFieldOffset);
        }

        if (lengthFieldLength != 1 && lengthFieldLength != 2 &&
                lengthFieldLength != 3 && lengthFieldLength != 4 &&
                lengthFieldLength != 8) {
            throw new IllegalArgumentException("lengthFieldLength must be 1, 2, 3, 4, or 8: " + lengthFieldLength);
        }

        if (initialBytesToStrip < 0) {
            throw new IllegalArgumentException("initialBytesToStrip must be non-negative: " + initialBytesToStrip);
        }

        this.maxFrameLength = maxFrameLength;
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
        this.initialBytesToStrip = initialBytesToStrip;
        this.lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;
    }

    /**
     * 简化构造函数：使用默认最大帧长度 1MB
     */
    public LengthFieldBasedFrameDecoder(int lengthFieldOffset, int lengthFieldLength,
                                        int lengthAdjustment, int initialBytesToStrip) {
        this(1024 * 1024, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        //负责解析一帧 解析成功放进out;解析失败 返回null
        Object decoded = decodeFrame(ctx, in);
        if (decoded != null) {
            out.add(decoded);
        }
    }

    /**
     * @param ctx
     * @param in
     * @return
     */
    protected Object decodeFrame(ChannelHandlerContext ctx, ByteBuf in) {
        //1.如果当前数据中可读的字节大小 <  [请求头][长度字段] 所需要的字节数的话
        if (in.readableBytes() < lengthFieldEndOffset) {
            return null;
        }
        //2.到达长度字段的第一个字节
        int actualLengthFieldOffset = in.readerIndex() + lengthFieldOffset;
        //frameLength 是长度字段表示的值
        long frameLength = getUnadjustedFrameLength(in, actualLengthFieldOffset, lengthFieldLength);

        //应用长度调整 lengthAdjustment:修正值（可能加两个长度字段字节值）
        frameLength += lengthAdjustment + lengthFieldEndOffset;

        if (frameLength < lengthFieldEndOffset) {
            throw new DecoderException("Adjusted frame length (" + frameLength + ") is less than lengthFieldEndOffset: " + lengthFieldEndOffset);
        }

        if (frameLength > maxFrameLength) {
            throw new DecoderException("Frame length exceeds maximum: " + frameLength + " > " + maxFrameLength);
        }

        int frameLengthInt = (int) frameLength;

        //检查是否有足够的数据
        if (in.readableBytes() < frameLengthInt) {
            return null;
        }
        // 检查跳过的字节是否超过帧长度
        if (initialBytesToStrip > frameLengthInt) {
            throw new DecoderException("initialBytesToStrip (" + initialBytesToStrip +
                    ") exceeds frame length: " + frameLengthInt);
        }
        //跳过头部
        in.skipBytes(initialBytesToStrip);

        //读取帧数据
        int actualFrameLength = frameLengthInt - initialBytesToStrip;
        ByteBuf frame = extractFrame(in, actualFrameLength);
        return frame;
    }

    /**
     * 读取无符号长度值
     */
    private long getUnadjustedFrameLength(ByteBuf buf, int offset, int length) {
        //1.保存当前的读指针
        int savedReaderIndex = buf.readerIndex();
        //将当前的readerIndex修改为长度字段的第一个字节值
        buf.readerIndex(offset);

        //2.根据字节长度的大小
        long frameLength;
        switch (length) {
            case 1:
                frameLength = buf.readByte() & 0xFF;
                break;
            case 2:
                frameLength = buf.readShort() & 0xFFFF;
                break;
            case 3:
                frameLength = (buf.readByte() & 0xFF) << 16 |
                        (buf.readByte() & 0xFF) << 8 |
                        (buf.readByte() & 0xFF);
                break;
            case 4:
                frameLength = buf.readInt() & 0xFFFFFFFFL;
                break;
            case 8:
                frameLength = buf.readLong();
                break;
            default:
                throw new DecoderException("Unsupported length field length: " + length);
        }
        //3.还原readerIndex
        buf.readerIndex(savedReaderIndex);
        return frameLength;
    }

    /**
     * 提取帧数据
     *
     * @param in                ByteBuf
     * @param actualFrameLength 真实帧长度
     * @return
     */
    private ByteBuf extractFrame(ByteBuf in, int actualFrameLength) {
        ByteBuf frame = new HeapByteBuf(actualFrameLength, actualFrameLength);
        byte[] data = new byte[actualFrameLength];
        in.readBytes(data);
        frame.writeBytes(data);
        return frame;
    }

    // Getter 方法
    public int getLengthFieldOffset() {
        return lengthFieldOffset;
    }

    public int getLengthFieldLength() {
        return lengthFieldLength;
    }

    public int getLengthAdjustment() {
        return lengthAdjustment;
    }

    public int getInitialBytesToStrip() {
        return initialBytesToStrip;
    }

    public int getMaxFrameLength() {
        return maxFrameLength;
    }

    public int getLengthFieldEndOffset() {
        return lengthFieldEndOffset;
    }
}
