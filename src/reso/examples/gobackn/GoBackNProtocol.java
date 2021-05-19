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
import java.util.HashMap;
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
     * Numero de sequence courrant a attribuer dans le buffer
     */
    private int seq_number = 0;

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
    private static double rtt = 0.01;

    private AbstractTimer timer;

    private HashMap<Integer, RTT> RTTs = new HashMap<>();

    private int MSS = Integer.SIZE / 8; // Taille des messages (entier) en octets
    private int cwnd = MSS; // Fenêtre de congestion
    private boolean isSlowStart = true; // True si c'est en phase de Slow Start et False si c'est en phase d'AIMD (Congestion Avoidance)
    private int sstresh = 2000; // Slow start Threshold

    private int duplicateACKs = 0; // Nombre d'acquittements dupliqués

	public GoBackNProtocol(IPHost host) {
		this.host= host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
//    	RTT.scheduler = this.host.getNetwork().getScheduler();
	}
	
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
    	TCPSegment segment= (TCPSegment) datagram.getPayload();
		System.out.println("Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
				" host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
				datagram.dst + ", iif=" + src + ", data=" + segment);

        if(segment.isAck()){

            if (segment.sequenceNumber == send_base){//Calculer le nombre de fois qu'un acquittement dupliqué est recu
                duplicateACKs++;
            }

            if(isSlowStart){ // Si c'est en phase de Slow Start
                if (cwnd >= sstresh){ // Si la fenêtre arrive au seuil(sstresh), on passe a la phase d'AIMD
                    isSlowStart = false;
                }else { // Si n'est pas arrive a son seuil, on incremente la fenêtre de congestion d'un MSS
                    slowStart();
                }

            }else { // Si c'est en phase d'AIMD (Congestion Avoidance)
                if (duplicateACKs == 3){ // S'il ya 3 acquittements dupliqués
                    cwnd = cwnd/2;
                }

                addIncrease();
            }

            int previous_sb = send_base;
            send_base = segment.sequenceNumber +1;

            RTT rtt = RTTs.get(segment.sequenceNumber);
            if (!rtt.retransmission){ // Verifier si ce n'est pas une retransmission du message pour ne pas le considere dans le calcul du RTT
                rtt.on_receive(host.getNetwork().getScheduler());// Detecter la reception de l'Acquittement et Recalculer le RTT ainsi que le RTO
            }

            if(send_base == next_seq_number){
                stopTimer();
            }else {
                if( send_base < pending_msg.size()){
                    startTimer(pending_msg.get(send_base));
                }
            }

            /**
             * Faire évoluer la fenêtre d'émission par rapport au buffer de messages en attentent
             */
            for(int i = previous_sb + window_size; i < send_base + window_size; i++){
                if(i < pending_msg.size() && i >= window_size){ // Verifier s'il existe un prochain message en attente dans le buffer
                    window.add(i, pending_msg.get(i));
                    send(current_dest, pending_msg.get(i));
                }
                else{
                    break;
                }
            }
        }
        else{
           if(exp_seq_number == segment.sequenceNumber){// Si le message est celui attendu (arrivé dans l'ordre)
               last_ack = datagram;// Sauver le dernier datagram acquitte en cas d'une potentielle retransmission
               sendAcknowledgment(datagram);
               exp_seq_number++;
           }else if(last_ack != null){ // Si le message recue n'est celui attendu (perdu ou arrivé dans le désordre)
               System.out.println("Warning! Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)"+ segment + " arrived out of order");
               sendLastAcknowledgment(last_ack);
           }
        }
	}

    public void sendData(int data, IPAddress destination) throws Exception{
        int[] segmentData = new int[]{data};
        current_dest = destination;
        TCPSegment tcpSegment = new TCPSegment(segmentData, seq_number);

        pending_msg.add(tcpSegment);
//
//        AbstractTimer lossTimer = new SimulateLossTimer( host.getNetwork().getScheduler(), RTT/2);
//        lossTimer.start();

        if(seq_number <  window_size){
            window.add(seq_number, tcpSegment);
            send(destination,tcpSegment);
//            host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, tcpSegment);
//
//            if(send_base == next_seq_number){
//                startTimer();
//            }
//            next_seq_number++;
        }

        seq_number++;
    }

    private void send(IPAddress destination, TCPSegment tcpSegment) throws Exception{
        if(send_base == next_seq_number){
            startTimer(tcpSegment);
        }

        if (RTTs.get(next_seq_number) == null){ // Démarrer le calcul du RTT dans le cas d'une non retransmission
            RTT rtt = new RTT(next_seq_number);
            rtt.on_send(host.getNetwork().getScheduler());
            RTTs.put(next_seq_number,rtt);
        }else { // Marquer le RTT de referencement du message comme une retransmission pour ne pas le prendre en compte dans le calcul
            RTTs.get(next_seq_number).retransmission = true;
        }

        host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, tcpSegment);

        next_seq_number++;
    }

    public void timeOut() throws Exception{
	    if (isSlowStart){
	        sstresh = cwnd/2;
	        isSlowStart = false;
        }else {
	        cwnd = MSS;
	        sstresh = sstresh/2;
	        isSlowStart = true;
        }

	    startTimer(window.get(send_base));
        for(int i = send_base; i < next_seq_number; i++){
            host.getIPLayer().send(IPAddress.ANY, current_dest, IP_PROTO_GOBACKN, window.get(i));
        }
    }

    private void sendAcknowledgment(Datagram datagram) throws Exception{
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment(exp_seq_number));
    }

    private void sendLastAcknowledgment(Datagram lastDatagramAck) throws Exception{
        TCPSegment segment= (TCPSegment) lastDatagramAck.getPayload();
        System.out.println("Resend the last acknowledgment"+ segment );
        host.getIPLayer().send(IPAddress.ANY, lastDatagramAck.src, IP_PROTO_GOBACKN, new TCPSegment(segment.sequenceNumber));
    }

    public void setWindow_size(int window_size){
	    this.window_size = window_size;
    }

    public void startTimer(TCPSegment tcpSegment){
	    if(timer != null){
            timer.stop();
        }

	    timer = new MyTimer(tcpSegment, host.getNetwork().getScheduler(), rtt);
        timer.start();
    }

    public void stopTimer(){
	    timer.stop();
    }

    private class MyTimer extends AbstractTimer {

	    TCPSegment segment;
        public MyTimer(TCPSegment tcpSegment, AbstractScheduler scheduler, double interval) {
            super(scheduler, interval,false);
            this.segment = tcpSegment;
        }

        public void run() throws Exception{
            System.out.println("Time Out data: "+segment+ " - Current Time: " + scheduler.getCurrentTime()*1000 + "ms");
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

        public void run() throws Exception{
            System.out.println("Simulate the loss of a sequence number data:" + next_seq_number);
            System.out.println("Current Time: " + scheduler.getCurrentTime()*1000 + "ms");

            next_seq_number ++;

        }
    }

    /**
     * Augmentation additive de la fenêtre de congestion
     */
    private void addIncrease(){ // Additive increase
        cwnd = cwnd + (MSS * MSS / cwnd);
    }

    /**
     * Diminution multiplicative de la fenêtre de congestion
     */
    private void multDecrease(){ // Multiplicative decrease
        cwnd = cwnd / 2;
    }

    /**
     * Incremente la fenetre de congestion d'un MSS en phase de Slow start
     */
    private void slowStart(){
	    cwnd = cwnd + MSS;
    }
}
