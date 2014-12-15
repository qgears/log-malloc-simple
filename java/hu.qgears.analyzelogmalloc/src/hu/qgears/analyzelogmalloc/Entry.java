package hu.qgears.analyzelogmalloc;

import java.util.ArrayList;
import java.util.List;

import hu.qgears.commons.UtilString;

public class Entry {
	private String startLine;
	private String address;
	private String title;
	private boolean known=false;
	private boolean allocation;
	private boolean free;
	private long size;
	private String allocatorKey;
	private List<String> lines=new ArrayList<String>();
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
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@Override
	public String toString() {
		return ""+startLine+"\n"+linesToString();
	}
	public String linesToString()
	{
		return UtilString.concat(lines, "\n\t", "\n\t", "\n");
	}
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
