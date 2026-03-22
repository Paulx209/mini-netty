package com.getian.netty.handler.codec;

/**
 * @Author: sonicge
 * @CreateTime: 2026-03-22
 */

public class DecoderException extends RuntimeException {
    public DecoderException() {
        super();
    }

    public DecoderException(String message) {
        super(message);
    }

    public DecoderException(String message, Throwable cause) {
        super(message, cause);
    }

    public DecoderException(Throwable cause) {
        super(cause);
    }
}
