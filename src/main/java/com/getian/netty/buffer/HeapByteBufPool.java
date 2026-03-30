package com.getian.netty.buffer;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

final class HeapByteBufPool {
    static final int MIN_POOLED_CAPACITY = 64; //归一化最小容量 64kb
    static final int MAX_POOLED_CAPACITY = 4 * 1024 * 1024; //归一化最大容量 4mb

    private static final int MIN_SHIFT = 6; //64字节为2^6
    private static final int BUCKET_COUNT = 17;// 64 - > 4mb 一共有17个
    private static final int DEFAULT_MAX_ENTRIES_PER_BUCKET = 32; //每一个桶最多有32个ByteBuf

    private final ConcurrentLinkedQueue<PooledHeapByteBuf>[] buckets;
    private final AtomicInteger[] bucketSizes;
    private final int maxEntriesPerBucket;

    @SuppressWarnings("unchecked")
    HeapByteBufPool() {
        this(DEFAULT_MAX_ENTRIES_PER_BUCKET);
    }

    @SuppressWarnings("unchecked")
    HeapByteBufPool(int maxEntriesPerBucket) {
        if (maxEntriesPerBucket <= 0) {
            throw new IllegalArgumentException("maxEntriesPerBucket: " + maxEntriesPerBucket + " (expected: > 0)");
        }
        this.maxEntriesPerBucket = maxEntriesPerBucket;
        this.buckets = new ConcurrentLinkedQueue[BUCKET_COUNT];
        this.bucketSizes = new AtomicInteger[BUCKET_COUNT];
        for (int i = 0; i < BUCKET_COUNT; i++) {
            buckets[i] = new ConcurrentLinkedQueue<>();
            bucketSizes[i] = new AtomicInteger();
        }
    }

    /**
     * 从池中申请一个可用的 PooledHeapByteBuf。
     *
     * @param initialCapacity 调用方本次至少需要的初始容量，例如预计要写 200 字节时通常会传 200
     * @param maxCapacity 这个 ByteBuf 后续允许扩容到的最大容量，不只是本次借出时的数组长度
     * @return 如果命中池则返回复用对象；如果桶里没有则返回新对象；如果该容量不适合池化则返回 null，让外层走非池化分配
     */
    PooledHeapByteBuf acquire(int initialCapacity, int maxCapacity) {
        // 第一步：先校验调用方传入的容量参数是否合法。
        validateRequest(initialCapacity, maxCapacity);
        // 第二步：把请求容量归一化到某个桶容量，例如 200 -> 256。
        int normalizedCapacity = normalizeCapacity(initialCapacity);
        // 第三步：如果这个容量不在池化范围内，就直接交给外层走非池化逻辑。
        if (!isPoolableCapacity(normalizedCapacity)) {
            return null;
        }
        // 第四步：maxCapacity 不能比“归一化后的初始容量”还小，否则连初始数组都放不下。
        if (maxCapacity < normalizedCapacity) {
            throw new IllegalArgumentException(String.format(
                    "maxCapacity: %d (expected: >= normalizedInitialCapacity(%d))",
                    maxCapacity, normalizedCapacity));
        }

        // 第五步：根据归一化后的容量，计算应该去哪个桶里找对象。
        int bucketIndex = bucketIndex(normalizedCapacity);
        // 第六步：从对应桶里取出一个空闲对象；如果桶里没有则会返回 null。
        PooledHeapByteBuf pooled = buckets[bucketIndex].poll();
        if (pooled != null) {
            // 第七步：取走一个对象后，该桶中的缓存计数要减一。
            bucketSizes[bucketIndex].decrementAndGet();
            // 第八步：把旧对象重置成“刚借出去”的状态，例如 refCnt、读写指针、maxCapacity。
            pooled.activate(maxCapacity);
            // 第九步：把这个已经重置好的对象返回给上层使用。
            return pooled;
        }
        // 第十步：如果桶里没有可复用对象，就创建一个新的池化 ByteBuf。
        return new PooledHeapByteBuf(this, normalizedCapacity, maxCapacity);
    }

    void recycle(PooledHeapByteBuf buf, int bucketCapacity) {
        // 第一步：先判断当前容量还能不能放回池里。
        if (!isPoolableCapacity(bucketCapacity)) {
            // 第二步：如果当前容量已经不属于任何合法桶，就退化成普通释放，交给 GC。
            buf.dropForGc();
            return;
        }

        // 第三步：根据当前容量算出应该回收到哪个桶。
        int bucketIndex = bucketIndex(bucketCapacity);
        // 第四步：先增加桶的计数，用来判断该桶是否已满。
        int newSize = bucketSizes[bucketIndex].incrementAndGet();
        // 第五步：如果桶已经满了，就不再缓存这个对象。
        if (newSize > maxEntriesPerBucket) {
            // 第六步：因为最终没有成功回桶，所以要把刚才加上的计数减回去。
            bucketSizes[bucketIndex].decrementAndGet();
            // 第七步：直接丢弃对象，避免池无限增长。
            buf.dropForGc();
            return;
        }
        // 第八步：桶未满时，把对象放回对应队列，等待下一次 acquire() 复用。
        buckets[bucketIndex].offer(buf);
    }

    int normalizeCapacity(int requestedCapacity) {
        if (requestedCapacity < 0) {
            throw new IllegalArgumentException("requestedCapacity: " + requestedCapacity + " (expected: >= 0)");
        }
        if (requestedCapacity <= MIN_POOLED_CAPACITY) {
            return MIN_POOLED_CAPACITY;
        }
        if (requestedCapacity > MAX_POOLED_CAPACITY) {
            // 超过池化上限时不再归一化，后续会走非池化路径。
            return requestedCapacity;
        }

        // 例如 200 -> 256，300 -> 512，这样相近容量的请求才能复用同一个桶里的对象。
        int normalized = MIN_POOLED_CAPACITY;
        while (normalized < requestedCapacity) {
            normalized <<= 1;
        }
        return normalized;
    }

    /**
     * 判断当前的容量是否符合规则，capacity & capacity -1 不为0的话，说明不是2的幂次方
     * @param capacity 归一化之后的capacity
     * @return
     */
    boolean isPoolableCapacity(int capacity) {
        return capacity >= MIN_POOLED_CAPACITY
                && capacity <= MAX_POOLED_CAPACITY
                && (capacity & (capacity - 1)) == 0;
    }

    /**
     * 计算bucketIndex的下标：由于最小的容量为64 -> 2^6
     * numberOfTrailingZeros方法会判断出normalizedCapacity二进制中后面有几个0，有几个0说明是2的几次方
     * 8 - 6 = 2 -> 2^8对应的就是数组中下标为2的
     * @param normalizedCapacity
     * @return
     */
    private int bucketIndex(int normalizedCapacity) {
        return Integer.numberOfTrailingZeros(normalizedCapacity) - MIN_SHIFT;
    }

    private static void validateRequest(int initialCapacity, int maxCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity + " (expected: >= 0)");
        }
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity: %d (expected: <= maxCapacity(%d))",
                    initialCapacity, maxCapacity));
        }
    }
}
