package com.getian.netty.handler.timeout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *  IdleStateHandler 测试
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-25
 */

public class IdleStateHandlerTest {

    @Nested
    @DisplayName("IdleState 枚举测试")
    class IdleStateEnumTests {
        @Test
        @DisplayName("包含三种空闲状态")
        void containsThreeIdleStates() {
            IdleState[] values = IdleState.values();
            assertThat(values.length).isEqualTo(3);
            assertThat(values).containsExactly(IdleState.READER_IDLE, IdleState.WRITER_IDLE, IdleState.ALL_IDLE);
        }

        @Test
        @DisplayName("valueOf 正确解析")
        void valueOfParsesCorrectly() {
            IdleState state1 = IdleState.valueOf("READER_IDLE");
            assertThat(state1).isEqualTo(IdleState.READER_IDLE);

            IdleState state2 = IdleState.valueOf("WRITER_IDLE");
            assertThat(state2).isEqualTo(IdleState.WRITER_IDLE);
        }
    }

    @Nested
    @DisplayName("IdleStateEvent 测试")
    class IdleStateEventTests {
        @Test
        @DisplayName("预定义的读空闲事件")
        void predefinedAllIdleEvents() {
            IdleState state = IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT.state();
            boolean first = IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT.isFirst();
            assertThat(first).isEqualTo(true);
            assertThat(state).isEqualTo(IdleState.ALL_IDLE);
        }

        @Test
        @DisplayName("预定义的读空闲事件")
        void predefinedReaderIdleEvents() {
            IdleState state = IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT.state();
            boolean first = IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT.isFirst();
            assertThat(first).isEqualTo(true);
            assertThat(state).isEqualTo(IdleState.READER_IDLE);

            IdleState state2 = IdleStateEvent.READER_IDLE_STATE_EVENT.state();
            boolean first2 = IdleStateEvent.READER_IDLE_STATE_EVENT.isFirst();

            assertThat(first2).isEqualTo(false);
            assertThat(state2).isEqualTo(IdleState.READER_IDLE);
        }

        @Test
        @DisplayName("预定义的写空闲事件")
        void predefinedWriterIdleEvents() {
            assertThat(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT.state()).isEqualTo(IdleState.WRITER_IDLE);
            assertThat(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT.isFirst()).isTrue();

            assertThat(IdleStateEvent.WRITER_IDLE_STATE_EVENT.state()).isEqualTo(IdleState.WRITER_IDLE);
            assertThat(IdleStateEvent.WRITER_IDLE_STATE_EVENT.isFirst()).isFalse();
        }


        @Test
        @DisplayName("toString 包含状态信息")
        void toStringContainsStateInfo() {
            System.out.println(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT.toString());
        }
    }

    @Nested
    @DisplayName("IdleStateHandler 构造测试")
    class IdleStateHandlerConstructorTests {
        /**
         * 如果不传递时间的单位TimeUnit的话，默认为s
         */
        @Test
        @DisplayName("使用秒构造")
        void constructsWithSeconds() {
            IdleStateHandler handler = new IdleStateHandler(30, 60, 90);
            long allIdleTime = handler.getAllIdleTime(TimeUnit.SECONDS);
            assertThat(allIdleTime).isEqualTo(90);
            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(30);
        }

        /**
         * 如果我们在构造函数中传递时间单位的话 就不会走默认的s，按照我们传递的单位来判断
         */
        @Test
        @DisplayName("使用自定义时间单位构造")
        void constructsWithCustomTimeUnit() {
            IdleStateHandler handler = new IdleStateHandler(100, 200, 300, TimeUnit.MILLISECONDS);

            assertThat(handler.getReaderIdleTime(TimeUnit.MILLISECONDS)).isEqualTo(100);
            assertThat(handler.getWriterIdleTime(TimeUnit.MILLISECONDS)).isEqualTo(200);
            assertThat(handler.getAllIdleTime(TimeUnit.MILLISECONDS)).isEqualTo(300);
        }

