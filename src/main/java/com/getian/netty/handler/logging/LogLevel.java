package com.getian.netty.handler.logging;

/**
 * 日志级别枚举
 * 定义 LoggingHandler 支持的日志级别。
 */
public enum LogLevel {
    /**
     * TRACE 最详细的日志级别
     */
    TRACE,
    /**
     * DEBUG 调试信息
     */
    DEBUG,
    /**
     * INFO 一般信息
     */
    INFO,
    /**
     * WARN 警告信息
     */
    WARN,
    /**
     * ERROR 错误信息
     */
    ERROR
}
