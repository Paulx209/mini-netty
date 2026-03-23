package com.getian.netty.handler.codec.string;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.buffer.HeapByteBuf;
import com.getian.netty.buffer.ReferenceCounted;
import com.getian.netty.buffer.UnpooledByteBufAllocator;
import com.getian.netty.channel.*;
import com.getian.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StringDecoder 和 StringEncoder 测试
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-23
 */

public class StringCodecTest {
    /**
     * 记录接收消息的 Handler
     */
    private static class MessageRecordingHandler extends ChannelInboundHandlerAdapter {
        final List<Object> messages = new ArrayList<>();
        Throwable lastException;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            messages.add(msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            lastException = cause;
        }
    }

    private static class OutboundRecordingHandler extends ChannelOutboundHandlerAdapter {
        final List<Object> messages = new ArrayList<>();

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            messages.add(msg);
            promise.setSuccess();
        }
    }


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
     * 模拟 Channel
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

    private MockChannel channel;
    private ChannelPipeline pipeline;
    private MessageRecordingHandler inboundRecorder;
    private OutboundRecordingHandler outboundRecorder;
    private UnpooledByteBufAllocator allocator;

    @BeforeEach
    void setup() {
        this.channel = new MockChannel();
        this.pipeline = new DefaultChannelPipeline(channel);
        inboundRecorder = new MessageRecordingHandler();
        outboundRecorder = new OutboundRecordingHandler();
        allocator = new UnpooledByteBufAllocator(false);
    }

    @Nested
    @DisplayName("StringDecoder Tests")
    class StringDecoderTests {
        /**
         * 说一下执行逻辑
         * 1.首先pipeline中添加了两个handler节点 一个是StringDecoder(负责将ByteBuf中的数据转换为String) 一个是recorder
         * 2.head节点传播事件到decoder上，然后执行channelRead，转换为string，然后传递下去
         * 3.然后recorder就收到该消息存储起来
         *
         * @throws Exception
         */
        @Test
        @DisplayName("编码支持从byteBuf 到 utf-8格式")
        void shouldDecodeWithUtf8() throws Exception {
            StringDecoder decoder = new StringDecoder();
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", inboundRecorder);

            ByteBuf input = allocator.heapBuffer(32, 32);
            input.writeBytes("hello,etge".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(input);

            assertThat(inboundRecorder.messages).hasSize(1);
            assertThat(inboundRecorder.messages.get(0)).isEqualTo("hello,etge");
        }

        @Test
        @DisplayName("构造函数传入指定的字符集 将ByteBuf转换为String")
        void shouldDecodeWithSpecifiedCharset() throws Exception {
            StringDecoder decoder = new StringDecoder(StandardCharsets.UTF_8);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", inboundRecorder);

            ByteBuf input = allocator.heapBuffer(32, 32);
            input.writeBytes("hello,etge".getBytes(StandardCharsets.UTF_8));
            pipeline.fireChannelRead(input);

            assertThat(inboundRecorder.messages).hasSize(1);
            assertThat(inboundRecorder.messages.get(0)).isEqualTo("hello,etge");
        }

        @Test
        @DisplayName("解码中文字符")
        void shouldDecodeChineseCharacters() throws Exception {
            StringDecoder decoder = new StringDecoder(StandardCharsets.UTF_8);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", inboundRecorder);

            ByteBuf buffer = allocator.heapBuffer(64, 64);
            buffer.writeBytes("葛恩涛".getBytes(StandardCharsets.UTF_8));

            ByteBuf buffer2 = allocator.heapBuffer(64, 64);
            buffer2.writeBytes("hahah".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(buffer);

            pipeline.fireChannelRead(buffer2);

            assertThat(inboundRecorder.messages.get(0)).isEqualTo("葛恩涛");
            assertThat(inboundRecorder.messages.get(1)).isEqualTo("hahah");
        }

        /**
         * decoder在channelRead()方法中处理完后会将buf.release()掉，所以这里的refCnt就为0了
         *
         * @throws Exception
         */
        @Test
        @DisplayName("Should release ByteBuf after decoding")
        void shouldReleaseByteBufAfterDecoding() throws Exception {
            StringDecoder decoder = new StringDecoder();
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", inboundRecorder);

            ByteBuf input = new HeapByteBuf(32, 32);
            input.writeBytes("Test".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(input);

            assertThat(input.refCnt()).isEqualTo(0);
        }

        /**
         * decoder对于非ByteBuf类型的消息，会走if的另一个分支，直接交给下一个handler进行处理
         *
         * @throws Exception
         */
        @Test
        @DisplayName("能处理非ByteBuf类型的消息")
        void shouldPassThroughNonByteBufMessages() throws Exception {
            StringDecoder decoder = new StringDecoder();
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", inboundRecorder);

            Integer number = 20;
            pipeline.fireChannelRead(number);

            assertThat(inboundRecorder.messages).hasSize(1);
            assertThat(inboundRecorder.messages.get(0)).isEqualTo(42);
        }

        /**
         * StringDecoder的构造函数中会有判空逻辑
         */
        @Test
        @DisplayName("什么都没传递时 直接抛出异常")
        void shouldRejectNullCharset() {
            assertThatThrownBy(() -> new StringDecoder(null))
                    .isInstanceOf(NullPointerException.class);
        }

        /**
         * 开始使用StringEncoder 进行编码 message -> byte
         */
        private static class TestableStringEncoder extends StringEncoder {
            public TestableStringEncoder() {
                super();
            }

            public TestableStringEncoder(Charset charset) {
                super(charset);
            }

            public void testEncode(CharSequence msg, ByteBuf out) throws Exception {
                encode(null, msg, out);
            }
        }

        @Nested
        @DisplayName("StringEncoder Tests")
        class StringEncoderTests {
            @Test
            @DisplayName("可以编码String类型的数据 ")
            void shouldEncodeWithUtf8() throws Exception {
                TestableStringEncoder encoder = new TestableStringEncoder();
                ByteBuf out = new HeapByteBuf(64, 64);
                //将msg中的内容 编码成byte 然后放到out中
                encoder.testEncode("hello", out);

                assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("hello");
            }

            @Test
            @DisplayName("可以编码中文数据")
            void shouldEncodeChineseCharacters() throws Exception {
                ByteBuf buf = allocator.heapBuffer(64, 64);
                TestableStringEncoder encoder = new TestableStringEncoder(StandardCharsets.UTF_8);
                encoder.testEncode("你好中国", buf);

                assertThat(buf.toString(StandardCharsets.UTF_8)).isEqualTo("你好中国");
            }

            @Test
            @DisplayName("可以处理空字符串")
            void shouldHandleEmptyString() throws Exception {
                ByteBuf buf = allocator.heapBuffer(64, 64);
                TestableStringEncoder encoder = new TestableStringEncoder(StandardCharsets.UTF_8);

                encoder.testEncode("", buf);

                System.out.println(buf.toString(StandardCharsets.UTF_8));
            }

            @Test
            @DisplayName("什么都不传 直接报错")
            void shouldRejectNullCharset() {
                assertThatThrownBy(() -> new StringEncoder(null))
                        .isInstanceOf(NullPointerException.class);
            }
        }

        @Nested
        class CombinedCodecTests {

            /**
             * 三个入站handler一起配合工作
             * 1.首先是frameDecoder 长度帧解析器 进行解析，然后将数据存储到byteBuf中
             * 2.然后是stringDecoder 将byteBuf中的字节数据进行解码 转换成string类型；不过buf的toString()方法也是解码的过程
             * 3.最后由recorder进行记录
             *
             * @throws Exception
             */
            @Test
            @DisplayName("和基于长度的帧解码器配合工作")
            void shouldWorkWithFrameDecoder() throws Exception {
                // 模拟完整的解码流程
                LengthFieldBasedFrameDecoder frameDecoder = new LengthFieldBasedFrameDecoder(
                        1024, 0, 2, 0, 2);

                StringDecoder stringDecoder = new StringDecoder();

                pipeline.addLast("frameDecoder", frameDecoder);
                pipeline.addLast("stringDecoder", stringDecoder);
                pipeline.addLast("recorder", inboundRecorder);

                // 发送带长度前缀的消息
                ByteBuf input = new HeapByteBuf(32, 32);
                input.writeShort(5);  // 长度
                input.writeBytes("Hello".getBytes(StandardCharsets.UTF_8));

                pipeline.fireChannelRead(input);//frameDecode -> stringDecode -> recorder

                assertThat(inboundRecorder.messages).hasSize(1);
                assertThat(inboundRecorder.messages.get(0)).isEqualTo("Hello");
            }

            @Test
            @DisplayName("使用帧解码器处理多条信息")
            void shouldHandleMultipleMessagesWithFrameDecoder() throws Exception {
                LengthFieldBasedFrameDecoder decoder
                        = new LengthFieldBasedFrameDecoder(1024, 0, 2, 0, 2);
                StringDecoder stringDecoder = new StringDecoder();
                pipeline.addLast("decoder", decoder);
                pipeline.addLast("stringDecoder", stringDecoder);
                pipeline.addLast("recorder", inboundRecorder);

                // 发送两个粘在一起的消息
                ByteBuf buf = allocator.heapBuffer(64, 64);
                buf.writeShort(5);
                buf.writeBytes("hello".getBytes(StandardCharsets.UTF_8));

                buf.writeShort(4);
                buf.writeBytes("etge".getBytes(StandardCharsets.UTF_8));

                pipeline.fireChannelRead(buf);

                //断言
                assertThat(inboundRecorder.messages).hasSize(2);
                assertThat(inboundRecorder.messages.get(0)).isEqualTo("hello");
                assertThat(inboundRecorder.messages.get(1)).isEqualTo("etge");
            }
        }

        @Nested
        @DisplayName("场景测试")
        class AcceptanceScenarioTests {
            @Test
            @DisplayName("聊天消息解码场景")
            void chatMessageDecodingScenario() throws Exception {
                // 模拟聊天消息解码
                LengthFieldBasedFrameDecoder frameDecoder = new LengthFieldBasedFrameDecoder(
                        1024, 0, 2, 0, 2);
                StringDecoder stringDecoder = new StringDecoder();

                pipeline.addLast("frameDecoder", frameDecoder);
                pipeline.addLast("stringDecoder", stringDecoder);
                pipeline.addLast("recorder", inboundRecorder);

                // 模拟收到的聊天消息
                String[] messages = {"Hello!", "How are you?", "I'm fine, thanks!"};
                ByteBuf input = new HeapByteBuf(256, 256);
                for (String msg : messages) {
                    byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                    input.writeShort(bytes.length);
                    input.writeBytes(bytes);
                }

                pipeline.fireChannelRead(input);


                assertThat(inboundRecorder.messages).hasSize(3);
                assertThat(inboundRecorder.messages.get(0)).isEqualTo("Hello!");
                assertThat(inboundRecorder.messages.get(1)).isEqualTo("How are you?");
                assertThat(inboundRecorder.messages.get(2)).isEqualTo("I'm fine, thanks!");
            }

            @Test
            @DisplayName("rpc请求的响应值解码")
            void rpcResponseDecodingScenario() throws Exception {
                LengthFieldBasedFrameDecoder frameDecoder = new LengthFieldBasedFrameDecoder(
                        1024, 4, 2, 0, 6);  // 跳过请求ID和长度
                StringDecoder stringDecoder = new StringDecoder();


                pipeline.addLast("frameDecoder", frameDecoder);
                pipeline.addLast("stringDecoder", stringDecoder);
                pipeline.addLast("recorder", inboundRecorder);

                // 发送RPC响应
                ByteBuf input = new HeapByteBuf(32, 32);
                input.writeInt(12345);  // 请求ID
                input.writeShort(7);    // 数据长度
                input.writeBytes("Success".getBytes(StandardCharsets.UTF_8));


                pipeline.fireChannelRead(input);

                assertThat(inboundRecorder.messages).hasSize(1);
                assertThat(inboundRecorder.messages.get(0)).isEqualTo("Success");
            }
        }
    }

}
