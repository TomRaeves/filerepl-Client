package com.company.filereplClient;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
import java.util.Scanner;
import java.util.stream.Collectors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Client {

    //https://www.baeldung.com/java-broadcast-multicast

    //This is for UDP
    private static final int receivePort = 4500;
    private static final int sendPort = 4501;
    private static final int multiCastPort = 3456;
    private static final String multicastAddress = "225.10.10.10"; //Dit moet nog specifiek worden

    //This is for TCP
    private static final int TCPServerSendPort = 5501;
    private static final int TCPFileSendPort = 5502;
    private static final int TCPFileReceivePort = TCPFileSendPort;
    private static InetAddress serverAddress;

    public static int amountOtherNodes = 0;

    private static int previousNodeID = -1;
    private static int currentNodeID= -1;
    private static int nextNodeID = -1;
    private static String hostName;

    private static boolean running = true;

    public static void main(String[] args) {
        SpringApplication.run(Client.class, args);

        Scanner sc = new Scanner(System.in);
        //Hostname
        System.out.println("Choose your hostname: ");
        String hostName = sc.nextLine();
        //hostID
        Client.currentNodeID = hashCode(hostName); //read the name and then hash the name ////this is point 5b of Discovery and Bootstrap
        Client.nextNodeID = Client.currentNodeID;
        Client.previousNodeID = Client.currentNodeID;
        //ServerIP
        System.out.println("Give the IP-address of the server: ");
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

        //TCPFileHandler
        TCPFileReceiveHandler TCPFileReceive = new TCPFileReceiveHandler(TCPFileReceivePort);
        TCPFileReceive.start();

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
                    TCPFileReceive.shutdown();
                    Client.running = false;
                    //try {
                    //    sendPUT("removeNode", sAddress);
                    //}catch (IOException err) {err.printStackTrace();}
                    break;

                case "help":
                    System.out.println("List of all commands:");
                    System.out.println("Leave network: 'Exit'");
                    System.out.println("Get ID of a node: 'getID'");
                    System.out.println("Add file to client: 'addFile'");
                    System.out.println("Show your previous and next ID: 'show'");
                    System.out.println("Find ID of host where file is saved: 'searchFile'\n");
                    break;

                case "getID":
                    try {
                        sendGET("getID",sAddress);
                    } catch (IOException err) {
                        err.printStackTrace();
                    }
                    break;

                case "addFile":
                    System.out.println("\nGive the name for the file to be added: ");
                    String name = sc.nextLine();
                    try {
                        sendPUT("addFile/"+name,sAddress);
                    } catch (IOException err) {
                        err.printStackTrace();
                    }
                    break;

                case "searchFile":
                    System.out.println("\nGive the name of the file you want to find the owner of: ");
                    name = sc.nextLine();
                    try {
                        sendGET("searchFile/"+name,sAddress);
                    } catch (IOException err) {
                        err.printStackTrace();
                    }
                    break;

                case "show":
                    topologyInfo();
                    break;

                default:
                    break;
            }
        }
        System.out.println("\nQuiting program, till next time!");
        System.exit(0);
    }

    private static void replicationStart(InetAddress serverAddress) {
        System.out.println(" ");
        System.out.println("----------------------------------------------------");
        System.out.println("Starting replication process...");
        String location = System.getProperty("user.dir");
        ArrayList<String> fileList = new ArrayList<>();
        fileList = scanFiles(location);
        if (fileList.size() != 0) {
            Iterator<String> iterator = fileList.iterator();
            System.out.println("Creating TCP connection with server on port: "+TCPServerSendPort);
            while(iterator.hasNext()){
                Socket socket = null;
                OutputStream outputStream = null;
                InputStream inputStream = null;
                DataOutputStream dataOutputStream = null;
                DataInputStream dataInputStream = null;
                String received = null;
                try {
                    socket = new Socket(serverAddress, TCPServerSendPort);
                    outputStream = Objects.requireNonNull(socket).getOutputStream();
                    inputStream = Objects.requireNonNull(socket).getInputStream();
                    dataOutputStream = new DataOutputStream(outputStream);
                    dataInputStream = new DataInputStream(Objects.requireNonNull(inputStream));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                NodeFile file = new NodeFile(iterator.next());
                try {
                    dataOutputStream.writeUTF(hostName+","+file.getFilename()); //hostName,fileName
                    System.out.println("Sending TCP: ["+serverAddress+"]: "+hostName+","+file.getFilename());
                    dataOutputStream.flush();
                    received = dataInputStream.readUTF();
                    System.out.println("[" + socket.getInetAddress() + "]TCP packet received: " + received);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                TCPFileSendHandler TCPFileSend = new TCPFileSendHandler(received,file.getFilename(),hostName,TCPFileSendPort,file.getFilename()); //moet laatste niet filepath zijn ???
                TCPFileSend.start();
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Closing TCP connection on port: "+TCPServerSendPort);
        }else
            System.out.println("No files on this node, waiting for replicated files from other nodes...");
        System.out.println("----------------------------------------------------");
    }

    private static ArrayList<String> scanFiles(String fileLocation) {
        fileLocation = fileLocation.concat("/nodeFiles"); //for Linux
        //fileLocation = fileLocation.concat("\\nodeFiles"); //for windows
        try (Stream<Path> walk = Files.walk(Paths.get(fileLocation))) {
            ArrayList<String> fileList = new ArrayList<>();
            List<String> result = walk.filter(Files::isRegularFile).map(x -> x.toString()).collect(Collectors.toList());
            //remove full directory, so that only the filename remains and add to "fileList"
            for (String x : result) {
                x = x.replaceAll("[\\/|\\\\|\\*|\\:|\\||\"|\'|\\<|\\>|\\{|\\}|\\?|\\%|,]","");
                String[] parts = x.split("nodeFiles"); //add directory name here
                String file = parts[1];
                fileList.add(file);
            }
            return fileList;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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
        System.out.println("\nNew node detected with hash: "+id+" , other nodes on network: " + Client.amountOtherNodes);
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
        topologyInfo();
    }

    public static void updateInitial(String message) {
        String amount;
        String previous;
        String next;
        int index = message.indexOf(",");
        if (index != -1) {
            replicationStart(serverAddress);
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
        topologyInfo();
    }

    public static void exitUpdateNext(String message) {
        String newID;
        String exitID;
        int index = message.indexOf(",");
        newID = message.substring(0,index);
        exitID = message.substring(index+1);
        System.out.println(exitID+" sends exit, updating nextNodeID to : "+newID);
        Client.nextNodeID = Integer.parseInt(newID);
    }

    public static void exitUpdatePrev(String message) {
        String newID;
        String exitID;
        int index = message.indexOf(",");
        newID = message.substring(0,index);
        exitID = message.substring(index+1);
        System.out.println(exitID+" sends exit, updating previousNodeID to : "+newID);
        Client.previousNodeID = Integer.parseInt(newID);
    }

    public static void topologyInfo(){
        System.out.println("\nOther nodes in the network: "+Client.amountOtherNodes);
        System.out.println("Previous ID: " + Client.previousNodeID + " || Current ID: "+Client.currentNodeID+" || Next ID: " + Client.nextNodeID);
        System.out.println("Give a command: <help> for a list of all commands");
    }
}
