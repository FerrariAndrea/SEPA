package it.unibo.arces.wot.sepa.engine.processing;

public interface ProcessorMBean {

	public void reset();
	
	public long getProcessedRequests();
	public long getProcessedQueryRequests();
	public long getProcessedUpdateRequests();
	
	public float getTimings_UpdateTime_ms();
	public float getTimings_UpdateTime_Min_ms();
	public float getTimings_UpdateTime_Average_ms();
	public float getTimings_UpdateTime_Max_ms();
	
	public float getTimings_QueryTime_ms();
	public float getTimings_QueryTime_Min_ms();
	public float getTimings_QueryTime_Average_ms();
	public float getTimings_QueryTime_Max_ms();
	
	public int getUpdateTimeout();
	public int getQueryTimeout();
	
	public void setUpdateTimeout(int t);
	public void setQueryTimeout(int t);
}
