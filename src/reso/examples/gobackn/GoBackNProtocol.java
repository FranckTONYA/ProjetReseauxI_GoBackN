package reso.examples.gobackn;

import reso.ip.Datagram;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPInterfaceAdapter;
import reso.ip.IPInterfaceListener;
import reso.ip.IPLayer;

import java.util.Date;
import java.util.Random;

public class GoBackNProtocol implements IPInterfaceListener {

	public static final int IP_PROTO_GOBACKN= Datagram.allocateProtocolNumber("GOBACKN");
	
	private final IPHost host; 
	
	public GoBackNProtocol(IPHost host) {
		this.host= host;
    	host.getIPLayer().addListener(this.IP_PROTO_GOBACKN, this);
	}
	
	@Override
	public void receive(IPInterfaceAdapter src, Datagram datagram) throws Exception {
        
    	TCPSegment segment= (TCPSegment) datagram.getPayload();

        //We don't need to display resend trigger
        if(!segment.type.contains("RESEND")){
            System.out.println("Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
            " host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
            datagram.dst + ", iif=" + src + ", data=" + segment);
        }
        //Test => est ce qu'on a recu un acquittement ? Yes or No
		//System.out.println("Acknowledge received : "+segment.isAck()+ " ");

        if(segment.type.contains("REQUEST")){
            try{
                if(segment.isAck()){
                    //We need to send a response to the Requester
                    //System.out.println("Sending Response for packet Num : "+segment.sequenceNumber+" Requester: "+datagram.src +" Destination: "+ datagram.dst);
                    //We make sure that only the machine Host 1 is the one the request. <= Toutes requetes provenant de la machine Host 1 est de type Request
                    //La machine Host 2 ne fait que repondre. <= Toutes requetes provenant de la machine Host 2 est de type Reponse/Resend
                    //Pour traiter une requete sur la machine Host 2 et envoyé une reponse, la sequence doit etre respecté. 
                    //Le paquet 1 doit avoir un status 'OK', pour que la machine Host 2 puisse traiter le paquet 2 et ainsi de suite. (RTT.Status.equals("OK") && ( segment.sequenceNumber == RTT.LastSqNb+1))
                    //Une Requete retransféré doit etre retraité (RTT.LastSqNb == segment.sequenceNumber)
                    //Si aucune requete n'a encore été traité, envoyé la reponse de la requete (RTT.LastSqNb < 0).
                    if( RTT.LastSqNb < 0 || RTT.LastSqNb == segment.sequenceNumber || (RTT.Status.equals("OK") && ( segment.sequenceNumber == RTT.LastSqNb+1) )){
                        if(segment.sequenceNumber == RTT.LastSqNb+1){
                            RTT.Status = "";
                        }
                        RTT.LastSqNb = segment.sequenceNumber;
                        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment("RESPONSE",segment.data,segment.sequenceNumber,(new Random()).nextBoolean(),segment.dataString));
                    }
                    else{
                        //Si la requete/packet précedent n'a pas encore ete traiter (reponse recu), la requete suivante doit etre mise dans la queue
                        System.out.println("Previous packet Num : "+ RTT.LastSqNb +" not yet treated. Packet number: "+segment.sequenceNumber + " is requeue");
                        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment("RESEND",segment.data,segment.sequenceNumber,true,segment.dataString));
                    }
                    
                }
                else{    
                    //We'll make the assumption that we won't need to retrigger a request <= We'll never go through this part of the code
                    System.out.println("Trigger resent of package : "+segment.sequenceNumber+" Requester: "+datagram.src +" Destination: "+ datagram.dst);                    
                    host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment("RESEND",segment.data,segment.sequenceNumber,true,segment.dataString));
                }
                
            }catch (Exception ex){
                System.out.println("Exception Message: "+ ex.getMessage());
            }
        }
        else if(segment.type.contains("RESEND")){
            //Une requete qui ne pouvait pas etre traité sur la machine Host 2, la machine Host 1 doit la retransmettre. Request => Host 1 vers Host 2
            host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment("REQUEST",segment.data,segment.sequenceNumber,true,segment.dataString));
        }
        else if(segment.type.contains("RESPONSE")){
            //Toutes les requetes de type 'Response' sont traité sur la machine Host 1.
            boolean ReqRetry = false; // When we need to retransmit
            double sRttpacket = RTT.getsRTT(); // 1 er Packet  =  SRTT0, for next packet SRTTi (estimated RTT)
            String Prev_Status = RTT.Status; // Statut du dernier packet recu, au cas ou on doit pouvoir prendre une action en fonction du status precedent
            
            //System.out.println("Sequence : "+segment.sequenceNumber); // ID Sequence du packet dans le cas ou on voudrait prendre des action specifique sur packet bien precis
            //System.out.println("Previous status : "+ Prev_Status); //Affichage du status de l'envoie precedent

            //On teste la valeur isAck, pour savoir si une reponse est retourner ou non
            if(segment.isAck()){

                long timeReceived = (new Date()).getTime(); //Temps de reception du paquet
                long timeTransmission = Long.parseLong ((segment.dataString.split(":")[1]).toString().trim()); //Extraction du temps emission du message

                long elpseCalculated = (timeReceived - timeTransmission)*1000; //Calcul du RTT en milliseconds = temps reception - temps transmission
                //System.out.println("duration Ri : "+ elpse); //Affichage du delai RTT calculer

                //System.out.println("timeReceived : "+ timeReceived); // Temps reception
                //System.out.println("timeTransmission : "+ timeTransmission); //Affichage du resultat extrait du message

                //long random = (new Random()).nextInt(20); //variable aleatoire pour generer un coefficient entre 0 et 20 qu'on multiplie au temps d'ecouler du simulateur
                //System.out.println("coefficient : "+random); //valeur aleatoire recu
                //double elpse = host.getNetwork().getScheduler().getCurrentTime()*1000*random;
                double elpse = host.getNetwork().getScheduler().getCurrentTime()*1000;
                
                if(Prev_Status.equals("OK") && RTT.waitTimeReceive < elpseCalculated){
                    System.out.println("sRTT : "+RTT.waitTimeReceive+" < calculated RTT : "+elpseCalculated);
                }
                else if(Prev_Status.equals("OK") && RTT.waitTimeReceive > elpseCalculated){
                    System.out.println("sRTT : "+RTT.waitTimeReceive+" > calculated RTT : "+elpseCalculated);
                }
                if(Prev_Status.equals("OK") && RTT.waitTimeReceive < elpse){
                    System.out.println("sRTT : "+RTT.waitTimeReceive+" < simulated RTT : "+elpse);
                }
                else if(Prev_Status.equals("OK") && RTT.waitTimeReceive > elpse){
                    System.out.println("sRTT : "+RTT.waitTimeReceive+" > simulated RTT : "+elpse);
                }
                if(Prev_Status.equals("NOK") && RTT.waitTimeReceive < elpseCalculated){
                    System.out.println("RTO : "+RTT.waitTimeReceive+" < calculated RTT : "+elpseCalculated);
                }
                else if(Prev_Status.equals("NOK") && RTT.waitTimeReceive > elpseCalculated){
                    System.out.println("RTO : "+RTT.waitTimeReceive+" > calculated RTT : "+elpseCalculated);
                }
                if(Prev_Status.equals("NOK") && RTT.waitTimeReceive < elpse){
                    System.out.println("RTO : "+RTT.waitTimeReceive+" < simulated RTT : "+elpse);
                }
                else if(Prev_Status.equals("NOK") && RTT.waitTimeReceive > elpse){
                    System.out.println("RTO : "+RTT.waitTimeReceive+" > simulated RTT : "+elpse);
                }

                // Case where the acknowledge was received
                //System.out.println("duration (ms): "+ elpse);
                //On initie le calcul du RTT, car on a recu le packet dans la fenetre d'attente
                //RTT mRtt = new RTT(segment.sequenceNumber,elpse,sRttpacket);
                // To use when after a successful retransmission is done and the RTO is divided by two for next transfer window
                if (Prev_Status.equals("NOK")){
                    //System.out.println("Last sRTTi-1 to work : "+ sRttpacket);
                    if(RTT.RTO > elpse)
                    {
                        //On initie le calcul du RTT, car on a recu le packet dans la fenetre d'attente
                        RTT mRtt = new RTT(segment.sequenceNumber,elpse,sRttpacket);
                        //System.out.println("Previous RTO : "+ RTT.RTO);
                        //System.out.println("New sRTT to use : "+ RTT.getsRTT());
                        System.out.println("(TTL: "+ mRtt.getRTT()+")");
                        //System.out.println("calculated TTL: "+ mRtt.getRTT());
                        RTT.setRTO(RTT.Status); //Reduce the RTO by 2, when after retransmission a response a recieved
                        //System.out.println("New RTO : "+ RTT.getRTO()); //To use when we want to carefully decrease the window of the RTO when going back into a normal state. When moving from status NOK to OK  => we first divide the RTO and use it a listener window.
                    }
                    else
                    {
                        //Doit t'on recalculer le sRTT ou passer au suivant ?
                        System.out.println("Packet arrived outside the window : Seq Number = "+segment.sequenceNumber+" to be Ignoered ?");
                        ReqRetry = true;
                    }

                }
                else{
                        if(elpse < sRttpacket){
                            //On initie le calcul du RTT, car on a recu le packet dans la fenetre d'attente
                            RTT mRtt = new RTT(segment.sequenceNumber,elpse,sRttpacket);
                            //System.out.println("RTT < sRTT : OK");
                            //System.out.println("Previous sRTT : "+ sRttpacket);
                            //System.out.println("New sRTT (Time to wait for next package): "+ RTT.getsRTT());
                            //System.out.println("calculated TTL: "+ mRtt.getRTT());
                            System.out.println("(calculated TTL: "+ mRtt.getRTT()+")");
                        }
                        else{
                            //Doit t'on recalculer le sRTT ou passer au suivant ?
                            System.out.println("RTT > sRTT : NOK ! What to do ? Ignored package");
                            ReqRetry = true;
                        }

                }
                //System.out.println("calculated TTL: "+ mRtt.getRTT());
                RTT.waitTimeReceive = RTT.getsRTT(); //Value to use for the listener window
            }
            else{
                // Case where the acknowledge is not received, no RTT => We need to retransmit
                //System.out.println("duration : Unknown");
                RTT mRtt = new RTT(segment.sequenceNumber,-1,sRttpacket);
                // Condition de reception et de la prochaine etape
                System.out.println("Previous sRTT : "+ sRttpacket);
                
                RTT.setRTO(RTT.Status); //Calculate new RTO
                //System.out.println("New RTO : "+ RTT.getRTO());
                //System.out.println("Current status : "+ RTT.Status);
                //System.out.println("Timeout for the retransmission : "+RTT.RTO);
                System.out.println("(RTO : "+RTT.RTO+")");
                RTT.waitTimeReceive = RTT.RTO; //Value to use for the listener window
                ReqRetry = true;                
            }

            if(ReqRetry){
                try{
                    //We need to trigger the retransmission
                    //System.out.println("retransmission from: "+datagram.dst +" to: "+ datagram.src);
                    host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment("REQUEST",segment.data,segment.sequenceNumber,true,segment.dataString));
                    
                }catch (Exception ex){
                    System.out.println("Exception Message: "+ ex.getMessage());
                }
            }
            //System.out.println("Wait window for next send: "+ RTT.waitTimeReceive);
            System.out.println("RTO/sRRT: "+ RTT.waitTimeReceive);
            System.out.println("===========================================================");
        }
	}

    public void sendData(int data, IPAddress destination) throws Exception{
        int[] segmentData = new int[]{data};
        int sequenceNumber = 1;
        host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, new TCPSegment(segmentData,sequenceNumber));
    }
    public void sendData(int data, IPAddress destination, int msequenceNumber) throws Exception{
        int[] segmentData = new int[]{data};
        int sequenceNumber = msequenceNumber;
        boolean isAckval = (new Random()).nextBoolean();
        host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, new TCPSegment(segmentData,sequenceNumber,isAckval));
    }
    public void sendData(int data, IPAddress destination, int msequenceNumber,String mdataString, String type) throws Exception{
        int[] segmentData = new int[]{data};
        int sequenceNumber = msequenceNumber;
        boolean isAckval = (new Random()).nextBoolean();
        String dataString = mdataString;
        host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, new TCPSegment(type,segmentData,sequenceNumber,isAckval,dataString));
    }
    public void sendData(int data, IPAddress destination, int msequenceNumber,String mdataString, String type, boolean misAckval) throws Exception{
        int[] segmentData = new int[]{data};
        int sequenceNumber = msequenceNumber;
        boolean isAckval = misAckval;
        String dataString = mdataString;
        host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, new TCPSegment(type,segmentData,sequenceNumber,isAckval,dataString));
    }
    private void sendAcknowledgment(Datagram datagram) throws Exception{
        int ackSequenceNumber = 1;
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment(ackSequenceNumber));
    }


}
