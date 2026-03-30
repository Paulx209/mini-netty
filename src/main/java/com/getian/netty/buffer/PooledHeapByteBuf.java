package com.getian.netty.buffer;

/**
 * 可以回收到 HeapByteBufPool 的堆内缓冲区。
 */
final class PooledHeapByteBuf extends HeapByteBuf {
    private final HeapByteBufPool pool;
    private int bucketCapacity;
    private boolean recyclable;

    PooledHeapByteBuf(HeapByteBufPool pool, int bucketCapacity, int maxCapacity) {
        super(bucketCapacity, maxCapacity);
        this.pool = pool;
        this.bucketCapacity = bucketCapacity;
        this.recyclable = true;
    }

    void activate(int maxCapacity) {
        // 第一步：复用原来的 byte[]，不重新申请数组内存。
        super.reuse(array, maxCapacity);
        // 第二步：把引用计数重置为 1，表示这次重新借出去后只有一个持有者。
        resetRefCnt();
        // 第三步：根据当前数组容量，刷新这个对象后续是否还能回池的状态。
        updateRecycleState(array.length);
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        int actualCapacity = newCapacity;
        if (newCapacity <= HeapByteBufPool.MAX_POOLED_CAPACITY) {
            // 池化对象扩容时尽量对齐到桶容量，方便后续按桶回收复用。
            actualCapacity = pool.normalizeCapacity(newCapacity);
        }
        super.capacity(actualCapacity);
        updateRecycleState(array.length);
        return this;
    }

    @Override
    protected void deallocate() {
        // 第一步：先清空 readerIndex 和 writerIndex，避免下次复用时带着旧状态。
        resetIndexes();
        // 第二步：再清空 markReaderIndex 和 markWriterIndex。
        resetMarkers();
        // 第三步：如果当前容量已经不适合回池，就走普通释放逻辑。
        if (!recyclable) {
            // 第四步：普通释放会把底层数组引用置空，后续交给 GC。
            super.deallocate();
            return;
        }
        // 第五步：如果仍然适合池化，就把对象本身交回池里等待复用。
        pool.recycle(this, bucketCapacity);
    }

    void dropForGc() {
        super.deallocate();
    }

    private void updateRecycleState(int currentCapacity) {
        // 扩容后容量可能已经跳出池化区间，所以这里要重新计算是否还能回池。
        recyclable = pool.isPoolableCapacity(currentCapacity);
        bucketCapacity = recyclable ? currentCapacity : 0;
    }
}
