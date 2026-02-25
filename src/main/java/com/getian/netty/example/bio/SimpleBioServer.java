package com.getian.netty.example.bio;

import javax.swing.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Bio 阻塞Io 服务器？
 *
 * @Author: sonicge
 * @CreateTime: 2026-02-24
 */

public class SimpleBioServer {
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running;

    /**
     * 创建一个 BIO 服务端
     *
     * @param port 监听端口号
     */
    public SimpleBioServer(int port) {
        this.port = port;
    }

    /**
     * 开始监听port端口
     *
     * @throws IOException
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("[SimpleBioServer] 服务器启动，监听端口号：" + port);
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SimpleBioServer] 客户端连接: " + clientSocket.getRemoteSocketAddress());
                //处理客户端请求 （阻塞当前线程）
                handleClient(clientSocket);
            } catch (IOException e) {
                if (running) {
                    System.err.println("[SimpleBioServer] 接受连接失败: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 处理单个客户端连接
     *
     * @param clientSocket
     */
    private void handleClient(Socket clientSocket) {
        try(
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter printWriter =new PrintWriter(clientSocket.getOutputStream())
        ){
            String line ;
            while ((line = bufferedReader.readLine())!=null){
                System.out.println("[SimpleBioServer] 收到消息: " + line);
                //收到响应
                String response = "hello mini-netty";
                printWriter.println(response);
                System.out.println("[SimpleBioServer] 收到消息: " + line);
            }
        }catch(IOException e){
            System.err.println("[SimpleBioServer] 处理客户端失败: " + e.getMessage());
        }finally {
            try {
                clientSocket.close();
                System.out.println("[SimpleBioServer] 客户端断开连接");
            } catch (IOException e) {
                //
            }
        }
    }

    /**
     * 在后台线程启动服务端
     *
     * @return
     */
    public Thread startInBackGround() {
        Thread thread = new Thread(() -> {
            //线程里面执行启动服务端的功能
            try {
                start();
            } catch (IOException e) {
                if (running) {
                    System.err.println("[SimpleBioServer] 服务端异常: " + e.getMessage());
                }
            }
        }, "bio-server");
        //将线程设置为守护线程，如果主线程停止的话，该线程就会自动停止，不会执行。
        thread.setDaemon(true);

        return thread;
    }

    /**
     * 停止服务端
     */
    public void stop() {
        running = false;
        //如果serverSocket不为null，并且没有被关闭
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("[SimpleBioServer] 服务端已停止");
            } catch (IOException e) {
                System.err.println("[SimpleBioServer] 关闭服务端失败: " + e.getMessage());
            }
        }
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 主方法 - 启动服务端
     * @param args  命令行参数（可选：端口号，默认 8080）
     * @throws IOException 如果启动失败
     */
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        SimpleBioServer server = new SimpleBioServer(port);
        server.start();
    }
}
