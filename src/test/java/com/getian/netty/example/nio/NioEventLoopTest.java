package com.getian.netty.example.nio;

import com.getian.netty.channel.*;
import com.getian.netty.channel.nio.NioEventLoop;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NioEventLoop 单元测试
 * 启动和停止
 * 任务执行
 * 线程判断
 * Selector 事件处理
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-16
 */

public class NioEventLoopTest {
    private NioEventLoop eventLoop;

    @BeforeEach
    void setup() {
        eventLoop = new NioEventLoop(null);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (eventLoop != null || eventLoop.isRunning()) {
            eventLoop.shutdownGracefully();
            //等待事件停止
            Thread.sleep(200);
        }
    }

    @Nested
    @DisplayName("基本功能")
    class BasicFunctionalityTests {
        @Test
        @DisplayName("NioEventLoop 实现了 EventLoop 接口")
        void implementsEventLoop() {
            assertThat(eventLoop).isInstanceOf(EventLoop.class);
        }

        @Test
        @DisplayName("NioEventLoop 继承自 SingleThreadEventLoop")
        void extendsSingleThreadEventLoop() {
            assertThat(eventLoop).isInstanceOf(SingleThreadEventLoop.class);
        }

        @Test
        @DisplayName("NioEventLoop 有 Selector")
        void hasSelector() {
            Selector selector = eventLoop.selector();
            assertThat(selector).isNotNull();
            assertThat(selector.isOpen()).isTrue();
        }

        @Test
        @DisplayName("next() 返回自身")
        void nextReturnsSelf() {
            assertThat(eventLoop.next()).isEqualTo(eventLoop);
        }

        @Test
        @DisplayName("parent() 返回构造时传入的 EventLoopGroup")
        void parentReturnsConstructorArg() {
            assertThat(eventLoop.parent()).isNull();

            EventLoopGroup mockParent = new EventLoopGroup() {
                @Override
                public EventLoop next() {
                    return null;
                }

                @Override
                public ChannelFuture register(Channel channel) {
                    return null;
                }

                @Override
                public Future<?> shutdownGracefully() {
                    return null;
                }

                @Override
                public boolean isShutdown() {
                    return false;
                }

                @Override
                public boolean isTerminated() {
                    return false;
                }
            };
            //1.传入的parent参数
            EventLoop eventLoop1 = new NioEventLoop(mockParent);
            //2.和parent()返回的对象是一致的
            EventLoopGroup parent = eventLoop1.parent();
            assertThat(parent).isSameAs(mockParent);

            eventLoop1.shutdownGracefully();
        }
    }

    @Nested
    @DisplayName("启动和停止")
    class StartStopTests {
        @Test
        @DisplayName("初始状态：未运行、未关闭、未终止")
        void initialState() {
            //初始状态的时候 三个状态都是false
            assertThat(eventLoop.isRunning()).isFalse();
            assertThat(eventLoop.isTerminated()).isFalse();
            assertThat(eventLoop.isShutdown()).isFalse();
        }

        @Test
        @DisplayName("start() 启动事件循环")
        void startStartsEventLoop() throws InterruptedException {
            eventLoop.start();

            Thread.sleep(100);
            assertThat(eventLoop.isRunning()).isTrue();
            assertThat(eventLoop.isShutdown()).isFalse();
            assertThat(eventLoop.isTerminated()).isFalse();
        }

        @Test
        @DisplayName("shutdownGracefully() 停止事件循环")
        void shutdownGracefullyStopsEventLoop() throws InterruptedException {
            eventLoop.start();
            Thread.sleep(100);
            //true false false
            assertThat(eventLoop.isRunning()).isTrue();
            assertThat(eventLoop.isShutdown()).isFalse();
            assertThat(eventLoop.isTerminated()).isFalse();

            eventLoop.shutdownGracefully();
            Thread.sleep(30);
            assertThat(eventLoop.isShutdown()).isTrue();
            assertThat(eventLoop.isShutdown()).isTrue();
            assertThat(eventLoop.isTerminated()).isTrue();
        }

        @Test
        @DisplayName("重复调用 start() 只启动一次")
        void multipleStartsOnlyStartOnce() throws InterruptedException {
            eventLoop.start();
            Thread thread1 = getEventLoopThread();

            eventLoop.start();
            Thread thread2 = getEventLoopThread();

            assertThat(thread1).isSameAs(thread2);
        }

        /**
         * 探针任务
         *
         * @return Thread loopEvent中对应的线程
         * @throws InterruptedException
         */
        private Thread getEventLoopThread() throws InterruptedException {
            Thread.sleep(50);
            CountDownLatch latch = new CountDownLatch(1);
            Thread[] holder = new Thread[1];
            eventLoop.execute(() -> {
                //使用一个数组 lambda表达式中一般不允许我们修改变量的地址 我们修改的是数组中下标为0元素的地址 而不是数组指向的地址
                holder[0] = Thread.currentThread();
                latch.countDown();
            });
            latch.await(1, TimeUnit.SECONDS);
            return holder[0];
        }


