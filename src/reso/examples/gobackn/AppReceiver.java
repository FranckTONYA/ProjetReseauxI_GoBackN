package reso.examples.gobackn;

import reso.common.AbstractApplication;
import reso.ip.IPHost;
import reso.ip.IPLayer;

public class AppReceiver
	extends AbstractApplication
{
	
	private final IPLayer ip;
    	
	public AppReceiver(IPHost host) {
		super(host, "receiver");
		ip= host.getIPLayer();
    }
	
	public void start() {
        GoBackNProtocol transport = new GoBackNProtocol((IPHost) host);
    }
	
	public void stop() {}
	
}
