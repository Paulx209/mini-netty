package com.getian.netty.channel;

import com.getian.netty.channel.nio.NioEventLoop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.junit.jupiter.api.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EventLoop 任务队列测试
 * 验证 EventLoop 任务执行的核心功能：
 * 任务提交和执行
 * 任务执行顺序（FIFO）
 * 多线程提交任务的安全性
 * 异常处理
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-17
 */
@DisplayName("EventLoop 任务队列测试")
public class TaskQueueTest {
    private NioEventLoop eventLoop;

    @BeforeEach
    void setup() {
        eventLoop = new NioEventLoop(null);
        eventLoop.start();
        try {
            //睡眠的时候被打断 就是被中断了 需要将interrupt标志位设置为true
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterEach
    void tearDown() {
        if (eventLoop != null) {
            try {
                eventLoop.shutdownGracefully();
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nested
    @DisplayName("execute() 方法")
    class ExecuteMethodTests {
        @Test
        @DisplayName("execute() 执行单个任务")
        void executesSingleTask() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            Thread[] holder = new Thread[1];
            eventLoop.execute(() -> {
                holder[0] = Thread.currentThread();
                latch.countDown();
            });
            boolean completed = latch.await(50, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            System.out.println(holder[0].getName());
        }

        @Test
        @DisplayName("execute() 按 FIFO 顺序执行任务")
        void executesTasksInFifoOrder() throws InterruptedException {
            int taskCount = 5;
            CountDownLatch latch = new CountDownLatch(5);
            int[] res = new int[taskCount];

            for (int i = 0; i < taskCount; i++) {
                final int num = i;
                eventLoop.execute(() -> {
                    res[num] = num;
                    latch.countDown();
                });
            }
            boolean completed = latch.await(100, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            for (int i = 0; i < taskCount; i++) {
                assertThat(res[i]).isEqualTo(i);
            }
        }

        @Test
        @DisplayName("execute() 不接受 null 任务")
        void rejectsNullTask() {
            assertThatThrownBy(() -> eventLoop.execute(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("任务在 EventLoop 线程中执行")
        void tasksExecuteInEventLoopThread() throws InterruptedException {
            eventLoop.execute(() -> {
                boolean flag = eventLoop.inEventLoop(Thread.currentThread());
                assertThat(flag).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("多线程提交")
    class ConcurrentSubmissionTests {
        @Test
        @DisplayName("多个线程同时提交任务")
        void multipleThreadsSubmitTasks() throws InterruptedException {
            int threadCount = 10;
            int perThreadTasks = 20;
            int totalTasks = threadCount * perThreadTasks;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completedLatch = new CountDownLatch(totalTasks);
            AtomicInteger res = new AtomicInteger();

            for (int i = 0; i < threadCount; i++) {
                final int num = i;
                Thread thread = new Thread(() -> {
                    try {
                        startLatch.await();
                        System.out.println(num + " " + Thread.currentThread().getName());
                        for (int j = 0; j < perThreadTasks; j++) {
                            eventLoop.execute(() -> {
                                res.incrementAndGet();
                                completedLatch.countDown();
                            });
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                thread.start();
            }
            startLatch.countDown();
            // 等待所有任务完成
            boolean completed = completedLatch.await(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            assertThat(res.get()).isEqualTo(totalTasks);
        }

        @Test
        @DisplayName("从 EventLoop 线程内提交任务")
        void submitTaskFromEventLoopThread() throws InterruptedException {
            CountDownLatch testLatch = new CountDownLatch(2);
            AtomicInteger num = new AtomicInteger(0);

            eventLoop.execute(() -> {
                testLatch.countDown();
                num.incrementAndGet();
                eventLoop.execute(() -> {
                    testLatch.countDown();
                    num.incrementAndGet();
                });
            });

            boolean completed = testLatch.await(10, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(num.get()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandlingTests {
        @Test
        @DisplayName("任务异常不影响后续任务执行")
        void exceptionDoesNotAffectSubsequentTasks() throws InterruptedException {
            CountDownLatch testLatch = new CountDownLatch(2);
            AtomicInteger res = new AtomicInteger(0);

            eventLoop.execute(() -> {
                testLatch.countDown();
                res.incrementAndGet();
                throw new RuntimeException("测试异常");
            });

            eventLoop.execute(() -> {
                testLatch.countDown();
                res.incrementAndGet();
            });

            boolean completed = testLatch.await(1, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(res.get()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("性能和行为")
    class PerformanceTests {

        @Test
        @DisplayName("执行大量任务")
        void executeManyTasks() throws InterruptedException {
            int taskCount = 1000000;
            CountDownLatch latch = new CountDownLatch(taskCount);
            AtomicInteger counter = new AtomicInteger(0);

            int threadCount = 100;
            int preThreadCount = 10000;
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < threadCount; i++) {
                Thread thread = new Thread(() -> {
                    for (int j = 0; j < preThreadCount; j++) {
                        eventLoop.execute(() -> {
                            counter.incrementAndGet();
                            latch.countDown();
                        });
                    }
                });
                thread.start();
            }
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - startTime;
            assertThat(completed).isTrue();
            assertThat(counter.get()).isEqualTo(taskCount);
            System.out.println("[TaskQueueTest] 执行 " + taskCount + " 个任务耗时: " + elapsed + "ms");
        }

        @Test
        @DisplayName("wakeup() 立即唤醒阻塞的选择器")
        void wakeupImmediatelyWakesSelector() throws InterruptedException {
            //确保选择器正在阻塞
            Thread.sleep(50);

            long startTime = System.currentTimeMillis();
            CountDownLatch testLatch = new CountDownLatch(1);
            eventLoop.execute(() -> {
                testLatch.countDown();
            });

            boolean completed = testLatch.await(1, TimeUnit.SECONDS);
            System.out.println("执行的时间:" + (System.currentTimeMillis() - startTime));
            assertThat(completed).isTrue();
        }


        @Test
        @DisplayName("验收场景3: hasTasks() 正确反映队列状态")
        void acceptanceScenario3() throws InterruptedException {
            // 在 EventLoop 线程内检查任务队列状态
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean[] hasTasksResults = new AtomicBoolean[2];
            hasTasksResults[0] = new AtomicBoolean();
            hasTasksResults[1] = new AtomicBoolean();

            eventLoop.execute(() -> {
                // 添加一个任务
                eventLoop.execute(() -> {
                });

                // 检查 hasTasks
                hasTasksResults[0].set(((SingleThreadEventLoop) eventLoop).hasTasks());
                latch.countDown();
            });

            latch.await(2, TimeUnit.SECONDS);
            // 在添加任务后，hasTasks 应该返回 true
            assertThat(hasTasksResults[0].get()).isTrue();
        }
    }
}

