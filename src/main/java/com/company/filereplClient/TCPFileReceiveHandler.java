package com.company.filereplClient;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPFileReceiveHandler extends  Thread {

    private static int port;
    private static String path;
    private static boolean running = true;
    private static ServerSocket serverSocket;

    public TCPFileReceiveHandler(int tcpFileReceivePort) {
        port = tcpFileReceivePort;
        path = System.getProperty("user.dir");
        //path = path.concat("\\receivedFiles");    //Windows
        path = path.concat("/receivedFiles");   //Linux
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run(){
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                TCPGetFile TCPGet = new TCPGetFile(socket, path);
                TCPGet.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void shutdown() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}