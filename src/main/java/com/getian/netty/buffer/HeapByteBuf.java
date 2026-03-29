package com.getian.netty.buffer;


import java.nio.ByteBuffer;

/**
 * 堆内存 ByteBuf 实现
 * 使用 Java 堆内存（byte[]）存储数据。 适合需要频繁访问数据的场景，GC 可以自动管理内存。
 * <p>
 * 特点：
 * 1.分配速度快
 * 2.数据存储在JVM堆中
 * 3.可直接访问底层数组
 * 4.网络IO时需要额外拷贝到直接内存
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-21
 */

public class HeapByteBuf extends AbstractReferenceCountedByteBuf {
    private byte[] array;

    /**
     * @param initialCapacity 初始化容量
     * @param maxCapacity     最大容量
     */
    public HeapByteBuf(int initialCapacity, int maxCapacity) {
        super(maxCapacity);
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity + " (expected: >= 0)");
        }
        if (initialCapacity > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity: %d (expected: <= maxCapacity(%d))",
                    initialCapacity, maxCapacity));
        }
        this.array = new byte[initialCapacity];
    }

    public HeapByteBuf(byte[] initialArray, int maxCapacity) {
        super(maxCapacity);
        if (initialArray.length > maxCapacity) {
            throw new IllegalArgumentException(String.format(
                    "initialCapacity: %d (expected: <= maxCapacity(%d))",
                    initialArray.length, maxCapacity));
        }
        this.array = initialArray;
        this.writerIndex = initialArray.length;
    }


    @Override
    public int capacity() {
        return array.length;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        //1.如果newCapacity 大于 最大容量的话
        if (newCapacity < 0 || newCapacity > maxCapacity()) {
            throw new IllegalArgumentException(String.format(
                    "newCapacity: %d (expected: 0 <= newCapacity <= maxCapacity(%d))",
                    newCapacity, maxCapacity()));
        }
        //2.如果newCapacity == oldCapacity的话 就不需要扩容了
        int oldCapacity = array.length;
        if (oldCapacity == newCapacity) {
            return this;
        }

        //3.修改底层的byte数组
        byte[] newArray = new byte[newCapacity];
        //newCapacity可能比olcCapacity小 -> 缩容 所以这里要取一个最小值
        System.arraycopy(array, 0, newArray, 0, Math.min(oldCapacity, newCapacity));
        this.array = newArray;

        //4.重新更新读写的指针  因为这个newCapacity有可能是变小的 所以可能是缩容
        if (readerIndex > newCapacity) {
            readerIndex = newCapacity;
            writerIndex = newCapacity;
        } else if (writerIndex > newCapacity) {
            //读指针不需要改变 写指针需要减小
            writerIndex = newCapacity;
        }
        return this;
    }


    @Override
    public boolean hasArray() {
        return true;
    }

    @Override
    public byte[] array() {
        return array;
    }

    @Override
    public int arrayOffset() {
        return 0;
    }


    // =====================
    // 随机访问实现
    // =====================

    @Override
    public byte getByte(int index) {
        checkIndex(index, 1);
        return array[index];
    }

    /**
     * 取array[index]  和 array[index+1] 分别放到short的高八位和低八位
     *
     * @param index 位置
     * @return
     */
    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        return (short) ((array[index] & 0xff) << 8 | (array[index + 1] & 0xff));
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        return (array[index] & 0xff) << 24 |
                (array[index + 1] & 0xff) << 16 |
                (array[index + 2] & 0xff) << 8 |
                (array[index + 3] & 0xff);
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        return ((long) array[index] & 0xff) << 56 |
                ((long) array[index + 1] & 0xff) << 48 |
                ((long) array[index + 2] & 0xff) << 40 |
                ((long) array[index + 3] & 0xff) << 32 |
                ((long) array[index + 4] & 0xff) << 24 |
                ((long) array[index + 5] & 0xff) << 16 |
                ((long) array[index + 6] & 0xff) << 8 |
                ((long) array[index + 7] & 0xff);
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        checkIndex(index, 1);
        array[index] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        checkIndex(index, 2);
        array[index] = (byte) (value >>> 8);
        array[index + 1] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        checkIndex(index, 4);
        array[index] = (byte) (value >>> 24);
        array[index + 1] = (byte) (value >>> 16);
        array[index + 2] = (byte) (value >>> 8);
        array[index + 3] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        checkIndex(index, 8);
        array[index] = (byte) (value >>> 56);
        array[index + 1] = (byte) (value >>> 48);
        array[index + 2] = (byte) (value >>> 40);
        array[index + 3] = (byte) (value >>> 32);
        array[index + 4] = (byte) (value >>> 24);
        array[index + 5] = (byte) (value >>> 16);
        array[index + 6] = (byte) (value >>> 8);
        array[index + 7] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src) {
        return setBytes(index, src, 0, src.length);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        System.arraycopy(src, srcIndex, array, index, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        return getBytes(index, dst, 0, dst.length);
    }

    /**
     * 将array中的字节数据 读取到另一个数组中 所以src数字是array 起始的位置为index
     *
     * @param index    起始位置
     * @param dst      目标字节数组
     * @param dstIndex 目标数组起始位置
     * @param length   长度
     * @return
     */
    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        System.arraycopy(array, index, dst, dstIndex, length);
        return this;
    }

    // =====================
    // NIO 转换
    // =====================

    @Override
    public ByteBuffer nioBuffer() {
        return nioBuffer(0, array.length);
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return ByteBuffer.wrap(array, index, length).slice();
    }


    // =====================
    // 引用计数 大部分方法交给AbstractReferenceCountedByteBuf了
    // =====================

    /**
     * 释放资源
     */
    protected void deallocate() {
        // 堆内存由 GC 管理，这里只是标记
        array = null;
    }

}
