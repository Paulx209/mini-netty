package com.getian.netty.buffer;

/**
 * 先尝试从池里拿堆内 ByteBuf，拿不到时再退回非池化分配。
 */
public class PooledByteBufAllocator implements ByteBufAllocator {
    public static final PooledByteBufAllocator DEFAULT = new PooledByteBufAllocator(false);

    private static final int DEFAULT_INITIAL_CAPACITY = 256;
    private static final int DEFAULT_MAX_CAPACITY = Integer.MAX_VALUE;

    private final boolean preferDirect;
    private final HeapByteBufPool heapPool;
    private final UnpooledByteBufAllocator fallbackAllocator;

    public PooledByteBufAllocator(boolean preferDirect) {
        this(preferDirect, new HeapByteBufPool());
    }

    PooledByteBufAllocator(boolean preferDirect, HeapByteBufPool heapPool) {
        this.preferDirect = preferDirect;
        this.heapPool = heapPool;
        this.fallbackAllocator = new UnpooledByteBufAllocator(preferDirect);
    }

    @Override
    public ByteBuf buffer() {
        return buffer(DEFAULT_INITIAL_CAPACITY);
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
        return buffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
        if (preferDirect) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        return heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf heapBuffer() {
        return heapBuffer(DEFAULT_INITIAL_CAPACITY);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
        return heapBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
        // 这里是池化分配入口：优先复用旧对象，避免频繁 new byte[]。
        PooledHeapByteBuf pooled = heapPool.acquire(initialCapacity, maxCapacity);
        if (pooled != null) {
            return pooled;
        }
        // 超出池化范围或桶里无对象时，退回普通分配逻辑。
        return fallbackAllocator.heapBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public ByteBuf directBuffer() {
        return directBuffer(DEFAULT_INITIAL_CAPACITY);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
        return directBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
        // 当前实现只池化 heap buffer，direct buffer 仍走原来的 fallback 路径。
        return fallbackAllocator.directBuffer(initialCapacity, maxCapacity);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }
}
