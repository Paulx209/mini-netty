package com.getian.netty.buffer;

/**
 * 非池化 ByteBuf 分配器
 * 每次调用都创建新的 ByteBuf 实例，不复用已释放的缓冲区。
 * 1.每次创建新的ByteBuf对象
 * 2.释放后由GC内存回收
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-22
 */

public class UnpooledByteBufAllocator implements ByteBufAllocator {
    public static final UnpooledByteBufAllocator DEFAULT = new UnpooledByteBufAllocator(false);

    /**
     * 默认初始容量
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 256;

    /**
     * 默认最大容量
     */
    private static final int DEFAULT_MAX_CAPACITY = Integer.MAX_VALUE;

    //是否优先使用直接内存
    private final boolean preDirect;

    public UnpooledByteBufAllocator(boolean preferDirect) {
        this.preDirect = preferDirect;
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
        //直接内存
        if (preDirect) {
            return directBuffer(initialCapacity, maxCapacity);
        }
        //堆内存
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
        return new HeapByteBuf(initialCapacity, maxCapacity);
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
        // 简化实现：暂时使用堆内存代替直接内存
        // 完整实现应返回 DirectByteBuf
        return new HeapByteBuf(initialCapacity, maxCapacity);
    }

    @Override
    public boolean isDirectBufferPooled() {
        return false;
    }
}
