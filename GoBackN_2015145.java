import java.io.*;
import java.net.*;
import java.util.ArrayList;

class Packet implements Serializable{

    public int src;
    public int dest;
    public int seqno;
    public int rcwnd;
    public int ackno;
    public boolean ack = false;
    public int dupack = 0;
    public long time;
    public boolean end = false;
    public byte[] data = new byte[512];

    public Packet(byte[] data,int no){
        this.data = data;
        this.seqno = no;
    }
}

class Sender {

    private static DatagramSocket outsocket;
    private static DatagramSocket insocket;
    private static int windowS = 0;
    private static InetAddress dest_src;
    private static int outport;
    private static int inport;
    private static int windowsize = 1;
    private static int ssthresh = 4;
    private static int windowE = windowsize + windowS;
    private String datafile;
    private static ArrayList<Packet> list = new ArrayList<>();
    private int seq = 0;
    private static int timeout;
    private static BufferedWriter Logger;
    private static int recieverWindow;
    private static boolean[] record = new boolean[10000];

    Sender(int outport, InetAddress ip, String s, int inputport, int timeout, BufferedWriter logger) throws SocketException {
        outsocket = new DatagramSocket();
        this.datafile = s;
        dest_src = ip;
        this.outport = outport;
        this.inport = inputport;
        this.timeout = timeout;
        insocket = new DatagramSocket(inport);
        this.Logger = logger;
        insocket.setSoTimeout(5);
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Sender sender = new Sender(8000,InetAddress.getByName("127.0.0.1"),"datafile.txt",8001,500,new BufferedWriter(new FileWriter(new File("Clientlogger.txt"))));
        sender.makePackets();
        sender.send();
        RecvAck();
    }

    private void makePackets() throws IOException {
        InputStream inputStream = new FileInputStream(new File(datafile));
        byte[] data = new byte[512];
        int length = inputStream.read(data);
        while (length != -1) {
            if (!(length > 512)) {
                byte[] data1 = new byte[length];
                System.arraycopy(data, 0, data1, 0, length);
                data = data1;
            }
            list.add(new Packet(data.clone(), seq++));
            length = inputStream.read(data);
        }
    }


    private void send() throws IOException {
        for (int i = windowS; i < windowE && i < list.size(); i++) {
            if (!list.get(i).ack) {
                record[i] = true;
                list.get(i).time = System.currentTimeMillis();
                list.get(i).src = inport;
                list.get(i).dest = outport;
                list.get(i).dupack = 0;
                sendPacket(list.get(i));
            }
        }
    }

    private static void sendPacket(Packet packet) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);
        outputStream.writeObject(packet);
        outputStream.flush();
        byte[] datatoSend;
        datatoSend = byteArrayOutputStream.toByteArray();
        Logger.write("Sending pack: " + packet.seqno + "\n");
        Logger.flush();
        outsocket.send(new DatagramPacket(datatoSend, datatoSend.length, dest_src, outport));
    }

    private static void RecvAck() throws IOException, ClassNotFoundException {
        while (true) {
            try{
                byte[] ack = new byte[1024];
                DatagramPacket packet = new DatagramPacket(ack,ack.length);
                insocket.receive(packet);
                Packet finalPacket = (Packet) (new ObjectInputStream(new ByteArrayInputStream(packet.getData())).readObject());
                Logger.write("Ack Recieved: " + finalPacket.ackno + " " + "for packet number: " + (finalPacket.ackno - 1) + "\n");
                Logger.flush();
                recieverWindow = finalPacket.rcwnd;
                int nwindowE;
                if (AckRecord(finalPacket.ackno)) {
                    if (windowsize <= ssthresh) {
                        if (windowsize * 2 > recieverWindow)
                            windowsize = recieverWindow;
                        else windowsize = windowsize * 2;
                    }
                    else if (windowsize > ssthresh) {
                        windowsize = windowsize + 1;
                    }
                    if(windowsize>recieverWindow) {
                        windowsize = recieverWindow;
                    }
                    Logger.write("Congestion Window: " + windowsize+"\n");
                    Logger.flush();
                    windowS = finalPacket.ackno;
                    System.out.println("windows: "+windowS+" windowe: "+windowE);
                    if(windowS+windowsize>list.size())
                        nwindowE = list.size();
                    else nwindowE = windowS + windowsize;
                    for (int i = windowE;i<nwindowE;i++) {
                        record[i] = true;
                        sendSinglePacket(list.get(i));
                    }
                } else {
                    ssthresh = windowsize;
                    if(windowsize==1)
                        windowsize = 1;
                    else windowsize = windowsize/2;
                    Logger.write("Duplicate Ack recieved of: "+(finalPacket.ackno-1)+"\n");
                    Logger.write("ssthresh: " + ssthresh+"\n");
                    Logger.flush();
                    windowS = finalPacket.ackno;
                    if (windowS + windowsize > list.size())
                        nwindowE = list.size();
                    else nwindowE = windowS + windowsize;
                }
                windowE = nwindowE;
            }catch (Exception e){
                int t = -1;
                for (int i = windowS; i < windowE; i++) {
                    if (!list.get(i).ack) {
                        t = i;
                        break;
                    }
                }
                if (t != -1) {
                    if (System.currentTimeMillis() - list.get(t).time > timeout) {
                        for (int i = windowS; i < windowE; i++) {
                            if (!list.get(i).ack && record[i]) {
                                list.get(i).time = System.currentTimeMillis();
                                list.get(i).src = inport;
                                list.get(i).dest = outport;
                                try {
                                    Logger.write("Retransmitting: " + list.get(i).seqno + " ");
                                    Logger.flush();
                                    sendPacket(list.get(i));
                                } catch (IOException fe) {
                                    fe.printStackTrace();
                                }
                            }
                        }
                        if(windowsize==1)
                            ssthresh = 1;
                        else ssthresh = windowsize/2;
                        windowsize = 1;
                        Logger.write("ssthresh: "+ssthresh+"\n");
                        Logger.write("Congestion window: "+windowsize+"\n");
                        if (windowS + windowsize > list.size())
                            windowE = list.size();
                        else windowE = windowS + windowsize;
                    }
                }
            }
            if (!Stop()){
                Packet finish = new Packet(null, -1);
                finish.end = true;
                sendPacket(finish);
                sendPacket(finish);
                sendPacket(finish);
                sendPacket(finish);
                System.exit(0);
            }
        }
    }

    private static void sendSinglePacket(Packet packet) throws IOException {
        packet.time = System.currentTimeMillis();
        packet.src = inport;
        packet.dest = outport;
        packet.dupack = 0;
        sendPacket(packet);
    }

    private static boolean AckRecord(int ackno) {
        for (Packet aList : list) {
            if (aList.seqno <= ackno - 1) {
                if(!aList.ack) {
                    aList.ack = true;
                    aList.dupack++;
                }
            }
            if (aList.dupack >= 3)
                return false;
        }
        return true;
    }

    private static boolean Stop() {
        for (Packet aList : list) {
            if (!aList.ack) {
                return true;
            }
        }
        return false;
    }

}
