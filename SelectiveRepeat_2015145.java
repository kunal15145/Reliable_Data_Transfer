import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;

class Packet1 implements Serializable {

    public int src;
    public int dest;
    public int seqno;
    public int ackno;
    public boolean ack = false;
    public int dupack = 0;
    public long time;
    public boolean end = false;
    public byte[] data = new byte[512];

    public Packet1(byte[] data,int no){
        this.data = data;
        this.seqno = no;
    }
}

class SelectiveRepeat {

    private static int outport;
    private static int inport;
    private static InetAddress ip;
    private static String datafile;
    private static int timeout;
    private static BufferedWriter logger;
    private static ArrayList<Packet1> list = new ArrayList<>();
    private static int seq = 0;
    private static int windowsize = 4;
    private static String k = "127.0.0.1";
    private static DatagramSocket outsocket;
    private static DatagramSocket insocket;
    private static int windows = 0;
    private static int windowe = windows + windowsize;
    private static boolean[] record = new boolean[10000];


    public SelectiveRepeat(int outport, InetAddress ip, String datafile, int inputport, int timeout, BufferedWriter clientLogger) throws IOException, ClassNotFoundException {
        this.outport = outport;
        this.ip = ip;
        this.inport = inputport;
        this.timeout = timeout;
        this.datafile = datafile;
        this.logger = clientLogger;
        outsocket = new DatagramSocket();
        insocket = new DatagramSocket(inport);
        insocket.setSoTimeout(15);
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        new SelectiveRepeat(8000,InetAddress.getByName("127.0.0.1"),"datafile.txt",8001,500,new BufferedWriter(new FileWriter(new File("clientlogger.txt"))));
        makePackets();
        recvAck();
    }

    private static void Send() throws IOException {
        for(int i=windows;i<windowe;i++){
            record[i]=true;
            sendSinglePacket(list.get(i));
        }
    }

    private static void makePackets() throws IOException {
        InputStream inputStream = new FileInputStream(new File(datafile));
        byte[] data = new byte[512];
        int length = inputStream.read(data);
        while (length != -1) {
            if (!(length > 512)) {
                byte[] data1 = new byte[length];
                System.arraycopy(data, 0, data1, 0, length);
                data = data1;
            }
            list.add(new Packet1(data.clone(), seq++));
            length = inputStream.read(data);
        }
    }

    private static void sendSinglePacket(Packet1 packet1) throws IOException {
        packet1.time = System.currentTimeMillis();
        packet1.src = inport;
        packet1.dest = outport;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream);
        outputStream.writeObject(packet1);
        outputStream.flush();
        byte[] datatoSend;
        datatoSend = byteArrayOutputStream.toByteArray();
        logger.write("Sending Pack: "+packet1.seqno+"\n");
        logger.flush();
        outsocket.send(new DatagramPacket(datatoSend, datatoSend.length,
                InetAddress.getByName(k), outport));
    }
    
    private static boolean Stop() {
        for(Packet1 packet1:list){
            if(!packet1.ack)
                return true;
        }
        return false;
    }

    private static void recvAck() throws IOException, ClassNotFoundException {
        Send();
        while (true) {
            if(packetAvailable()) {
                int temp = checkPacketTosend();
                if(temp!=-1) {
                    record[temp] = true;
                    sendSinglePacket(list.get(temp));
                }
            }
            for(int i=windows;i<windowe;i++){
                if(System.currentTimeMillis()-list.get(i).time>timeout){
                    RetransmitPackets();
                }
            }
            try {
                byte[] ack = new byte[1024];
                DatagramPacket packet = new DatagramPacket(ack, ack.length);
                insocket.receive(packet);
                Packet1 finalPacket = (Packet1) (new ObjectInputStream(new ByteArrayInputStream(packet.getData())).readObject());
                logger.write("Recieved ack: "+finalPacket.ackno+" for "+(finalPacket.ackno-1)+"\n");
                logger.flush();
                if (finalPacket.ackno - 1 >= windows && finalPacket.ackno - 1 < windowe) {
                    list.get(finalPacket.ackno - 1).ack = true;
                    windows = NextUnackedPack();
                    if(windows+windowsize>list.size())
                        windowe = list.size();
                    else windowe = windows+windowsize;
                }
            }catch (Exception e){
                System.out.print("");
            }
            if (!Stop()) {
                Packet1 packet1 = new Packet1(null, -1);
                packet1.end = true;
                sendSinglePacket(packet1);
                sendSinglePacket(packet1);
                sendSinglePacket(packet1);
                sendSinglePacket(packet1);
                System.exit(0);
            }
        }
    }

    private static int NextUnackedPack() {
        for(int i=0;i<list.size();i++){
            if(!list.get(i).ack)
                return i;
        }
        return -1;
    }

    private static void RetransmitPackets() throws IOException {
        logger.write("Timeout"+"\n");
        logger.flush();
        for(int i=windows;i<windowe;i++){
            if(!list.get(i).ack)
                sendSinglePacket(list.get(i));
        }
    }

    private static int checkPacketTosend() {
        for(int i=windows;i<windowe;i++){
            if(!record[i])
                return i;
        }
        return -1;
    }

    private static boolean packetAvailable() {
        for(int i=windows;i<windowe;i++){
            //System.out.print();
            if(!record[i]){
                return true;
            }
        }
        return false;
    }
}
