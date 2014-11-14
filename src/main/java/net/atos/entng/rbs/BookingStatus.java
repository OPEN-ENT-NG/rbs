package net.atos.entng.rbs;

public enum BookingStatus {
	CREATED(1), VALIDATED(2), REFUSED(3);
	
	private final int status;
	
	BookingStatus(int pStatus) {
		this.status = pStatus;
	}
	public int status(){
		return status;
	}
}
