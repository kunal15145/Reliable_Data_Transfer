import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;

class Reciever1 {

    private static int outport;
    private static int inport;
    private static InetAddress ip;
    private static String s;
    private static BufferedWriter logger;
    private static ArrayList<Packet1> list = new ArrayList<>();
    private static DatagramSocket outsocket;
    private static DatagramSocket insocket;
    private static int windowsize = 4;
    private static int windows = 0;
    private static int windowe = windows + windowsize;
    private static String dest_src = "127.0.0.1";
    private static boolean[] record = new boolean[10000];

    public Reciever1(int outport, InetAddress ip, String s, int inputport, BufferedWriter serverLogger) throws IOException, ClassNotFoundException {
        this.outport = outport;
        this.ip = ip;
        this.inport = inputport;
        this.s = s;
        this.logger = serverLogger;
        outsocket = new DatagramSocket();
        insocket = new DatagramSocket(this.outport);
        for(int i=0;i<record.length;i++) {
            record[i] = false;
        }
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        new Reciever1(8000,InetAddress.getByName("127.0.0.1"),"output.txt",8001,new BufferedWriter(new FileWriter(new File("Serverlogger.txt"))));
        recieve();
    }

    private static void recieve() throws IOException, ClassNotFoundException {
        boolean fin = false;
        while (!fin) {
            byte[] dataRecieved = new byte[1024];
            DatagramPacket packetRe = new DatagramPacket(dataRecieved, dataRecieved.length);
            insocket.receive(packetRe);
            Packet1 recvPack = (Packet1) new ObjectInputStream(new ByteArrayInputStream(packetRe.getData())).readObject();
            logger.write("Recieved Packet: " + recvPack.seqno+"\n");
            logger.flush();
            if (recvPack.seqno >= windows && recvPack.seqno < windowe) {
                sendAck(recvPack.seqno);
                if(checkforDuplicate(recvPack.seqno)){
                    logger.write("Duplicate packet: "+recvPack.seqno+"\n");
                    logger.flush();
                }
                if (recvPack.seqno >= windowe) {
                    list.add(recvPack);
                }
                if (recvPack.seqno >= windows && recvPack.seqno < windowe) {
                    list.add(recvPack);
                    int temp = getNextNot();
                    if (temp == -1) {
                        windows = windows + windowsize;
                        windowe = windows + windowsize;
                    } else {
                        windows = getNextNot();
                        windowe = windows + windowsize;
                    }
                }
            }
            else sendAck(recvPack.seqno);
            fin = recvPack.end;
        }
        writetofile();
    }

    private static boolean checkforDuplicate(int seqno) {
        for (Packet1 aList : list) {
            if (aList.seqno == seqno)
                return true;
        }
        return false;
    }

    private static void writetofile() throws IOException {
        list.sort(Comparator.comparingInt(packet -> packet.seqno));
        OutputStream outputStream;
        File file = new File(s);
        if(!file.exists())
            file.createNewFile();
        outputStream = new FileOutputStream(s);
        for (Packet1 aList : list) {
            outputStream.write(aList.data);
            outputStream.flush();
        }
        outputStream.close();
        System.exit(0);
    }

    private static void sendAck(int sqno) throws IOException {
        Packet1 Ack = new Packet1(null, sqno);
        Ack.ack = true;
        Ack.src = outport;
        Ack.dest = inport;
        Ack.ackno = sqno+1;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);
        outputStream.writeObject(Ack);
        outputStream.flush();
        logger.write("Sending Ack: "+(sqno+1)+" for "+sqno+"\n");
        logger.flush();
        outsocket.send(new DatagramPacket(byteArrayOutputStream.toByteArray(),
                byteArrayOutputStream.toByteArray().length,InetAddress.getByName(dest_src),
                inport));
    }


    private static int getNextNot() {
        int q = -1;
        for(int i=windows;i<windowe;i++){
            if(!Check(i)) {
                return i;
            }
        }
        return q;
    }

    private static boolean Check(int i) {
        for (Packet1 aList : list) {
            if (aList.seqno == i) {
                return true;
            }
        }
        return false;
    }
}
