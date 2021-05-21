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

//    /**
//     * Send window size
//     */
//    private int window_size = 10;

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
//    private static double rtt = 0.01;

    private AbstractTimer timer;

    private HashMap<Integer, RTT> RTTs = new HashMap<>();

    /**
     * Taille des messages en paquet
     */
    private int MSS = 1;
    /**
     * Fenêtre de congestion
     */
    private int cwnd = MSS;
    /**
     * True si c'est en phase de Slow Start et False si c'est en phase d'AIMD (Congestion Avoidance)
     */
    private boolean isSlowStart = true;
    /**
     * Slow start Threshold
     */
    private int ssthresh = 100;

    /**
     * Nombre d'acquittements dupliqués
     */
    private int duplicateACKs = 0;



	public GoBackNProtocol(IPHost host) {
		this.host= host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
    	RTT.scheduler = this.host.getNetwork().getScheduler();
    	init_log_cwn();
    	init_log_congControl();
	}
	
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
    	TCPSegment segment= (TCPSegment) datagram.getPayload();
		System.out.println("Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
				" host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
				datagram.dst + ", iif=" + src + ", data=" + segment);

        if(segment.isAck()){  // Si le paquet recu est un acquittement

            if (segment.sequenceNumber == (send_base - 1) ){ //Si l'acquittement correspond au dernier paquet deja acquitté, calculer le nombre de fois qu'un acquittement dupliqué est recu
                duplicateACKs++;
            }

            /**
             * Fast recovery, implementing multiplicative decrease. cwnd will not drop below 1 MSS.
             */
            if (duplicateACKs == 3){ // S'il ya 3 acquittements dupliqués
                multiplDecrease();
                isSlowStart = false;
            }

            if(isSlowStart){ // Si c'est en phase de Slow Start
                if (cwnd > ssthresh){ // Si la fenêtre arrive au seuil(ssthresh), on passe au Congestion Avoidance
                    isSlowStart = false;
                }else { // Si n'est pas arrivé a son seuil, on incremente la fenêtre de congestion d'un MSS
                    slowStart();
                }

            }else { // Si c'est en phase d'AIMD (Congestion Avoidance)
                addIncrease();
            }

            int previous_sb = send_base;
            send_base = segment.sequenceNumber +1;

            RTT rtt = RTTs.get(segment.sequenceNumber);
            if (!rtt.retransmission){ // Verifier si ce n'est pas une retransmission du message pour ne pas le considere dans le calcul du RTT
                rtt.on_receive();// Detecter la reception de l'Acquittement et Recalculer le RTT ainsi que le RTO
            }

            if(send_base == next_seq_number){ // Si le message acquitté est le dernier de la fenêtre d'émission
                stopTimer();
            }else {
                if( send_base < window.size()){ // Si ya encore des paquets envoyé dans la fenêtre d'émission (en attente d'acquittement)
                    startTimer(window.get(send_base)); // Démarrer le Timer pour le plus vieux message de la fenêtre d'émission
                }
            }

            /**
             * Faire évoluer la fenêtre d'émission par rapport au buffer de messages en attentent
             */
            for(int i = next_seq_number; i < send_base + cwnd; i++){ // A partir du dernier numéro de sequence a utiliser jusqu'au nombre de place disponible dans la fenêtre d'émission
                if(i < pending_msg.size()){ // Verifier s'il existe un prochain message en attente dans le buffer
                    window.add(i, pending_msg.get(i));
                    send(current_dest, pending_msg.get(i));
                }
                else{
                    break;
                }
            }
        }
        else{ // Si le paquet recu est un message
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

        pending_msg.add(tcpSegment); // Garder les paquets a envoyer dans un buffer
//
//        AbstractTimer lossTimer = new SimulateLossTimer( host.getNetwork().getScheduler(), RTT/2);
//        lossTimer.start();

        if(seq_number <  cwnd){ //Envoyer les premiers paquets dont les numéros de sequence sont compris dans la fenêtre d'émission
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

    /**
     * Envoyer un paquet a sa destination et démarrer le timer pour le plus vieux paquet de la fenêtre d'émission
     * @param destination
     * @param tcpSegment
     * @throws Exception
     */
    private void send(IPAddress destination, TCPSegment tcpSegment) throws Exception{
        if(send_base == next_seq_number){ // Démarrer le Timer si c'est le plus vieux paquet dans la fenêtre d'émission
            startTimer(tcpSegment);
        }

        if (RTTs.get(next_seq_number) == null){ // Démarrer le calcul du RTT dans le cas d'une non retransmission
            RTT rtt = new RTT(next_seq_number);
            rtt.on_send();
            RTTs.put(next_seq_number,rtt);
        }else { // Marquer le RTT de referencement du message comme une retransmission pour ne pas le prendre en compte dans le calcul
            RTTs.get(next_seq_number).retransmission = true;
        }

        host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, tcpSegment);

        next_seq_number++;
    }

    public void timeOut() throws Exception{
        /**
         * Timeout, enter slow start
         */
        setCwnd(MSS);
        ssthresh = ssthresh/2;
        isSlowStart = true;

//	    if (isSlowStart){ // Si en phase Slow start
//            ssthresh = cwnd/2;
//	        isSlowStart = false;
//        }else { // Si en phase AIMD (Congestion Avoidance)
//	        cwnd = MSS;
//            ssthresh = ssthresh/2;
//	        isSlowStart = true;
//        }
        /**
         * Démarrer le timer et retransmettre tous les paquets depuis le dernier non acquitté;
         */
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

    public void startTimer(TCPSegment tcpSegment){
	    if(timer != null){
            timer.stop();
        }

	    timer = new MyTimer(tcpSegment, host.getNetwork().getScheduler(), RTT.RTO);
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
        System.out.println("Congestion control (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)"
                + "Additive increase" + "[ssthresh=" + ssthresh + "]");
        setCwnd(cwnd + (MSS * MSS / cwnd));
    }

    /**
     * Diminution multiplicative de la fenêtre de congestion
     */
    private void multiplDecrease(){ // Multiplicative decrease
        ssthresh = cwnd / 2;
        System.out.println("Congestion control (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)"
                + " Multiplicative decrease " + " [ssthresh=" + ssthresh + "]");

        setCwnd(ssthresh);
    }

    /**
     * Incrementer la fenêtre de congestion d'un MSS en phase de Slow start
     */
    private void slowStart(){
        System.out.println("Congestion control (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)"
                + " Slow Start" + " [ssthresh=" + ssthresh + "]");
        setCwnd(cwnd + MSS);
    }

    private  void setCwnd(int cwnd){
        this.cwnd = cwnd;

        System.out.println("Window size (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
                " [cwnd=" + cwnd + "], [send base=" + send_base + "], [next sequence number=" + next_seq_number + "]");

    }

    private void init_log_cwn(){
        System.out.println("Window size (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
                " [cwnd=" + cwnd + "], [send base=" + send_base + "], [next sequence number=" + next_seq_number + "]");
    }

    private void init_log_congControl(){
        System.out.println("Congestion control (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)"
                + " Slow Start" + " [ssthresh=" + ssthresh + "]");
    }
}
