package reso.examples.gobackn;

import reso.common.AbstractTimer;
import reso.ip.Datagram;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPInterfaceAdapter;
import reso.ip.IPInterfaceListener;
import reso.ip.IPLayer;
import reso.scheduler.AbstractScheduler;

import java.util.ArrayList;
import java.util.List;

public class GoBackNProtocol implements IPInterfaceListener {

	public static final int IP_PROTO_GOBACKN= Datagram.allocateProtocolNumber("GOBACKN");
	
	private final IPHost host;

    /**
     * Send window size
     */
    private int window_size = 10;

    /**
     * Send window
     */
    private List<TCPSegment> window = new ArrayList<TCPSegment>();

    /**
     * Pending messages buffer
     */
    private List<TCPSegment> pending_msg = new ArrayList<TCPSegment>();

    /**
     * First sequence number in the send window
     */
    private int send_base = 0;

    /**
     * Next sequence number to use in the send window to send a new message
     */
    private int next_seq_number = 0;

    /**
     * Last message acknowledged
     */
	private Datagram last_ack = null;

	/**
     * Expected sequence number
     */
	private int exp_seq_number = 0;

    /**
     * Current destination
     */
    private IPAddress current_dest;

    /** Duration of RTT in seconds (simulated time) */
    private static double RTT = 0.2;

    private AbstractTimer timer;


	public GoBackNProtocol(IPHost host) {
		this.host= host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
	}
	
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
    	TCPSegment segment= (TCPSegment) datagram.getPayload();
		System.out.println("Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
				" host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
				datagram.dst + ", iif=" + src + ", data=" + segment);
        if(segment.isAck()){
           send_base = segment.sequenceNumber +1;
           if(send_base == next_seq_number){
               stopTimer();
           }else {
               startTimer();
           }
        }
        else{
           if(exp_seq_number == segment.sequenceNumber){
               last_ack = datagram;
               sendAcknowledgment(datagram);
               exp_seq_number++;
           }else if(last_ack != null){
               System.out.println("Data "+ segment + " arrived out of order");
               sendLastAcknowledgment(last_ack);
           }
        }
	}

    public void sendData(int data, IPAddress destination) throws Exception{
        int[] segmentData = new int[]{data};
        current_dest = destination;
        TCPSegment tcpSegment = new TCPSegment(segmentData, next_seq_number);

        AbstractTimer lossTimer = new SimulateLossTimer( host.getNetwork().getScheduler(), RTT/2);
        lossTimer.start();

        if(next_seq_number <  send_base + window_size){
            window.add(next_seq_number, tcpSegment);
            host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, tcpSegment);

            if(send_base == next_seq_number){
                startTimer();
            }
            next_seq_number++;
        }
        else {
            pending_msg.add(tcpSegment);
        }
    }

    public void timeOut() throws Exception{
	    startTimer();
        for(int i = send_base; i < next_seq_number; i++){
            host.getIPLayer().send(IPAddress.ANY, current_dest, IP_PROTO_GOBACKN, window.get(i));
        }
    }

    private void sendAcknowledgment(Datagram datagram) throws Exception{
        TCPSegment segment= (TCPSegment) datagram.getPayload();
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment(exp_seq_number));
    }

    private void sendLastAcknowledgment(Datagram datagram) throws Exception{
        TCPSegment segment= (TCPSegment) datagram.getPayload();
        System.out.println("Resend the last acknowledgment"+ segment );
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment(exp_seq_number));
    }

    public void setWindow_size(int window_size){
	    this.window_size = window_size;
    }

    public void startTimer(){
	    if(timer != null){
            timer.stop();
        }

	    timer = new MyTimer( host.getNetwork().getScheduler(), RTT);
        timer.start();
    }

    public void stopTimer(){
	    timer.stop();
    }

    private class MyTimer extends AbstractTimer {

        public MyTimer(AbstractScheduler scheduler, double interval) {
            super(scheduler, interval,true);
        }

        @Override
        public void run() throws Exception{
            System.out.println("Current Time: " + scheduler.getCurrentTime()*1000 + "ms");
            timeOut();
//            while ((host.getNetwork().getScheduler().getCurrentTime() < rtt)){
//            }
//            try {
//                timeOut();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
        }
    }

    private class SimulateLossTimer extends AbstractTimer {
        public SimulateLossTimer(AbstractScheduler scheduler, double interval) {
            super(scheduler, interval,false);
        }

        @Override
        public void run() throws Exception{
            System.out.println("Simulate the loss of a sequence number data:" + next_seq_number);
            System.out.println("Current Time: " + scheduler.getCurrentTime()*1000 + "ms");

            next_seq_number ++;

        }
    }
}
