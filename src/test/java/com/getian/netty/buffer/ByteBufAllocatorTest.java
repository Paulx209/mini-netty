package com.getian.netty.buffer;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ByteBuf 分配器测试
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-22
 */

public class ByteBufAllocatorTest {
    @Nested
    @DisplayName("UnpooledByteBufAllocator 测试")
    class UnpooledAllocatorTests {
        //创建一个allocator DEFAULT -> 不使用直接内存
        private final ByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT;

        @Test
        @DisplayName("buffer() 应分配默认大小的 ByteBuf")
        void bufferShouldAllocateDefaultSize() {
            //buffer -> 初始容量为默认的 256
            ByteBuf buffer = allocator.buffer();
            try {
                assertThat(buffer).isNotNull();
                assertThat(buffer.capacity()).isEqualTo(256);
                assertThat(buffer.refCnt()).isEqualTo(1);
            } finally {
                //释放资源
                buffer.release();
            }
        }

        @Test
        @DisplayName("buffer(initialCapacity) 应分配指定容量")
        void bufferWithCapacityShouldWork() {
            ByteBuf buf = allocator.buffer(512);
            try {
                assertThat(buf).isNotNull();
                assertThat(buf.refCnt()).isEqualTo(1);
                assertThat(buf.capacity()).isEqualTo(512);
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("buffer(initialCapacity, maxCapacity) 应设置最大容量")
        void bufferWithMaxCapacityShouldWork() {
            ByteBuf buf = allocator.buffer(512, 1024);
            try {
                assertThat(buf).isNotNull();
                assertThat(buf.refCnt()).isEqualTo(1);
                assertThat(buf.capacity()).isEqualTo(512);
                assertThat(buf.maxCapacity()).isEqualTo(1024);
            } finally {
                buf.release();
            }
        }


        @Test
        @DisplayName("heapBuffer() 应分配堆内存 ByteBuf")
        void heapBufferShouldAllocateHeapMemory() {
            ByteBuf buf = allocator.heapBuffer();
            try {
                assertThat(buf).isInstanceOf(ByteBuf.class);
                assertThat(buf.hasArray()).isTrue();
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("heapBuffer 可以读写数据")
        void heapBufferShouldBeReadWritable() {
            ByteBuf buf = allocator.heapBuffer();
            try {
                assertThat(buf).isInstanceOf(ByteBuf.class);
                assertThat(buf.hasArray()).isTrue();

                //1.写操作
                buf.writeBytes("hello,world".getBytes(StandardCharsets.UTF_8));

                assertThat(buf.writerIndex()).isEqualTo(11);
                assertThat(buf.readableBytes()).isEqualTo(11);

                //2.读操作
                //读操作如果写到前面的话 readerIndex指针会移动的 所以readable就会发生变化
                byte[] data = new byte[11];
                buf.readBytes(data);
                System.out.println(new String(data, StandardCharsets.UTF_8));
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("directBuffer 应可用（简化实现使用堆内存）")
        void directBufferShouldBeAvailable() {
            ByteBuf buf = allocator.directBuffer();
            try {
                assertThat(buf).isNotNull();
                buf.writeInt(42);
                assertThat(buf.readInt()).isEqualTo(42);
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("isDirectBufferPooled 应返回 false")
        void isDirectBufferPooledShouldReturnFalse() {
            boolean directBufferPooled = allocator.isDirectBufferPooled();
            assertThat(directBufferPooled).isFalse();
        }
    }

    @Nested
    @DisplayName("分配器配置测试")
    class AllocatorConfigTests {
        @Test
        @DisplayName("preferDirect=false 时 buffer() 应返回堆内存")
        void bufferShouldReturnHeapWhenPreferDirectIsFalse() {
            UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(false);
            ByteBuf buf = allocator.buffer();
            try {
                assertThat(buf.array()).isNotNull();
                assertThat(buf).isInstanceOf(HeapByteBuf.class);
            } finally {
                buf.release();
            }
        }

        @Test
        @DisplayName("preferDirect=true 时 buffer() 应返回直接内存（简化实现）")
        void bufferShouldReturnDirectWhenPreferDirectIsTrue() {
            UnpooledByteBufAllocator allocator = new UnpooledByteBufAllocator(true);
            ByteBuf buf = allocator.buffer();
            try {
                assertThat(buf).isNotNull();
            } finally {
                buf.release();
            }
        }
    }

    @Nested
    @DisplayName("验收场景测试")
    class AcceptanceScenarioTests {

        @Test
        @DisplayName("典型的消息处理场景")
        void typicalMessageProcessingScenario() {
            UnpooledByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT;

            //1.分配ByteBuf写入请求
            ByteBuf buf = allocator.buffer(256);
            try {
                String requestStr = "http request";
                buf.writeBytes(requestStr.getBytes(StandardCharsets.UTF_8));

                //读取数据
                byte[] data = new byte[requestStr.length()];
                buf.readBytes(data);
                String response = new String(data, StandardCharsets.UTF_8);
                System.out.println(response);
                assertThat(response).isEqualTo(requestStr);
            } finally {
                buf.release();
            }

            //2.分配 buffer 写入响应
            ByteBuf responseBuf = allocator.buffer(512);
            try {
                String response = "HTTP/1.1 200 OK\r\nContent-Length: 13\r\n\r\nHello, World!";
                responseBuf.writeBytes(response.getBytes(StandardCharsets.UTF_8));
                assertThat(responseBuf.readableBytes()).isEqualTo(response.length());
            } finally {
                responseBuf.release();
            }
        }

        @Test
        @DisplayName("多个 ByteBuf 的独立生命周期")
        void multipleBuffersIndependentLifecycle() {
            UnpooledByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT;
            ByteBuf buf1 = allocator.buffer(64);
            ByteBuf buf2 = allocator.buffer(128);
            ByteBuf buf3 = allocator.buffer(256);

            try {
                assertThat(buf1.refCnt()).isEqualTo(1);
                assertThat(buf2.refCnt()).isEqualTo(1);
                assertThat(buf3.refCnt()).isEqualTo(1);
            } finally {
                buf1.release();
                buf2.release();
                buf3.release();
            }
            assertThat(buf1.refCnt()).isZero();
            assertThat(buf2.refCnt()).isZero();
            assertThat(buf3.refCnt()).isZero();
        }

        @Test
        @DisplayName("buffer 扩容场景")
        void bufferExpansionScenario() {
            UnpooledByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT;
            ByteBuf buf = allocator.buffer(16, 1024);

            try {
                for (int i = 0; i < 10; i++) {
                    buf.writeInt(i);
                    System.out.println(buf.writerIndex());
                }
                System.out.println(buf.writerIndex());
                System.out.println(buf.capacity());
                assertThat(buf.capacity()).isGreaterThan(16);
            } finally {
                buf.release();
            }

        }
    }
}
