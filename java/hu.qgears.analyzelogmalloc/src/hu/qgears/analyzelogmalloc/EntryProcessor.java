package hu.qgears.analyzelogmalloc;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import hu.qgears.analyzelogmalloc.Analyze.Args;
import hu.qgears.commons.MultiMapHashImpl;

/**
 * Processes entries from the log stream by finding allocation/free pairs and summarizing
 * allocated but not freed data.
 */
public class EntryProcessor {
	/**
	 * New entries that were allocated while this analyser was on.
	 */
	private Map<Long, Entry> allocations = new TreeMap<Long, Entry>();
	/**
	 * Free entries that correspond to objects that were allocated before reset or when analyser was off.
	 */
	private Map<Long, Entry> beforeAllocations = new TreeMap<Long, Entry>();
	/**
	 * Timestamp of the first entry processed.
	 */
	private long tStart=0;
	private long balance;
	private long beforeBalance;
	private int beforeN;
	private int matching;
	private long matchingSum;
	private long pid;
	/**
	 * All entries stored currently.
	 */
	public static class Entries
	{
		long sum;
		String key;
		List<Entry> entries;
	}
	public void processOutput(PrintStream out) {
		out.println("Processing timespan in millis (since first log processed after reset, measured with currentTimeMillis): "+formatMem( (System.currentTimeMillis()-tStart)));
		out.println("Allocation balance (bytes, negative means leak): "
				+ formatMem(balance));
		out.println("Allocation+oldfree balance (bytes, negative means leak): "
				+ formatMem(balance+beforeBalance));
		out.println("Number of objects allocated in log session but not freed yet: " + allocations.size());
		out.println("Size of objects freed in log session but not allocated in log session (\"oldfree\" bytes): "
				+ formatMem(beforeBalance));
		out.println("Number of objects freed in session but not allocated in session: " + beforeN + " without multiple frees: "
				+ beforeAllocations.size());
		out.println("Matching alloc/free pairs through the logging session (n, bytes): " + matching + " "
				+ formatMem(matchingSum));
		ProcessedEntries processed=getProcessedEntries();
		Collections.sort(processed.ess, new Comparator<Entries>() {
			@Override
			public int compare(Entries o1, Entries o2) {
				return (int)(o2.key.compareTo(o1.key));
			}
		});
		long allSum=0;
		for(Entries e:processed.ess)
		{
			out.println("\nallocator: "+e.key+"\n\tN:" + e.entries.size() + " BYTES: "
				+ formatMem(e.sum)+"\n"+e.entries.get(0).toString());
			allSum+=e.sum;
		}
		
		out.println("Sum of all allocated but not freed objects within this session: "+allSum);
	}
	private ProcessedEntries getProcessedEntries() {
		ProcessedEntries ret=new ProcessedEntries();
		ret.entriesByAllocator = new MultiMapHashImpl<String, Entry>();
		for (Entry e : allocations.values()) {
			// System.out.println(""+e);
			ret.entriesByAllocator.putSingle(e.getAllocatorKey(), e);
		}
		for (String key : ret.entriesByAllocator.keySet()) {
			Entries es=new Entries();
			es.key=""+key;
			ret.ess.add(es);
			es.entries = ret.entriesByAllocator.get(key);
			for (Entry e : es.entries) {
				es.sum += e.getSize();
			}
		}
		return ret;
	}
	private String formatMem(long mem) {
		DecimalFormat formatter = (DecimalFormat) NumberFormat
				.getInstance(Locale.US);
		DecimalFormatSymbols symbols = formatter.getDecimalFormatSymbols();
		symbols.setGroupingSeparator(',');
		formatter.setDecimalFormatSymbols(symbols);
		return formatter.format(mem);
	}

