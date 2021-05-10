package com.company.discoveryclient;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

public class Client {

    //https://www.baeldung.com/java-broadcast-multicast

    private static final int receivePort = 4500;
    private static final int sendPort = 4501;
    private static final int multiCastPort = 3456;
    private static final String multicastAddress = "225.6.7.8"; //Dit moet nog specifiek worden

    private static int amountOtherNodes = -1;
    private static ArrayList<Integer> nodes;

    private static int previousNodeID = -1;
    private static int currentNodeID= -1;
    private static int nextNodeID = -1;

    private static boolean running = true;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        //Hostname
        System.out.println("Choose your hostname: ");
        String hostName = sc.nextLine();
        //hostID
        Client.currentNodeID = hashCode(hostName); //read the name and then hash the name ////this is point 5b of Discovery and Bootstrap
        Client.nextNodeID = Client.currentNodeID;
        Client.previousNodeID = Client.currentNodeID;
        //ServerIP
        System.out.println("Give the name of the server: ");
        String sAddress = sc.nextLine();
        InetAddress serverAddress = null;
        try {
            serverAddress = InetAddress.getByName(sAddress); //find the server IP using it's name
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        //UDPHANDLER
        UDPReceiveHandler UDPHandler = new UDPReceiveHandler(Client.receivePort);
        UDPHandler.start();

        //UDPMULTIHANDLER
        UDPMultiReceiveHandler UDPMultiReceiveHandler = null;
        try {
            UDPMultiReceiveHandler = new UDPMultiReceiveHandler(hostName, InetAddress.getByName(Client.multicastAddress), Client.multiCastPort);
        } catch (UnknownHostException err) {
            err.printStackTrace();
        }
        if (UDPMultiReceiveHandler != null) {
            UDPMultiReceiveHandler.start();
        }

        //DISCOVERY   //This is point 3 of Discovery and Bootstrap
        try {
            sendMultiCast("Start,"+hostName, InetAddress.getByName(Client.multicastAddress));
        } catch (IOException e) {
            e.printStackTrace();
        }
        while(Client.running) {
            String command = sc.nextLine();
            switch (command) {
                case "Exit":
                    String toSend = "Exit,Curr: " + Client.currentNodeID + ",Prev: " + Client.previousNodeID + ",Next: " + Client.nextNodeID;
                    sendUnicast(toSend, serverAddress);
                    UDPHandler.shutdown();
                    if (UDPMultiReceiveHandler != null) {
                        UDPMultiReceiveHandler.shutdown();
                    }
                    try {
                        sendPUT("removeNode", sAddress);
                    }catch (IOException err) {err.printStackTrace();}
                    Client.running = false;
                    break;

                case "help":
                    System.out.println("List of all commands:");
                    System.out.println("Leave network: 'Exit'");
                    System.out.println("Get ID of a node: 'getID'");
                    System.out.println("Add file to client: 'addFile'");
                    System.out.println("Find ID of host where file is saved: 'searchFile'");
                    break;

                case "getID":
                    try {
                        sendGET("getID",sAddress);
                    } catch (IOException err) {
                        err.printStackTrace();
                    }
                    break;

                case "addFile":
                    System.out.println("Give the name for the file to be added: ");
                    String name = sc.nextLine();
                    try {
                        sendPUT("addFile/"+name,sAddress);
                    } catch (IOException err) {
                        err.printStackTrace();
                    }
                    break;

                case "searchFile":
                    System.out.println("Give the name of the file you want to find the owner of: ");
                    name = sc.nextLine();
                    try {
                        sendGET("searchFile/"+name,sAddress);
                    } catch (IOException err) {
                        err.printStackTrace();
                    }
                    break;

                default:
                    break;
            }
        }
        System.out.println("Quiting program, till next time!");
        System.exit(0);
    }

    public static int hashCode(String input){ //This is point 2 of Discovery and Bootstrap
        long max = 2147483647;
        long min = -2147483647;

        long result = ((input.hashCode()+max)*32768)/(max+Math.abs(min));

        //double result = (IP.hashCode() + hostname.hashCode()+max)*(32768d/(max+abs(min)));
        //Eventueel hash maken over naam EN IP zodat je telkens een verschillende ID hebt

        return (int) result;
    }

