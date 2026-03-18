package com.getian.netty.channel;

public interface ChannelId extends Comparable<ChannelId> {
    /**
     * 返回 Channel ID 的短格式字符串
     * 短格式适合日志输出，通常是 ID 的哈希值。
     * @return 短格式字符串
     */
    String asShortText();

    /**
     * 返回 Channel ID 的长格式字符串
     *    长格式包含完整的 ID 信息，适合精确识别。
     * @return 长格式字符串
     */
    String asLongText();
}