        @Nested
        @DisplayName("线程判断")
        class InEventLoopTests {
            @Test
            @DisplayName("在非 EventLoop 线程调用 inEventLoop() 返回 false")
            void inEventLoopReturnsFalseFromOtherThread() throws InterruptedException {
                eventLoop.start();
                Thread[] threadHolder = new Thread[1];
                CountDownLatch countDownLatch = new CountDownLatch(1);
                eventLoop.execute(() -> {
                    threadHolder[0] = Thread.currentThread();
                    countDownLatch.countDown();
                });
                countDownLatch.await(1000, TimeUnit.SECONDS);
                System.out.println(threadHolder[0]);
                System.out.println(Thread.currentThread());
                assertThat(eventLoop.inEventLoop()).isFalse();
            }

            @Test
            @DisplayName("在 EventLoop 线程内调用 inEventLoop() 返回 true")
            void inEventLoopReturnsTrueFromEventLoopThread() throws InterruptedException {
                eventLoop.start();
                CountDownLatch countDownLatch = new CountDownLatch(1);
                boolean[] ans = new boolean[1];
                eventLoop.execute(() -> {
                    ans[0] = eventLoop.inEventLoop();
                    countDownLatch.countDown();
                });
                countDownLatch.await(1000, TimeUnit.SECONDS);
                assertThat(ans[0]).isTrue();
            }

            @Test
            @DisplayName("inEventLoop(Thread) 正确判断线程")
            void inEventLoopWithThreadParameter() throws InterruptedException {
                eventLoop.start();
                Thread.sleep(100);

                CountDownLatch latch = new CountDownLatch(1);
                Thread[] eventLoopThread = new Thread[1];
                eventLoop.execute(() -> {
                    eventLoopThread[0] = Thread.currentThread();
                    latch.countDown();
                });
                latch.await(5, TimeUnit.SECONDS);

                assertThat(eventLoop.inEventLoop(eventLoopThread[0])).isTrue();
                assertThat(eventLoop.inEventLoop(Thread.currentThread())).isFalse();
            }
        }

    }

    /**
     * 上强度了
     */
    @Nested
    @DisplayName("任务执行")
    class TaskExecutionTests {
        @Test
        @DisplayName("execute() 执行任务")
        void executeRunsTask() throws InterruptedException {
            eventLoop.start();
            Thread.sleep(100);

            CountDownLatch testLatch = new CountDownLatch(1);
            AtomicBoolean testBoolean = new AtomicBoolean(false);

            eventLoop.execute(() -> {
                testLatch.countDown();
                testBoolean.set(true);
            });

            boolean computed = testLatch.await(5, TimeUnit.SECONDS);

            assertThat(computed).isTrue();
            assertThat(testBoolean.get()).isTrue();
        }

        @Test
        @DisplayName("execute() 按顺序执行多个任务")
        void executeRunsTasksInOrder() throws InterruptedException {
            //1.先启动
            eventLoop.start();
            Thread.sleep(100);

            //2.开始按照顺序执行任务
            AtomicInteger count = new AtomicInteger(0);
            CountDownLatch testLatch = new CountDownLatch(3);
            int[] nums = new int[3];
            for (int i = 0; i < 3; i++) {
                final int index = i;
                eventLoop.execute(() -> {
                    testLatch.countDown();
                    nums[index] = count.incrementAndGet();
                });
            }
            boolean competed = testLatch.await(5, TimeUnit.SECONDS);
            assertThat(competed).isTrue();
            assertThat(nums).containsExactly(0, 1, 2);
        }

        @Test
        @DisplayName("任务在 EventLoop 线程执行")
        void tasksRunInEventLoopThread() throws InterruptedException {
            //1.启动
            eventLoop.start();
            Thread.sleep(100);

            CountDownLatch latch = new CountDownLatch(1);
            Thread[] holder = new Thread[1];
            eventLoop.execute(() -> {
                holder[0] = Thread.currentThread();
                latch.countDown();
            });
            latch.await(1, TimeUnit.SECONDS);
            assertThat(eventLoop.inEventLoop(holder[0])).isTrue();
        }

        @Test
        @DisplayName("从非 EventLoop 线程 execute() 会唤醒选择器")
        void executeFromOtherThreadWakesUpSelector() throws InterruptedException {
            eventLoop.start();
            Thread.sleep(100);

            long start = System.currentTimeMillis();
            CountDownLatch countDownLatch = new CountDownLatch(1);

            //从主线程提交任务 会直接被唤醒 然后开始处理的
            eventLoop.execute(countDownLatch::countDown);

            boolean completed = countDownLatch.await(5, TimeUnit.SECONDS);
            long time = System.currentTimeMillis() - start;
            System.out.println("经过的时间为: " + time);

            assertThat(completed).isTrue();
        }

        @Nested
        @DisplayName("Selector 事件处理")
        class SelectorEventTests {

            @Test
            @DisplayName("可以注册 Channel 到 Selector")
            void canRegisterChannelToSelector() throws IOException, InterruptedException {
                //主要实现的逻辑就是：可以将eventLoop中的Selector获取到，然后和channel进行绑定

                //1.eventLoop进行启动
                eventLoop.start();
                Thread.sleep(100);

                //2.创建一个Channel
                ServerSocketChannel serverChannel = ServerSocketChannel.open();
                serverChannel.configureBlocking(false);
                serverChannel.bind(new InetSocketAddress(0));
                SelectionKey key = serverChannel.register(eventLoop.selector(), SelectionKey.OP_ACCEPT);
                assertThat(key.isValid()).isTrue();

                //3.关闭
                serverChannel.close();
                eventLoop.shutdownGracefully();
            }
        }

    }

}
