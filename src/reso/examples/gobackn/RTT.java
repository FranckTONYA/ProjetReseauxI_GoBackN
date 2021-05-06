package reso.examples.gobackn;

import reso.ip.IPAddress;
import java.util.Date;

public class RTT {
    private final static int Rto_init = 3000; // Rto initial temps à attendre pour la première retransmission
    private static int RO = 1000; //RTT initial
    private int SequenceNumber;
    private Date TransmissionTime; //Transmission Time
    private Date ReceiveTime; //Receive Time
    public static String Status = ""; //Status
    public static int LastSqNb = -1; //Last Paket sequence treated
    private double RTTvalue; // RTT Value
    public static double RTO = 0; //RTO value = Retransmission time
    private static double sRTT; //Estimated RTT, which is the smooth RTT
    private final double alpha = 0.125; // Weight
    public static double waitTimeReceive = 0; //retain the value of the RTO or sRRT to apply to the window of the listener.

    public RTT(int sqNmb, Date mtransmissionTime, Date mreceiveTime, double prevsRtt) {
        this.SequenceNumber = sqNmb;
        this.TransmissionTime = mtransmissionTime;
        this.ReceiveTime = mreceiveTime;

        if(mreceiveTime == null)
        {
            Status = "NOK";
        }
        else
        {
            setRTT();
            double actuallRtt = getRTT();
            Status = "OK";
            setsRTT(prevsRtt,actuallRtt);
        }

    }
    public RTT(int sqNmb, double elapseTime, double prevsRtt) {
        this.SequenceNumber = sqNmb;

        if(elapseTime < 0)
        {
            Status = "NOK";
        }
        else
        {
            setRTT(elapseTime);
            Status = "OK";
            setsRTT(prevsRtt,elapseTime);
        }

    }

    public int getSequenceNumber() {
        return SequenceNumber;
    }
    private void setRTT() {
        this.RTTvalue = (this.ReceiveTime.getTime() - this.TransmissionTime.getTime());
    }
    private void setRTT(double elapse) {
        this.RTTvalue = (elapse);
    }
    public double getRTT() {
        return RTTvalue;
    }
    public static double getsRTT() {
        if(sRTT == 0)
        {
            sRTT = RO;
        }

        return sRTT;
    }
    private void setsRTT (double sRTT_previous, double r) {
        sRTT = (1 - this.alpha)* sRTT_previous + (this.alpha * r);
    }
    public static double getRTO() {
        return RTO;
    }
    public static void setRTO(String mstatus) {
        if(RTO == 0 && !mstatus.equals("OK")){
            RTO = Rto_init;
        }
        else if(RTO > 0 && mstatus.equals("NOK")){
            RTO = RTO * 2;
        }
        else if(RTO > 0 && mstatus == "OK"){
            RTO = RTO / 2;
        }
        else if(RTO < 1){
            RTO = Rto_init;
        }
    }
}
