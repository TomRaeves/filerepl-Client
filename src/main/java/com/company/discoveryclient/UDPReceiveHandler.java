package com.company.discoveryclient;

import java.io.IOException;
import java.net.*;

public class UDPReceiveHandler extends Thread {

    private static int receivePort;
    private static DatagramSocket receiveSocket;
    private static boolean running = true;

    public UDPReceiveHandler(int receivePort){
        UDPReceiveHandler.receivePort = receivePort;
        System.out.println("UDPReceive handler starting...");
        try {
            receiveSocket = new DatagramSocket(UDPReceiveHandler.receivePort);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void shutdown(){
        System.out.println("UDPReceive handler stopping...");
        if(UDPReceiveHandler.receiveSocket.isClosed()){running = false;}
        else{UDPReceiveHandler.receiveSocket.close(); running = false;}
    }

    @Override
    public void run(){
        while(running){
            byte[] buffer = new byte[255];
            while(UDPReceiveHandler.running){
                DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
                try{
                    receiveSocket.receive(packet);
                } catch (IOException err) {err.printStackTrace();}
                onDataReceived(packet);
            }
        }
    }

    //onDataReceived and handleData is point 6 of Discovery and Bootstrap
    private void onDataReceived(DatagramPacket packet){
        InetAddress addr = packet.getAddress();
        String data = new String(packet.getData(),0, packet.getLength());
        System.out.println("Unicast addr ["+addr+"] UDP packet received: "+data);
        handleData(data);
    }

    private void handleData(String data){
        int index = data.indexOf(",");
        String command = null;
        String message = null;
        if (index != -1) {
            command = data.substring(0,index);
            message = data.substring(index+1);
        }
        assert command != null;
        switch (command){
            case "Other nodes in the network":
                Client.updateInitial(message);
                break;
            case "NewNext":
                System.out.println("Updating the next ID...");
                Client.exitUpdateNext(message);
                break;
            case "NewPrev":
                System.out.print("Updating the previous ID...");
                Client.exitUpdatePrev(message);
                Client.topologyInfo();
                break;
            default:
                break;
        }
    }
}