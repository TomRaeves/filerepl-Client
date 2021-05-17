package com.company.filereplClient;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Objects;

public class TCPFileSendHandler extends Thread{

    private static InetAddress sendAddress;
    private static String fileName;
    private static String hostName;
    private static String path;
    private static int port;
    private static Socket socket;

    public TCPFileSendHandler(String received, String filename, String hostName, int tcpFileSendPort, String filePath) {
        fileName = filename;
        String temp = received.substring(1);
        try {
            sendAddress = InetAddress.getByName(temp);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        TCPFileSendHandler.hostName = hostName;
        port = tcpFileSendPort;
        String workingDirectory = System.getProperty("user.dir");
        //String filesLocation = workingDirectory+"\\nodeFiles";   //Windows
        String filesLocation = workingDirectory+"/nodeFiles";   //Linux
        //path = filesLocation+"\\"+filePath; //Windows
        path = filesLocation+"/"+filePath;  //Linux
    }

    public void run(){
        System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+"] Created thread to send replicated file '"+fileName+"' located at: '"+path+"' to: "+sendAddress);
        socket = null;
        OutputStream outputStream = null;
        InputStream inputStream = null;
        DataOutputStream dataOutputStream = null;
        DataInputStream dataInputStream = null;
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        File file = new File(path);
        byte[] byteArray = new byte[(int)file.length()];
        String received = null;
        try {
            socket = new Socket(sendAddress,port);
            outputStream = Objects.requireNonNull(socket).getOutputStream();
            dataOutputStream = new DataOutputStream(outputStream);
            inputStream = Objects.requireNonNull(socket).getInputStream();
            dataInputStream = new DataInputStream(Objects.requireNonNull(inputStream));
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try{
            Objects.requireNonNull(dataOutputStream).writeUTF(hostName+","+fileName+","+byteArray.length);
            System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+"] Sending hostname: "+hostName+", filename: "+fileName+" and filesize: "+byteArray.length);
            dataOutputStream.flush();
            received = Objects.requireNonNull(dataInputStream).readUTF();
            System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+"] Received: "+received);
            if (received.equals("ACK")){
                Objects.requireNonNull(bufferedInputStream).read(byteArray,0,byteArray.length);
                System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+"] Sending file "+ fileName);
                dataOutputStream.write(byteArray,0,byteArray.length);
                dataOutputStream.flush();
                received = Objects.requireNonNull(dataInputStream).readUTF();
                System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+"] Received: "+received);
                if (received.equals("ACK")){
                    System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+"Node sends ACK, received file correctly...");
                    close(outputStream, inputStream, dataOutputStream, dataInputStream, fileInputStream, bufferedInputStream);
                }
                else if (received.equals("NACK")){
                    System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+"Node sends NACK, received file incorrectly...");
                    close(outputStream, inputStream, dataOutputStream, dataInputStream, fileInputStream, bufferedInputStream);
                }
                else{
                    System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+"Node did not send anything back, something went wrong");
                    close(outputStream, inputStream, dataOutputStream, dataInputStream, fileInputStream, bufferedInputStream);
                }
            }else{
                System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+"node does not ACK hostName and FileName packet, something went wrong!");
                close(outputStream, inputStream, dataOutputStream, dataInputStream, fileInputStream, bufferedInputStream);}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void close(OutputStream outputStream, InputStream inputStream, DataOutputStream dataOutputStream, DataInputStream dataInputStream, FileInputStream fileInputStream, BufferedInputStream bufferedInputStream) {
        try {
            System.out.println("["+TCPFileSendHandler.currentThread().getId()+" | "+TCPFileSendHandler.currentThread().getName()+" Closing socket and cleaning up I/O streams");
            dataInputStream.close();
            dataOutputStream.close();
            outputStream.close();
            inputStream.close();
            fileInputStream.close();
            bufferedInputStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}