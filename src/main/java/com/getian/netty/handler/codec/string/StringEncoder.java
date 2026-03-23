package com.getian.netty.handler.codec.string;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.channel.ChannelHandlerContext;
import com.getian.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 字符串编码(编造字节码)器 Message -> byte
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-23
 */

public class StringEncoder extends MessageToByteEncoder<CharSequence> {
    /**
     * 字符编码格式
     */
    private final Charset charset;

    /**
     * 使用 UTF-8 编码创建编码器
     */
    public StringEncoder() {
        this(StandardCharsets.UTF_8);
    }

    public StringEncoder(Charset charset) {
        super(CharSequence.class);
        if (charset == null) {
            throw new NullPointerException("charset");
        }
        this.charset = charset;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, CharSequence msg, ByteBuf out) {
        if (msg.length() == 0) return;
        byte[] bytes = msg.toString().getBytes(charset);
        out.writeBytes(bytes);
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
