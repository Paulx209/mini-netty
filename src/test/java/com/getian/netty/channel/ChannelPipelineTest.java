package com.getian.netty.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChannelPipeline 测试
 * 测试 Pipeline 的双向链表结构和事件传递机制。
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-19
 */
public class ChannelPipelineTest {
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
        private final ChannelPipeline channelPipeline;
        private final SimpleChannelId channelId = new SimpleChannelId();
        private EventLoop eventLoop;

        MockChannel() {
            this.channelPipeline = new DefaultChannelPipeline(this);
        }

        @Override
        public ChannelId id() {
            return channelId;
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
            return channelPipeline;
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

        public RecordingHandler(String name) {
            this.name = name;
        }


        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelRegistered");
            //继续将事件向下一个handler传递
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelUnregistered");
            ctx.fireChannelUnregistered();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelActive");
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            events.add(name + ":channelRead:" + msg);
            ctx.fireChannelRead(msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelReadComplete");
            ctx.fireChannelReadComplete();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            events.add(name + ":exceptionCaught:" + cause.getMessage());
            ctx.fireExceptionCaught(cause);
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
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            events.add(name + ":userEventTriggered:" + evt);
            ctx.fireUserEventTriggered(evt);
        }
    }

    private MockChannel channel;
    private ChannelPipeline pipeline;

    @BeforeEach
    void setup() {
        channel = new MockChannel();
        pipeline = new DefaultChannelPipeline(channel);
    }

    @DisplayName("channel绑定的pipeline 和 新建的pipeline是同一个对象吗？")
    @Test
    public void test(){
        Channel channel1 = pipeline.channel();
        //同一个对象;
        assertThat(channel1).isEqualTo(channel);

        ChannelPipeline pipeline1 = channel.pipeline();
        //并非同一个对象;
        assertThat(pipeline1).isEqualTo(pipeline);
    }

    @Nested
    @DisplayName("Pipeline 结构测试")
    class PipelineStructureTests {
        @Test
        @DisplayName("Pipeline 应关联到 Channel")
        void pipelineShouldBeAssociatedWithChannel() {
            assertThat(pipeline.channel()).isEqualTo(channel);
        }

        @Test
        @DisplayName("新创建的 Pipeline 应只有 Head 和 Tail")
        void newPipelineShouldOnlyHaveHeadAndTail() {
            assertThat(pipeline.names()).isEmpty();
        }

        @Test
        @DisplayName("addLast 应在尾部添加 Handler")
        void addLastShouldAddHandlerAtTail() {
            RecordingHandler handler1 = new RecordingHandler("handler1");
            RecordingHandler handler2 = new RecordingHandler("handler2");

            pipeline.addLast("handler1", handler1);
            pipeline.addLast("handler2", handler2);


            assertThat(pipeline.names()).containsExactly("handler1", "handler2");
            assertThat(pipeline.get("handler1")).isEqualTo(handler1);
        }

        @Test
        @DisplayName("addFirst 应在头部添加 Handler")
        void addFirstShouldAddHandlerAtHead() {
            RecordingHandler handler1 = new RecordingHandler("H1");
            RecordingHandler handler2 = new RecordingHandler("H2");

            pipeline.addFirst("handler1", handler1);
            pipeline.addFirst("handler2", handler2);

            assertThat(pipeline.names()).containsExactly("handler2", "handler1");
        }

        @Test
        @DisplayName("重复名称应抛出异常")
        void duplicateNameShouldThrowException() {
            RecordingHandler handler1 = new RecordingHandler("H1");
            RecordingHandler handler2 = new RecordingHandler("H1");

            //重复名称抛异常：使用了一个Map集合存储，然后key是name，containsKey()来判断的
            pipeline.addFirst("handler1", handler1);
            pipeline.addFirst("handler2", handler2);
        }
    }

    @Nested
    @DisplayName("Handler 管理测试")
    class HandlerManagementTests {

