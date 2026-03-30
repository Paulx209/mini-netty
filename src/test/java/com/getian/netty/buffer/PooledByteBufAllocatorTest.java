package com.getian.netty.buffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DisplayName("PooledByteBufAllocator 测试")
class PooledByteBufAllocatorTest {

    @Test
    @DisplayName("池化缓冲区复用对象并重置状态")
    void pooledBufferShouldReuseInstanceAndResetState() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false, new HeapByteBufPool(1));

        ByteBuf first = allocator.heapBuffer(200, 1024);
        first.writeInt(42);
        first.readByte();
        first.markReaderIndex();
        first.markWriterIndex();
        first.release();

        ByteBuf second = allocator.heapBuffer(200, 2048);

        try {
            assertThat(second).isSameAs(first);
            assertThat(second).isInstanceOf(PooledHeapByteBuf.class);
            assertThat(second.capacity()).isEqualTo(256);
            assertThat(second.maxCapacity()).isEqualTo(2048);
            assertThat(second.readerIndex()).isZero();
            assertThat(second.writerIndex()).isZero();
            assertThat(second.refCnt()).isEqualTo(1);
        } finally {
            second.release();
        }
    }

    @Test
    @DisplayName("preferDirect=true 时 buffer 走非池化 direct 路径")
    void bufferShouldUseDirectFallbackWhenPreferDirectIsTrue() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(true, new HeapByteBufPool(1));

        ByteBuf first = allocator.buffer(128, 1024);
        ByteBuf second = allocator.buffer(128, 1024);

        try {
            assertThat(first).isNotSameAs(second);
            assertThat(first).isNotInstanceOf(PooledHeapByteBuf.class);
            assertThat(second).isNotInstanceOf(PooledHeapByteBuf.class);
        } finally {
            first.release();
            second.release();
        }
    }

    @Test
    @DisplayName("超出池化上限的请求回退为非池化")
    void oversizedBufferShouldFallbackToUnpooled() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false, new HeapByteBufPool(1));

        ByteBuf buf = allocator.heapBuffer(HeapByteBufPool.MAX_POOLED_CAPACITY + 1, Integer.MAX_VALUE);

        try {
            assertThat(buf).isNotInstanceOf(PooledHeapByteBuf.class);
            assertThat(buf.capacity()).isEqualTo(HeapByteBufPool.MAX_POOLED_CAPACITY + 1);
        } finally {
            buf.release();
        }
    }

    @Test
    @DisplayName("maxCapacity 小于归一化初始容量时快速失败")
    void invalidNormalizedCapacityShouldFailFast() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false, new HeapByteBufPool(1));

        assertThatThrownBy(() -> allocator.heapBuffer(65, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("扩容后按新的桶容量回收复用")
    void expandedBufferShouldRecycleIntoExpandedBucket() {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false, new HeapByteBufPool(1));

        ByteBuf first = allocator.heapBuffer(64, 1024);
        first.capacity(300);
        first.release();

        ByteBuf second = allocator.heapBuffer(300, 1024);

        try {
            assertThat(second).isSameAs(first);
            assertThat(second.capacity()).isEqualTo(512);
            assertThat(second.maxCapacity()).isEqualTo(1024);
        } finally {
            second.release();
        }
    }

    @Test
    @DisplayName("最终 release 只回收一次")
    void finalReleaseShouldRecycleOnceUnderConcurrency() throws InterruptedException {
        PooledByteBufAllocator allocator = new PooledByteBufAllocator(false, new HeapByteBufPool(1));
        ByteBuf buf = allocator.heapBuffer(64, 1024);
        buf.retain();

        AtomicInteger successCount = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    if (buf.release()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);

        ByteBuf reused = allocator.heapBuffer(64, 1024);
        try {
            assertThat(reused).isSameAs(buf);
        } finally {
            reused.release();
        }
    }
}
