package com.getian.netty.buffer;

import java.nio.charset.Charset;

/**
 * ByteBuf 的抽象基类
 * 提供读写索引管理和边界检查的通用实现。
 * 子类需要实现具体的字节读写方法。
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-21
 */

public abstract class AbstractByteBuf extends ByteBuf {

    //读索引
    protected int readerIndex;

    //写索引
    protected int writerIndex;

    //标记的读索引
    private int markedReaderIndex;

    //标记的写索引
    private int markedWriterIndex;

    //最大容量
    private int maxCapacity;

    protected AbstractByteBuf(int maxCapacity) {
        setMaxCapacity(maxCapacity);
    }

    @Override
    public int maxCapacity() {
        ensureAccessible();
        return maxCapacity;
    }

    @Override
    public int readerIndex() {
        ensureAccessible();
        return readerIndex;
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        ensureAccessible();
        if (readerIndex < 0 || readerIndex > writerIndex) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex: %d (expected: 0 <= readerIndex <= writerIndex(%d))",
                    readerIndex, writerIndex));
        }
        this.readerIndex = readerIndex;
        return this;
    }

    @Override
    public int writerIndex() {
        ensureAccessible();
        return writerIndex;
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        ensureAccessible();
        if (writerIndex < 0 || writerIndex < readerIndex || writerIndex > capacity()) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex: %d, writerIndex: %d (expected: 0 <= readerIndex <= writerIndex <= capacity(%d))",
                    readerIndex, writerIndex, capacity()));
        }
        this.writerIndex = writerIndex;
        return this;
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        ensureAccessible();
        if (readerIndex < 0 || readerIndex > writerIndex || writerIndex > capacity()) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex: %d, writerIndex: %d (expected: 0 <= readerIndex <= writerIndex <= capacity(%d))",
                    readerIndex, writerIndex, capacity()));
        }
        this.writerIndex = writerIndex;
        this.readerIndex = readerIndex;
        return this;
    }

    @Override
    public int readableBytes() {
        ensureAccessible();
        return writerIndex - readerIndex;
    }

    @Override
    public int writableBytes() {
        ensureAccessible();
        return capacity() - writerIndex;
    }

    @Override
    public boolean isReadable() {
        ensureAccessible();
        return writerIndex > readerIndex;
    }

    @Override
    public boolean isReadable(int size) {
        ensureAccessible();
        return (writerIndex - readerIndex) >= size;
    }

    @Override
    public boolean isWritable() {
        ensureAccessible();
        return capacity() - writerIndex > 0;
    }

    @Override
    public boolean isWritable(int size) {
        ensureAccessible();
        return (capacity() - writerIndex) >= size;
    }

    @Override
    public ByteBuf clear() {
        ensureAccessible();
        resetIndexes();
        resetMarkers();
        return this;
    }

    @Override
    public ByteBuf markReaderIndex() {
        ensureAccessible();
        markedReaderIndex = readerIndex;
        return this;
    }

    @Override
    public ByteBuf resetReaderIndex() {
        ensureAccessible();
        readerIndex(markedReaderIndex);
        return this;
    }

    @Override
    public ByteBuf markWriterIndex() {
        ensureAccessible();
        markedWriterIndex = writerIndex;
        return this;
    }

    @Override
    public ByteBuf resetWriterIndex(){
        ensureAccessible();
        writerIndex(markedWriterIndex);
        return this;
    }

    // =====================
    // 顺序读取实现
    // =====================

    @Override
    public byte readByte() {
        ensureAccessible();
        checkReadableBytes(1);
        int i = readerIndex;
        byte b = getByte(i);
        readerIndex = i + 1;
        return b;
    }

    @Override
    public short readShort() {
        ensureAccessible();
        checkReadableBytes(2);
        short res = getShort(readerIndex);
        readerIndex += 2;
        return res;
    }

    @Override
    public int readInt() {
        ensureAccessible();
        checkReadableBytes(4);
        int res = getInt(readerIndex);
        readerIndex += 4;
        return res;
    }

    @Override
    public long readLong() {
        ensureAccessible();
        checkReadableBytes(8);
        long v = getLong(readerIndex);
        readerIndex += 8;
        return v;
    }

    @Override
    public ByteBuf readBytes(byte[] dst) {
        ensureAccessible();
        return readBytes(dst, 0, dst.length);
    }

    @Override
    public ByteBuf readBytes(byte[] dst, int dstIndex, int length) {
        ensureAccessible();
        checkReadableBytes(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
        return this;
    }

    @Override
    public ByteBuf skipBytes(int length) {
        ensureAccessible();
        checkReadableBytes(length);
        readerIndex += length;
        return this;
    }

    // =====================
    // 顺序写入实现
    // =====================

    @Override
    public ByteBuf writeByte(int value) {
        ensureAccessible();
        ensureWritable(1);
        setByte(writerIndex++, value);
        return this;
    }

    @Override
    public ByteBuf writeShort(int value) {
        ensureAccessible();
        ensureWritable(2);
        setShort(writerIndex, value);
        writerIndex += 2;
        return this;
    }

    @Override
    public ByteBuf writeInt(int value) {
        ensureAccessible();
        ensureWritable(4);
        setInt(writerIndex, value);
        writerIndex += 4;
        return this;
    }

    @Override
    public ByteBuf writeLong(long value) {
        ensureAccessible();
        ensureWritable(8);
        setLong(writerIndex, value);
        writerIndex += 8;
        return this;
    }

    @Override
    public ByteBuf writeBytes(byte[] src) {
        ensureAccessible();
        return writeBytes(src, 0, src.length);
    }

    @Override
    public ByteBuf writeBytes(byte[] src, int srcIndex, int length) {
        ensureAccessible();
        ensureWritable(length);
        setBytes(writerIndex, src, srcIndex, length);
        writerIndex += length;
        return this;
    }

    // =====================
    // 字符串方法
    // =====================


    @Override
    public String toString(Charset charset) {
        ensureAccessible();
        return toString(readerIndex, readableBytes(), charset);
    }

    @Override
    public String toString(int index, int length, Charset charset) {
        ensureAccessible();
        if (length == 0) {
            return "";
        }
        byte[] bytes = new byte[length];
        getBytes(index, bytes, 0, length);
        return new String(bytes, charset);
    }


    // =====================
    // 辅助方法
    // =====================
    protected void checkReadableBytes(int minimumReadableBytes) {
        ensureAccessible();
        if (writerIndex - readerIndex < minimumReadableBytes) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex(%d) + length(%d) exceeds writerIndex(%d)",
                    readerIndex, minimumReadableBytes, writerIndex));
        }

    }

    protected void ensureWritable(int minWritableBytes) {
        ensureAccessible();
        if (minWritableBytes < 0) {
            throw new IllegalArgumentException(String.format(
                    "minWritableBytes: %d (expected: >= 0)", minWritableBytes));
        }
        if (minWritableBytes <= writableBytes()) {
            return;
        }
        if (minWritableBytes > maxCapacity - writerIndex) {
            throw new IndexOutOfBoundsException(String.format(
                    "writerIndex(%d) + minWritableBytes(%d) exceeds maxCapacity(%d)",
                    writerIndex, minWritableBytes, maxCapacity));
        }
        //如果容量不足的话 扩容到恰好能写的容量
        int newCapacity = calculateNewCapacity(writerIndex + minWritableBytes);
        capacity(newCapacity);
    }

    protected int calculateNewCapacity(int minNewCapacity) {
        final int THRESHOLD = 4 * 1024 * 1024; //4MB
        //1.第一种情况 恰好等于阈值
        if (minNewCapacity == THRESHOLD) return THRESHOLD;

        //2.第二种情况 大于阈值
        if (minNewCapacity > THRESHOLD) {
            int newCapacity = (minNewCapacity / THRESHOLD) * THRESHOLD;
            if (newCapacity < minNewCapacity) {
                newCapacity += THRESHOLD;
            }
            return Math.min(newCapacity, maxCapacity);
        }
        //3.第三种情况 小于阈值 以64为起点翻倍增长
        int newCapacity = 64;
        while (newCapacity < minNewCapacity) {
            newCapacity <<= 1;
        }
        return Math.min(maxCapacity, newCapacity);
    }

    protected void checkIndex(int index, int length) {
        ensureAccessible();
        if (index < 0 || index > capacity() - length) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d, length: %d (expected: index >= 0 && index + length <= capacity(%d))",
                    index, length, capacity()));
        }
    }

    /**
     * 丢弃已读字节，压缩缓冲区
     *
     * @return this
     */
    @Override
    public ByteBuf discardReadBytes() {
        ensureAccessible();
        if (readerIndex == 0) {
            return this;
        }
        if (readerIndex != writerIndex) {
            //将未读数据移动到开头,旧array中的[readerIndex,writerIndex]这部分数据 移动到 array中的[0,writeIndex-readerIndex]
            setBytes(0, array(), arrayOffset() + readerIndex, writerIndex - readerIndex);
            writerIndex -= readerIndex;
            adjustMarkers(readerIndex);
            readerIndex = 0;
        } else {
            // 没有可读数据，直接清空
            adjustMarkers(readerIndex);
            writerIndex = readerIndex = 0;
        }
        return this;
    }

    private void adjustMarkers(int decrement) {
        markedWriterIndex = Math.max(markedWriterIndex - decrement, 0);
        markedReaderIndex = Math.max(markedReaderIndex - decrement, 0);
    }

    protected final void setMaxCapacity(int maxCapacity) {
        if (maxCapacity < 0) {
            throw new IllegalArgumentException("maxCapacity: " + maxCapacity + " (expected: >= 0)");
        }
        this.maxCapacity = maxCapacity;
    }

    protected final void resetIndexes() {
        readerIndex = 0;
        writerIndex = 0;
    }

    protected final void resetMarkers() {
        markedReaderIndex = 0;
        markedWriterIndex = 0;
    }

    protected final void ensureAccessible() {
        int refCnt = refCnt();
        if (refCnt <= 0) {
            throw new AbstractReferenceCountedByteBuf.IllegalReferenceCountException(refCnt, 1);
        }
    }
}