        @Test
        @DisplayName("get 应返回指定名称的 Handler")
        void getShouldReturnHandlerByName() {
            RecordingHandler handler1 = new RecordingHandler("H1");
            pipeline.addLast("h1", handler1);

            assertThat(pipeline.get("h1")).isEqualTo(handler1);
            System.out.println(pipeline.context("h1").handler().equals(handler1));
        }

        @Test
        @DisplayName("context 应返回指定名称的 Context")
        void contextShouldReturnContextByName() {
            RecordingHandler handler1 = new RecordingHandler("H1");
            pipeline.addLast("h1", handler1);

            ChannelHandlerContext h1 = pipeline.context("h1");
            String name = h1.name();
            assertThat(name).isEqualTo("h1");
        }

        @Test
        @DisplayName("context(ChannelHandler) 应返回 Handler 的 Context")
        void contextShouldReturnContextByHandler() {
            RecordingHandler h1 = new RecordingHandler("h1");
            pipeline.addFirst("h1", h1);

            ChannelHandlerContext ctx = pipeline.context(h1);
            assertThat(ctx.handler()).isEqualTo(h1);
        }

        @Test
        @DisplayName("remove(Handler) 应移除 Handler")
        void removeShouldRemoveHandler() {
            RecordingHandler h1 = new RecordingHandler("h1");
            pipeline.addFirst("h1", h1);

            ChannelHandler pre = pipeline.remove("h1");
            assertThat(pre).isEqualTo(h1);

            ChannelHandler h2 = pipeline.get("h1");
            assertThat(h2).isNull();
        }


        @Test
        @DisplayName("remove(name) 应返回被移除的 Handler")
        void removeByNameShouldReturnRemovedHandler() {
            RecordingHandler h1 = new RecordingHandler("h1");
            pipeline.addFirst("h1", h1);

            ChannelHandler pre = pipeline.remove("h1");
            assertThat(pre).isEqualTo(h1);
        }

