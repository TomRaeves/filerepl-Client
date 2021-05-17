package com.company.filereplClient;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Objects;

public class TCPGetFile extends Thread{

    private static Socket socket;
    private static String path;
    private static boolean running = true;

    public TCPGetFile(Socket serverSocket, String path) {
        TCPGetFile.socket = serverSocket;
        TCPGetFile.path = path;
    }

    public void run(){
        if (running) {
            System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] Created thread to receive replicated file");
            byte[] byteArray = new byte[0];
            InputStream inputStream = null;
            OutputStream outputStream = null;
            DataInputStream dataInputStream = null;
            DataOutputStream dataOutputStream = null;
            FileOutputStream fileOutputStream = null;
            BufferedOutputStream bufferedOutputStream = null;
            String hostName = null;
            String fileName = null;
            int fileSize = 0;
            //INITIALIZE STREAMS
            try {
                inputStream = socket.getInputStream();
                dataInputStream = new DataInputStream(inputStream);
                outputStream = socket.getOutputStream();
                dataOutputStream = new DataOutputStream(outputStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //GET HOSTNAME/FILENAME
            try {
                String received = Objects.requireNonNull(dataInputStream).readUTF();    //hostName,fileName,fileSize
                int index = received.indexOf(",");
                hostName = received.substring(0, index);
                String temp = received.substring(index + 1);
                index = temp.indexOf(",");
                fileName = temp.substring(0,index);
                fileSize = Integer.parseInt(temp.substring(index+1));
                System.out.println(hostName+","+fileName+","+fileSize);
                byteArray = new byte[fileSize];
                if (!fileName.equals(null) && !hostName.equals(null)) {
                    System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] Succesfully got hostname: " + hostName + " ,filename: " + fileName + " and filesize: "+fileSize+" ,sending ACK");
                    Objects.requireNonNull(dataOutputStream).writeUTF("ACK");   //SEND ACK
                } else {
                    System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] Something went wrong reading fileName and hostName...");
                    System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] Received fileName: " + fileName);
                    System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] Received hostName: " + hostName);
                    Objects.requireNonNull(dataOutputStream).writeUTF("NACK");  //SEND NACK | something went wrong
                }
                dataOutputStream.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
            //GET FILE
            try {
                if (!fileName.equals(null) && !hostName.equals(null)) {
                    System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] receiving file " + fileName);
                    fileOutputStream = new FileOutputStream(path+"/"+fileName);  //linux
                    //fileOutputStream = new FileOutputStream(path+"\\"+fileName);  //windows
                    bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                    /*int bytesRead = inputStream.read(byteArray, 0, byteArray.length);
                    int current = bytesRead;
                    do {
                        bytesRead = inputStream.read(byteArray, current, (byteArray.length - current));
                        System.out.println("BytesRead: " + bytesRead);
                        if (bytesRead >= 0) current += bytesRead;
                    } while (bytesRead > -1);*/
                    int x;
                    while (fileSize > 0 && (x = dataInputStream.read(byteArray,0,(int)Math.min(byteArray.length, fileSize))) != -1){
                        fileOutputStream.write(byteArray,0,x);
                        fileSize -= x;
                    }
                    System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] done reading "+fileName+", writing to receivedFile location");
                    //bufferedOutputStream.write(byteArray, 0, current);
                    //bufferedOutputStream.flush();
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    bufferedOutputStream.close();
                    if (!(Arrays.toString(byteArray)).equals(null)) {
                        System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] Succesfully received file " + fileName + " ,sending ACK");
                        dataOutputStream.writeUTF("ACK");
                    } else {
                        System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] Something went wrong reading " + fileName + " ,sending NACK");
                        System.out.println("[" + TCPGetFile.currentThread().getId() + " | " + TCPGetFile.currentThread().getName() + "] byteArray: " + Arrays.toString(byteArray));
                        dataOutputStream.writeUTF("NACK");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        exit();
    }

    public void exit() {
        System.out.println("["+TCPGetFile.currentThread().getId()+" | "+TCPGetFile.currentThread().getName()+"] Shutting down...");
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        running = false;
    }
}