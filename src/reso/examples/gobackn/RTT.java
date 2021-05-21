package reso.examples.gobackn;

import reso.common.AbstractTimer;
import reso.ip.IPAddress;
import reso.scheduler.AbstractScheduler;

import java.util.Date;

public class RTT {
//    private final static int RTO_init = 3000; // Rto initial temps à attendre pour la première retransmission
//    private static int RO = 1000; //RTT initial
//    private int SequenceNumber;
    private Double transmissionTime; //Transmission Time
    private Double receiveTime; //Receive Time

//    public static int LastSqNb = -1; //Last Paket sequence treated
    private static double RTTValue = 0; // RTT Value
    public static double RTO = 3000; //RTO value = Retransmission Time Out
    private static double SRTT = RTO; //Estimated RTT, which is the smooth RTT
    private static double DevRTT = RTO / 2 ; //
    private static final double alpha = 0.125; // Weight
    private static final double beta = 0.25; // Weight
    private int seq_number;
    public boolean retransmission = false; // Savoir si c'est une retransmission du message

    public static AbstractScheduler scheduler;

//    public static double waitTimeReceive = 0; //retain the value of the RTO or sRRT to apply to the window of the listener.

//    public double previousRtt;
//    public static String status = ""; //Status
//    public double elapseTime;
    //    public RTT(int sqNmb, Date mtransmissionTime, Date mreceiveTime, double prevsRtt) {
//        this.SequenceNumber = sqNmb;
//        this.TransmissionTime = mtransmissionTime;
//        this.ReceiveTime = mreceiveTime;
//
//        if(mreceiveTime == null)
//        {
//            Status = "NOK";
//        }
//        else
//        {
//            setRTT();
//            double actuallRtt = getRTT();
//            Status = "OK";
//            setsRTT(prevsRtt,actuallRtt);
//        }
//
//    }

    public RTT(int sequenceNumber){
        this.seq_number = sequenceNumber;
    }

    public void on_send(){
        transmissionTime = scheduler.getCurrentTime();
    }

    public void on_receive(){
        receiveTime = scheduler.getCurrentTime();
        RTTValue = receiveTime - transmissionTime;

        calcul_RTO(RTTValue);
    }

    private void calcul_RTO(Double RTT){
        SRTT = (1 - alpha) * SRTT + alpha * RTT;
        DevRTT = (1 - beta) * DevRTT + beta * Math.abs(SRTT - RTT);
        RTO = SRTT + 4 * DevRTT;

        System.out.println("Calcul RTT and RTO (" + (int) (scheduler.getCurrentTime()*1000) + "ms)" +
                " Sequence Number=" + seq_number + ", [RTT value=" + RTT*1000 + "ms]" + ", [RTO value=" + RTO*1000 + "ms]");
    }

//    public RTT() {
//
//        if(elapseTime < 0)
//        {
//            status = "NOK";
//        }
//        else
//        {
//            setRTT(elapseTime);
//            status = "OK";
//            setsRTT(prevsRtt,elapseTime);
//        }
//
//    }
//
//    public int getSequenceNumber() {
//        return SequenceNumber;
//    }
//
//    private void setRTT() {
//        this.RTTvalue = (this.ReceiveTime.getTime() - this.TransmissionTime.getTime());
//    }
//
//    private void setRTT(double elapse) {
//        this.RTTvalue = (elapse);
//    }
//
//    public double getRTT() {
//        return RTTvalue;
//    }
//
//    public static double getsRTT() {
//        if(sRTT == 0)
//        {
//            sRTT = RO;
//        }
//
//        return sRTT;
//    }
//
//    private void setsRTT (double sRTT_previous, double r) {
//        sRTT = (1 - this.alpha)* sRTT_previous + (this.alpha * r);
//    }
//
//    public static double getRTO() {
//        return RTO;
//    }
//
//    public static void setRTO(String mstatus) {
//        if(RTO == 0 && !mstatus.equals("OK")){
//            RTO = Rto_init;
//        }
//        else if(RTO > 0 && mstatus.equals("NOK")){
//            RTO = RTO * 2;
//        }
//        else if(RTO > 0 && mstatus == "OK"){
//            RTO = RTO / 2;
//        }
//        else if(RTO < 1){
//            RTO = Rto_init;
//        }
//    }


}
