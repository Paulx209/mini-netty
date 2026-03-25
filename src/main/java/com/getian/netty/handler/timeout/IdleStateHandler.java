package com.getian.netty.handler.timeout;

import com.getian.netty.channel.ChannelHandlerContext;
import com.getian.netty.channel.ChannelInboundHandlerAdapter;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 空闲状态处理器
 * IdleStateHandler 用于检测连接的空闲状态。当连接在指定时间内
 * 没有 I/O 活动时，会触发 {@link IdleStateEvent} 事件。
 *
 * <p>支持三种空闲检测：
 * <ul>
 * <li><b>readerIdleTime</b> - 读空闲时间，超时未读取数据触发 READER_IDLE</li>
 * <li><b>writerIdleTime</b> - 写空闲时间，超时未写入数据触发 WRITER_IDLE</li>
 * <li><b>allIdleTime</b> - 全部空闲时间，超时无任何 I/O 触发 ALL_IDLE</li>
 * </ul>
 *
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-25
 */

public class IdleStateHandler extends ChannelInboundHandlerAdapter {
    /**
     * 最小超时时间（纳秒）
     */
    private static final long MIN_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    /**
     * 读空闲超时时间（纳秒）
     */
    private final long readerIdleTimeNanos;

    /**
     * 写空闲超时时间（纳秒）
     * 该字段如果设置为0的话 表示禁用写空闲超时检测
     */
    private final long writerIdleTimeNanos;

    /**
     * 全部空闲超时时间（纳秒）
     */
    private final long allIdleTimeNanos;

    /**
     * 该变量用作定时任务的句柄，用来后续对定时任务进行管理、取消等
     * 读空闲定时任务
     */
    private ScheduledFuture<?> readerIdleTimeout;

    /**
     * 该变量用作定时任务的句柄，用来后续对定时任务进行管理、取消等
     * 写空闲定时任务
     */
    private ScheduledFuture<?> writerIdleTimeout;

    /**
     * 该变量用作定时任务的句柄，用来后续对定时任务进行管理、取消等
     * 全部空闲定时任务
     */
    private ScheduledFuture<?> allIdleTimeout;


    /**
     * 最后读取时间（纳秒）
     */
    private long lastReadTime;

    /**
     * 最后写入时间（纳秒）
     */
    private long lastWriteTime;


    /**
     * 是否是第一次读空闲
     * 既然刚刚发生了读取，那上一轮空闲周期结束了。下一次如果再进入读空闲，那应该重新算作第一次读空闲事件。
     */
    private boolean firstReaderIdleEvent = true;

    /**
     * 是否是第一次写空闲
     */
    private boolean firstWriterIdleEvent = true;

    /**
     * 是否是第一次全部空闲
     */
    private boolean firstAllIdleEvent = true;


    /**
     * 处理器状态：0-未初始化，1-已初始化，2-已销毁
     */
    private byte state;

    /**
     * 是否正在读取
     */
    private boolean reading;

    public IdleStateHandler(int readerIdleTimeNanos, int writerIdleTimeNanos, int allIdleTimeNanos) {
        this(readerIdleTimeNanos, writerIdleTimeNanos, allIdleTimeNanos, TimeUnit.SECONDS);
    }

    /**
     * 创建空闲状态处理器
     *
     * @param readerIdleTime 读空闲超时，0 表示禁用
     * @param writerIdleTime 写空闲超时，0 表示禁用
     * @param allIdleTime    全部空闲超时，0 表示禁用
     * @param timeUnit           时间单位
     */
    public IdleStateHandler(long readerIdleTime, long writerIdleTime, long allIdleTime, TimeUnit timeUnit) {
        if (timeUnit == null) {
            throw new NullPointerException("unit");
        }
        this.readerIdleTimeNanos = Math.max(timeUnit.toNanos(readerIdleTime), 0);
        this.writerIdleTimeNanos = Math.max(timeUnit.toNanos(writerIdleTime), 0);
        this.allIdleTimeNanos = Math.max(timeUnit.toNanos(allIdleTime), 0);
    }

