package com.getian.netty.channel;

/**
 *  可写入的 ChannelFuture，用于设置操作结果
 *  ChannelPromise 是 ChannelFuture 的可写版本，允许设置操作成功或失败的结果。
 *  通常在 Handler 内部使用，用于通知操作完成。
 *
 */
public interface ChannelPromise extends ChannelFuture{
    /**
     * 标记操作成功完成
     * @return this 便于链式调用
     */
    ChannelPromise setSuccess();

    /**
     * 尝试标记操作成功完成
     *
     * @return 如果成功标记返回 true，如果已经完成返回 false
     */
    boolean trySuccess();

    /**
     * 尝试标记操作失败
     * @param able 失败原因
     * @return 如果成功标记返回 true，如果已经完成返回 false
     *
     */
    ChannelPromise setFailure(Throwable able);


    @Override
    ChannelPromise addListener(ChannelFutureListener listener);

    @Override
    ChannelPromise sync() throws InterruptedException;

}
