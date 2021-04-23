package hu.qgears.analyzelogmalloc;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class DifferentEntries implements Comparable<DifferentEntries>{
	public long diffNum=0;
	public long diffSize=0;
	public String key;
	public List<Entry> freed=new ArrayList<Entry>();
	public List<Entry> allocated=new ArrayList<Entry>();
	@Override
	public int compareTo(DifferentEntries o) {
		return key.compareTo(o.key);
		// return Long.compare(o.diffSize, diffSize);
	}
	public void printFirst(PrintStream out)
	{
		if(allocated.size()>0)
		{
			allocated.get(0).printToWhole(out);
		}
	}
}
