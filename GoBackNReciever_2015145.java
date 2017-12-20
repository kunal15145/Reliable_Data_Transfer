import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.io.*;

class Reciever{

    private static DatagramSocket outsocket;
    private static DatagramSocket insocket;
    private static InetAddress dest_src;
    private static int outport;
    private static int inport;
    private static int RecieverWindow = 5;
    private static String outputfile;
    private static ArrayList<Packet> list = new ArrayList<>();
    private static int seq = 0;
    private static BufferedWriter Logger;
    private static Packet LastPacket;
    private static ArrayList<Integer> seq1=new ArrayList<>();

    Reciever(int outport, InetAddress ip, String datafile, int inputport, BufferedWriter logger) throws IOException, ClassNotFoundException {
        outsocket = new DatagramSocket();
        this.outputfile = datafile;
        dest_src = ip;
        this.outport = outport;
        this.inport = inputport;
        this.Logger = logger;
        insocket = new DatagramSocket(inport);
        LastPacket = new Packet(null,-1);

    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Reciever reciever = new Reciever(8001,InetAddress.getByName("127.0.0.1"),"output.txt",8000,new BufferedWriter(new FileWriter(new File("Serverlogger.txt"))));
        recieve();
    }

    private static void recieve() throws IOException, ClassNotFoundException {
        boolean fin = false;
        while (!fin) {
            byte[] dataRecieved = new byte[1024];
            DatagramPacket packetRe = new DatagramPacket(dataRecieved, dataRecieved.length);
            insocket.receive(packetRe);
            Packet recvPack = (Packet) new ObjectInputStream(new ByteArrayInputStream(packetRe.getData())).readObject();
            Logger.write("Recieved Packet: " + recvPack.seqno + "\n");
            Logger.flush();
            fin = recvPack.end;
            if (seq1.contains(recvPack.seqno)) {
                Logger.write("Duplicate Packet: " + recvPack.seqno + "\n");
                Logger.flush();
                sendAck();
            } else seq1.add(recvPack.seqno);
            if (LastPacket.seqno + 1 == recvPack.seqno) {
                LastPacket = recvPack;
                ConfirmPacket(recvPack);
                list.sort(Comparator.comparingInt(packet -> packet.seqno));
            }
            if (fin) {
                writeToFile();
            }
        }
    }

    public static void writeToFile() throws IOException {
        OutputStream outputStream;
        File file = new File(outputfile);
        if(!file.exists())
            file.createNewFile();
        outputStream = new FileOutputStream(outputfile);
        for (Packet aList : list) {
            outputStream.write(aList.data);
            outputStream.flush();
        }
        outputStream.close();
        System.exit(0);
    }

    private static void ConfirmPacket(Packet recvPack) throws IOException {
        boolean checkAlready = false;

        for (Packet aList : list) {
            //System.out.println("checking seq no:"+recvPack.seqno+"  "+aList.seqno);
            if (recvPack.seqno == aList.seqno) {
                checkAlready = true;
                Logger.write("Packet with "+recvPack.seqno+" already present\n");
                Logger.flush();
                break;
            }
        }
        if(!checkAlready){
            Logger.write("Successfully inserted Packet no "+recvPack.seqno+" to the buffer\n");
            Logger.flush();
            list.add(recvPack);
            sendAck();
        }
    }

    private static void sendAck() throws IOException {
       if(list.contains(LastPacket)) {
           Packet Ack = new Packet(null, seq++);
           Ack.rcwnd = RecieverWindow;
           Ack.ack = true;
           Ack.src = outport;
           Ack.dest = inport;
           Ack.ackno = LastPacket.seqno+1;
           ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
           ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);
           outputStream.writeObject(Ack);
           outputStream.flush();
           Logger.write("Sending Ack: "+Ack.ackno+" for packet "+(Ack.ackno-1)+"\n");
           Logger.flush();
           outsocket.send(new DatagramPacket(byteArrayOutputStream.toByteArray(), byteArrayOutputStream.toByteArray().length, dest_src,outport));
       }
    }
}
