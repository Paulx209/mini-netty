package com.getian.netty.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 入站事件处理测试
 *
 * <p>测试 ChannelInboundHandlerAdapter 和入站事件传递机制。
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-20
 */
public class InboundHandlerTest {
    private static class SimpleChannelId implements ChannelId {
        private static int counter = 0;
        private final int id = ++counter;

        @Override
        public String asShortText() {
            return "ch-" + id;
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
        private final ChannelId channelId = new SimpleChannelId();
        private final ChannelPipeline pipeline;

        public MockChannel() {
            this.pipeline = new DefaultChannelPipeline(this);
        }

        @Override
        public ChannelId id() {
            return channelId;
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

    private static class RecodingHandler extends ChannelInboundHandlerAdapter {
        final List<String> events = new ArrayList<>();
        final String name;

        public RecodingHandler(String name) {
            this.name = name;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":handlerAdded");
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":handlerRemoved");
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelRegistered");
            //向下传播
            super.channelRegistered(ctx);
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelUnregistered");
            super.channelUnregistered(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelInactive");
            super.channelInactive(ctx);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelActive");
            super.channelActive(ctx);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelReadComplete");
            super.channelReadComplete(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            events.add(name + ":channelRead:" + msg);
            super.channelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            events.add(name + ":exceptionCaught:" + cause.getMessage());
            super.exceptionCaught(ctx, cause);
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
    @DisplayName("ChannelInboundHandlerAdapter 测试")
    class AdapterTests {
        @Test
        @DisplayName("Adapter 应提供所有入站方法的默认实现")
        void adapterShouldProvideDefaultImplementations() {
            ChannelInboundHandlerAdapter adapter = new ChannelInboundHandlerAdapter();
            //判断adapater
            assertThat(adapter).isInstanceOf(ChannelInboundHandler.class);
        }

        @Test
        @DisplayName("Adapter 默认实现应传递事件")
        void defaultImplementationShouldPropagateEvents() {
            //适配器中默认帮我们实现了 向下传播的逻辑
            RecodingHandler h1 = new RecodingHandler("h1");
            RecodingHandler h2 = new RecodingHandler("h2");

            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);
            h1.events.clear();
            h2.events.clear();

            pipeline.fireChannelRead("haha");

            assertThat(h1.events).contains("h1:channelRead:haha");
            assertThat(h2.events).contains("h2:channelRead:haha");
        }
    }

    @Nested
    @DisplayName("入站事件传递测试")
    class InboundEventPropagationTests {
        @Test
        @DisplayName("channelRegistered 应按顺序传递")
        void channelRegisteredShouldPropagate() {
            RecodingHandler h1 = new RecodingHandler("h1");
            RecodingHandler h2 = new RecodingHandler("h2");

            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);

            h1.events.clear();
            h2.events.clear();

            pipeline.fireChannelRegistered();

            assertThat(h1.events).contains("h1:channelUnregistered");
            assertThat(h2.events).contains("h2:channelUnregistered");
        }

        @Test
        @DisplayName("channelActive 应按顺序传递")
        void channelActiveShouldPropagate() {
            RecodingHandler h1 = new RecodingHandler("h1");
            RecodingHandler h2 = new RecodingHandler("h2");

            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);

            h1.events.clear();
            h2.events.clear();

            pipeline.fireChannelActive();

            assertThat(h1.events).contains("h1:channelActive");
            assertThat(h2.events).contains("h2:channelActive");
        }

        @Test
        @DisplayName("channelRead 应传递消息")
        void channelReadShouldPropagateMessage() {
            RecodingHandler h1 = new RecodingHandler("H1");
            RecodingHandler h2 = new RecodingHandler("H2");
            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);
            h1.events.clear();
            h2.events.clear();

            pipeline.fireChannelRead("TestMessage");

            assertThat(h1.events).contains("H1:channelRead:TestMessage");
            assertThat(h2.events).contains("H2:channelRead:TestMessage");
        }


        @Test
        @DisplayName("channelReadComplete 应按顺序传递")
        void channelReadCompleteShouldPropagate() {
            RecodingHandler h1 = new RecodingHandler("H1");
            RecodingHandler h2 = new RecodingHandler("H2");
            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);
            h1.events.clear();
            h2.events.clear();

            pipeline.fireChannelReadComplete();

            assertThat(h1.events).contains("H1:channelReadComplete");
            assertThat(h2.events).contains("H2:channelReadComplete");
        }