    private static void sendPUT(String command, String address) throws IOException {
        URL url = new URL("http://"+address+":8080/"+command);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        int responseCode = connection.getResponseCode();
        if(responseCode == HttpURLConnection.HTTP_OK){
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            System.out.println(response.toString());
        }else {
            System.out.println("PUT request failed");
        }
    }

    private static void sendGET(String command, String address) throws IOException {
        URL url = new URL("http://"+address+":8080/"+command);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        int responseCode = connection.getResponseCode();
        if(responseCode == HttpURLConnection.HTTP_OK){
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            System.out.println(response.toString());
        }else {
            System.out.println("GET request failed");
        }
    }

    private static void sendMultiCast(String message, InetAddress address){  //This is point 1 of Discovery and Bootstrap
        System.out.println("Sending multicast: ["+address+"]: "+message);
        if (message != null) {
            MulticastSocket UDPSocket = null; //creation of a new multicast socket
            try {
                UDPSocket = new MulticastSocket();
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] buffer = message.getBytes(); //here we put every byte of the message into the buffer
            DatagramPacket packet = new DatagramPacket(buffer,buffer.length, address, Client.multiCastPort);
            try {
                if (UDPSocket != null) {
                    UDPSocket.send(packet);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (UDPSocket != null) { //after the message is sent we close the UDP socket again
                UDPSocket.close();
            }
        }
    }

    private static void sendUnicast(String message, InetAddress adress){
        System.out.println("Sending unicast: ["+adress+"]: "+message);
        if (message != null){
            DatagramSocket UDPSocket = null;
            try {
                UDPSocket = new DatagramSocket();
            } catch (SocketException e) {
                e.printStackTrace();
            }
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer,buffer.length, adress, sendPort);
            try {
                if (UDPSocket != null) {
                    UDPSocket.send(packet);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (UDPSocket != null) {
                UDPSocket.close();
            }
        }
    }

    //this is point 5c-d of Discovery and Bootstrap
    public static void update(int id, InetAddress hostAddress) {
        Client.amountOtherNodes = Client.amountOtherNodes + 1;
        System.out.println("New node detected with hash: "+id+" , other nodes on network: " + Client.amountOtherNodes);
        //Als de eerste client joined in het netwerk
        if (Client.currentNodeID == Client.nextNodeID && Client.currentNodeID == Client.previousNodeID) {
            Client.previousNodeID = id;
            Client.nextNodeID = id;
        } else {
            //Niet de eerste, maar de tweede
            if (Client.nextNodeID == Client.previousNodeID){
                if (Client.currentNodeID < id) {
                    Client.nextNodeID = id;
                    sendUnicast("I ["+Client.currentNodeID+"], have put you as my nextNode",hostAddress);
                }
                else {
                    Client.previousNodeID = id;
                    sendUnicast("I ["+Client.currentNodeID+"], have put you as my previousNode",hostAddress);
                }
            }
            //Normale werking
            else if (Client.currentNodeID < id && id < Client.nextNodeID){
                Client.nextNodeID = id;
                sendUnicast("I ["+Client.currentNodeID+"], have put you as my nextNode",hostAddress);
            }
            //Normale werking
            else if (Client.previousNodeID < id && id < Client.currentNodeID) {
                Client.previousNodeID = id;
                sendUnicast("I ["+Client.currentNodeID+"], have put you as my previousNode",hostAddress);
            }
            //Als je op het einde van de ring zit
            else if (Client.currentNodeID < id && Client.nextNodeID < id && Client.currentNodeID > Client.nextNodeID){
                Client.nextNodeID = id;
                sendUnicast("I ["+Client.currentNodeID+"], have put you as my nextNode",hostAddress);
            }
            //Als je in het begin van de ring zit
            else if (Client.previousNodeID < id && Client.currentNodeID < id && Client.previousNodeID > Client.currentNodeID){
                Client.previousNodeID = id;
                sendUnicast("I ["+Client.currentNodeID+"], have put you as my previousNode",hostAddress);
            }
        }
        System.out.println("\nOther nodes in the network: "+Client.amountOtherNodes);
        System.out.println("Previous ID: " + Client.previousNodeID + " || Current ID: "+Client.currentNodeID+" || Next ID: " + Client.nextNodeID);
        System.out.println("Give a command: <help> for a list of all commands");
    }

    /*
    public static void update(int id, InetAddress hostAddress){
        nodes.add(id);
        System.out.println(nodes);
        System.out.println("Sorting nodes through their ID's...");
        Collections.sort(nodes);
        System.out.println(nodes);

        if(nodes.size()==2){
            nextNodeID=id;
            previousNodeID=id;
        }
        else{
            for (int i = 0; i < nodes.size(); i++) {
                if(nodes.get(i)==currentNodeID){
                    if(i==0){
                        previousNodeID=nodes.get(nodes.size());
                        nextNodeID=nodes.get(1);
                    }
                    else if(i==nodes.size()-1){
                        previousNodeID=nodes.get(nodes.size()-2);
                        nextNodeID=nodes.get(0);
                    }
                    else{ // normale werking
                        nextNodeID=nodes.get(i+1);
                        previousNodeID= nodes.get(i-1);
                    }
                }
            }
        }
        System.out.println("\nOther nodes in the network: "+(nodes.size()-1));
        System.out.println("Previous ID: " + previousNodeID + " || Current ID: "+currentNodeID+" || Next ID: " + nextNodeID);
        System.out.println("Give a command: <help> for a list of all commands");
    }*/

    public static void updateInitial(String message) {
        //VB message: 3
        //VB message: 3, Previous ID: 68465, Next ID: 321846
        String amount;
        String previous;
        String next;
        int index = message.indexOf(",");
        if (index != -1) {
            amount = message.substring(0, index);
            index = (message.indexOf("Previous ID: ")) + 13;
            int indexx = message.indexOf(", Next ID: ");
            previous = message.substring(index, indexx);
            index = (message.indexOf("Next ID: ")) + 9;
            next = message.substring(index, message.length());
            Client.amountOtherNodes = Integer.parseInt(amount);
            Client.nextNodeID = Integer.parseInt(next);
            Client.previousNodeID = Integer.parseInt(previous);
        }
        else
            Client.amountOtherNodes = Integer.parseInt(message);
        System.out.println("\nOther nodes in the network: "+Client.amountOtherNodes);
        System.out.println("Previous ID: " + Client.previousNodeID + " || Current ID: "+Client.currentNodeID+" || Next ID: " + Client.nextNodeID);
        System.out.println("Give a command: <help> for a list of all commands");
    }

    public static void exitUpdateNext(String message) {
        String newID;
        String exitID;
        int index = message.indexOf(",");
        newID = message.substring(0,index);
        exitID = message.substring(index+1);
        System.out.println(exitID+" sends exit, updating nextNodeID to : "+newID);
        Client.nextNodeID = Integer.parseInt(newID);
        System.out.println("\nOther nodes in the network: "+Client.amountOtherNodes);
        System.out.println("Previous ID: " + Client.previousNodeID + " || Current ID: "+Client.currentNodeID+" || Next ID: " + Client.nextNodeID);
        System.out.println("Give a command: <help> for a list of all commands");
    }

    public static void exitUpdatePrev(String message) {
        String newID;
        String exitID;
        int index = message.indexOf(",");
        newID = message.substring(0,index);
        exitID = message.substring(index+1);
        System.out.println(exitID+" sends exit, updating previousNodeID to : "+newID);
        Client.nextNodeID = Integer.parseInt(newID);
        System.out.println("\nOther nodes in the network: "+Client.amountOtherNodes);
        System.out.println("Previous ID: " + Client.previousNodeID + " || Current ID: "+Client.currentNodeID+" || Next ID: " + Client.nextNodeID);
        System.out.println("Give a command: <help> for a list of all commands");
    }

}
