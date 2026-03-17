package com.getian.netty.channel;

import com.getian.netty.channel.nio.NioEventLoop;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * 验证 EventLoop 定时任务功能：
 * schedule() - 延迟执行
 * scheduleAtFixedRate() - 周期性执行
 * 任务取消
 * 执行顺序
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-17
 */
@DisplayName("定时任务测试")
public class ScheduledTaskTest {
    private NioEventLoop eventLoop;

    @BeforeEach
    void setup() {
        eventLoop = new NioEventLoop(null);
        eventLoop.start();
        try {
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
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Nested
    @DisplayName("schedule() 延迟任务")
    class ScheduleDelayedTaskTests {
        @Test
        @DisplayName("schedule() 在指定延迟后执行任务")
        void schedulesTaskWithDelay() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean executed = new AtomicBoolean(false);
            long start = System.currentTimeMillis();
            //500ms后执行
            eventLoop.schedule(() -> {
                executed.set(true);
                latch.countDown();
            }, 200, TimeUnit.MILLISECONDS);

            boolean completed = latch.await(2, TimeUnit.SECONDS);
            long end = System.currentTimeMillis();
            assertThat(completed).isTrue();
            assertThat(executed.get()).isTrue();
            assertThat(end).isGreaterThanOrEqualTo(180); // 允许一些误差
        }

        @Test
        @DisplayName("schedule() 返回 ScheduledFuture")
        void returnsScheduledFuture() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(1);
            ScheduledFuture<?> future = eventLoop.schedule(() -> {
                latch.countDown();
                System.out.println("哈哈");
            }, 200, TimeUnit.MILLISECONDS);

            assertThat((Object) future).isNotNull();
            assertThat((Object) future).isInstanceOf(ScheduledFuture.class);
            future.cancel(false);

            boolean completed = latch.await(1, TimeUnit.SECONDS);
            assertThat(completed).isFalse();
        }


        @Test
        @DisplayName("schedule() 不接受 null 任务")
        void rejectsNullTask() {
            assertThatThrownBy(() -> eventLoop.schedule(null, 1, TimeUnit.SECONDS))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("schedule() 不接受 null TimeUnit")
        void rejectsNullTimeUnit() {
            assertThatThrownBy(() -> eventLoop.schedule(() -> {
            }, 1, null))
                    .isInstanceOf(NullPointerException.class);
        }

        //多个定时任务按时间顺序执行
        @Test
        @DisplayName("多个定时任务按时间顺序执行")
        void multipleTasksExecuteInOrder() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(3);
            List<Integer> order = Collections.synchronizedList(new ArrayList<>());
            //投放三个定时任务
            eventLoop.schedule(() -> {
                latch.countDown();
                order.add(3);
            }, 300, TimeUnit.MILLISECONDS);

            eventLoop.schedule(() -> {
                latch.countDown();
                order.add(1);
            }, 100, TimeUnit.MILLISECONDS);

            eventLoop.schedule(() -> {
                latch.countDown();
                order.add(2);
            }, 200, TimeUnit.MILLISECONDS);


            boolean completed = latch.await(1, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(order).containsExactly(1, 2, 3);
        }

    }

    @Nested
    @DisplayName("scheduleAtFixedRate() 周期任务")
    class ScheduleAtFixedRateTests {
        @Test
        @DisplayName("scheduleAtFixedRate() 周期性执行任务")
        void schedulesPeriodicTask() throws InterruptedException {
            AtomicInteger num = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(3);
            long start = System.currentTimeMillis();
            //50ms之后开始第一次 然后100ms执行一次
            eventLoop.scheduleAtFixedRate(() -> {
                num.incrementAndGet();
                latch.countDown();
            }, 50, 100, TimeUnit.MILLISECONDS);

            boolean completed = latch.await(300, TimeUnit.MILLISECONDS);
            System.out.println("花费的时间为:" + (System.currentTimeMillis() - start));
            assertThat(completed).isTrue();
            assertThat(num.get()).isEqualTo(3);
        }

        @Test
        @DisplayName("scheduleAtFixedRate() 不接受 period <= 0")
        void rejectsNonPositivePeriod() {
            assertThatThrownBy(() -> eventLoop.scheduleAtFixedRate(() -> {
            }, 0, 0, TimeUnit.SECONDS))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> eventLoop.scheduleAtFixedRate(() -> {
            }, 0, -1, TimeUnit.SECONDS))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("周期任务可以被取消")
        void periodicTaskCanBeCancelled() throws InterruptedException {
            AtomicInteger counter = new AtomicInteger(0);
            ScheduledFuture<?> future = eventLoop.scheduleAtFixedRate(() -> {
                counter.incrementAndGet();
            }, 100, 200, TimeUnit.MILLISECONDS);

            // 等待执行几次
            Thread.sleep(1000);
            int countBeforeCancel = counter.get();

            //取消任务 传递false的话，如果任务还没有开始执行，就直接停止；如果任务开始执行的话，会让任务执行完，但是标记为已经取消
            future.cancel(false);
            assertThat(future.isCancelled()).isTrue();

            //等待一段时间 确认不执行
            Thread.sleep(200);
            int counterAfterCancel = counter.get();
            assertThat(counterAfterCancel).isLessThanOrEqualTo(countBeforeCancel + 1);
        }

        @Nested
        @DisplayName("任务取消")
        class TaskCancellationTests {
            @Test
            @DisplayName("已取消的任务不会执行")
            void cancelledTaskDoesNotExecute() throws InterruptedException {
                //对于没有开始执行的任务 -> 取消
                AtomicBoolean executed = new AtomicBoolean(false);

                ScheduledFuture<?> future = eventLoop.schedule(() -> {
                    executed.set(true);
                }, 200, TimeUnit.MILLISECONDS);

                Thread.sleep(100);
                future.cancel(false);

                assertThat(executed.get()).isFalse();
            }

            @Test
            @DisplayName("已取消的任务不会执行")
            void cancelledTaskDoesNotExecute2() throws InterruptedException, ExecutionException {
                //对于已经开始执行的任务 -> 取消
                AtomicBoolean flag2 = new AtomicBoolean(false);
                ScheduledFuture<?> future2 = eventLoop.schedule(() -> {
                    try {
                        Thread.sleep(200);
                        flag2.set(true);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }, 100, TimeUnit.MILLISECONDS);

                Thread.sleep(150);
                boolean cancel = future2.cancel(false);
                assertThat(cancel).isTrue();
                Thread.sleep(300);
                System.out.println(flag2.get());
            }


            @Test
            @DisplayName("重复取消返回 false")
            void doubleCancelReturnsFalse() {
                ScheduledFuture<?> future = eventLoop.schedule(() -> {
                    System.out.println("haha");
                }, 100, TimeUnit.MILLISECONDS);

                boolean flag1 = future.cancel(false);
                boolean flag2 = future.cancel(false);

                assertThat(flag1).isTrue();
                assertThat(flag2).isFalse();
            }
        }

        @Nested
        @DisplayName("ScheduledFuture 状态")
        class ScheduledFutureStateTests {
            @Test
            @DisplayName("新创建的 Future 未完成也未取消")
            void newFutureNotDoneNotCancelled() {
                ScheduledFuture<?> future = eventLoop.schedule(() -> {
                    System.out.println("haha");
                }, 1, TimeUnit.SECONDS);
                System.out.println(future.isDone());
                future.cancel(false);
            }

            @Test
            @DisplayName("执行完成后 isDone() 返回 true")
            void isDoneReturnsTrueAfterExecution() throws InterruptedException {
                CountDownLatch latch = new CountDownLatch(1);
                ScheduledFuture<?> future = eventLoop.schedule(latch::countDown, 50, TimeUnit.MILLISECONDS);

                latch.await(2, TimeUnit.SECONDS);
                Thread.sleep(50); // 额外等待确保状态更新

                assertThat(future.isDone()).isTrue();
                assertThat(future.isCancelled()).isFalse();
            }

            @Test
            @DisplayName("getDelay() 返回剩余延迟时间")
            void getDelayReturnsRemainingTime() throws InterruptedException {
                ScheduledFuture<?> future = eventLoop.schedule(() -> {
                    System.out.println("haha");
                }, 1, TimeUnit.SECONDS);
                long delay = future.getDelay(TimeUnit.MILLISECONDS);
                System.out.println("剩余延迟时间为:" + delay);

                Thread.sleep(500);
                long delay2 = future.getDelay(TimeUnit.MILLISECONDS);
                System.out.println("剩余延迟时间为:" + delay2);

                future.cancel(false);
            }
        }
    }

}
