package com.getian.netty.channel;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface EventLoop extends EventLoopGroup{

    /**
     * 返回父 EventLoopGroup
     *
     * @return 父 EventLoopGroup，如果没有则返回 null
     */
    EventLoopGroup parent();


    /**
     * 返回自身（EventLoop由于继承了Group 所以派人干活的时候 只能派自己）
     *
     * @return 自身
     */
    @Override
    EventLoop next();

    /**
     * 判断当前线程是否是 EventLoop 线程
     *
     * <p>这是一个非常重要的方法，用于确保线程安全：
     * <ul>
     *   <li>如果返回 true，可以直接执行操作</li>
     *   <li>如果返回 false，应该通过 execute() 提交任务</li>
     * </ul>
     *
     * @return 如果当前线程是 EventLoop 线程返回 true
     */
    boolean inEventLoop();

    /**
     * 判断指定线程是否是 EventLoop 线程
     *
     * @param thread 要判断的线程
     * @return 如果指定线程是 EventLoop 线程返回 true
     */
    boolean inEventLoop(Thread thread);

    /**
     * 提交一个任务到 EventLoop 执行
     *
     * <p>如果当前线程是 EventLoop 线程，任务可能会立即执行；
     * 否则任务会被添加到任务队列，等待 EventLoop 线程执行。
     *
     * @param task 要执行的任务
     */
    void execute(Runnable task);


    /**
     * 提交一个定时任务
     *
     * <p>任务会在指定延迟后执行。
     *
     * @param task  要执行的任务
     * @param delay 延迟时间
     * @param unit  时间单位
     * @return 可用于取消任务的 ScheduledFuture
     */
    ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit);

    /**
     * 提交一个周期性任务
     *
     * <p>任务会在初始延迟后首次执行，然后按指定周期重复执行。
     *
     * @param task         要执行的任务
     * @param initialDelay 初始延迟
     * @param period       执行周期
     * @param unit         时间单位
     * @return 可用于取消任务的 ScheduledFuture
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit);

}
