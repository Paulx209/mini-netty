package com.getian.netty.handler.codec;

import com.getian.netty.buffer.ByteBuf;
import com.getian.netty.buffer.HeapByteBuf;
import com.getian.netty.buffer.UnpooledByteBufAllocator;
import com.getian.netty.channel.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ByteToMessageDecoder测试
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-22
 */

@DisplayName("ByteToMessageDecoder 测试")
public class ByteToMessageCoderTest {

    /**
     * 整数解码器 - 每次读取 4 字节
     */
    private static class IntegerDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (in.readableBytes() >= 4) {
                //只要>4，就说明可以读取一次数据，然后将读取的数据添加到list集合中
                out.add(in.readInt());
            }
        }
    }

    /**
     * 行解码器 -以换行符分割
     */
    private static class LineCoder extends ByteToMessageDecoder {

        /**
         * 找到换行符的下标之后 然后从readIndex -> 换行符的数据读取出来，添加到list集合中
         *
         * @param ctx 上下文
         * @param in  输入缓冲区
         * @param out 解码后的消息列表
         * @throws Exception
         */
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            int index = findNewLine(in);
            if (index != -1) {
                int start = in.readerIndex();
                //不包括换行符 3-1 -> [1,2]
                byte[] line = new byte[index - start];
                in.readBytes(line);
                in.skipBytes(1); // 跳过换行符
                out.add(new String(line));
            }
        }

        /**
         * 寻找下一个换行符在哪里
         *
         * @param in
         * @return
         */
        private int findNewLine(ByteBuf in) {
            int start = in.readerIndex();
            int end = in.writerIndex();
            for (int i = start; i < end; i++) {
                if (in.getByte(i) == '\n') {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * 长度前缀解析器 [长度][对应长度的数据]
     */
    private static class LengthDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (in.readableBytes() < 4) {
                return; //等待补齐字段
            }
            in.markReaderIndex(); //这里标记 主要是为了后面如果数据不够的话 能reset

            int length = in.readInt();
            //数据不够length长度 避免读的不对
            if (in.readableBytes() < length) {
                in.resetReaderIndex();
                return;
            }
            //数据足够 直接读
            byte[] data = new byte[length];
            in.readBytes(data);
            out.add(new String(data));
        }
    }

    /**
     * 记录接收消息的 Handler
     */
    private static class RecordingHandler extends ChannelInboundHandlerAdapter {
        final List<Object> messages = new ArrayList<>();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            messages.add(msg);
            //不向下传递吗？
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

    private RecordingHandler recorder;

    private UnpooledByteBufAllocator allocator;

    @BeforeEach
    void setup() {
        channel = new MockChannel();
        pipeline = new DefaultChannelPipeline(channel);
        recorder = new RecordingHandler();
        allocator = UnpooledByteBufAllocator.DEFAULT;
    }


    @Nested
    @DisplayName("基础解码测试")
    class BasicDecodingTests {
        /**
         * 整个的执行流程是这样的：
         * 1.首先pipeline触发读取事件，将buf传入进去，然后由第一个节点执行，第一个是头结点，handler类型非入站类型，所以找到下一个handler，也就是decoder类型
         * 2.然后执行channelRead()方法，由于IntegerDecoder继承自ByteToMessageDecoder类，这个channelRead()方法的逻辑是这样的
         * 2.1 首先积累内容到缓冲区，会判断当前旧的缓冲区空间是否足够，如果足够的话，就进行积累；不够的话，就进行库容
         * 2.2 然后调用子类的decode()方法，这里的decode()方法就是四个字节解码一次，解码完毕之后将内容放到list集合中
         * 2.3 然后调用handler.fireChannelRead()方法，继续向下传递
         * 3.传递到recorder节点的时候，会将msg添加到events集合中，然后我们断言的时候就能从该集合中发现
         */
        @Test
        @DisplayName("应解码完整的整数消息")
        void shouldDecodeCompleteIntegerMessage() {
            //往pipeline中添加handler节点 包括解码的节点
            pipeline.addLast("decoder", new IntegerDecoder());
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = allocator.heapBuffer(16);
            buf.writeInt(42);

            //触发事件
            pipeline.fireChannelRead(buf);

            //断言
            assertThat(recorder.messages).containsExactly(42);
        }

        /**
         * 如果是传递多个整数消息的话，执行的逻辑是这样的：
         * 1.首先bytebuf中写入的话，writeIndex - readIndex 每次都是有值的（因为数据还没有读取完毕）
         * 2.所以while循环可以一直执行下去，然后每次读取四个字节，每读取一次就传播一次
         * 3.由pipeline.refireChannelRead(buf)直接触发的这次read，decoder执行完毕之后不会向下传播了
         * 4.recorder执行是因为，decoder解码的过程中，将消息传播到下一个了，所以才会继续执行，非执行完毕之后的向下传播
         */
        @Test
        @DisplayName("应解码多个整数消息")
        void shouldDecodeMultipleIntegerMessages() {
            pipeline.addLast("decoder", new IntegerDecoder());
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = allocator.buffer(256, 512);
            buf.writeInt(20);
            buf.writeInt(90);
            buf.writeInt(209);

            //触发事件
            pipeline.fireChannelRead(buf);

            assertThat(recorder.messages).containsExactly(20, 90, 209);
        }

        /**
         * 1.decode()函数执行的过程中，如果不够4字节的话，不会进行解析的，因此返回的list集合就是empty的。
         * 2.然后，由于字节数不够，readInt()操作也不会执行，所以readIndex也不会变化
         */
        @Test
        @DisplayName("数据不足时应等待更多数据")
        void shouldWaitForMoreDataIfIncomplete() {
            pipeline.addLast("decoder", new IntegerDecoder());
            pipeline.addLast("recoder", recorder);

            ByteBuf buf = allocator.buffer(64, 128);
            //只发送 2 字节，不足 4 字节  0x1234为十六进制
            buf.writeShort(0x1234);

            pipeline.fireChannelRead(buf);

            assertThat(recorder.messages).isEmpty();
        }
    }

    @Nested
    @DisplayName("粘包拆包测试")
    class PacketSplitMergeTests {
        @Test
        @DisplayName("应处理粘包（多条消息合并）")
        void shouldHandlePacketMerging() {
            pipeline.addLast("decoder", new IntegerDecoder());
            pipeline.addLast("recorder", recorder);

            // 一次发送包含两条消息的数据
            ByteBuf buf = new HeapByteBuf(16, 64);
            buf.writeInt(100);
            buf.writeInt(200);

            pipeline.fireChannelRead(buf);

            assertThat(recorder.messages).containsExactly(100, 200);
        }

        /**
         * 1.由于ByteToMessageDecoder的cumulation是累计的，会记录第一次读取到的内容
         * 2.当第二次fireChannelRead()的时候，发现cumulation不为null，所以就会进行数据融合（会判断数据是否扩容）
         * 3.不需要扩容，然后就将数据拼接到一起，这下就够4字节了，所以就可以被decode()解析掉了
         * 4.解析掉之后，将message添加到list中，然后fireChannelRead()方法，向下传递。
         */
        @Test
        @DisplayName("应处理拆包（一条消息分多次发送）")
        void shouldHandlePacketSplitting() {
            pipeline.addLast("decoder", new IntegerDecoder());
            pipeline.addLast("recorder", recorder);

            ByteBuf buf1 = allocator.buffer(16, 64);
            buf1.writeShort(0x0000);
            pipeline.fireChannelRead(buf1);

            //只有两个字节，所有没有完整消息
            assertThat(recorder.messages).isEmpty();

            ByteBuf buf2 = allocator.buffer(16, 64);
            buf2.writeShort(0x002A);
            pipeline.fireChannelRead(buf2);

            //现在应该有一条消息
            assertThat(recorder.messages).hasSize(1);
        }
    }

    @Nested
    @DisplayName("行解码器测试")
    class LineDecoderTests {

        @Test
        @DisplayName("应解码单行消息")
        void shouldDecodeSingleLine() {
            pipeline.addLast("decoder", new LineCoder());
            pipeline.addLast("recoder", recorder);

            ByteBuf buf = allocator.buffer(64, 128);
            buf.writeBytes("Hello\n".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(buf);

            assertThat(recorder.messages).containsExactly("Hello");
        }

        @Test
        @DisplayName("应解码多行消息")
        void shouldDecodeMultipleLines() {
            pipeline.addLast("decoder", new LineCoder());
            pipeline.addLast("recoder", recorder);

            ByteBuf buf = allocator.buffer(64, 128);
            buf.writeBytes("Hello\nWorld\n".getBytes(StandardCharsets.UTF_8));

            pipeline.fireChannelRead(buf);

            assertThat(recorder.messages).containsExactly("Hello", "World");

        }

        @Test
        @DisplayName("不完整行应等待换行符")
        void shouldWaitForNewline() {
            pipeline.addLast("decoder", new LineCoder());
            pipeline.addLast("recorder", recorder);

            ByteBuf buf = new HeapByteBuf(32, 128);
            buf.writeBytes("Incomplete".getBytes());

            pipeline.fireChannelRead(buf);

            assertThat(recorder.messages).isEmpty();
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("典型的网络消息解码场景")
        void typicalNetworkMessageDecodingScenario() {
            LengthDecoder lengthDecoder = new LengthDecoder();
            pipeline.addLast("decoder", lengthDecoder);
            pipeline.addLast("recoder", recorder);

            ByteBuf buffer = allocator.buffer(16, 64);
            buffer.writeInt(5);
            buffer.writeBytes("hello".getBytes(StandardCharsets.UTF_8));

            //触发对应的时间
            pipeline.fireChannelRead(buffer);

            //断言
            System.out.println(recorder.messages.get(0));
            assertThat(recorder.messages).containsExactly("hello");
        }

        @Test
        @DisplayName("混合粘包拆包场景")
        void mixedPacketScenario() {
            pipeline.addLast("decoder", new IntegerDecoder());
            pipeline.addLast("recorder", recorder);

            // 第一批：1.5 条消息
            ByteBuf batch1 = new HeapByteBuf(16, 64);
            batch1.writeInt(1);
            batch1.writeShort(0); // 第二条消息的前半部分
            pipeline.fireChannelRead(batch1);

            assertThat(recorder.messages).containsExactly(1);

            // 第二批：0.5 + 2 条消息
            ByteBuf batch2 = new HeapByteBuf(16, 64);
            batch2.writeShort(2); // 第二条消息的后半部分 (值为2)
            batch2.writeInt(3);
            batch2.writeInt(4);
            pipeline.fireChannelRead(batch2);

            assertThat(recorder.messages).containsExactly(1, 2, 3, 4);
        }
    }


}
