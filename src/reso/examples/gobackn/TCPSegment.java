package reso.examples.gobackn;

import reso.common.Message;

public class TCPSegment
implements Message {
	
	public final int sequenceNumber; 
    public final int[] data;
    public final boolean isAck;
	
	public TCPSegment(int[] data, int sequenceNumber) {
		this.data = data;
        this.sequenceNumber = sequenceNumber;
        this.isAck = false;
	}
	
    public TCPSegment(int sequenceNumber) {
        this.isAck = true;
		this.data = new int[]{};
        this.sequenceNumber = sequenceNumber;
	}
	
	public String toString() {
		return "Segment [seq. num.=" + sequenceNumber + ", isAck=" + isAck +"]";
	}

    public boolean isAck(){
        return this.isAck;
    }

	@Override
	public int getByteLength() {
		// The TCP segment carries an array of 'int'
		return 4*this.data.length+1;
	}
}
