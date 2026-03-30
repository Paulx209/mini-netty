package com.getian.netty.buffer;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 带原子引用计数的 ByteBuf 基类。
 */
public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {
    // 通过 AtomicIntegerFieldUpdater 在无锁场景下修改 refCnt。
    public static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> REF_CNT_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");

    // 新创建或重新激活的 ByteBuf 默认只有一个持有者，所以初始值是 1。
    private volatile int refCnt = 1;

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    public int refCnt() {
        return refCnt;
    }

    protected void setRefCnt(int refCnt) {
        REF_CNT_UPDATER.set(this, refCnt);
    }

    protected final void resetRefCnt() {
        setRefCnt(1);
    }

    @Override
    public ReferenceCounted retain() {
        return retain(1);
    }

    @Override
    public ReferenceCounted retain(int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("increment: " + increment + " (expected: > 0)");
        }
        int oldRef = refCnt;
        int nextRef = oldRef + increment;

        if (oldRef <= 0 || oldRef >= nextRef) {
            throw new IllegalReferenceCountException(oldRef, increment);
        }

        while (!REF_CNT_UPDATER.compareAndSet(this, oldRef, nextRef)) {
            oldRef = refCnt;
            nextRef = oldRef + increment;
            if (oldRef <= 0 || nextRef < oldRef) {
                throw new IllegalReferenceCountException(oldRef, increment);
            }
        }
        return this;
    }

    @Override
    public boolean release() {
        // 无参 release 等价于把引用计数减 1。
        return release(1);
    }

    @Override
    public boolean release(int decrement) {
        // 第一步：decrement 必须是正数，0 或负数都属于非法调用。
        if (decrement <= 0) {
            throw new IllegalArgumentException("increment: " + decrement + " (expected: > 0)");
        }
        // 第二步：先读取当前引用计数，作为 CAS 更新时的旧值基准。
        int oldRefCnt = refCnt;
        // 第三步：如果本次要减掉的数量比当前引用计数还大，说明调用方多释放了。
        if (oldRefCnt < decrement) {
            throw new IllegalReferenceCountException(oldRefCnt, -decrement);
        }

        // 第四步：通过 CAS 循环做无锁减计数，保证并发下只有一个线程能更新成功。
        while (!REF_CNT_UPDATER.compareAndSet(this, oldRefCnt, oldRefCnt - decrement)) {
            // 第五步：CAS 失败说明别的线程先一步修改了 refCnt，这里重新读取最新值。
            oldRefCnt = refCnt;
            // 第六步：读取到最新值后，再次检查是否发生了过度释放。
            if (oldRefCnt < decrement) {
                throw new IllegalReferenceCountException(oldRefCnt, -decrement);
            }
        }

        // 第七步：如果旧引用计数刚好等于 decrement，说明这次 release 之后 refCnt 会变成 0。
        if (oldRefCnt == decrement) {
            // 第八步：引用计数归零后，触发真正的资源释放逻辑，例如回池或丢给 GC。
            deallocate();
            // 第九步：返回 true，表示这是最后一次释放。
            return true;
        }
        // 第十步：返回 false，表示对象还在被其他引用持有，当前还不能真正回收。
        return false;
    }

    protected abstract void deallocate();

    public static class IllegalReferenceCountException extends IllegalStateException {

        public IllegalReferenceCountException(int refCnt, int increment) {
            super("refCnt: " + refCnt + ", " + (increment > 0 ? "increment: " : "decrement: ") + Math.abs(increment));
        }
    }
}
