package reso.examples.gobackn;

import reso.ip.Datagram;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import reso.ip.IPInterfaceAdapter;
import reso.ip.IPInterfaceListener;
import reso.ip.IPLayer;

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
		System.out.println("Data (" + (int) (host.getNetwork().getScheduler().getCurrentTime()*1000) + "ms)" +
				" host=" + host.name + ", dgram.src=" + datagram.src + ", dgram.dst=" +
				datagram.dst + ", iif=" + src + ", data=" + segment);
        if(segment.isAck()){
            // ...
        }
        else{
            // ...
        }
	}

    public void sendData(int data, IPAddress destination) throws Exception{
        int[] segmentData = new int[]{data};
        int sequenceNumber = 1;
        host.getIPLayer().send(IPAddress.ANY, destination, IP_PROTO_GOBACKN, new TCPSegment(segmentData,sequenceNumber));
    }

    private void sendAcknowledgment(Datagram datagram) throws Exception{
        int ackSequenceNumber = 1;
        host.getIPLayer().send(IPAddress.ANY, datagram.src, IP_PROTO_GOBACKN, new TCPSegment(ackSequenceNumber));
    }


}
