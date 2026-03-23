package com.getian.netty.handler.codec;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.buffer.HeapByteBuf;
import com.getian.netty.buffer.UnpooledByteBufAllocator;
import com.getian.netty.channel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * LengthFieldBasedFrameDecoder 测试
 * 这节有点小难 吗的
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-23
 */
@DisplayName("LengthFieldBasedFrameDecoder 测试")
public class LengthFieldBasedFrameDecoderTest {
    /**
     * 自定义channelId
     */
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
            return o.asLongText().compareTo(asLongText());
        }
    }

    /**
     * 模拟 Channel
     */
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

    private static class FrameRecordingHandler extends ChannelInboundHandlerAdapter {
        final List<ByteBuf> frames = new ArrayList<>();
        final List<String> frameStrings = new ArrayList<>();
        Throwable lastException;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            //1.如果类型的ByteBuf的话
            if (msg instanceof ByteBuf) {
                ByteBuf buf = (ByteBuf) msg;
                frames.add(buf);
                frameStrings.add(buf.toString(StandardCharsets.UTF_8));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            lastException = cause;
        }

        void releaseAll() {
            for (ByteBuf frame : frames) {
                if (frame.refCnt() > 0) {
                    frame.release();
                }
            }
        }
    }

    private MockChannel channel;
    private ChannelPipeline pipeline;
    private FrameRecordingHandler recorder;
    UnpooledByteBufAllocator allocator;

    @BeforeEach
    void setup() {
        channel = new MockChannel();
        pipeline = new DefaultChannelPipeline(channel);
        recorder = new FrameRecordingHandler();
        allocator = new UnpooledByteBufAllocator(false);
    }

    @Nested
    @DisplayName("构造参数校验")
    class ConstructorTests {
        @Test
        @DisplayName("构造函数必须有效")
        void shouldCreateWithValidParameters() {
            LengthFieldBasedFrameDecoder decoder =
                    new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4);

            assertThat(decoder.getMaxFrameLength()).isEqualTo(1024);
            assertThat(decoder.getLengthFieldLength()).isEqualTo(4);
            assertThat(decoder.getLengthFieldOffset()).isEqualTo(0);
            assertThat(decoder.getLengthAdjustment()).isEqualTo(0);
            assertThat(decoder.getInitialBytesToStrip()).isEqualTo(4);
        }


        @Test
        @DisplayName("Should reject invalid lengthFieldLength")
        void shouldRejectInvalidLengthFieldLength() {
            assertThatThrownBy(() -> new LengthFieldBasedFrameDecoder(1024, 0, 5, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lengthFieldLength");
        }


        @Test
        @DisplayName("Should accept valid lengthFieldLength values")
        void shouldAcceptValidLengthFieldLengths() {
            int[] validLengths = {1, 2, 3, 4, 8};
            for (int length : validLengths) {
                LengthFieldBasedFrameDecoder decoder = new LengthFieldBasedFrameDecoder(1024, 0, length, 0, 0);
                assertThat(decoder.getLengthFieldLength()).isEqualTo(length);
            }
        }
    }

    @Nested
    @DisplayName("基础功能测试")
    class BasicDecodingTests {
        /**
         * 具体的逻辑 & 流程
         * 1. pipeline.fireChannelRead(buf); 事件到达decoder -> channelRead() -> callDecode() -> decode() -> decodeFrame()
         * 2. decodeFrame方法中首先判断当前的可读数据的长度 是否足够 表示字段长度的大小; 如果够的话话，根据字段表示的字节获取到对应的长度值
         * 3. 然后再去计算真正帧的大小（包括协议头 + 字段长度 + 真实数据） ，然后再去跳过前面字节，到达真实数据，将这部分的内容读取出来，将byteBuffer返回出去
         * 4. 相当于解码结束了，然后将结果添加到out集合中，回到callDecode()方法后，遍历out集合，将消息传递给下一个handler处理
         *
         * @throws Exception
         */
        @Test
        @DisplayName("解码具有2字节长度字段的帧")
        void shouldDecodeWith2ByteLength() throws Exception {
            LengthFieldBasedFrameDecoder decoder =
                    new LengthFieldBasedFrameDecoder(1024, 0, 2, 0, 2);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            //准备数据
            ByteBuf buf = allocator.heapBuffer(64, 1024);
            buf.writeShort(5);
            buf.writeBytes("hello".getBytes(StandardCharsets.UTF_8));

            //触发事件
            pipeline.fireChannelRead(buf);

            //断言
            assertThat(recorder.frames.size()).isEqualTo(1);
        }

        /**
         * 具体流程 & 逻辑：
         * 和上面唯一不同的地方就是：通过字节内容获取字节长度的时候，一个处理的是2字节的，一个处理的是4字节的。
         *
         * @throws Exception
         */
        @Test
        @DisplayName("解码具有4字节长度字段的帧")
        void shouldDecodeWith4ByteLength() throws Exception {
            LengthFieldBasedFrameDecoder decoder =
                    new LengthFieldBasedFrameDecoder(512, 0, 4, 0, 4);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = allocator.heapBuffer(64, 128);
            buf.writeInt(10);
            buf.writeBytes("hello,etge".getBytes(StandardCharsets.UTF_8));

            //触发
            pipeline.fireChannelRead(buf);

            assertThat(recorder.frames).hasSize(1);
            assertThat(recorder.frameStrings.get(0)).isEqualTo("hello,etge");
        }

        /**
         * 第一次触发的时候，计算下来的完整的数据帧长度为12，但是byteBuf的中可读的数据<12，所以直接return null
         * 但是积累缓冲区cumulation还存在，第二次就会继续累加
         *
         * @throws Exception
         */
        @Test
        @DisplayName("数据不完整时 应该等待")
        void shouldWaitForMoreData() throws Exception {
            LengthFieldBasedFrameDecoder decoder =
                    new LengthFieldBasedFrameDecoder(1024, 0, 2, 0, 2);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            // 只发送长度字段，数据不足
            ByteBuf buf1 = new HeapByteBuf(8, 8);
            buf1.writeShort(10);
            buf1.writeBytes("Hello".getBytes());  // 只有5字节，需要10字节

            pipeline.fireChannelRead(buf1);
            assertThat(recorder.frames).isEmpty();

            // 发送剩余数据
            ByteBuf buf2 = new HeapByteBuf(8, 8);
            buf2.writeBytes("World".getBytes());  // 剩余5字节

            pipeline.fireChannelRead(buf2);
            assertThat(recorder.frames).hasSize(1);
        }
    }

    @Nested
    @DisplayName("偏正调整测试")
    class LengthAdjustmentTests {
        /**
         * 补充：
         * 从长度字段的字节解析出来的长度是包含长度的字节数的，下面在计算总长度的时候，因为通用公式还会再加一次长度进来
         * 所以adjustment就是为了将多加的这一次长度减掉
         *
         * @throws Exception
         */
        @Test
        @DisplayName("处理包含请求头的长度")
        void shouldHandleLengthIncludingHeader() throws Exception {
            // 消息格式: [长度=7][5字节数据] 其中长度包含2字节长度字段  adjustment =-2
            LengthFieldBasedFrameDecoder decoder =
                    new LengthFieldBasedFrameDecoder(512, 0, 2, -2, 2);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = allocator.heapBuffer(512, 1024);
            buf.writeShort(7);
            buf.writeBytes("hello".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(buf);

            //断言
            assertThat(recorder.frames).hasSize(1);
            System.out.println(recorder.frameStrings.get(0));
        }

        /**
         * 协议占的字节数 + 长度字段字节数 = lengthFieldEndOffset
         *
         * @throws Exception
         */
        @Test
        @DisplayName("处理头部偏移的byteBuf")
        void shouldHandleHeaderOffset() throws Exception {
            //2头部 + 2长度
            LengthFieldBasedFrameDecoder decoder =
                    new LengthFieldBasedFrameDecoder(1024, 2, 2, 0, 4);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = allocator.heapBuffer(512, 512);
            buf.writeShort(0xCAFE);  // 魔数
            buf.writeShort(10);
            buf.writeBytes("hello,etge".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(buf);

            //断言
            assertThat(recorder.frames).hasSize(1);
            assertThat(recorder.frameStrings.get(0)).isEqualTo("hello,etge");
        }
    }

    @Nested
    class MultipleFramesTests {
        /**
         * 执行的流程与逻辑
         * 1.首先一次性的写入2+2  2+5   -> 11个字节
         * 2.Decoder类中的callDecode()方法有while循环处理cumulation缓存积累区，每次只根据第一个数字去解析帧，然后while循环继续解析第二帧
         * 3.逐帧解析
         */
        @Test
        @DisplayName("解码一个数据包中的多个帧")
        void shouldDecodeMultipleFrames() throws Exception {
            LengthFieldBasedFrameDecoder decoder = new LengthFieldBasedFrameDecoder(
                    1024, 0, 2, 0, 2);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            ByteBuf input = new HeapByteBuf(32, 32);
            input.writeShort(2);
            input.writeBytes("Hi".getBytes());
            input.writeShort(5);
            input.writeBytes("World".getBytes());

            pipeline.fireChannelRead(input);
            assertThat(recorder.frames).hasSize(2);

            ByteBuf frame1 = recorder.frames.get(0);
            byte[] data1 = new byte[frame1.readableBytes()];
            frame1.readBytes(data1);
            assertThat(new String(data1)).isEqualTo("Hi");


            ByteBuf frame2 = recorder.frames.get(1);
            byte[] data2 = new byte[frame2.readableBytes()];
            frame2.readBytes(data2);
            assertThat(new String(data2)).isEqualTo("World");
        }

        @Test
        @DisplayName("正确解决拆包问题 -> 一条完整消息被分成多次网络接收")
        void shouldHandleSplitPackets() throws Exception {
            LengthFieldBasedFrameDecoder decoder =
                    new LengthFieldBasedFrameDecoder(1024, 0, 2, 0, 2);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = allocator.heapBuffer(64, 64);
            buf.writeByte(0);
            //第一次发送数据
            pipeline.fireChannelRead(buf);

            //0 长度都没有

            assertThat(recorder.frames).hasSize(0);

            ByteBuf buf2 = allocator.buffer(64, 64);
            buf2.writeByte(5);
            buf2.writeBytes("hel".getBytes(StandardCharsets.UTF_8));


            //第二次发送数据
            pipeline.fireChannelRead(buf2);
            //[05] [hel]  长度有了  数据不够

            assertThat(recorder.frames).hasSize(0);

            ByteBuf buf3 = allocator.buffer(64, 64);
            buf3.writeBytes("lo".getBytes(StandardCharsets.UTF_8));

            //第三次发送数据
            pipeline.fireChannelRead(buf3);
            //[05][hel][lo] 长度有了 数据够了
            assertThat(recorder.frames).hasSize(1);
        }
    }

    @Nested
    class ErrorHandlingTests {
        @Test
        @DisplayName("长度字段声明的帧长度超过了解码器允许的最大值，解码器应该直接报错，")
        void shouldThrowOnFrameExceedingMax() throws Exception {
            LengthFieldBasedFrameDecoder decoder =
                    new LengthFieldBasedFrameDecoder(100, 0, 2, 0, 2);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = allocator.heapBuffer(16, 16);
            buf.writeShort(200);
            buf.writeBytes("haha".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(buf);

            assertThat(recorder.lastException)
                    .isInstanceOf(DecoderException.class)
                    .hasMessageContaining("exceeds maximum");

        }
    }

    @Nested
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("协议消息解码：消息头 + 长度 + 数据")
        void protocolMessageDecoding() throws Exception {
            LengthFieldBasedFrameDecoder decoder = new LengthFieldBasedFrameDecoder(
                    1024, 1, 2, 0, 0);  // 保留整个帧
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            ByteBuf input = new HeapByteBuf(16, 16);
            input.writeByte(1);     // 类型
            input.writeShort(4);    // 数据长度
            input.writeBytes("user".getBytes());

            pipeline.fireChannelRead(input);

            assertThat(recorder.frames).hasSize(1);
            ByteBuf frame = recorder.frames.get(0);
            assertThat(frame.readableBytes()).isEqualTo(7);  // 1 + 2 + 4
            assertThat(frame.readByte()).isEqualTo((byte) 1);  // 类型
            assertThat(frame.readShort()).isEqualTo((short) 4);  // 长度
        }

        @Test
        @DisplayName("带请求id的rpc消息解码")
        void rpcMessageDecoding() throws Exception {
            // RPC 协议: [4字节请求ID][4字节长度][N字节数据]
            LengthFieldBasedFrameDecoder decoder =
                    new LengthFieldBasedFrameDecoder(1024, 4, 4, 0, 8);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            ByteBuf input = allocator.heapBuffer(512,512);
            input.writeInt(99998);//消息id
            input.writeInt(3);
            input.writeBytes("rpc".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(input);

            assertThat(recorder.frames).hasSize(1);
            assertThat(recorder.frameStrings.get(0)).isEqualTo("rpc");
        }
    }}
