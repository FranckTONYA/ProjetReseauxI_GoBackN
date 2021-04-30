package reso.examples.gobackn;

import reso.common.AbstractApplication;
import reso.ip.IPAddress;
import reso.ip.IPHost;
import java.util.Random;

public class AppSender
    extends AbstractApplication
{ 
	
    private final IPAddress dst;
    private final int numberOfPackets;

    public AppSender(IPHost host, IPAddress dst, int numberOfPackets) {	
    	super(host, "sender");
    	this.dst= dst;
    	this.numberOfPackets = numberOfPackets;
    }

    public void start()
    throws Exception {
        GoBackNProtocol transport = new GoBackNProtocol((IPHost) host);
        Random rand = new Random();
        for(int i=0; i < numberOfPackets; i++){
            transport.sendData(rand.nextInt(), dst);
        }
    }
    
    public void stop() {}
    
}

