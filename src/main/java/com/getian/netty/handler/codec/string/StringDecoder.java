package com.getian.netty.handler.codec.string;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.channel.ChannelHandlerContext;
import com.getian.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 入口
 * 字符串解码器 byte  -> message
 * 将 ByteBuf 解码为字符串。通常与 LengthFieldBasedFrameDecoder  或其他帧解码器配合使用。
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-23
 */

public class StringDecoder extends ChannelInboundHandlerAdapter {

    private final Charset charset;

    /**
     * 使用 UTF-8 编码创建解码器
     */
    public StringDecoder() {
        this(StandardCharsets.UTF_8);
    }

    /**
     * 使用指定编码创建解码器
     *
     * @param charset 字符编码
     */
    public StringDecoder(Charset charset) {
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.charset = charset;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                String decoded = buf.toString(charset);
                ctx.fireChannelRead(decoded);
            } finally {
                buf.release();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }


    /**
     * 获取使用的字符编码
     *
     * @return 字符编码
     */
    public Charset getCharset() {
        return charset;
    }
}
