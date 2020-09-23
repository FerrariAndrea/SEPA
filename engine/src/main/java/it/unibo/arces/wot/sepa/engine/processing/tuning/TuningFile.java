package it.unibo.arces.wot.sepa.engine.processing.tuning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import it.unibo.arces.wot.sepa.timing.Timings;

public class TuningFile {

	private long start;
	private long end;
	private int realAdded;
	private int realRemoved;
	private int added;
	private int removed;
	private long id;
	private boolean used = false;
	private long startTotal;
	private long endTotal;
	
	
	
	public TuningFile(long start, long end, int added, int removed) {
		super();
		this.start = start;
		this.end = end;
		this.added = added;
		this.removed = removed;
		this.id =  Timings.getTime();
	}
	
	public long getStart() {
		return start;
	}

	public void setStart(long start) {
		this.start = start;
	}

	public long getEnd() {
		return end;
	}

	public void setEnd(long end) {
		this.end = end;
	}

	public int getRealAdded() {
		return realAdded;
	}

	public void setRealAdded(int realAdded) {
		this.realAdded = realAdded;
	}

	public int getRealRemoved() {
		return realRemoved;
	}

	public void setRealRemoved(int realRemoved) {
		this.realRemoved = realRemoved;
	}

	public int getAdded() {
		return added;
	}

	public void setAdded(int added) {
		this.added = added;
	}

	public int getRemoved() {
		return removed;
	}

	public void setRemoved(int removed) {
		this.removed = removed;
	}



	public void setStartTotal(long startTotal) {
		this.startTotal = startTotal;
	}

	public void setEndTotal(long endTotal) {
		this.endTotal = endTotal;
	}

	public  void write() {
		if(!used) {
			used=true;
			long time = end-start;
			//long time2 = endTotal-startTotal;
			String line = id+";"+time+";"+realAdded+ ";"+realRemoved+ ";"+added+ ";"+removed+ ";\n";
			 try {
				  Files.write(Paths.get(System.getProperty("user.dir")+"\tuningFile.txt"), line.getBytes(), StandardOpenOption.APPEND);
			    } catch (IOException e) {
			      System.out.println("An error occurred on write TuningFile.");
			      e.printStackTrace();
			    }
		}
		
	}
}