    public long getReaderIdleTime(TimeUnit unit) {
        return unit.convert(readerIdleTimeNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 获取写空闲超时时间
     * @param unit 时间单位
     * @return 写空闲超时时间
     */
    public long getWriterIdleTime(TimeUnit unit) {
        //纳秒转换为参数中的unit单位
        return unit.convert(writerIdleTimeNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 获取全部空闲超时时间
     *
     * @param unit 时间单位
     * @return 全部空闲超时时间
     */
    public long getAllIdleTime(TimeUnit unit) {
        return unit.convert(allIdleTimeNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            //channel已经激活 初始化定时任务 todo 再看一下
            initialize(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        destroy();
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isActive()) {
            initialize(ctx);
        }
        super.channelRegistered(ctx);
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //这里可以确保成功连接
        initialize(ctx);
        super.channelActive(ctx);
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        destroy();
        super.channelInactive(ctx);
    }

    /**
     * 开始读取，设置reading为true，并且首次读空闲和all空闲设置为true，然后传递读事件
     * @param ctx 上下文
     * @param msg 读取到的消息
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (readerIdleTimeNanos > 0 || allIdleTimeNanos > 0) {
            reading = true;
            //这里为true是因为：当前触发了一次读事件，所以下一次发生读空闲的话就是首次
            firstReaderIdleEvent = true;
            firstAllIdleEvent = true;
        }
        ctx.fireChannelRead(msg);
    }


    /**
     * 读取完成之后，将reading设置为false，并且记录上次最近一次的读取时间
     * @param ctx 上下文
     * @throws Exception
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        if ((readerIdleTimeNanos > 0 || allIdleTimeNanos > 0) && reading) {
            lastReadTime = ticksInNanos();
            reading = false;
        }
        ctx.fireChannelReadComplete();
    }

    /**
     * 写操作完成时更新写入时间
     */
    public void writeComplete() {
        if (writerIdleTimeNanos > 0 || allIdleTimeNanos > 0) {
            lastWriteTime = ticksInNanos();
            firstWriterIdleEvent = true;
            firstAllIdleEvent = true;
        }
    }

    /**
     * 执行逻辑：
     * 1.如果状态为已初始化  | 已销毁 的话，就直接return，不需要执行初始化了
     * 2.判断用户设置的等待空闲时间是否为0，如果是0的话，就不需要处理了。
     * 3.然后将读、写、全部事件的监控放到定时任务队列中
     * 初始化定时任务
     * @param ctx
     */
    private void initialize(ChannelHandlerContext ctx) {
        if (state == 1 || state == 2) {
            return;
        }
        state = 1;
        long currenTime = ticksInNanos();
        lastReadTime = currenTime;
        lastWriteTime = currenTime;
        //如果该变量的值 ==0的话，就说明不需要对该状态进行监控了
        if (readerIdleTimeNanos > 0) {
            schedule(ctx, new ReaderIdleTimeoutTask(ctx), readerIdleTimeNanos, TimeUnit.NANOSECONDS);
        }
        if (writerIdleTimeNanos > 0) {
            schedule(ctx, new WriterIdleTimeoutTask(ctx), writerIdleTimeNanos, TimeUnit.NANOSECONDS);
        }
        if (allIdleTimeNanos > 0) {
            schedule(ctx, new AllIdleTimeoutTask(ctx), allIdleTimeNanos, TimeUnit.NANOSECONDS);
        }

    }

    /**
     * 销毁定时任务
     */
    private void destroy() {
        state = 2;
        if (readerIdleTimeout != null) {
            readerIdleTimeout.cancel(false);
            readerIdleTimeout = null;
        }
        if (writerIdleTimeout != null) {
            writerIdleTimeout.cancel(false);
            writerIdleTimeout = null;
        }
        if (allIdleTimeout != null) {
            allIdleTimeout.cancel(false);
            allIdleTimeout = null;
        }
    }


    /**
     * 调度定时任务
     * @param ctx ctx
     * @param task 定时任务
     * @param delay 等待时间
     * @param unit 时间单位
     * @return
     */
    private ScheduledFuture<?> schedule(ChannelHandlerContext ctx, Runnable task, long delay, TimeUnit unit) {
        return ctx.channel().eventLoop().schedule(task, delay, unit);
    }

    /**
     * 获取当前时间 （纳秒单位） ticks = 刻度
     * @return
     */
    long ticksInNanos() {
        return System.nanoTime();
    }

    /**
     * 触发空闲事件
     * @param ctx
     * @param event
     */
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent event) {
        ctx.fireUserEventTriggered(event);
    }

    /**
     * 创建空闲状态事件
     * @param state 空闲状态
     * @param first 是否为首次
     * @return IdleStateEvent
     */
    protected IdleStateEvent newIdleStateEvent(IdleState state, boolean first) {
        switch (state) {
            case READER_IDLE:
                return first ? IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT : IdleStateEvent.READER_IDLE_STATE_EVENT;
            case WRITER_IDLE:
                return first ? IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT : IdleStateEvent.WRITER_IDLE_STATE_EVENT;
            case ALL_IDLE:
                return first ? IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT : IdleStateEvent.ALL_IDLE_STATE_EVENT;
            default:
                throw new IllegalArgumentException("Unknown state: " + state);
        }
    }

    /**
     * 该任务是被添加到定时任务执行队列中的，执行的逻辑就是：
     * 判断“距离上次读取完成，是否已经超过了 readerIdleTimeNanos”。
     * 1.判断当前的连接是否存在
     * 2.判断还有多长时间到设置的空闲读时间 比如说当前已经5s没发生读了，但是我们设置的是10s
     * 3.判断是否过期
     *      3.1 如果过期的话，就要再次将判断空闲读的事件放到队列中，等待重新调度，还要把首次读空闲的值修改为false,然后继续向下传播事件
     *      3.2 如果不过期的话，就继续重新调度，不更新任何的变量
     *  其实读空闲和写空闲周期任务停止执行的点就是：当这个channel通道关闭，selector不是open
     */
    private final class ReaderIdleTimeoutTask implements Runnable {
        private final ChannelHandlerContext ctx;

        public ReaderIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            //1.如果连接不存在的话 直接return
            if (!ctx.channel().isOpen()) {
                return;
            }
            //2.计算还有多长时间到设置的空闲读时间
            long nextDelay = readerIdleTimeNanos;
            //如果当前的reading为false的话，说明没有执行读取任务，需要减去（上次读任务距离现在的时间）
            if (!reading) {
                nextDelay -= ticksInNanos() - lastReadTime; //(ticksInNanos() - lastReadTime 上次读任务距离现在的时间)
            }

            //3.判断读任务的空闲时间是否大于约定的时间 即nextDelay是否<=0
            if (nextDelay <= 0) {
                //过期的话
                readerIdleTimeout = schedule(ctx, this, readerIdleTimeNanos, TimeUnit.NANOSECONDS);

                boolean first = firstReaderIdleEvent;
                firstReaderIdleEvent = false;
                try {
                    IdleStateEvent idleStateEvent = newIdleStateEvent(IdleState.READER_IDLE, first);
                    channelIdle(ctx, idleStateEvent);
                } catch (Exception e) {
                    ctx.fireExceptionCaught(e);
                }
            } else {
                //还没有超时的话
                readerIdleTimeout = schedule(ctx, this, readerIdleTimeNanos, TimeUnit.NANOSECONDS);
            }
        }
    }

    /**
     * 该任务是被添加到定时任务执行队列中的，执行的逻辑就是：
     * 判断“距离上次写入完成，是否已经超过了 writerIdleTimeNanos”
     * 具体逻辑
     * 1.首先判断当前的连接是否还存在
     * 2.判断距离上次写任务的空闲时间 是否超过设定的写空闲时间
     * 3.如果超过的话 继续放入到任务中 并且将事件传递给下一个handler
     * 4.如果没有超过的话 继续放入到任务中执行
     */
    private final class WriterIdleTimeoutTask implements Runnable {
        private final ChannelHandlerContext ctx;

        public WriterIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }
            long lastWriteTime = IdleStateHandler.this.lastWriteTime;
            long nextDelay = writerIdleTimeNanos - (ticksInNanos() - lastWriteTime);
            if (nextDelay <= 0) {
                //写空闲超时
                writerIdleTimeout = schedule(ctx, this, writerIdleTimeNanos, TimeUnit.NANOSECONDS);
                boolean first = firstWriterIdleEvent;
                firstWriterIdleEvent = false;
                try {
                    IdleStateEvent event = newIdleStateEvent(IdleState.WRITER_IDLE, first);
                    channelIdle(ctx, event);
                } catch (Exception e) {
                    ctx.fireExceptionCaught(e);
                }
            } else {
                //没有超时的话，继续放入task任务中
                writerIdleTimeout = schedule(ctx, this, nextDelay, TimeUnit.NANOSECONDS);
            }
        }
    }

    private final class AllIdleTimeoutTask implements Runnable {
        private final ChannelHandlerContext ctx;

        public AllIdleTimeoutTask(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            if (!ctx.channel().isOpen()) {
                return;
            }

            long nextDelay = allIdleTimeNanos;
            if (!reading) {
                long lastIoTime = Math.max(lastReadTime, lastWriteTime);
                nextDelay -= ticksInNanos() - lastIoTime;
            }

            if (nextDelay <= 0) {
                allIdleTimeout = schedule(ctx, this, allIdleTimeNanos, TimeUnit.NANOSECONDS);
                boolean first = firstAllIdleEvent;
                firstAllIdleEvent = false;
                try {
                    IdleStateEvent event = newIdleStateEvent(IdleState.ALL_IDLE, first);
                    channelIdle(ctx, event);
                } catch (Exception e) {
                    ctx.fireExceptionCaught(e);
                }
            } else {
                allIdleTimeout = schedule(ctx, this, allIdleTimeNanos, TimeUnit.NANOSECONDS);
            }
        }
    }

}
