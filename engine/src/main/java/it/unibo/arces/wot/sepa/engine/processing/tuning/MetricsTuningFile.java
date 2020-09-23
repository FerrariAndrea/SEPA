package it.unibo.arces.wot.sepa.engine.processing.tuning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import it.unibo.arces.wot.sepa.commons.sparql.BindingsResults;
import it.unibo.arces.wot.sepa.timing.Timings;

public class MetricsTuningFile {

	private long timePocessorTuned;
	private long timeSPUNormal;
	private long timeSPUTuned;
	private long id;
	private boolean used = false;
	private String tunedAdded="{tunedAdded: null}";
	private String tunedRemoved="{tunedRemoved: null}";
	private String added="{added: null}";
	private String removed="{removed: null}";
	
	
	public MetricsTuningFile(long time) {
		super();
		this.timePocessorTuned = time;
		this.id =  Timings.getTime();
	}
	



	public long getTimePocessorTuned() {
		return timePocessorTuned;
	}




	public void setTimePocessorTuned(long timePocessorTuned) {
		this.timePocessorTuned = timePocessorTuned;
	}




	public long getTimeSPUNormal() {
		return timeSPUNormal;
	}




	public void setTimeSPUNormal(long timeSPUNormal) {
		this.timeSPUNormal = timeSPUNormal;
	}




	public long getTimeSPUTuned() {
		return timeSPUTuned;
	}




	public void setTimeSPUTuned(long timeSPUTuned) {
		this.timeSPUTuned = timeSPUTuned;
	}




	public long getId() {
		return id;
	}




	public void setId(long id) {
		this.id = id;
	}




	public String getTunedAdded() {
		return tunedAdded;
	}




	public void setTunedAdded(String tunedAdded) {
		this.tunedAdded = tunedAdded;
	}




	public String getTunedRemoved() {
		return tunedRemoved;
	}




	public void setTunedRemoved(String tunedremoved) {
		this.tunedRemoved = tunedremoved;
	}




	public String getAdded() {
		return added;
	}




	public void setAdded(String added) {
		this.added = added;
	}




	public String getRemoved() {
		return removed;
	}




	public void setRemoved(String removed) {
		this.removed = removed;
	}




	public  void write() {
		//if(!used) {
		//	used=true;

			String json = "{\n\t id:"+id+ 
					",\n\t timePocessorTuned:"+timePocessorTuned+
					",\n\t timeSPUNormal:"+timeSPUNormal+
					",\n\t timeSPUTuned:"+timeSPUTuned+",";

			json+="\n\t tunedTriples:{\n\t\t tunedAdded:"+tunedAdded+",\n\t\t tunedRemoved:"+tunedRemoved+"},";
			json+="\n\t normalTriples:{\n\t\t added:"+added+",\n\t\t removed:"+removed+"}";
			
			json+="\n}";
			 try {
				  Files.write(Paths.get(System.getProperty("user.dir")+"\\tuningFile.txt"), json.getBytes(), StandardOpenOption.APPEND);
			    } catch (IOException e) {
			      System.out.println("An error occurred on write TuningFile.");
			      e.printStackTrace();
			    }
		//}
		
	}
}
