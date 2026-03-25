package com.getian.netty.channel;

/**
 * EventLoop 选择器工厂接口
 * EventLoopChooserFactory 负责创建 EventLoopChooser 实例。
 * 工厂可以根据 EventLoop 数量选择最优的选择策略。
 * @Author: sonicge
 * @CreateTime: 2026-03-25
 */

public interface EventLoopChooserFactory {
    /**
     * 创建新的 EventLoopChooser
     * @param eventLoops  EventLoop 数组
     * @return 新创建的 EventLoopChooser
     */
    EventLoopChooser newChooser(EventLoop[] eventLoops);
}
