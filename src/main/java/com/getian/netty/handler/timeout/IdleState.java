package com.getian.netty.handler.timeout;

/**
 * 连接之后，长时间没有读取 / 写入数据。
 * 空闲状态类型枚举
 * READER_IDLE - 读空闲，一段时间内没有读取到数据
 * WRITER_IDLE - 写空闲，一段时间内没有写入数据
 * ALL_IDLE - 全部空闲，一段时间内既没有读也没有写
 */
public enum IdleState {
    /**
     * 读空闲 在指定的时间内没有接收到任何数据。
     * 一般用于检测对端是否存活
     */
    READER_IDLE,
    /**
     * 写空闲 在指定的时间内没有发送任何数据。
     * 可用于触发心跳发送。
     */
    WRITER_IDLE,

    /**
     * 全部空闲
     * 在指定的时间内既没有读也没有写。
     */
    ALL_IDLE
}
