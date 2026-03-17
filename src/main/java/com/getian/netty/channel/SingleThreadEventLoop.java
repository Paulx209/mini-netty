package com.getian.netty.channel;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单线程事件循环的抽象基类
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-16
 */

public abstract class SingleThreadEventLoop implements EventLoop {
    /**
     * 父 EventLoopGroup
     */
    private final EventLoopGroup parent;

    /**
     * 事件循环线程
     */
    protected volatile Thread thread;

    /**
     * 任务队列
     */
    protected final Queue<Runnable> taskQueue;

    /**
     * 运行状态
     */
    protected final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 关闭状态
     */
    protected final AtomicBoolean shutdown = new AtomicBoolean(false);


    /**
     * 终止状态
     */
    protected final AtomicBoolean terminated = new AtomicBoolean(false);

    /**
     * 优先级队列
     */
    private final PriorityQueue<ScheduledTask> scheduledTaskQueue;


    public SingleThreadEventLoop(EventLoopGroup parent) {
        this.parent = parent;
        this.taskQueue = new ConcurrentLinkedDeque<>();
        this.scheduledTaskQueue = new PriorityQueue<>();
    }

    /**
     * 返回父 EventLoopGroup
     *
     * @return
     */
    @Override
    public EventLoopGroup parent() {
        return parent;
    }

    /**
     * 获取下一个员工
     *
     * 但是由于EventLoop继承了Group接口，也就是组建了只有一个员工的团队
     * 所以每次派人出来干活的时候，只能派自己
     *
     * @return
     */
    @Override
    public EventLoop next() {
        return this;
    }

    /**
     * 判断当前执行的线程 是否是 EventLoop中的那个专属线程
     *
     * @return
     */
    @Override
    public boolean inEventLoop() {
        return this.inEventLoop(Thread.currentThread());
    }

    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    /**
     * 添加任务
     *
     * 提交一个任务到eventLoop执行
     * 如果当前线程是 EventLoop 线程，任务可能会立即执行；
     * 否则任务会被添加到任务队列，等待 EventLoop 线程执行。
     *
     * @param task 要执行的任务
     */
    @Override
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        taskQueue.offer(task);
        if (!inEventLoop()) {
            //为什么这里需要wakeup();因为eventLoop的这个线程还在select()方法里面阻塞执行，对于外部任务的到来，他不知道，就必须要wakeup从阻塞状态唤醒它，然后处理queue中的任务
            wakeup();
        }
    }


    /**
     * 提交定时任务 延迟时间后执行
     *
     * @param task  要执行的任务
     * @param delay 延迟时间
     * @param unit  时间单位
     * @return
     */
    @Override
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        if (unit == null) {
            throw new NullPointerException("unit is null");
        }

        ScheduledTask scheduledTask = new ScheduledTask(this, task, delay, unit);
        if (inEventLoop()) {
            scheduledTaskQueue.offer(scheduledTask);
        } else {
            execute(() -> {
                scheduledTaskQueue.offer(scheduledTask);
            });
        }
        return scheduledTask;
    }

    /**
     * 提交一个周期性任务 过了这个周期会继续执行
     *
     * @param task         要执行的任务
     * @param initialDelay 初始延迟
     * @param period       执行周期
     * @param unit         时间单位
     * @return
     */
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        if (unit == null) {
            throw new NullPointerException("unit is null");
        }
        if (period < 0) {
            throw new NullPointerException("period is invalid");
        }

        ScheduledTask scheduledTask = new ScheduledTask(this, task, initialDelay, period, unit);
        if (inEventLoop()) {
            scheduledTaskQueue.offer(scheduledTask);
        } else {
            this.execute(() -> {
                scheduledTaskQueue.offer(scheduledTask);
            });
        }
        return scheduledTask;
    }

    /**
     * 从EventLoop线程内部重新调度任务（用于周期性任务)
     *
     * @param task
     */
    void scheduledFromEventLoop(ScheduledTask task) {
        scheduledTaskQueue.offer(task);
    }


    @Override
    public ChannelFuture register(Channel channel) {
        // 简化实现：在后续迭代中完善
        throw new UnsupportedOperationException("将在后续迭代实现 Channel 注册");
    }

    @Override
    public Future<?> shutdownGracefully() {
        if (shutdown.compareAndSet(false, true)) {
            wakeup(); //shutdown关闭之后 就不需要再去select监听了
        }
        return null; // 简化实现
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public boolean isTerminated() {
        return terminated.get();
    }

    /**
     * 判断事件循环是否正在运行
     *
     * @return 如果正在运行返回 true
     */
    public boolean isRunning() {
        return running.get();
    }


    /**
     * 启动事件循环
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this::run, getThreadName());
            thread.start();
        }
    }

    /**
     * 获取线程的名称
     *
     * @return
     */
    protected String getThreadName() {
        return "eventloop-" + Integer.toHexString(hashCode());
    }

    /**
     * 事件循环主逻辑
     *
     * <p>子类实现具体的事件处理逻辑。
     */
    protected abstract void run();


    /**
     * 唤醒事件循环
     *
     * <p>用于在提交任务后唤醒可能阻塞的选择器。
     */
    protected abstract void wakeup();


    /**
     * 运行所有待处理的任务
     *
     * @return 运行的任务数量
     */
    protected int runAllTasks() {
        int count = 0;
        //先运行到期的定时任务
        count += runScheduledTasks();

        //在执行普通任务
        Runnable task;
        while ((task = taskQueue.poll()) != null) {
            try {
                task.run();
                count++;
            } catch (Exception e) {
                System.err.println("[EventLoop] 任务执行失败: " + e.getMessage());
            }
        }
        return count;
    }

    /**
     * 运行所有到期的定时任务
     *
     * @return 运行的定时任务数量
     */
    protected int runScheduledTasks() {
        int count = 0;
        while (true) {
            ScheduledTask scheduledTask = scheduledTaskQueue.peek();
            //如果没有任务 或者 该任务还没有到执行时间
            if (scheduledTask == null || !scheduledTask.isExpired()) {
                break;
            }
            ScheduledTask task = scheduledTaskQueue.poll();
            if (!task.isCancelled()) {
                try {
                    task.run();
                    count++;
                } catch (Exception e) {
                    System.err.println("[EventLoop] 定时任务执行失败: " + e.getMessage());
                }
            }
        }
        return count;
    }

    /**
     * 检查是否有待处理的任务
     *
     * @return 如果有任务返回 true
     */
    protected boolean hasTasks() {
        return !taskQueue.isEmpty() || hasScheduledTasks();
    }

    protected boolean hasScheduledTasks() {
        ScheduledTask task = scheduledTaskQueue.peek();
        return task != null && task.isExpired();
    }

    /**
     * 获取下一个定时任务的延时时间（纳秒）
     */
    protected long getNextScheduledTaskDelayNanos() {
        ScheduledTask task = scheduledTaskQueue.peek();
        if (task == null) {
            return -1;
        }
        return Math.max(0, task.delayNanos());
    }
}
