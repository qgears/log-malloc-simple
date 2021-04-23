package hu.qgears.analyzelogmalloc;

import java.io.PrintStream;
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
	private long address;
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
	 * PID of the process.
	 */
	private long pid;
	/**
	 * Thread id TODO implement
	 */
	private long tid;
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
			switch (title) {
			case "calloc":
				allocation=true;
				known=true;
				parseFields(pieces);
				break;
			case "free":
				known=true;
				parseFields(pieces);
				free=true;
				break;
			case "malloc":
				allocation=true;
				known=true;
				parseFields(pieces);
				break;
			case "realloc_alloc":
				known=true;
				parseFields(pieces);
				allocation=true;
				break;
			case "realloc_free":
				known=true;
				parseFields(pieces);
				free=true;
				break;
			case "posix_memalign":
				known=true;
				parseFields(pieces);
				allocation=true;
				break;
			case "INIT":
				// Nothing to do but no error log
				known=true;
				break;
			case "FINI":
				// Nothing to do but no error log
				known=true;
				break;
			default:
				break;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void parseFields(List<String> pieces) {
		try {
			String a=pieces.get(3);
			if(a.startsWith("0x"))
			{
				address=Long.parseLong(a.substring(2), 16);
			}else
			{
				System.err.println("Address does not start with 0x: "+a);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		String pidField=null;
		try {
			pidField=pieces.get(4);
			pid=Long.parseLong(pidField);
		} catch (Exception e) {
			System.err.println("Pid parse error:"+pidField+" "+e);
		}
		size=Long.parseLong(pieces.get(2));
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
	public long getAddress() {
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
	public String getStartLine() {
		return startLine;
	}
	public List<String> getLines() {
		return lines;
	}
	public String printToWhole()
	{
		StringBuilder ret=new StringBuilder();
		printToWhole(ret);
		return ret.toString();
	}
	public void printToWhole(PrintStream out) {
		out.println(getStartLine());
		for(String l: getLines())
		{
			out.println(l);
		}
		out.println("-");
	}
	public void printToWhole(StringBuilder out) {
		out.append(getStartLine());
		out.append("\n");
		for(String l: getLines())
		{
			out.append(l);
			out.append("\n");
		}
		out.append("-");
		out.append("\n");
	}
	public long getPid() {
		return pid;
	}
	public boolean containsPattern(String p) {
		if(allocatorKey.contains(p))
		{
			return true;
		}
		for(String l: lines)
		{
			if(l.contains(p))
			{
				return true;
			}
		}
		return false;
	}
	public String printToWholePointerBlurred() {
		StringBuilder out=new StringBuilder();
		out.append("+ ");
		out.append(title);
		out.append(" ");
		out.append(size);
		out.append(" PTR ");
		out.append(pid);
		out.append("\n");
		for(String l: getLines())
		{
			out.append(l);
			out.append("\n");
		}
		out.append("-");
		out.append("\n");
		return out.toString();
	}
}
