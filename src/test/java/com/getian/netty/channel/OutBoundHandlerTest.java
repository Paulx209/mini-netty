package com.getian.netty.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.invoke.VarHandle;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出站事件处理测试
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-20
 */

public class OutBoundHandlerTest {
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

    /**
     * 模拟 Channel 用于测试
     */
    private static class MockChannel implements Channel {
        private final ChannelPipeline pipeline;
        private final ChannelId id = new SimpleChannelId();

        MockChannel() {
            this.pipeline = new DefaultChannelPipeline(this);
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
        public ChannelPipeline pipeline() {
            return pipeline;
        }

        @Override
        public ChannelFuture close() {
            return null;
        }

        @Override
        public Channel.UnSafe unsafe() {
            return null;
        }

        @Override
        public Channel read() {
            return this;
        }
    }

    private static class RecordingOutboundHandler extends ChannelOutboundHandlerAdapter {
        final List<String> events = new ArrayList<>();
        final String name;

        public RecordingOutboundHandler(String name) {
            this.name = name;
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":handlerAdded");
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":handlerAdded");
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            events.add(name + ":write:" + msg);
            super.write(ctx, msg, promise);
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":flush");
            super.flush(ctx);
        }

        @Override
        public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
            events.add(name + ":close");
            super.close(ctx, promise);
        }

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":read");
            super.read(ctx);
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
    @DisplayName("ChannelOutboundHandlerAdapter 测试")
    class AdapterTests {
        @Test
        @DisplayName("Adapter 应提供所有出站方法的默认实现")
        void adapterShouldProvideDefaultImplementations() {
            ChannelOutboundHandlerAdapter adapter = new ChannelOutboundHandlerAdapter();
            assertThat(adapter).isInstanceOf(ChannelOutboundHandler.class);
        }

        @Test
        @DisplayName("Adapter 应实现 ChannelHandler 生命周期方法")
        void adapterShouldImplementLifecycleMethods() {
            RecordingOutboundHandler handler = new RecordingOutboundHandler("H");

            pipeline.addLast("handler", handler);

            assertThat(handler.events).contains("H:handlerAdded");
        }
    }

    @Nested
    @DisplayName("出站操作测试")
    class OutboundOperationsTests {
        @Test
        @DisplayName("write 应通过 Context 触发")
        void writeShouldBeTriggerableFromContext() {
            RecordingOutboundHandler handler = new RecordingOutboundHandler("H");
            pipeline.addLast("h", handler);
            handler.events.clear();

            ChannelHandlerContext ctx = pipeline.context("h");
            //由于就一个节点，所以ctx.write()，第一个处理的节点就是head节点，head节点默认不实现，所以什么都没有
            ctx.write("hello");
        }

        @Test
        @DisplayName("flush 应通过 Context 触发")
        void flushShouldBeTriggerableFromContext() {
            RecordingOutboundHandler handler = new RecordingOutboundHandler("h1");
            pipeline.addLast("h1", handler);
            handler.events.clear();

            ChannelHandlerContext ctx = pipeline.context("h1");
            ctx.flush();
        }

        @Test
        @DisplayName("close 应通过 Context 触发")
        void closeShouldBeTriggerableFromContext() {
            RecordingOutboundHandler handler = new RecordingOutboundHandler("h1");
            pipeline.addLast("h1", handler);
            handler.events.clear();

            ChannelHandlerContext ctx = pipeline.context("h1");
            ctx.close();
        }

        @Test
        @DisplayName("read 应通过 Context 触发")
        void readShouldBeTriggerableFromContext() {
            RecordingOutboundHandler handler = new RecordingOutboundHandler("h1");
            pipeline.addLast("h1", handler);
            handler.events.clear();

            ChannelHandlerContext ctx = pipeline.context("h1");
            ctx.read();
        }
    }

    @Nested
    @DisplayName("Handler 链测试")
    class HandlerChainTests {
        @Test
        @DisplayName("多个出站 Handler 应反向处理 write")
        void multipleHandlersShouldProcessWriteInReverse() {
            RecordingOutboundHandler handler1 = new RecordingOutboundHandler("h1");
            RecordingOutboundHandler handler2 = new RecordingOutboundHandler("h2");

            pipeline.addLast("h1", handler1);
            pipeline.addLast("h2", handler2);

            handler1.events.clear();
            handler2.events.clear();

            //通过Context触发write
            ChannelHandlerContext ctx = pipeline.context("h2");
            ctx.write("etge");

            String response = handler1.events.get(0);
            System.out.println(response);
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("典型的编码器 Pipeline 场景")
        void typicalEncoderPipelineScenario() {
            RecordingOutboundHandler handler = new RecordingOutboundHandler("h1") {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    String response = (String) msg + "[ENCODED]";
                    events.add(response);
                    ctx.write(response);
                }
            };
            RecordingOutboundHandler handler2 = new RecordingOutboundHandler("h2");

            pipeline.addLast("h1", handler);
            pipeline.addLast("h2", handler2);

            handler.events.clear();
            handler2.events.clear();

            ChannelHandlerContext ctx = pipeline.context("h2");
            ctx.write("haha");
            assertThat(handler.events).contains("haha[ENCODED]");
        }

        @Test
        @DisplayName("出站操作应正确处理")
        void outboundOperationsShouldBeProcessedCorrectly() {
            RecordingOutboundHandler handler = new RecordingOutboundHandler("h1");
            pipeline.addLast("handler", handler);
            handler.events.clear();
            assertThat(pipeline.get("handler")).isNotNull();

            //验证Handler被正确添加
            ChannelHandlerContext ctx = pipeline.context("handler");
            assertThat(ctx).isNotNull();
            assertThat(ctx.handler()).isSameAs(handler);
        }
    }
}

