package com.getian.netty.example.nio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NIO Channel 和 Buffer测试
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-13
 */

public class NioChannelBufferTest {
    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ByteBuffer 可以正确分配指定容量")
    void byteBufferAllocation() {
        ByteBuffer buffer1 = ByteBuffer.allocateDirect(10);
        ByteBuffer buffer2 = ByteBuffer.allocate(20);
        buffer1.put("ahhhah".getBytes(StandardCharsets.UTF_8));
        buffer2.put("hahaha".getBytes(StandardCharsets.UTF_8));


        printBufferState("写模式", buffer1);
        buffer1.flip();
        printBufferState("读模式", buffer1);

        assertThat(buffer1.remaining()).isEqualTo(4);
        assertThat(buffer2.capacity()).isEqualTo(20);
    }

    @Test
    @DisplayName("写入数据后 position 增加")
    void positionIncreaseAfterWrite() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put((byte) 'A');
        printBufferState("写入一个字母时,", buffer);

        buffer.put((byte) 'B');
        buffer.put((byte) 'C');
        printBufferState("写入三个字母时,", buffer);
    }

    @Test
    @DisplayName("flip() 切换到读模式")
    void flipSwitchesToReadMode() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(10);
        buffer.put((byte) 'A');
        buffer.put((byte) 'A');

        //写模式的时候 limit为10
        buffer.flip();
        //读模式的时候 limit为2
        assertThat(buffer.limit()).isEqualTo(2);
    }

    @Test
    @DisplayName("可以从 Buffer 读取写入的数据")
    void canReadWrittenData() {
        //从Buffer读取写入的数据

        //1.写入数据
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("hello".getBytes(StandardCharsets.UTF_8));

        //2.读取数据
        buffer.flip();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        //3.输出数据
        System.out.println(new String(data, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("clear() 重置 Buffer 到写模式")
    void clearResetsBufferToWriteMode() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("hello".getBytes(StandardCharsets.UTF_8));

        //置换 clear()会将position直接置为0，之前的数据还在，但是会直接被覆盖掉的
        buffer.clear();
        assertThat(buffer.position()).isEqualTo(0);
    }

    @Test
    @DisplayName("compact() 保留未读数据并切换到写模式")
    void compactPreservesUnreadData() {
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("hello".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        char ch = (char) buffer.get();
        char ch1 = (char) buffer.get();

        System.out.println(ch);
        System.out.println(ch1);

        buffer.compact();
        assertThat(buffer.position()).isEqualTo(3);

        buffer.put((byte) '!');
        assertThat(buffer.position()).isEqualTo(4);

        buffer.flip();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        System.out.println(new String(data));
    }


    @Test
    @DisplayName("直接缓冲区和堆缓冲区的区别")
    void directVsHeapBuffer() {
        // 堆缓冲区
        ByteBuffer heapBuffer = ByteBuffer.allocate(1024);
        assertThat(heapBuffer.isDirect()).isFalse();

        // 直接缓冲区
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        assertThat(directBuffer.isDirect()).isTrue();
    }

    @Test
    @DisplayName("FileChannel 可以写入和读取数据")
    void fileChannelReadWrite() throws IOException {
        Path path = tempDir.resolve("test.txt");
        //写入文件
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "rw");
             FileChannel channel = randomAccessFile.getChannel()) {
            String content = "hello,mini-netty";
            ByteBuffer buffer = ByteBuffer.allocate(20);
            //buffer写数据
            buffer.put(content.getBytes(StandardCharsets.UTF_8));
            //写模式 -> 读模式
            buffer.flip();
            //放到channel轨道中
            int bytesWrite = channel.write(buffer);
            System.out.println("往文件中写入了" + bytesWrite + "个字节");
        }

        //读取文件
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "rw");
             FileChannel channel = randomAccessFile.getChannel()){
            ByteBuffer buffer = ByteBuffer.allocate(20);
            //buffer写模式
            channel.read(buffer);
            //buffer读模式
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            System.out.println("文件的数据为:"+new String(data));
        }
    }

    @Test
    @DisplayName("wrap() 可以将字节数组包装为 Buffer")
    void wrapCreatesBufferFromArray(){
        byte[] array = "hello,mini-netty".getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.wrap(array);
        assertThat(buffer.position()).isEqualTo(0);
        assertThat(buffer.limit()).isEqualTo(array.length);
        assertThat(buffer.capacity()).isEqualTo(array.length);

        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        assertThat(new String(data, StandardCharsets.UTF_8)).isEqualTo("hello,mini-netty");
    }

    /**
     * 如果变成读模式的话，get()一次position就会往后移动，slice()会创建一个视图，视图从position开始到limit
     */
    @Test
    @DisplayName("slice() 创建 Buffer 的视图")
    void sliceCreatesBufferView(){
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("HelloWorld".getBytes(StandardCharsets.UTF_8));

        buffer.flip();
        buffer.position(5);
        char ch = (char) buffer.get();
        System.out.println(ch);
        ByteBuffer newBuffer = buffer.slice();
        byte[] data = new byte[newBuffer.remaining()];
        newBuffer.get(data);
        System.out.println("输出的数据为:"+new String(data,StandardCharsets.UTF_8));
    }



    private static void printBufferState(String description, ByteBuffer buffer) {
        System.out.println(description + ":");
        System.out.println("  capacity=" + buffer.capacity() +
                ", position=" + buffer.position() +
                ", limit=" + buffer.limit() +
                ", remaining=" + buffer.remaining());
    }
}
