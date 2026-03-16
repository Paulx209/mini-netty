package com.getian.netty.channel;

/**
 * 该接口用来监听所有的IO操作，由于是异步执行的， 所以需要一个监听器
 * 只要操作已完成不管成功还是失败，channel网络连接就断开
 */
@FunctionalInterface
public interface ChannelFutureListener {
    /**
     * 操作完成后关闭 Channel 的监听器
     */
    ChannelFutureListener CLOSE = future -> future.channel().close();

    /**
     * 操作失败时关闭 Channel 的监听器
     */
    ChannelFutureListener CLOSE_ON_FAILURE = future -> {
        if (!future.isSuccess()) {
            future.channel().close();
        }
    };

    /**
     * 当操作完成时调用
     *
     * @param future 已完成的 ChannelFuture
     * @throws Exception 如果处理过程中发生异常
     */
    void operationComplete(ChannelFuture future) throws Exception;
}
