package com.getian.netty.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: sonicge
 * @CreateTime: 2026-03-20
 */

public class ChannelHandlerContextTest {
    /**
     * 简单的 ChannelId 实现
     */
    private static class SimpleChannelId implements ChannelId {
        private static int counter = 0;
        private final int id = ++counter;

        @Override
        public String asShortText() {
            return "ch" + id;
        }

        @Override
        public String asLongText() {
            return "channel-" + id;
        }

        @Override
        public int compareTo(ChannelId o) {
            return asLongText().compareTo(o.asLongText());
        }
    }

    private static class MockChannel implements Channel {
        private final ChannelPipeline pipeline;
        private final ChannelId id = new SimpleChannelId();
        private EventLoop eventLoop;

        public MockChannel() {
            this.pipeline = new DefaultChannelPipeline(this);
        }

        @Override
        public ChannelId id() {
            return id;
        }

        @Override
        public EventLoop eventLoop() {
            return eventLoop;
        }

        public void setEventLoop(EventLoop eventLoop) {
            this.eventLoop = eventLoop;
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
            return true;
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

    private static class RecordingHandler implements ChannelInboundHandler {
        final List<String> events = new ArrayList<>();
        final String name;
        boolean stopPropagation = false;

        public RecordingHandler(String name) {
            this.name = name;
        }


        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelRegistered");
            if (!stopPropagation) {
                ctx.fireChannelRegistered();
            }
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelUnregistered");
            if (!stopPropagation) {
                ctx.fireChannelUnregistered();
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelActive");
            if (!stopPropagation) {
                ctx.fireChannelActive();
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelInactive");
            if (!stopPropagation) {
                ctx.fireChannelInactive();
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            events.add(name + ":channelRead:" + msg);
            if (!stopPropagation) {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelReadComplete");
            if (!stopPropagation) {
                ctx.fireChannelReadComplete();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            events.add(name + ":exceptionCaught:" + cause.getMessage());
            if (!stopPropagation) {
                ctx.fireExceptionCaught(cause);
            }
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":handlerAdded");
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":handlerRemoved");
        }
    }

    private MockChannel channel;
    private ChannelPipeline pipeline;

    @BeforeEach
    void setup() {
        channel = new MockChannel();
        pipeline = new DefaultChannelPipeline(channel);
    }

    @Nested
    @DisplayName("Context 基本属性测试")
    class ContextBasicPropertiesTests {
        @Test
        @DisplayName("Context 应返回正确的 Channel")
        void contextShouldReturnChannel() {
            RecordingHandler handler = new RecordingHandler("h1");
            //会将name 和 handler作为参数包装成一个ctx的
            pipeline.addFirst("h1", handler);

            ChannelHandlerContext ctx = pipeline.context("h1");
            Channel channel1 = ctx.channel();
            assertThat(channel1).isEqualTo(channel);
        }

        @Test
        @DisplayName("Context 应返回正确的名称")
        void contextShouldReturnName() {
            RecordingHandler handler = new RecordingHandler("h1");
            pipeline.addFirst("h1", handler);

            ChannelHandlerContext context = pipeline.context(handler);
            String name = context.name();
            assertThat(name).isEqualTo("h1");
        }

        @Test
        @DisplayName("Context 应返回正确的 Handler")
        void contextShouldReturnHandler() {
            RecordingHandler handler = new RecordingHandler("h1");
            pipeline.addFirst("h1", handler);

            ChannelHandlerContext ctx = pipeline.context("h1");
            assertThat(ctx.handler()).isEqualTo(handler);
        }

        @Test
        @DisplayName("Context 应返回正确的 Pipeline")
        void contextShouldReturnPipeline() {
            RecordingHandler handler = new RecordingHandler("h1");
            pipeline.addFirst("h1", handler);

            ChannelHandlerContext ctx = pipeline.context("h1");
            ChannelPipeline pipeline1 = ctx.pipeline();

            assertThat(pipeline1).isEqualTo(pipeline);
        }
    }


    @Nested
    @DisplayName("事件传递测试")
    class EventPropagationTests {
        @Test
        @DisplayName("fireChannelRead 应传递到下一个 Handler")
        void fireChannelReadShouldPropagateToNext() {
            RecordingHandler handler1 = new RecordingHandler("h1");
            RecordingHandler handler2 = new RecordingHandler("h2");

            //添加也会在events集合里面添加内容的
            pipeline.addFirst("h1", handler1);
            pipeline.addFirst("h2", handler2);

            handler1.events.clear();
            handler2.events.clear();

            //触发事件
            pipeline.fireChannelRead("etge");
            assertThat(handler1.events.get(0)).isEqualTo("h1:channelRead:etge");
            assertThat(handler2.events.get(0)).isEqualTo("h2:channelRead:etge");
        }

        @Test
        @DisplayName("停止传播时事件不应传递到下一个 Handler")
        void eventShouldNotPropagateWhenStopped() {
            RecordingHandler h1 = new RecordingHandler("h1");
            h1.stopPropagation = true;
            RecordingHandler h2 = new RecordingHandler("h2");

            //先添加h2 h2就在后边了 所以如果h1停止的话 h2就没办法执行了
            pipeline.addFirst("h2", h2);
            pipeline.addFirst("h1", h1);

            h1.events.clear();
            h2.events.clear();

            pipeline.fireChannelActive();
            assertThat(h1.events).containsExactly("h1:channelActive");
        }

        @Test
        @DisplayName("fireExceptionCaught 应传递异常")
        void fireExceptionCaughtShouldPropagate() {
            RecordingHandler h1 = new RecordingHandler("h1");
            RecordingHandler h2 = new RecordingHandler("h2");

            pipeline.addFirst("h1", h1);
            pipeline.addFirst("h2", h2);

            h1.events.clear();

            ChannelHandlerContext ctx = pipeline.context("h1");

            ctx.fireExceptionCaught(new RuntimeException("haha"));
        }
    }

    @Nested
    @DisplayName("链式传递测试")
    class ChainPropagationTests {
        @Test
        @DisplayName("多个 Handler 按顺序接收事件")
        void multipleHandlersShouldReceiveEventsInOrder() {

            RecordingHandler h1 = new RecordingHandler("H1");
            RecordingHandler h2 = new RecordingHandler("H2");
            RecordingHandler h3 = new RecordingHandler("H3");
            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);
            pipeline.addLast("h3", h3);
            h1.events.clear();
            h2.events.clear();
            h3.events.clear();

            pipeline.fireChannelRegistered();

            assertThat(h1.events).contains("H1:channelRegistered");
            assertThat(h2.events).contains("H2:channelRegistered");
            assertThat(h3.events).contains("H3:channelRegistered");
        }

        @Test
        @DisplayName("从中间 Context 触发事件应跳过之前的 Handler")
        void firingFromMiddleShouldSkipPreviousHandlers() {
            RecordingHandler h1 = new RecordingHandler("h1");
            RecordingHandler h2 = new RecordingHandler("h2");
            RecordingHandler h3 = new RecordingHandler("h3");

            pipeline.addFirst("h1", h1);
            pipeline.addFirst("h2", h2);
            pipeline.addFirst("h3", h3);

            h1.events.clear();
            h2.events.clear();
            h3.events.clear();

            //开始事件通知
            pipeline.fireChannelActive();
            assertThat(h1.events).contains("h1:channelActive");
            assertThat(h2.events).contains("h2:channelActive");
            assertThat(h3.events).contains("h3:channelActive");

            //删除一个节点
            pipeline.remove(h2);

            h1.events.clear();
            h3.events.clear();

            //继续事件通知
            pipeline.fireChannelRead("hello");
            assertThat(h1.events).contains("h1:channelRead:hello");
            assertThat(h3.events).contains("h3:channelRead:hello");
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("完整的事件处理链场景")
        void completeEventChainScenario() {

            // 模拟一个典型的 Pipeline：Decoder -> Handler -> Encoder
            RecordingHandler decoder = new RecordingHandler("Decoder");
            RecordingHandler handler = new RecordingHandler("Handler");
            RecordingHandler encoder = new RecordingHandler("Encoder");

            pipeline.addLast("decoder", decoder);
            pipeline.addLast("handler", handler);
            pipeline.addLast("encoder", encoder);

            decoder.events.clear();
            handler.events.clear();
            encoder.events.clear();

            // 1. 触发 channelActive
            pipeline.fireChannelActive();
            assertThat(decoder.events).contains("Decoder:channelActive");
            assertThat(handler.events).contains("Handler:channelActive");
            assertThat(encoder.events).contains("Encoder:channelActive");

            // 2. 触发 channelRead
            pipeline.fireChannelRead("Hello");
            assertThat(decoder.events).contains("Decoder:channelRead:Hello");
            assertThat(handler.events).contains("Handler:channelRead:Hello");
            assertThat(encoder.events).contains("Encoder:channelRead:Hello");

            // 3. 触发 channelReadComplete
            pipeline.fireChannelReadComplete();
            assertThat(decoder.events).contains("Decoder:channelReadComplete");
            assertThat(handler.events).contains("Handler:channelReadComplete");
            assertThat(encoder.events).contains("Encoder:channelReadComplete");
        }

        @Test
        @DisplayName("异常处理场景")
        void exceptionHandlingScenario() {
            RecordingHandler normalHandler = new RecordingHandler("normal");
            RecordingHandler errorHandler = new RecordingHandler("error") {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    events.add(name + ":channelRead:" + msg);
                    throw new RuntimeException("Simulated Error");
                }
            };
            RecordingHandler catchHandler = new RecordingHandler("catch") {
                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    events.add("Catch:exceptionCaught:" + cause.getMessage());
                    // 不再传播
                }
            };

            pipeline.addLast("normal", normalHandler);
            pipeline.addLast("error", errorHandler);
            pipeline.addLast("catch", catchHandler);

            normalHandler.events.clear();
            errorHandler.events.clear();
            catchHandler.events.clear();


            pipeline.fireChannelRead("hello");

            assertThat(normalHandler.events).contains("normal:channelRead:hello");
            assertThat(errorHandler.events.size()).isEqualTo(2);
        }
    }

}
