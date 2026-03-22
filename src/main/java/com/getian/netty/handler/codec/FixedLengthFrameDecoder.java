package com.getian.netty.handler.codec;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.buffer.HeapByteBuf;
import com.getian.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * 定长帧解码器 将接收到的字节按固定长度切分成帧  这是最简单的帧解码策略之一
 * 使用场景
 * 1.消息长度固定的协议
 * 2.二进制传感器数据（每条信息的长度是固定的）
 * 3.定长记录的批处理
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-22
 */

public class FixedLengthFrameDecoder extends ByteToMessageDecoder {

    private final int frameLength;

    public FixedLengthFrameDecoder(int frameLength) {
        if (frameLength <= 0) {
            throw new IllegalArgumentException(
                    "frameLength must be a positive integer: " + frameLength);
        }
        this.frameLength = frameLength;
    }

    public int getFrameLength() {
        return this.frameLength;
    }

    /**
     * 读取frameLength长度的数据
     *
     * @param ctx 上下文
     * @param in  输入缓冲区
     * @param out 解码后的消息列表
     * @throws Exception
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        while(in.readableBytes() >= frameLength){
            ByteBuf frame = new HeapByteBuf(frameLength, frameLength);
            byte[] data = new byte[frameLength];
            in.readBytes(data);
            frame.writeBytes(data);
            out.add(frame);
        }
    }
}
