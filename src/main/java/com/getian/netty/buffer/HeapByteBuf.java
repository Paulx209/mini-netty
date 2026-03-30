package com.getian.netty.buffer;

import java.nio.ByteBuffer;

/**
 * Heap-backed {@link ByteBuf} implementation.
 */
public class HeapByteBuf extends AbstractReferenceCountedByteBuf {
    protected byte[] array;

    public HeapByteBuf(int initialCapacity, int maxCapacity) {
        super(maxCapacity);
        validateCapacity(initialCapacity, maxCapacity);
        initializeArray(new byte[initialCapacity], maxCapacity, 0);
    }

    public HeapByteBuf(byte[] initialArray, int maxCapacity) {
        super(maxCapacity);
        if (initialArray == null) {
            throw new NullPointerException("initialArray");
        }
        validateCapacity(initialArray.length, maxCapacity);
        initializeArray(initialArray, maxCapacity, initialArray.length);
    }

    @Override
    public int capacity() {
        ensureAccessible();
        return array.length;
    }

    @Override
    public ByteBuf capacity(int newCapacity) {
        ensureAccessible();
        if (newCapacity < 0 || newCapacity > maxCapacity()) {
            throw new IllegalArgumentException(String.format(
                    "newCapacity: %d (expected: 0 <= newCapacity <= maxCapacity(%d))",
                    newCapacity, maxCapacity()));
        }

        int oldCapacity = array.length;
        if (oldCapacity == newCapacity) {
            return this;
        }

        byte[] newArray = new byte[newCapacity];
        System.arraycopy(array, 0, newArray, 0, Math.min(oldCapacity, newCapacity));
        replaceArray(newArray);

        if (readerIndex > newCapacity) {
            readerIndex = newCapacity;
            writerIndex = newCapacity;
        } else if (writerIndex > newCapacity) {
            writerIndex = newCapacity;
        }
        return this;
    }

    @Override
    public boolean hasArray() {
        ensureAccessible();
        return true;
    }

    @Override
    public byte[] array() {
        ensureAccessible();
        return array;
    }

    @Override
    public int arrayOffset() {
        ensureAccessible();
        return 0;
    }

    @Override
    public byte getByte(int index) {
        ensureAccessible();
        checkIndex(index, 1);
        return array[index];
    }

    @Override
    public short getShort(int index) {
        ensureAccessible();
        checkIndex(index, 2);
        return (short) ((array[index] & 0xff) << 8 | (array[index + 1] & 0xff));
    }

    @Override
    public int getInt(int index) {
        ensureAccessible();
        checkIndex(index, 4);
        return (array[index] & 0xff) << 24 |
                (array[index + 1] & 0xff) << 16 |
                (array[index + 2] & 0xff) << 8 |
                (array[index + 3] & 0xff);
    }

    @Override
    public long getLong(int index) {
        ensureAccessible();
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
        ensureAccessible();
        checkIndex(index, 1);
        array[index] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        ensureAccessible();
        checkIndex(index, 2);
        array[index] = (byte) (value >>> 8);
        array[index + 1] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        ensureAccessible();
        checkIndex(index, 4);
        array[index] = (byte) (value >>> 24);
        array[index + 1] = (byte) (value >>> 16);
        array[index + 2] = (byte) (value >>> 8);
        array[index + 3] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        ensureAccessible();
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
        ensureAccessible();
        return setBytes(index, src, 0, src.length);
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        ensureAccessible();
        checkIndex(index, length);
        System.arraycopy(src, srcIndex, array, index, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst) {
        ensureAccessible();
        return getBytes(index, dst, 0, dst.length);
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        ensureAccessible();
        checkIndex(index, length);
        System.arraycopy(array, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuffer nioBuffer() {
        ensureAccessible();
        return nioBuffer(0, array.length);
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        ensureAccessible();
        checkIndex(index, length);
        return ByteBuffer.wrap(array, index, length).slice();
    }

    @Override
    protected void deallocate() {
        array = null;
    }

    protected final void initializeArray(byte[] initialArray, int maxCapacity, int writerIndex) {
        if (initialArray == null) {
            throw new NullPointerException("initialArray");
        }
        validateCapacity(initialArray.length, maxCapacity);
        // 这一步既用于首次创建，也用于池化对象重新激活时的“状态重置”。
        setMaxCapacity(maxCapacity);
        this.array = initialArray;
        resetIndexes();
        resetMarkers();
        this.writerIndex = writerIndex;
    }

    protected final void reuse(byte[] initialArray, int maxCapacity) {
        // 复用旧数组时不保留历史读写痕迹，重新作为一个空缓冲区借出。
        initializeArray(initialArray, maxCapacity, 0);
    }

    protected final byte[] replaceArray(byte[] newArray) {
        byte[] oldArray = array;
        array = newArray;
        return oldArray;
    }

    private static void validateCapacity(int initialCapacity, int maxCapacity) {
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
