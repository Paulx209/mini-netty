package com.getian.netty.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;

/**
 * Pipeline 集成测试
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-21
 */
@DisplayName("Pipeline 集成测试")
public class PipelineIntegrationTest {
    //1.定义channelId
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

    //2.定义channel
    private static class MockChannel implements Channel {
        private final ChannelPipeline pipeline;
        private final ChannelId id = new SimpleChannelId();

        public MockChannel() {
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

    //3.定义handler
    private static class RecordingDuplexHandler extends ChannelDuplexHandler {
        final List<String> events = new ArrayList<>();
        final String name;

        RecordingDuplexHandler(String name) {
            this.name = name;
        }


        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            events.add(name + ":handlerAdded");
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) {
            events.add(name + ":handlerRemoved");
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelActive");
            super.channelActive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            events.add(name + ":channelRead:" + msg);
            super.channelRead(ctx, msg);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            events.add(name + ":channelReadComplete");
            super.channelReadComplete(ctx);
        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            events.add(name + ":exceptionCaught:" + cause.getMessage());
            super.exceptionCaught(ctx, cause);
        }

        //出站事件

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
    }

    private MockChannel channel;
    private ChannelPipeline pipeline;

    @BeforeEach
    void setup() {
        channel = new MockChannel();
        pipeline = new DefaultChannelPipeline(channel);
    }


    @Nested
    @DisplayName("ChannelDuplexHandler 测试")
    class DuplexHandlerTests {
        @Test
        @DisplayName("DuplexHandler 应同时实现入站和出站接口")
        void duplexHandlerShouldImplementBothInterfaces() {
            RecordingDuplexHandler handler = new RecordingDuplexHandler("h1");
            //RecordingDuplexHandler 是 ChannelDuplexHandler的子类
            assertThat(handler).isInstanceOf(ChannelDuplexHandler.class);
            //RecordingDuplexHandler 是 ChannelInboundHandler接口的实现类
            assertThat(handler).isInstanceOf(ChannelInboundHandler.class);
            //RecordingDuplexHandler 是 ChannelOutboundHandler接口的实现类
            assertThat(handler).isInstanceOf(ChannelOutboundHandler.class);
        }

        @Test
        @DisplayName("DuplexHandler 应记录入站和出站事件")
        void duplexHandlerShouldRecordBothEvents() {
            //入站和出站也就是add 和 remove方法吧
            RecordingDuplexHandler handler1 = new RecordingDuplexHandler("h1");
            RecordingDuplexHandler handler2 = new RecordingDuplexHandler("h2");

            pipeline.addFirst("h1", handler1);
            pipeline.addFirst("h2", handler2);

            assertThat(handler1.events).contains("h1:handlerAdded");
            assertThat(handler2.events).contains("h2:handlerAdded");

            handler1.events.clear();
            handler2.events.clear();

            pipeline.fireChannelActive();
            pipeline.fireChannelRead("haha");

            assertThat(handler1.events).containsExactly("h1:channelActive", "h1:channelRead:haha");
        }

        @Test
        @DisplayName("DuplexHandler 可以在 Pipeline 中正常工作")
        void duplexHandlerShouldWorkInPipeline() {
            RecordingDuplexHandler h1 = new RecordingDuplexHandler("h1");
            pipeline.addLast("h1", h1);
            ChannelHandler handler = pipeline.get("h1");

            assertThat(handler).isInstanceOf(RecordingDuplexHandler.class);
            assertThat(handler).isEqualTo(h1);
        }
    }

    @Nested
    @DisplayName("异常传播测试")
    class ExceptionPropagationTests {
        @Test
        @DisplayName("异常应通过 exceptionCaught 传播")
        void exceptionShouldPropagateThroughExceptionCaught() {
            RecordingDuplexHandler h1 = new RecordingDuplexHandler("h1");
            RecordingDuplexHandler h2 = new RecordingDuplexHandler("h2");

            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);

            h1.events.clear();
            h2.events.clear();

            pipeline.fireExceptionCaught(new RuntimeException("error"));

            assertThat(h1.events).contains("h1:exceptionCaught:error");
            assertThat(h2.events).contains("h2:exceptionCaught:error");

        }

        @Test
        @DisplayName("异常可以在处理器中被拦截")
        void exceptionCanBeIntercepted() {
            List<String> errorMsg = new ArrayList<>();
            RecordingDuplexHandler interceptor = new RecordingDuplexHandler("h1") {
                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                    //不向下继续传播
                    errorMsg.add(cause.getMessage());
                }
            };
            RecordingDuplexHandler handler2 = new RecordingDuplexHandler("h2");

