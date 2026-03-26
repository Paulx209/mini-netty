package com.getian.netty.handler.logging;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.buffer.UnpooledByteBufAllocator;
import com.getian.netty.channel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LoggingHandler 测试
 * @Author: sonicge
 * @CreateTime: 2026-03-26
 */

public class LoggingHandlerTest {
    @Nested
    @DisplayName("LogLevel 枚举测试")
    class LogLevelTests {
        @Test
        @DisplayName("包含五种日志级别")
        void containsFiveLogLevels() {
            LogLevel[] values = LogLevel.values();
            assertThat(values.length).isEqualTo(5);

            assertThat(values).containsExactly(
                    LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO,
                    LogLevel.WARN, LogLevel.ERROR);
        }

        @Test
        @DisplayName("valueOf 正确解析")
        void valueOfParsesCorrectly() {
            assertThat(LogLevel.valueOf("TRACE")).isEqualTo(LogLevel.TRACE);
            assertThat(LogLevel.valueOf("DEBUG")).isEqualTo(LogLevel.DEBUG);
            assertThat(LogLevel.valueOf("INFO")).isEqualTo(LogLevel.INFO);
            assertThat(LogLevel.valueOf("WARN")).isEqualTo(LogLevel.WARN);
            assertThat(LogLevel.valueOf("ERROR")).isEqualTo(LogLevel.ERROR);
        }
    }

    @Nested
    @DisplayName("LoggingHandler 构造测试")
    class ConstructorTests {
        @Test
        @DisplayName("默认构造器使用 DEBUG 级别")
        void defaultConstructorUsesDebugLevel() {
            LoggingHandler handler = new LoggingHandler();
            LogLevel level = handler.level();
            assertThat(level).isEqualTo(LogLevel.DEBUG);
        }

        @Test
        @DisplayName("使用指定级别构造")
        void constructorWithLevel() {
            LoggingHandler handler = new LoggingHandler(LogLevel.ERROR);
            LogLevel level = handler.level();
            assertThat(level).isEqualTo(LogLevel.ERROR);
            String name = handler.name();
            assertThat(name).isEqualTo("LoggingHandler");
        }

        @Test
        @DisplayName("使用指定名称构造")
        void constructorWithName() {
            LoggingHandler handler = new LoggingHandler("etge", LogLevel.INFO);
            LogLevel level = handler.level();
            assertThat(level).isEqualTo(LogLevel.INFO);
            assertThat(handler.name()).isEqualTo("etge");
        }

        @Test
        @DisplayName("使用名称和级别构造")
        void constructorWithNameAndLevel() {
            LoggingHandler handler = new LoggingHandler("CLIENT", LogLevel.TRACE);

            assertThat(handler.level()).isEqualTo(LogLevel.TRACE);
            assertThat(handler.name()).isEqualTo("CLIENT");
        }

