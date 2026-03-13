package com.getian.netty.example.nio;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * NIO Channel(通道) 和 Buffer(缓冲区) 演示
 * 铁轨 和 车厢的关系
 *
 * @Author: sonicge
 * @CreateTime: 2026-03-13
 */

public class NioChannelBufferDemo {
    /**
     * 演示ByteBuffer基本操作
     */
    public static void demonstrateByteBuffer() {
        System.out.println("=== ByteBuffer 基本操作演示 ===\n");
        //1.创建Buffer
        ByteBuffer buffer = ByteBuffer.allocate(10);
        printBufferState("创建 Buffer (capacity=10)", buffer);

        //2.写入数据到Buffer
        buffer.put((byte) 'H');
        buffer.put((byte) 'E');
        buffer.put((byte) 'L');
        buffer.put((byte) 'L');
        buffer.put((byte) 'O');
        printBufferState("写入'HELLO'后", buffer);

        //3.切换到读模式 -flip()
        buffer.flip();
        printBufferState("flip() 后（切换到读模式）", buffer);

        //4.读取数据
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        System.out.println("读取的数据为:" + sb);
        printBufferState("读取完成后", buffer);

        //5.切换为写模式 - clear()
        buffer.clear();
        printBufferState("clear() 后（切换回写模式）", buffer);

        System.out.println();
    }

    /**
     * 演示使用 Channel 和 Buffer 读写文件
     *
     * @param filePath 临时文件路径
     * @throws IOException
     */
    public static void demonstrateFileChannel(Path filePath) throws IOException {
        System.out.println("=== FileChannel 读写演示 ===\n");

        String content = "Hello,NIO";

        //1.写入文件
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(filePath.toFile(), "rw");
             FileChannel channel = randomAccessFile.getChannel();
        ) {
            //创建buffer缓冲区
            ByteBuffer buffer = ByteBuffer.allocate(48);
            //写入缓冲区中
            buffer.put(content.getBytes(StandardCharsets.UTF_8));
            //写模式 -> 读模式
            buffer.flip();

            int bytesWritten = channel.write(buffer);
            System.out.println("写入 " + bytesWritten + " 字节到文件");
        }

        //2.读取文件
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(filePath.toFile(), "r");
             FileChannel channel = randomAccessFile.getChannel()
        ) {
            ByteBuffer buffer = ByteBuffer.allocate(48);
            //buffer的写入模式
            int bytesWritten = channel.read(buffer);
            System.out.println("写入 " + bytesWritten + " 字节到文件");

            //写->读模式
            buffer.flip();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            System.out.println("读取的内容为: " + new String(data, StandardCharsets.UTF_8));
        }
        System.out.println();

    }


    /**
     * 演示 Buffer 的 compact() 方法
     */
    public static void demonstrateCompact() {
        System.out.println("=== Buffer compact() 演示 ===\n");

        ByteBuffer buffer = ByteBuffer.allocate(10);

        // 写入数据
        buffer.put("Hello".getBytes(StandardCharsets.UTF_8));
        printBufferState("写入 'Hello' 后", buffer);

        buffer.flip();
        printBufferState("flip() 后", buffer);

        // 读取部分数据
        buffer.get(); // 读取 'H'
        buffer.get(); // 读取 'e'
        printBufferState("读取 'He' 后", buffer);

        // compact() - 将未读数据移到开头，准备继续写入
        buffer.compact();
        printBufferState("compact() 后", buffer);

        // 继续写入
        buffer.put((byte) '!');
        printBufferState("写入 '!' 后", buffer);

        // 读取所有数据
        buffer.flip();
        StringBuilder sb = new StringBuilder();
        while (buffer.hasRemaining()) {
            sb.append((char) buffer.get());
        }
        System.out.println("最终读取: " + sb);
        System.out.println();
    }

    /**
     * 演示直接缓冲区和非直接缓冲区
     */
    public static void demonstrateDirectBuffer() {
        System.out.println("=== 直接缓冲区 vs 非直接缓冲区 ===\n");

        // 非直接缓冲区（堆内存）
        ByteBuffer heapBuffer = ByteBuffer.allocate(1024);
        System.out.println("堆缓冲区 isDirect(): " + heapBuffer.isDirect());

        // 直接缓冲区（直接内存）
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
        System.out.println("直接缓冲区 isDirect(): " + directBuffer.isDirect());

        System.out.println("\n直接缓冲区特点：");
        System.out.println("- 分配和释放成本较高");
        System.out.println("- 读写性能更好（减少一次数据拷贝）");
        System.out.println("- 适合长期使用的大缓冲区");
        System.out.println();
    }


    /**
     * 打印 Buffer 状态
     */
    private static void printBufferState(String description, ByteBuffer buffer) {
        System.out.println(description + ":");
        System.out.println("  capacity=" + buffer.capacity() +
                ", position=" + buffer.position() +
                ", limit=" + buffer.limit() +
                ", remaining=" + buffer.remaining());
    }

    public static void main(String[] args) throws IOException {
//        demonstrateByteBuffer();
//        Path tempFile = Files.createTempFile("nio-demo-", ".txt");
//        try {
//            demonstrateFileChannel(tempFile);
//        } catch (IOException e) {
//            Files.deleteIfExists(tempFile);
//        }
        demonstrateCompact();
    }
}