            pipeline.addLast("h1", interceptor);
            pipeline.addLast("h2", handler2);

            interceptor.events.clear();
            handler2.events.clear();

            //开始测试
            pipeline.fireExceptionCaught(new RuntimeException("error"));

            assertThat(errorMsg).contains("error");
            assertThat(handler2.events.size()).isEqualTo(0);

        }
    }

    @Nested
    @DisplayName("完整事件流测试")
    class FullEventFlowTests {
        @Test
        @DisplayName("典型的请求响应场景")
        void typicalRequestResponseScenario() {
            //日志Handler
            RecordingDuplexHandler loggingHandler = new RecordingDuplexHandler("logging");
            RecordingDuplexHandler businessHandler = new RecordingDuplexHandler("business") {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    //1.不向下传播 往前发送
                    String response = "response:over";
                    pipeline.addFirst("log", loggingHandler);
                    loggingHandler.events.clear();
                    ctx.write(response);
                }
            };
            pipeline.addLast("handler", businessHandler);

            loggingHandler.events.clear();
            businessHandler.events.clear();

            //触发事件 先是入站事件  head -> businessHandler(写事件 ) -> loggingHandler
            pipeline.fireChannelRead("haha");

            String msg = loggingHandler.events.get(0);
            assertThat(msg).isEqualTo("logging:write:response:over");
        }

        @Test
        @DisplayName("多个双向 Handler 的事件传递顺序")
        void multipleHandlersEventOrder() {
            RecordingDuplexHandler h1 = new RecordingDuplexHandler("H1");
            RecordingDuplexHandler h2 = new RecordingDuplexHandler("H2");
            RecordingDuplexHandler h3 = new RecordingDuplexHandler("H3");

            pipeline.addLast("h1", h1);
            pipeline.addLast("h2", h2);
            pipeline.addLast("h3", h3);

            h1.events.clear();
            h2.events.clear();
            h3.events.clear();

            // 触发入站事件
            pipeline.fireChannelRead("Message");

            // 入站事件顺序: H1 -> H2 -> H3
            assertThat(h1.events).contains("H1:channelRead:Message");
            assertThat(h2.events).contains("H2:channelRead:Message");
            assertThat(h3.events).contains("H3:channelRead:Message");
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("完整的 Echo 服务器 Handler 链")
        void completeEchoServerHandlerChain() {
            List<String> processMessages = new ArrayList<>();
            //日志Handler 双向的
            RecordingDuplexHandler loggingHandler = new RecordingDuplexHandler("log") {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    processMessages.add("LOG_IN:" + msg);
                    super.channelRead(ctx, msg);
                }

                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    processMessages.add("LOG_OUT:" + msg);
                    super.write(ctx, msg, promise);
                }
            };

            //业务handler
            ChannelInboundHandlerAdapter echoHandler = new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    processMessages.add("ECHO:" + msg);
                    ctx.write("ECHO:" + msg);
                }
            };

            pipeline.addLast("log", loggingHandler);
            pipeline.addLast("echo", echoHandler);


            pipeline.fireChannelRead("hah");


            assertThat(processMessages).containsExactly(
                    "LOG_IN:hah",
                    "ECHO:hah",
                    "LOG_OUT:ECHO:hah"
            );
        }

        @Test
        @DisplayName("异常处理链场景")
        void exceptionHandlingChainScenario() {
            List<String> errorLog = new ArrayList<>();

            // 可能抛出异常的 Handler
            ChannelInboundHandlerAdapter riskyHandler = new ChannelInboundHandlerAdapter() {
//                @Override
//                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//                    if ("error".equals(msg)) {
//                        throw new RuntimeException("Simulated error");
//                    }
//                    ctx.fireChannelRead(msg);
//                }

                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    errorLog.add("Risky caught: " + cause.getMessage());
                    ctx.fireExceptionCaught(cause);
                }
            };

            // 最终异常处理器
            ChannelInboundHandlerAdapter finalHandler = new ChannelInboundHandlerAdapter() {
                @Override
                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                    errorLog.add("Final caught: " + cause.getMessage());
                    // 不再传播
                }
            };

            pipeline.addLast("risky", riskyHandler);
            pipeline.addLast("final", finalHandler);

            // 触发异常
            pipeline.fireExceptionCaught(new RuntimeException("Test exception"));

            // 验证异常被正确处理
            assertThat(errorLog)
                    .contains("Risky caught: Test exception", "Final caught: Test exception");
        }
    }
}
