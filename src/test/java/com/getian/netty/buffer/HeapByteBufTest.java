package com.getian.netty.buffer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * HeapByteBuf 测试
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-21
 */

public class HeapByteBufTest {
    private HeapByteBuf buf;

    @BeforeEach
    void setup() {
        //初始容量为256 最大容量为1024
        buf = new HeapByteBuf(256, 1024);
    }

    @Nested
    @DisplayName("基础属性测试")
    class BasicPropertyTests {
        @Test
        @DisplayName("初始状态应正确")
        void initialStateShouldBeCorrect() {
            assertThat(buf.capacity()).isEqualTo(256);
            assertThat(buf.maxCapacity()).isEqualTo(1024);
            assertThat(buf.readerIndex).isZero();
            assertThat(buf.writerIndex).isZero();
            assertThat(buf.readableBytes()).isZero();
            assertThat(buf.writableBytes()).isEqualTo(256);
        }

        @Test
        @DisplayName("hasArray 应返回 true")
        void hasArrayShouldReturnTrue() {
            //是否有底层字节数组 HeapByteBuf中存在byte[]数组属性
            assertThat(buf.hasArray()).isTrue();
            //获取底层字节数组
            assertThat(buf.array()).isNotNull();
            //获取底层数组的偏移量
            assertThat(buf.arrayOffset()).isZero();
        }

        @Test
        @DisplayName("清除应重置索引")
        void clearShouldResetIndexes() {
            //写入一个整型并增加 writerIndex
            buf.writeInt(42);
            //顺序读取一个字节
            buf.readByte();

            System.out.println(buf.readerIndex);
            System.out.println(buf.writerIndex);

            //清除索引
            buf.clear();

            assertThat(buf.readerIndex).isZero();
            assertThat(buf.writerIndex).isZero();
        }
    }

    @Nested
    @DisplayName("读写索引测试")
    class IndexTests {
        @Test
        @DisplayName("写入应增加 writerIndex")
        void writeShouldIncreaseWriterIndex() {
            buf.writeByte(1);
            assertThat(buf.writerIndex).isEqualTo(1);
            assertThat(buf.readableBytes()).isEqualTo(1);

            buf.writeInt(22);
            assertThat(buf.writerIndex).isEqualTo(5);

            buf.writeLong(8);
            assertThat(buf.writerIndex).isEqualTo(13);
        }


        @Test
        @DisplayName("读取应增加 readerIndex")
        void readShouldIncreaseReaderIndex() {
            buf.writeByte(1);
            buf.writeInt(42);
            buf.writeLong(100L);

            assertThat(buf.writerIndex).isEqualTo(13);

            //开始读取
            byte b = buf.readByte();
            System.out.println((byte) b);


            assertThat(buf.readerIndex).isEqualTo(1);

            //读取int
            int i = buf.readInt();
            System.out.println(i);
            assertThat(buf.readerIndex).isEqualTo(5);

            //读取long
            long l = buf.readLong();
            System.out.println(l);
            assertThat(buf.readerIndex).isEqualTo(13);
        }

        @Test
        @DisplayName("标记和重置应正确工作")
        void markAndResetShouldWork() {
            buf.writeBytes("hello".getBytes(StandardCharsets.UTF_8));

            //标记当前读索引
            buf.markReaderIndex();

            buf.readBytes(new byte[5]);

            assertThat(buf.readerIndex).isEqualTo(5);

            //reset重新设置 就变成0了
            buf.resetReaderIndex();
            assertThat(buf.readerIndex).isZero();
        }

