package reso.examples.gobackn;

import reso.common.Message;

public class TCPSegment
implements Message {
	
	public final int sequenceNumber; 
    public final int[] data; // Donnée du packet
	public final String type; //Permet de definir le type de requete
    public final String dataString; // Le contenu du message a envoyer
    public final boolean isAck;
	
	public TCPSegment(int[] data, int sequenceNumber) {
		this.data = data;
        this.sequenceNumber = sequenceNumber;
        this.isAck = false;
		this.dataString = "";
		this.type = "REQUEST";
	}

	public TCPSegment(int[] data, int sequenceNumber, boolean isAck) {
		this.data = data;
		this.sequenceNumber = sequenceNumber;
		this.isAck = isAck;
		this.dataString = "";
		this.type = "REQUEST";
	}

	public TCPSegment(String type,int[] data, int sequenceNumber, boolean isAck, String dataString){
		this.data = data;
		this.sequenceNumber = sequenceNumber;
		this.isAck = isAck;
		this.dataString = dataString;
		this.type = type;
	}

	public TCPSegment(String type,int[] data, int sequenceNumber, boolean isAck){
		this.data = data;
		this.sequenceNumber = sequenceNumber;
		this.isAck = isAck;
		this.dataString = "";
		this.type = type;
	}
	
    public TCPSegment(int sequenceNumber) {
        this.isAck = true;
		this.data = new int[]{};
        this.sequenceNumber = sequenceNumber;
		this.dataString = "";
		this.type = "REQUEST";
	}
	
	public String toString() {
		return "Segment [seq. num.=" + sequenceNumber + ", isAck=" + isAck +", Message = "+dataString+", Type = "+type+"]";
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
