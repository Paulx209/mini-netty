package com.getian.netty.handler.codec;

/**
 * @Author: sonicge
 * @CreateTime: 2026-03-23
 */

public class EncoderException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EncoderException() {
        super();
    }

    public EncoderException(String message) {
        super(message);
    }

    public EncoderException(String message, Throwable cause) {
        super(message, cause);
    }

    public EncoderException(Throwable cause) {
        super(cause);
    }
}