        @Test
        @DisplayName("null 名称抛出异常")
        void throwsExceptionForNullName() {
            assertThatThrownBy(() -> new LoggingHandler(null, LogLevel.DEBUG))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null 级别抛出异常")
        void throwsExceptionForNullLevel() {
            assertThatThrownBy(() -> new LoggingHandler("test", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("事件传递测试")
    class EventPropagationTests {
        private MockChannel channel;
        private RecordingHandler downstream;

        @BeforeEach
        void setup() {
            channel = new MockChannel();
            downstream = new RecordingHandler();
            channel.pipeline.addLast("logger", new LoggingHandler());
            channel.pipeline.addLast("recorder", downstream);
        }

        @Test
        @DisplayName("channelRegistered 传递")
        void channelRegisteredPropagates() {
            channel.pipeline().fireChannelRegistered();
            assertThat(downstream.events).contains("channelRegistered");
        }

        @Test
        @DisplayName("channelActive 传递")
        void channelActivePropagates() {
            channel.pipeline().fireChannelActive();

            assertThat(downstream.events).contains("channelActive");
        }

        @Test
        @DisplayName("channelRead 传递")
        void channelReadPropagates() {
            channel.pipeline().fireChannelRead("test message");

            assertThat(downstream.events).contains("channelRead:test message");
        }

        @Test
        @DisplayName("channelInactive 传递")
        void channelInactivePropagates() {
            channel.pipeline().fireChannelInactive();

            assertThat(downstream.events).contains("channelInactive");
        }

        @Test
        @DisplayName("userEventTriggered 传递")
        void userEventTriggeredPropagates() {
            channel.pipeline().fireUserEventTriggered("custom event");

            assertThat(downstream.events).contains("userEventTriggered:custom event");
        }

    }

    private static class SimpleChannelId implements ChannelId {
        private static int counter = 0;
        private final String id = "test-" + (++counter);

        @Override
        public String asShortText() {
            return id;
        }

        @Override
        public String asLongText() {
            return "test-channel-" + id;
        }

        @Override
        public int compareTo(ChannelId o) {
            return asLongText().compareTo(o.asLongText());
        }
    }

    private static class MockChannel implements Channel {
        private final ChannelPipeline pipeline;
        private final ChannelId id = new SimpleChannelId();

        public MockChannel() {
            pipeline = new DefaultChannelPipeline(this);
        }

        @Override
        public ChannelId id() {
            return id;
        }

        @Override
        public EventLoop eventLoop() {
            return null;
        }

        @Override
        public Channel parent() {
            return null;
        }

        @Override
        public ChannelConfig config() {
            return null;
        }

        @Override
        public ChannelPipeline pipeline() {
            return pipeline;
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public boolean isRegistered() {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public ChannelFuture close() {
            return null;
        }

        @Override
        public Channel read() {
            return this;
        }

        @Override
        public UnSafe unsafe() {
            return null;
        }
    }

    /**
     * 记录事件的 Handler
     */
    private static class RecordingHandler extends ChannelInboundHandlerAdapter {
        final List<String> events = new ArrayList<>();

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            events.add("channelRegistered");
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            events.add("channelUnregistered");
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            events.add("channelActive");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            events.add("channelInactive");
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            events.add("channelRead:" + msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            events.add("channelReadComplete");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            events.add("exceptionCaught:" + cause.getMessage());
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            events.add("userEventTriggered:" + evt);
        }
    }

    @Nested
    @DisplayName("ByteBuf 格式化测试")
    class ByteBufFormatTests {
        @Test
        @DisplayName("格式化空 ByteBuf")
        void formatsEmptyByteBuf() {
            LoggingHandler handler = new LoggingHandler();
            //DEFAULT默认不适用直接内存 使用堆内存 初始容量为0
            ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer(0);
            String response = handler.formatData(buf);
            assertThat(response).isEqualTo("ByteBuf(0B)");
        }

        @Test
        @DisplayName("格式化带内容的 ByteBuf")
        void formatsByteBufWithContent() {
            LoggingHandler handler = new LoggingHandler();
            ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
            buf.writeBytes("hello,etge".getBytes(StandardCharsets.US_ASCII));

            String response = handler.formatData(buf);
            System.out.println(response);

            assertThat(response).contains("hello,etge");
        }

        @Test
        @DisplayName("格式化二进制 ByteBuf 不显示字符串")
        void formatsBinaryByteBufWithoutString() {
            LoggingHandler handler = new LoggingHandler();
            ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
            buf.writeByte(0x00);
            buf.writeByte(0x01);
            buf.writeByte(0xFF);

            String response = handler.formatData(buf);
            assertThat(response).contains("ByteBuf(3B");
            assertThat(response).contains("hex=");
            assertThat(response).doesNotContain("str=");
            buf.release();
        }


        /**
         * 无论是字节还是字符串，只要超过64字节的，都截断使用...来展示
         */
        @Test
        @DisplayName("超过 64 字节截断显示")
        void truncatesLargeByteBuf() {
            LoggingHandler handler = new LoggingHandler();
            ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
            byte[] data = new byte[100];
            for (int i = 0; i < 100; i++) {
                data[i] = 0x41; // 'A'的阿斯克码值为65  65=1+4*16
            }
            buf.writeBytes(data);

            //最后的字符串展示的时候，也只是展示64长度，后面的内容全部省略掉
            String result = handler.formatData(buf);

            System.out.println(result);
            assertThat(result).contains("ByteBuf(100B");
            assertThat(result).contains("...");
            buf.release();
        }
    }

    @Nested
    @DisplayName("异常格式化测试")
    class ExceptionFormatTests {
        @Test
        @DisplayName("格式化异常信息")
        void formatsException() {
            LoggingHandler handler = new LoggingHandler();
            RuntimeException ex = new RuntimeException("exception:格式化异常");
            String response = handler.formatData(ex);
            System.out.println(response);
            assertThat(response).isEqualTo("RuntimeException exception:格式化异常");
        }

        @Test
        @DisplayName("格式化嵌套异常")
        void formatsNestedException() {
            LoggingHandler handler = new LoggingHandler();
            Exception cause = new IllegalArgumentException("inner");
            RuntimeException outer = new RuntimeException("outer", cause);
            String response = handler.formatData(outer);
            System.out.println(response);
        }
    }

    @Nested
    @DisplayName("toString 测试")
    class ToStringTests {
        @Test
        @DisplayName("toString 包含名称和级别")
        void toStringContainsNameAndLevel() {
            LoggingHandler handler =new LoggingHandler("SERVER",LogLevel.INFO);

            String response = handler.toString();
            assertThat(response).contains("SERVER");
            assertThat(response).contains("INFO");
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("场景: 服务端日志记录")
        void scenarioServerLogging() {
            LoggingHandler server = new LoggingHandler("SERVER",LogLevel.INFO);
            LogLevel level = server.level();
            assertThat(level).isEqualTo(LogLevel.INFO);
            assertThat(server.name()).isEqualTo("SERVER");
        }

        @Test
        @DisplayName("场景: 客户端日志记录")
        void scenarioClientLogging() {
            LoggingHandler client = new LoggingHandler("CLIENT",LogLevel.INFO);
            LogLevel level = client.level();
            assertThat(level).isEqualTo(LogLevel.INFO);
            assertThat(client.name()).isEqualTo("CLIENT");
        }

        @Test
        @DisplayName("场景: 调试网络数据")
        void scenarioDebugNetworkData() {
            LoggingHandler handler = new LoggingHandler();
            ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
            buf.writeBytes("hello,etge".getBytes(StandardCharsets.US_ASCII));
            String response = handler.formatData(buf);
            System.out.println(response);

            assertThat(response).contains("10B");
        }
    }
}