        @Test
        @DisplayName("设置索引应进行边界检查")
        void setIndexShouldCheckBounds() {
            buf.writeInt(42);

            assertThatThrownBy(() -> buf.readerIndex(-1))
                    .isInstanceOf(IndexOutOfBoundsException.class);

            assertThatThrownBy(() -> buf.readerIndex(10))
                    .isInstanceOf(IndexOutOfBoundsException.class);

            assertThatThrownBy(() -> buf.writerIndex(500))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Nested
    @DisplayName("顺序读写测试")
    class SequentialReadWriteTests {
        @Test
        @DisplayName("writeByte 和 readByte 应正确工作")
        void writeAndReadByteShouldWork() {
            buf.writeByte(127);
            buf.writeByte(-1);

            assertThat(buf.readByte()).isEqualTo((byte) 127);
            assertThat(buf.readByte()).isEqualTo((byte) -1);
        }


        @Test
        @DisplayName("writeShort 和 readShort 应正确工作")
        void writeAndReadShortShouldWork() {
            buf.writeShort(1);
            buf.writeShort(2);

            assertThat(buf.readShort()).isEqualTo((short) 1);
            assertThat(buf.readShort()).isEqualTo((short) 2);
        }

        @Test
        @DisplayName("writeInt 和 readInt 应正确工作")
        void writeAndReadIntShouldWork() {
            buf.writeInt(Integer.MAX_VALUE);
            buf.writeInt(Integer.MIN_VALUE);
            buf.writeInt(42);

            assertThat(buf.readInt()).isEqualTo(Integer.MAX_VALUE);
            assertThat(buf.readInt()).isEqualTo(Integer.MIN_VALUE);
            assertThat(buf.readInt()).isEqualTo(42);
        }

        @Test
        @DisplayName("writeLong 和 readLong 应正确工作")
        void writeAndReadLongShouldWork() {
            buf.writeLong(Long.MAX_VALUE);
            buf.writeLong(Long.MIN_VALUE);

            assertThat(buf.readLong()).isEqualTo(Long.MAX_VALUE);
            assertThat(buf.readLong()).isEqualTo(Long.MIN_VALUE);
        }

        @Test
        @DisplayName("writeBytes 和 readBytes 应正确工作")
        void writeAndReadBytesShouldWork() {
            byte[] data = "hello sonicge".getBytes(StandardCharsets.UTF_8);
            buf.writeBytes(data);

            byte[] res = new byte[data.length];
            //将buf中底层数组的数据 读取到res中，读取的长度为data.length;
            buf.readBytes(res);
            assertThat(res).isEqualTo(data);
        }

        @Test
        @DisplayName("skipBytes 应跳过指定字节数")
        void skipBytesShouldSkipBytes() {
            buf.writeBytes("hello,sonicge".getBytes(StandardCharsets.UTF_8));
            buf.skipBytes(6);
            byte[] res = new byte[7];
            //从当前的readIndex开始copy
            buf.readBytes(res);

            assertThat(new String(res, StandardCharsets.UTF_8)).isEqualTo("sonicge");
        }
    }

    @Nested
    @DisplayName("随机访问测试")
    class RandomAccessTests {
        @Test
        @DisplayName("getByte 和 setByte 不应改变索引")
        void getSetByteShouldNotChangeIndex() {
            buf.writerIndex(10);

            //setByte方法不会修改写指针的
            buf.setByte(5, 42);
            //getByte方法不会修改读指针的
            assertThat(buf.getByte(5)).isEqualTo((byte) 42);

            assertThat(buf.readerIndex()).isZero();
            assertThat(buf.writerIndex()).isEqualTo(10);
        }

        @Test
        @DisplayName("getInt 和 setInt 应正确工作")
        void getSetIntShouldWork() {
            buf.writerIndex(20);

            buf.setInt(4, 0x12345678);
            assertThat(buf.getInt(4)).isEqualTo(0x12345678);
        }

        @Test
        @DisplayName("getLong 和 setLong 应正确工作")
        void getSetLongShouldWork() {
            buf.writerIndex(20);

            buf.setLong(8, 0x123456789ABCDEF0L);
            assertThat(buf.getLong(8)).isEqualTo(0x123456789ABCDEF0L);

            System.out.println(buf.writerIndex);
        }

        @Test
        @DisplayName("getBytes 和 setBytes 应正确工作")
        void getSetBytesShouldWork() {
            buf.writerIndex(20);

            byte[] data = new byte[]{'1', '2', '3', '4'};

            buf.setBytes(5, data);

            byte[] res = new byte[4];
            buf.getBytes(5, res);

            assertThat(res).isEqualTo(data);
        }
    }

    @Nested
    @DisplayName("容量测试")
    class CapacityTests {
        @Test
        @DisplayName("容量应可以增加")
        void capacityShouldBeIncreasable() {
            assertThat(buf.capacity()).isEqualTo(256);

            buf.capacity(512);

            assertThat(buf.capacity()).isEqualTo(512);
        }

        @Test
        @DisplayName("容量应可以减少")
        void capacityShouldBeDecreasable() {
            assertThat(buf.capacity()).isEqualTo(256);

            buf.capacity(128);

            assertThat(buf.capacity()).isEqualTo(128);
        }

        @Test
        @DisplayName("扩容应保留数据")
        void capacityIncreaseShouldPreserveData() {
            buf.writeBytes("hello,etge".getBytes(StandardCharsets.UTF_8));
            assertThat(buf.capacity()).isEqualTo(256);

            buf.capacity(512);

            byte[] data = new byte[10];
            buf.readBytes(data);

            assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("hello,etge");
        }

        @Test
        @DisplayName("自动扩容应在写入时触发")
        void autoExpandShouldTriggerOnWrite() {
            HeapByteBuf smallBuf = new HeapByteBuf(8, 256);

            for (int i = 0; i < 20; i++) {
                smallBuf.writeByte((byte) i);
            }
            assertThat(smallBuf.capacity()).isGreaterThan(8);
            assertThat(smallBuf.writerIndex).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("NIO 转换测试")
    class NioConversionTests {
        @Test
        @DisplayName("nioBuffer 应返回正确的 ByteBuffer")
        void nioBufferShouldReturnCorrectByteBuffer() {
            buf.writeBytes("hello,sonicge".getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = buf.nioBuffer();

            byte[] data = new byte[13];
            buffer.get(data);

            System.out.println(new String(data, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("nioBuffer 带参数应返回指定范围")
        void nioBufferWithRangeShouldWork() {
            buf.writeBytes("hello,sonicge".getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = buf.nioBuffer(6, 7);

            byte[] data = new byte[7];
            buffer.get(data);

            System.out.println(new String(data, StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("字符串转换测试")
    class StringConversionTests {
        @Test
        @DisplayName("toString(Charset) 应返回可读内容")
        void toStringWithCharsetShouldReturnReadableContent() {
            buf.writeBytes("Hello, World!".getBytes(StandardCharsets.UTF_8));
            String res2 = buf.toString(StandardCharsets.UTF_8);
            System.out.println(res2);
            assertThat(res2).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("toString 带范围参数应返回指定内容")
        void toStringWithRangeShouldWork() {
            buf.writeBytes("hello,sonicge".getBytes(StandardCharsets.UTF_8));
            String res = buf.toString(6, 7, StandardCharsets.UTF_8);
            assertThat(res).isEqualTo("sonicge");
        }
    }

    @Nested
    @DisplayName("引用计数测试")
    class ReferenceCountTests {
        @Test
        @DisplayName("初始引用计数应为 1")
        void initialRefCntShouldBeOne() {
            assertThat(buf.refCnt()).isEqualTo(1);
        }


        @Test
        @DisplayName("retain 应增加引用计数")
        void retainShouldIncreaseRefCnt() {
            buf.retain();
            assertThat(buf.refCnt()).isEqualTo(2);

            buf.retain(3);
            assertThat(buf.refCnt()).isEqualTo(5);
        }

        @Test
        @DisplayName("release 应减少引用计数")
        void releaseShouldDecreaseRefCnt() {
            buf.retain(2);//3
            assertThat(buf.refCnt()).isEqualTo(3);

            boolean release = buf.release();
            //资源未被释放掉 所以为false
            assertThat(release).isFalse();

            assertThat(buf.refCnt()).isEqualTo(2);
        }

        @Test
        @DisplayName("引用计数为 0 时应释放资源")
        void shouldDeallocateWhenRefCntReachesZero() {
            buf.retain(3);
            assertThat(buf.refCnt()).isEqualTo(4);

            boolean release = buf.release(4);
            assertThat(release).isTrue();

            System.out.println(buf.refCnt());
        }
    }

    @Nested
    @DisplayName("discardReadBytes 测试")
    class DiscardReadBytesTests {
        @Test
        @DisplayName("discardReadBytes 应压缩缓冲区")
        void discardReadBytesShouldCompactBuffer() {
            buf.writeBytes("hello,sonicge".getBytes(StandardCharsets.UTF_8));
            buf.skipBytes(6);

            assertThat(buf.readerIndex()).isEqualTo(6);

            buf.discardReadBytes();

            assertThat(buf.readerIndex).isEqualTo(0);
            assertThat(buf.writerIndex).isEqualTo(7);

            byte[] data = new byte[7];
            buf.readBytes(data);
            System.out.println(new String(data, StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {
        @Test
        @DisplayName("典型的网络消息处理场景")
        void typicalNetworkMessageScenario() {
            String message = "hello,netty";
            buf.writeByte((byte) 11);
            buf.writeBytes(message.getBytes(StandardCharsets.UTF_8));

            //读取
            byte length = buf.readByte();
            System.out.println("长度为:" + length);
            byte[] data = new byte[11];
            buf.readBytes(data);
            String response = new String(data, StandardCharsets.UTF_8);
            System.out.println(response);
            assertThat(response).isEqualTo(message);
        }
    }
}