        @Test
        @DisplayName("channelInactive 应按顺序传递")
        void channelInactiveShouldPropagate() {
            RecodingHandler h1 = new RecodingHandler("h1");
            RecodingHandler h2 = new RecodingHandler("h2");

            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);

            h1.events.clear();
            h2.events.clear();

            pipeline.fireChannelInactive();

            assertThat(h1.events).contains("h1:channelInactive");
            assertThat(h2.events).contains("h2:channelInactive");
        }

        @Test
        @DisplayName("exceptionCaught 应传递异常")
        void exceptionCaughtShouldPropagate() {
            RecodingHandler h1 = new RecodingHandler("h1");
            RecodingHandler h2 = new RecodingHandler("h2");

            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);

            h1.events.clear();
            h2.events.clear();

            pipeline.fireExceptionCaught(new RuntimeException("error"));

            assertThat(h1.events).contains("h1:exceptionCaught:error");
            assertThat(h2.events).contains("h2:exceptionCaught:error");
        }
    }

    @Nested
    @DisplayName("事件拦截测试")
    class EventInterceptionTests {
        @Test
        @DisplayName("不调用 super 方法应阻止事件传递")
        void notCallingSuperShouldStopPropagation() {
            RecodingHandler first = new RecodingHandler("First") {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                    events.add(name + ":channelRead:" + msg);
                }
            };
            RecodingHandler second = new RecodingHandler("second");
            pipeline.addLast("first", first);
            pipeline.addLast("second", second);

            first.events.clear();
            second.events.clear();

            //触发事件
            pipeline.fireChannelRead("haha");

            //断言
            assertThat(first.events).contains("First:channelRead:haha");
            assertThat(second.events).isEmpty();
        }

        @Test
        @DisplayName("修改消息后传递")
        void modifyMessageBeforePropagation() {
            RecodingHandler h1 = new RecodingHandler("h1") {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    events.add(name + ":channelRead:" + msg);
                    ctx.fireChannelRead("被篡改过了:" + msg);
                }
            };
            RecodingHandler h2 = new RecodingHandler("h2");

            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);

            h1.events.clear();
            h2.events.clear();

            pipeline.fireChannelRead("Original");

            assertThat(h1.events).contains("h1:channelRead:Original");
            assertThat(h2.events).contains("h2:channelRead:被篡改过了:Original");
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("典型的消息处理 Pipeline 场景")
        void typicalMessageProcessingScenario() {
            RecodingHandler handler1 = new RecodingHandler("handler1") {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    events.add("handler1:channelRead:" + msg);
                    String upperCase = ((String) msg).toUpperCase();
                    //第一条路 向下传播
                    ctx.fireChannelRead(upperCase);
                }
            };

            pipeline.addLast("h1", handler1);
            handler1.events.clear();

            RecodingHandler handler2 = new RecodingHandler("handler2") {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    events.add("handler2:channelRead:" + msg);
                    String response = (String) msg + "haha";
                    ctx.fireChannelRead(response);
                }
            };

            pipeline.addLast("h2", handler2);
            handler2.events.clear();


            RecodingHandler handler3 = new RecodingHandler("handler3"){
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    events.add("handler3:channelRead:"+msg);
                }
            };

            pipeline.addLast("h3",handler3);
            handler3.events.clear();

            //触发事件
            pipeline.fireChannelRead("offers");

            assertThat(handler1.events).contains("handler1:channelRead:offers");
            assertThat(handler2.events).contains("handler2:channelRead:OFFERS");
            assertThat(handler3.events).contains("handler3:channelRead:OFFERShaha");
        }


        @Test
        @DisplayName("完整的 Channel 生命周期事件")
        void completeChannelLifecycleEvents() {
            //完整的生命周期 registered -> active -> read -> readComplete -> inActive -> unRegistered
            RecodingHandler handler1 = new RecodingHandler("handler1");

            pipeline.addLast("h1",handler1);

            handler1.events.clear();

            //1.注册
            pipeline.fireChannelRegistered();

            //2.连接 bind or connect
            pipeline.fireChannelActive();

            //3.read write
            pipeline.fireChannelRead("haha");

            //4.read complete
            pipeline.fireChannelReadComplete();

            //5.inActive
            pipeline.fireChannelInactive();

            //6.unRegister
            pipeline.fireChannelUnregistered();

            assertThat(handler1.events).containsExactly(
                    "handler1:channelRegistered",
                    "handler1:channelActive",
                    "handler1:channelRead:haha",
                    "handler1:channelReadComplete",
                    "handler1:channelInactive",
                    "handler1:channelUnregistered"
            );
        }
    }
}
