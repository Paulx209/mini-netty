package com.getian.netty.buffer;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @Author: sonicge
 * @CreateTime: 2026-03-22
 */

public abstract class AbstractReferenceCountedByteBuf extends AbstractByteBuf {
    //给 AbstractReferenceCountedByteBuf 这个类的 refCnt 字段，生成一个支持原子增减的更新器
    public static final AtomicIntegerFieldUpdater<AbstractReferenceCountedByteBuf> REF_CNT_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(AbstractReferenceCountedByteBuf.class, "refCnt");

    //volatile可以实现可见性，有序性。但是这里复合执行动作的原子性没有办法保证 。updater可以对refCnt属性进行原子修改
    private volatile int refCnt = 1;

    protected AbstractReferenceCountedByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    public int refCnt() {
        return refCnt;
    }

    /**
     * 内部使用
     *
     * @param refCnt
     */
    protected void setRefCnt(int refCnt) {
        //updater可以对refCnt属性进行原子修改
        REF_CNT_UPDATER.set(this, refCnt);
    }


    @Override
    public ReferenceCounted retain() {
        return retain(1);
    }

    /**
     * @param increment 增加的数量
     * @return
     */
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
            //争锁失败之后 是需要重新写oldRef 和 nextRef的，因为refCnt有可能会被修改
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
        return release(1);
    }

    @Override
    public boolean release(int decrement) {
        if (decrement <= 0) {
            throw new IllegalArgumentException("increment: " + decrement + " (expected: > 0)");
        }
        int oldRefCnt = refCnt;
        if (oldRefCnt < decrement) {
            throw new IllegalReferenceCountException(oldRefCnt, -decrement);
        }

        while (!REF_CNT_UPDATER.compareAndSet(this, oldRefCnt, oldRefCnt - decrement)) {
            oldRefCnt = refCnt;
            if (oldRefCnt < decrement) {
                throw new IllegalReferenceCountException(oldRefCnt, -decrement);
            }
        }

        //引用计数变成0之后，就可以标记array = null 然后就会触发gc垃圾回收
        if (oldRefCnt == decrement) {
            deallocate();
            return true;
        }
        return false;
    }


    /**
     * 释放资源的模板方法
     *
     * <p>当引用计数变为 0 时调用，子类需要实现具体的资源释放逻辑。
     */
    protected abstract void deallocate();


    /**
     * 非法引用计数异常
     */
    public static class IllegalReferenceCountException extends IllegalStateException {

        public IllegalReferenceCountException(int refCnt, int increment) {
            super("refCnt: " + refCnt + ", " + (increment > 0 ? "increment: " : "decrement: ") + Math.abs(increment));
        }
    }
}