	public void processEntry(Entry e) {
		if(tStart==0)
		{
			tStart=System.currentTimeMillis();
		}
		if(pid==0)
		{
			pid=e.getPid();
		}
		if (e.isFilled() && !e.isKnown()) {
			System.err.println("unknown entry: " + e);
		}
		if (e.isKnown()) {
			if(pid!=e.getPid())
			{
				// Entries from different pid (possible in case of fork+exec for example) are ignored
//				System.err.println("FORKED: ");
//				System.err.println(e.toString());
				return;
			}
			if (e.isAllocation()) {
				Entry prev=allocations.put(e.getAddress(), e);
				balance -= e.getSize();
				if(prev!=null)
				{
					System.err.println("Reallocation without free: "+e.toString());
					System.err.println("Prev: "+prev.toString());
					balance +=prev.getSize();
				}
			}
			if (e.isFree()) {
				Entry before = allocations.remove(e.getAddress());
				if (before != null) {
					if(before.getSize()!=e.getSize())
					{
						System.err.println("Sizes not equal: "+before.getSize()+" "+e.getSize());
					}
					balance += before.getSize();
					matching++;
					matchingSum += before.getSize();
				} else {
					System.err.println("Free without allocation: "+e.toString());
					Entry prev=beforeAllocations.put(e.getAddress(), e);
					if(prev!=null)
					{
						System.err.println("Memory freed twice: "+e+" "+prev);
					}
					beforeBalance += e.getSize();
					beforeN++;
				}
			}
		}
	}
	public void snapshot(PrintStream out) {
		for(Entry e: allocations.values())
		{
			e.printToWhole(out);
		}
	}
	public void processCompare(PrintStream out, EntryProcessor prev, Args args) {
		ProcessedEntries pePrev=prev.getProcessedEntries();
		ProcessedEntries peCurrent=getProcessedEntries();
		Set<String> keys=new HashSet<String>(pePrev.entriesByAllocator.keySet());
		keys.addAll(peCurrent.entriesByAllocator.keySet());
		List<DifferentEntries> diffs=new ArrayList<DifferentEntries>();
		for(String key: keys)
		{
			DifferentEntries de=new DifferentEntries();
			List<Entry> ep =pePrev.entriesByAllocator.get(key);
			List<Entry> ec =peCurrent.entriesByAllocator.get(key);
			for(Entry e:ep)
			{
				Entry curr=allocations.get(e.getAddress());
				if(curr==null || curr.getSize()!=e.getSize())
				{
					de.diffNum--;
					de.diffSize-=e.getSize();
					de.freed.add(e);
				}
			}
			for(Entry e:ec)
			{
				Entry p=prev.allocations.get(e.getAddress());
				if(p==null || p.getSize()!=e.getSize())
				{
					de.diffNum++;
					de.diffSize+=e.getSize();
					de.allocated.add(e);
				}
			}
			if(de.diffNum!=0 || de.diffSize!=0)
			{
				de.key=key;
				diffs.add(de);
			}
		}
		Collections.sort(diffs);
		outer:
		for(DifferentEntries de: diffs)
		{
			out.println(""+de.diffSize+" "+de.diffNum+" "+de.key);
			for(String p: args.printAllIfContains)
			{
				if(de.allocated.size()>1)
				{
					if(de.allocated.get(0).containsPattern(p))
					{
						HashMap<String, Integer> stacks=new HashMap<>();
						for(Entry e: de.allocated)
						{
							String stack=e.printToWholePointerBlurred();
							incStack(stacks, stack);
						}
						TreeMap<String, Integer> ordered=new TreeMap<String, Integer>(stacks);
						for(String key: ordered.keySet())
						{
							out.print("Number of instances: "+stacks.get(key)+"\n");
							out.print(key);
						}
						continue outer;
					}
				}
			}
			de.printFirst(out);
		}
	}
	private void incStack(HashMap<String, Integer> stacks, String stack) {
		Integer v=stacks.get(stack);
		if(v==null)
		{
			stacks.put(stack, 1);
		}else
		{
			stacks.put(stack, v+1);
		}
	}
}