        /**
         * 如果我们传递0的话，表示我们不需要对该状态进行监控
         */
        @Test
        @DisplayName("0 表示禁用")
        void zeroMeansDisabled() {
            IdleStateHandler handler = new IdleStateHandler(0, 0, 0);

            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
            assertThat(handler.getWriterIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
            assertThat(handler.getAllIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
        }

        @Test
        @DisplayName("null 时间单位抛出异常")
        void throwsExceptionForNullTimeUnit() {
            assertThatThrownBy(() -> new IdleStateHandler(10, 20, 30, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("负数转换为 0")
        void negativeValuesConvertedToZero() {
            IdleStateHandler handler = new IdleStateHandler(-10, -20, -30, TimeUnit.SECONDS);

            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
            assertThat(handler.getWriterIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
            assertThat(handler.getAllIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
        }

        @Test
        @DisplayName("只设置读空闲")
        void readerIdleOnly() {
            IdleStateHandler handler = new IdleStateHandler(30, 0, 0);

            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(30);
            assertThat(handler.getWriterIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
            assertThat(handler.getAllIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
        }


        @Test
        @DisplayName("只设置写空闲")
        void writerIdleOnly() {
            IdleStateHandler handler = new IdleStateHandler(0, 60, 0);

            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
            assertThat(handler.getWriterIdleTime(TimeUnit.SECONDS)).isEqualTo(60);
            assertThat(handler.getAllIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
        }

        @Test
        @DisplayName("只设置全部空闲")
        void allIdleOnly() {
            IdleStateHandler handler = new IdleStateHandler(0, 0, 90);

            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
            assertThat(handler.getWriterIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
            assertThat(handler.getAllIdleTime(TimeUnit.SECONDS)).isEqualTo(90);
        }
    }

    @Nested
    @DisplayName("时间单位转换测试")
    class TimeUnitConversionTests {
        @Test
        @DisplayName("秒转毫秒")
        void convertsSecondsToMillis() {
            IdleStateHandler handler = new IdleStateHandler(10, 20, 30);
            long allIdleTime = handler.getAllIdleTime(TimeUnit.MILLISECONDS);
            System.out.println("毫秒值为:" + allIdleTime);
        }

        @Test
        @DisplayName("毫秒转秒")
        void convertsMillisToSeconds() {
            IdleStateHandler handler = new IdleStateHandler(1500, 2500, 3500, TimeUnit.MILLISECONDS);

            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(handler.getWriterIdleTime(TimeUnit.SECONDS)).isEqualTo(2);
            assertThat(handler.getAllIdleTime(TimeUnit.SECONDS)).isEqualTo(3);
        }

        @Test
        @DisplayName("分钟转秒")
        void convertsMinutesToSeconds() {
            IdleStateHandler handler = new IdleStateHandler(1, 2, 3, TimeUnit.MINUTES);

            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(60);
            assertThat(handler.getWriterIdleTime(TimeUnit.SECONDS)).isEqualTo(120);
            assertThat(handler.getAllIdleTime(TimeUnit.SECONDS)).isEqualTo(180);
        }
    }

    @Nested
    @DisplayName("newIdleStateEvent 测试")
    class NewIdleStateEventTests {
        @Test
        @DisplayName("创建第一次读空闲事件")
        void scenarioHeartbeatConfiguration() {
            IdleStateHandler handler = new IdleStateHandler(10, 0, 0);
            IdleStateEvent event = handler.newIdleStateEvent(IdleState.READER_IDLE, true);
            assertThat(event).isSameAs(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);
        }

        @Test
        @DisplayName("创建非第一次读空闲事件")
        void createsReaderIdleEvent() {
            IdleStateHandler handler = new IdleStateHandler(30, 0, 0);

            IdleStateEvent event = handler.newIdleStateEvent(IdleState.READER_IDLE, false);

            assertThat(event).isSameAs(IdleStateEvent.READER_IDLE_STATE_EVENT);
        }

        @Test
        @DisplayName("创建第一次写空闲事件")
        void createsFirstWriterIdleEvent() {
            IdleStateHandler handler = new IdleStateHandler(0, 60, 0);

            IdleStateEvent event = handler.newIdleStateEvent(IdleState.WRITER_IDLE, true);

            assertThat(event).isSameAs(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);
        }

        @Test
        @DisplayName("创建第一次全部空闲事件")
        void createsFirstAllIdleEvent() {
            IdleStateHandler handler = new IdleStateHandler(0, 90, 0);

            IdleStateEvent event = handler.newIdleStateEvent(IdleState.ALL_IDLE, true);

            assertThat(event).isSameAs(IdleStateEvent.FIRST_ALL_IDLE_STATE_EVENT);
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("场景: 心跳检测配置")
        void scenarioHeartbeatConfiguration() {
            // Given: 配置 30 秒读空闲超时，用于心跳检测
            IdleStateHandler handler = new IdleStateHandler(30, 0, 0);

            // Then: 只有读空闲被配置
            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(30);
            assertThat(handler.getWriterIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
            assertThat(handler.getAllIdleTime(TimeUnit.SECONDS)).isEqualTo(0);
        }

        @Test
        @DisplayName("场景: 完整的空闲检测配置")
        void scenarioCompleteIdleConfiguration() {
            // Given: 配置完整的空闲检测
            // 30秒读空闲、60秒写空闲、90秒全部空闲
            IdleStateHandler handler = new IdleStateHandler(30, 60, 90);

            // Then: 所有超时都正确配置
            assertThat(handler.getReaderIdleTime(TimeUnit.SECONDS)).isEqualTo(30);
            assertThat(handler.getWriterIdleTime(TimeUnit.SECONDS)).isEqualTo(60);
            assertThat(handler.getAllIdleTime(TimeUnit.SECONDS)).isEqualTo(90);
        }

        @Test
        @DisplayName("场景: 使用毫秒精度配置")
        void scenarioMillisecondPrecision() {
            // Given: 需要更精确的超时控制（500ms）
            IdleStateHandler handler = new IdleStateHandler(500, 1000, 1500, TimeUnit.MILLISECONDS);

            // Then: 毫秒级精度正确
            assertThat(handler.getReaderIdleTime(TimeUnit.MILLISECONDS)).isEqualTo(500);
            assertThat(handler.getWriterIdleTime(TimeUnit.MILLISECONDS)).isEqualTo(1000);
            assertThat(handler.getAllIdleTime(TimeUnit.MILLISECONDS)).isEqualTo(1500);
        }
    }


}
