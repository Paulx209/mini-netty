package com.getian.netty.channel;

import java.util.concurrent.Future;

public interface ChannelFuture extends Future<Void> {
    /**
     * 返回关联的Channel
     * @return 关联的Channel
     */
    Channel channel();

    /**
     * 判断操作是否成功完成
     *
     * @return 如果操作成功完成返回 true
     */
    boolean isSuccess();

    /**
     * 返回操作失败的原因
     *
     * @return 失败原因，如果成功或未完成返回 null
     */
    Throwable cause();


    /**
     * 添加监听器，用来监听ChannelFuture执行状态
     * 当操作完成时（无论成功或失败），监听器会被调用。
     * 如果操作已经完成，监听器会立即被调用。
     * @param listener 监听器
     * @return 便于链式调用
     */
    ChannelFuture addListener(ChannelFutureListener listener);


    /**
     * 同步等待操作完成
     * 阻塞当前线程直到操作完成。
     * 不要在 EventLoop 线程中调用此方法。
     *
     * @return  this，便于链式调用
     * @throws InterruptedException 如果等待被中断
     */
    ChannelFuture sync() throws InterruptedException;

    /**
     * 等待操作完成
     *
     * @return this，便于链式调用
     * @throws InterruptedException 如果等待被中断
     */
    ChannelFuture await() throws InterruptedException;
}
