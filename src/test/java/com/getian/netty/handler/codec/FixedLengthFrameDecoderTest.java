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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FixedLengthFrameDecoder 测试
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-22
 */


public class FixedLengthFrameDecoderTest {
    private static class FrameRecordingHandler extends ChannelInboundHandlerAdapter {
        final List<ByteBuf> frames = new ArrayList<>();
        final List<String> frameStrings = new ArrayList<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HeapByteBuf) {
                ByteBuf frame = (ByteBuf) msg;
                frames.add(frame);
                frameStrings.add(frame.toString(StandardCharsets.UTF_8));
            }
        }

        void releaseAll() {
            for (ByteBuf frame : frames) {
                //release掉之后 底层的byte数组就为null了
                frame.release();
            }
        }
    }

    private static class SimpleChannelId implements ChannelId {
        private static int counter = 0;
        private final int id = ++counter;

        @Override
        public String asShortText() {
            return "ch-" + counter;
        }

        @Override
        public String asLongText() {
            return "channel-" + counter;
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

    private MockChannel channel;
    private ChannelPipeline pipeline;
    private FrameRecordingHandler recorder;
    private UnpooledByteBufAllocator allocator;

    @BeforeEach
    void setup() {
        channel = new MockChannel();
        pipeline = new DefaultChannelPipeline(channel);
        recorder = new FrameRecordingHandler();
        allocator = new UnpooledByteBufAllocator(false);
    }

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("应接受正数帧长度")
        void shouldAcceptPositiveFrameLength() {
            FixedLengthFrameDecoder decoder = new FixedLengthFrameDecoder(8);
            assertThat(decoder.getFrameLength()).isEqualTo(8);
        }

        @Test
        @DisplayName("应拒绝零或负数帧长度")
        void shouldRejectZeroOrNegativeFrameLength() {
            assertThatThrownBy(() -> new FixedLengthFrameDecoder(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("基本解码测试")
    class BasicDecodingTests {
        /**
         * 整个的流程是这样的：
         * 1.由pipeline主动触发，然后将byteBuf作为数据传进去
         * 2.首先经过head头结点，handler非入站；然后找下一个，找到decoder，然后执行ChannelRead()方法,该方法中首先对积累缓冲区进行判断，如果积累的话，拼接，没有积累的话，赋值。
         * 3.赋值完毕之后，开始解析字节，解析字节的时遇到decode()方法，就会找到具体实现的子类，然后走它的逻辑
         * 4.走完之火获取到数据，要传送给下一个入站处理器，也就是这里我们的recorder，会收集所有的byteBuf
         * 5.然后最后断言判断即可
         */
        @Test
        @DisplayName("应解码单个完整帧")
        void shouldDecodeSingleCompleteFrame() {
            FixedLengthFrameDecoder decoder = new FixedLengthFrameDecoder(4);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            //触发
            ByteBuf buf = allocator.heapBuffer(16, 64);
            buf.writeBytes("1234".getBytes(StandardCharsets.UTF_8));
            pipeline.fireChannelRead(buf);

            //最后验证
            assertThat(recorder.frames.size()).isEqualTo(1);
            assertThat(recorder.frameStrings.get(0)).isEqualTo("1234");
        }

        /**
         * 1.这里补充一点，就是我们是在decoder里面的decode方法中做的while循环，一次while读取一帧，知道读取完毕
         * 2.每次将读取的结果存储到list集合中，然后该方法返回之后，会遍历list集合，每遍历一次，都会向下传播一个事件
         * 3.所以我们recorder handler中就会收集四个byteBuf
         */
        @Test
        @DisplayName("应解码多个完整帧")
        void shouldDecodeMultipleCompleteFrames() {
            FixedLengthFrameDecoder decoder = new FixedLengthFrameDecoder(4);

            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            //准备数据
            ByteBuf buf = allocator.heapBuffer(16, 64);
            buf.writeBytes("hello,sonicge,ha".getBytes(StandardCharsets.UTF_8));

            //触发事件
            pipeline.fireChannelRead(buf);

            //断言
            assertThat(recorder.frames.size()).isEqualTo(4);
            assertThat(recorder.frameStrings.get(0)).isEqualTo("hell");
        }

        @Test
        @DisplayName("不完整帧应等待更多数据")
        void shouldWaitForMoreDataIfIncomplete() {
            FixedLengthFrameDecoder decoder = new FixedLengthFrameDecoder(4);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = allocator.heapBuffer(16, 64);
            buf.writeBytes("hello".getBytes(StandardCharsets.UTF_8));

            //触发
            pipeline.fireChannelRead(buf);
            //验证
            assertThat(recorder.frames.size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("粘包拆包测试")
    class PacketSplitMergeTests {
        @Test
        @DisplayName("应处理跨包的帧")
        void shouldHandleFrameAcrossPackets() {
            pipeline.addLast("decoder", new FixedLengthFrameDecoder(8));
            pipeline.addLast("recorder", recorder);

            // 第一批：4 字节
            ByteBuf part1 = new HeapByteBuf(8, 64);
            part1.writeBytes("ABCD".getBytes(StandardCharsets.UTF_8));
            pipeline.fireChannelRead(part1);

            assertThat(recorder.frames).isEmpty();

            // 第二批：4 字节，凑成完整帧
            ByteBuf part2 = new HeapByteBuf(8, 64);
            part2.writeBytes("EFGH".getBytes(StandardCharsets.UTF_8));
            pipeline.fireChannelRead(part2);

            try {
                assertThat(recorder.frameStrings).containsExactly("ABCDEFGH");
            } finally {
                recorder.releaseAll();
            }
        }

        @Test
        @DisplayName("应处理一批数据包含多个帧和部分帧")
        void shouldHandleMultipleFramesAndPartial() {
            FixedLengthFrameDecoder decoder = new FixedLengthFrameDecoder(4);
            pipeline.addLast("decoder", decoder);
            pipeline.addLast("recorder", recorder);

            //准备数据
            ByteBuf buf = allocator.heapBuffer(16, 64);
            buf.writeBytes("ABCDEFGH12".getBytes(StandardCharsets.UTF_8));

            //触发事件
            pipeline.fireChannelRead(buf);

            assertThat(recorder.frames.size()).isEqualTo(2);
            recorder.frameStrings.get(1).equals("EDGH");
            recorder.releaseAll();
            //继续写一点数据

            ByteBuf buf2 = allocator.heapBuffer(16, 64);
            buf2.writeBytes("34".getBytes(StandardCharsets.UTF_8));


            //触发事件
            pipeline.fireChannelRead(buf2);

            //断言
            assertThat(recorder.frames.size()).isEqualTo(3);
            System.out.println(recorder.frameStrings.get(0));
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("传感器数据解码场景")
        void sensorDataDecodingScenario() {
            pipeline.addLast("decoder", new FixedLengthFrameDecoder(8));
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = allocator.heapBuffer(24, 64);
            buf.writeInt(25);//温度值
            buf.writeInt(1000);//时间戳

            buf.writeInt(30);//温度值
            buf.writeInt(2000);//时间戳

            pipeline.fireChannelRead(buf);

            assertThat(recorder.frames.get(0).readInt()).isEqualTo(25);
            assertThat(recorder.frames.get(0).readInt()).isEqualTo(1000);


            assertThat(recorder.frames.get(1).readInt()).isEqualTo(30);
            assertThat(recorder.frames.get(1).readInt()).isEqualTo(2000);
        }

        @Test
        @DisplayName("固定长度命令协议场景")
        void fixedLengthCommandProtocolScenario() {
            // 命令格式：4 字节命令码
            pipeline.addLast("decoder", new FixedLengthFrameDecoder(4));
            pipeline.addLast("recorder", recorder);

            ByteBuf commands = new HeapByteBuf(20, 64);
            commands.writeBytes("PING".getBytes(StandardCharsets.UTF_8));
            commands.writeBytes("QUIT".getBytes(StandardCharsets.UTF_8));
            commands.writeBytes("STAT".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(commands);

            try {
                assertThat(recorder.frameStrings)
                        .containsExactly("PING", "QUIT", "STAT");
            } finally {
                recorder.releaseAll();
            }
        }
    }
}
