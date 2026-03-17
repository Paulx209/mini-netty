package com.getian.netty.channel;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务的 Future 实现
 * 封装一个定时任务，包含：
 * 要执行的任务
 * 执行时间（纳秒）
 * 周期（用于周期性任务）
 * 取消和完成状态
 * <p>
 * 说白了就是封装而已
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-17
 */

public class ScheduledTask implements ScheduledFuture<Void>, Runnable {
    //待执行的任务
    private final Runnable task;

    //开始执行的时间（纳秒）
    private long deadlineNanos;


    //周期（用于周期性任务）
    private final long periodNanos;

    //所属的 EventLoop
    private final SingleThreadEventLoop eventLoop;


    //是否取消
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    //是否执行完成
    private final AtomicBoolean done = new AtomicBoolean(false);

    /**
     * 创建一次性定时任务
     *
     * @param eventLoop 所属的 EventLoop
     * @param task      要执行的任务
     * @param delay     延迟时间
     * @param unit      时间单位
     */
    public ScheduledTask(SingleThreadEventLoop eventLoop, Runnable task, long delay, TimeUnit unit) {
        this(eventLoop, task, delay, 0, unit);
    }

    /**
     * 创建定时任务
     *
     * @param eventLoop 所属的 EventLoop
     * @param task      要执行的任务
     * @param delay     初始延迟
     * @param period    执行周期（0 表示一次性任务）
     * @param unit      时间单位
     */
    public ScheduledTask(SingleThreadEventLoop eventLoop, Runnable task, long delay, long period, TimeUnit unit) {
        this.eventLoop = eventLoop;
        this.task = task;
        this.periodNanos = unit.toNanos(period);
        this.deadlineNanos = System.nanoTime() + unit.toNanos(delay);
    }

    /**
     * 返回距离执行时间的剩余时间(纳秒) 周期性？
     *
     * @return
     */
    public long delayNanos() {
        return deadlineNanos - System.nanoTime();
    }

    /**
     * 判断任务是否已经过期
     *
     * @return 如果已到期返回 true
     */
    public boolean isExpired() {
        return System.nanoTime() > deadlineNanos;
    }

    /**
     * 判断是否是周期性任务
     *
     * @return 是周期性任务的话 -> 返回true
     */
    public boolean isPeriodic() {
        return periodNanos > 0;
    }

    @Override
    public void run() {
        //1.如果已经取消的话 直接返回
        if (cancelled.get() || done.get()) {
            return;
        }
        //2.执行任务
        try {
            task.run();
        } catch (Throwable e) {
            System.err.println("[ScheduledTask] 任务执行失败: " + e.getMessage());
        }

        //3.如果是周期性任务的话，需要计算出下次执行的时间，然后再次进去
        if (isPeriodic() && !cancelled.get()) {
            //计算下次执行的时间
            deadlineNanos = periodNanos + System.nanoTime();
            //重新加入调度队列
            eventLoop.scheduledFromEventLoop(this);
        } else {
            done.set(true);
        }
    }

    /**
     * 距离执行任务还有多少s
     *
     * @param unit the time unit
     * @return
     */
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(delayNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * 通过执行事件比较优先级 谁的执行时间越近 就越先执行谁！
     * @param other the object to be compared.
     * @return
     */
    @Override
    public int compareTo(Delayed other) {
        if (other == this) {
            return 0;
        }
        if (other instanceof ScheduledTask) {
            ScheduledTask otherTask = (ScheduledTask) other;
            long diff = deadlineNanos - otherTask.deadlineNanos;
            if (diff < 0) {
                return -1;
            } else if (diff > 0) {
                return 1;
            } else {
                return 0;
            }
        }
        long diff = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS);
        return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return cancelled.compareAndSet(false, true);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public boolean isDone() {
        return done.get() || cancelled.get();
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
        throw new UnsupportedOperationException("不支持阻塞获取结果");
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        throw new UnsupportedOperationException("不支持阻塞获取结果");
    }

}
