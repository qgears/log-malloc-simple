package hu.qgears.analyzelogmalloc;

import java.util.ArrayList;
import java.util.List;

import hu.qgears.commons.UtilString;

/**
 * A single entry in the log-malloc-simple output datastream.
 * Represents an allocation a free or other logged event.
 * Entries are built by reading the corresponding lines from the log stream. After feeding all data to this object
 * the entry becomes read-only.
 * @author rizsi
 *
 */
public class Entry {
	/**
	 * The first line of the entry.
	 */
	private String startLine;
	/**
	 * The address of allocated memory as a hexa string (eg. 0xabce1234)
	 */
	private String address;
	/**
	 * Title of this entry - the method called (eg. "malloc", "free" etc)
	 */
	private String title;
	/**
	 * This log entry is known by the analyser (unknown entries in the log stream raise an error log)
	 */
	private boolean known=false;
	/**
	 * This log entry means an allocation. Size must be added to allocated size.
	 */
	private boolean allocation;
	/**
	 * This log entry means freeing memory. Size must be substractd from allocated size.
	 */
	private boolean free;
	/**
	 * Size of the memory chunk referenced by this command.
	 */
	private long size;
	/**
	 * Allocator key is the method entry on the stack trace that has called the allocator.
	 */
	private String allocatorKey;
	/**
	 * Additional lines in the log entry (stack trace)
	 */
	private List<String> lines=new ArrayList<String>();
	/**
	 * The first line of the log entry tells what we are doing.
	 * @param line
	 */
	public void setStartLine(String line) {
		this.startLine=line;
		try {
			List<String> pieces=UtilString.split(startLine, " ");
			title=pieces.get(1);
			if("calloc".equals(title))
			{
				allocation=true;
				known=true;
				address=pieces.get(3);
				size=Long.parseLong(pieces.get(2));
			}else if("free".equals(title))
			{
				known=true;
				address=pieces.get(3);
				size=Long.parseLong(pieces.get(2));
				free=true;
			}else if("malloc".equals(title))
			{
				allocation=true;
				known=true;
				address=pieces.get(3);
				size=Long.parseLong(pieces.get(2));
			}else if("realloc_alloc".equals(title))
			{
				known=true;
				address=pieces.get(3);
				size=Long.parseLong(pieces.get(2));
				allocation=true;
			}else if("realloc_free".equals(title))
			{
				known=true;
				address=pieces.get(3);
				size=Long.parseLong(pieces.get(2));
				free=true;
			}else if("posix_memalign".equals(title))
			{
				known=true;
				address=pieces.get(3);
				size=Long.parseLong(pieces.get(2));
				allocation=true;
			}else if("INIT".equals(title))
			{
				// Nothing to do but no error log
				known=true;
			}
			else if("FINI".equals(title))
			{
				// Nothing to do but no error log
				known=true;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@Override
	public String toString() {
		return ""+startLine+"\n"+linesToString();
	}
	private String linesToString()
	{
		return UtilString.concat(lines, "\n\t", "\n\t", "\n");
	}
	/**
	 * True means that this entry object is initialized with data.
	 * @return
	 */
	public boolean isFilled()
	{
		return startLine!=null;
	}
	public boolean isKnown() {
		return known;
	}
	public boolean isAllocation() {
		return allocation;
	}
	public String getAddress() {
		return address;
	}
	public long getSize() {
		return size;
	}
	public boolean isFree() {
		return free;
	}
	/**
	 * Additional lines of the log message object are added through this method.
	 * @param line
	 */
	public void addLine(String line) {
		if(lines.size()==0)
		{
			allocatorKey=line;
		}
		lines.add(line);
	}
	public String getAllocatorKey() {
		return allocatorKey;
	}
}