        @Test
        @DisplayName("remove 不存在的 Handler 应抛出异常")
        void removeNonexistentHandlerShouldThrow() {
            RecordingHandler h1 = new RecordingHandler("h1");
            pipeline.addFirst("h1", h1);

            assertThatThrownBy(() -> {
                pipeline.remove("h2");
            }).isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("remove 不存在的名称应抛出异常")
        void removeNonexistentNameShouldThrow() {
            assertThatThrownBy(() -> pipeline.remove("nonexistent"))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("Handler 生命周期测试")
    class HandlerLifecycleTests {
        @Test
        @DisplayName("添加 Handler 时应调用 handlerAdded")
        void shouldCallHandlerAddedWhenAdding() {
            RecordingHandler handler = new RecordingHandler("h1");
            pipeline.addFirst("h1", handler);

            assertThat(handler.events).contains("h1:handlerAdd");
        }


        @Test
        @DisplayName("移除 Handler 时应调用 handlerRemoved")
        void shouldCallHandlerRemovedWhenRemoving() {
            RecordingHandler handler = new RecordingHandler("H");
            pipeline.addLast("handler", handler);
            handler.events.clear();

            pipeline.remove(handler);

            assertThat(handler.events).contains("H:handlerRemoved");
        }
    }

    @Nested
    @DisplayName("入站事件传递测试")
    class InboundEventPropagationTests {
        @Test
        @DisplayName("fireChannelRegistered 应按顺序传递事件")
        void fireChannelRegisteredShouldPropagate() {
            RecordingHandler handler1 = new RecordingHandler("h1");
            RecordingHandler handler2 = new RecordingHandler("h2");

            pipeline.addFirst("h1", handler1);
            pipeline.addFirst("h2", handler2);

            System.out.println(handler1.events.size());
            handler1.events.clear();
            handler2.events.clear();

            pipeline.fireChannelRegistered();

            System.out.println(handler1.events.size());
            System.out.println(handler2.events.size());

            assertThat(handler1.events).contains("h1:channelRegistered");
            assertThat(handler2.events).contains("h2:channelRegistered");
        }

        @Test
        @DisplayName("fireChannelActive 应按顺序传递事件")
        void fireChannelActiveShouldPropagate() {
            RecordingHandler handler1 = new RecordingHandler("h1");
            RecordingHandler handler2 = new RecordingHandler("h2");


            pipeline.addFirst("h1", handler1);
            pipeline.addFirst("h2", handler2);

            //暂时清空事件 避免影响判断
            handler1.events.clear();
            handler2.events.clear();

            pipeline.fireChannelActive();
            assertThat(handler1.events).contains("h1:channelActive");
            assertThat(handler2.events).contains("h2:channelActive");
        }


        @Test
        @DisplayName("fireChannelRead 应传递消息")
        void fireChannelReadShouldPropagateMessage() {
            RecordingHandler handler1 = new RecordingHandler("H1");
            RecordingHandler handler2 = new RecordingHandler("H2");
            pipeline.addLast("h1", handler1);
            pipeline.addLast("h2", handler2);
            handler1.events.clear();
            handler2.events.clear();

            pipeline.fireChannelRead("TestMessage");

            assertThat(handler1.events).contains("H1:channelRead:TestMessage");
            assertThat(handler2.events).contains("H2:channelRead:TestMessage");
        }

        @Test
        @DisplayName("fireExceptionCaught 应传递异常")
        void fireExceptionCaughtShouldPropagateException() {
            RecordingHandler handler1 = new RecordingHandler("H1");
            RecordingHandler handler2 = new RecordingHandler("H2");
            pipeline.addLast("h1", handler1);
            pipeline.addLast("h2", handler2);
            handler1.events.clear();
            handler2.events.clear();

            pipeline.fireExceptionCaught(new RuntimeException("TestError"));

            assertThat(handler1.events).contains("H1:exceptionCaught:TestError");
            assertThat(handler2.events).contains("H2:exceptionCaught:TestError");
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("完整的 Pipeline 操作场景")
        void completePipelineScenario() {
            //1.添加多个Handler
            RecordingHandler encoder = new RecordingHandler("encoder");
            RecordingHandler handler = new RecordingHandler("handler");
            RecordingHandler decoder = new RecordingHandler("decoder");

            pipeline.addFirst("encoder", encoder);
            pipeline.addFirst("handler", handler);
            pipeline.addFirst("decoder", decoder);

            assertThat(pipeline.names()).containsExactly("decoder", "handler", "encoder");

            encoder.events.clear();
            handler.events.clear();
            decoder.events.clear();

            //2.触发正确的事件并验证顺序
            pipeline.fireChannelActive();
            assertThat(encoder.events).contains("encoder:channelActive");
            assertThat(handler.events).contains("handler:channelActive");
            assertThat(decoder.events).contains("decoder:channelActive");

            pipeline.remove("handler");

            //3.继续触发对应的事件
            encoder.events.clear();
            handler.events.clear();
            decoder.events.clear();

            pipeline.fireChannelRead("haha");
            assertThat(encoder.events).contains("encoder:channelRead:haha");
            assertThat(decoder.events).contains("decoder:channelRead:haha");
        }

        @Test
        @DisplayName("动态添加和移除 Handler 场景")
        void dynamicHandlerManagement() {
            // 初始添加
            RecordingHandler h1 = new RecordingHandler("H1");
            pipeline.addLast("h1", h1);
            assertThat(pipeline.names()).containsExactly("h1");

            // 在头部添加
            RecordingHandler h0 = new RecordingHandler("H0");
            pipeline.addFirst("h0", h0);
            assertThat(pipeline.names()).containsExactly("h0", "h1");

            // 在尾部添加
            RecordingHandler h2 = new RecordingHandler("H2");
            pipeline.addLast("h2", h2);
            assertThat(pipeline.names()).containsExactly("h0", "h1", "h2");

            // 按名称移除
            pipeline.remove("h1");
            assertThat(pipeline.names()).containsExactly("h0", "h2");

            // 按 Handler 移除
            pipeline.remove(h0);
            assertThat(pipeline.names()).containsExactly("h2");

            // 清空
            pipeline.remove(h2);
            assertThat(pipeline.names()).isEmpty();
        }
    }
}
