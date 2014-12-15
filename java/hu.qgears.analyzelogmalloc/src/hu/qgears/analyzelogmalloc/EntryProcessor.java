package hu.qgears.analyzelogmalloc;

import hu.qgears.commons.MultiMapHashImpl;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EntryProcessor {
	private Map<String, Entry> allocations = new HashMap<String, Entry>();
	private Map<String, Entry> beforeAllocations = new HashMap<String, Entry>();
	long tStart=0;
	class Entries
	{
		long sum;
		String key;
		List<Entry> entries;
	}
	private long balance;
	private long beforeBalance;
	private int beforeN;
	private int matching;
	private long matchingSum;
	public void processOutput(PrintStream out) {
		out.println("Processing timespan (since first log processed, measured with currentTimeMillis): "+formatMem( (System.currentTimeMillis()-tStart)));
		out.println("Allocation balance (negative means leak): "
				+ formatMem(balance));
		out.println("Freed objects size allocated before log: "
				+ formatMem(beforeBalance));
		out.println("Objects remaining from log session: " + allocations.size());
		out.println("Freed objects N before log: " + beforeN + " "
				+ beforeAllocations.size());
		out.println("Matching alloc/free pairs (n, size): " + matching + " "
				+ formatMem(matchingSum));
		MultiMapHashImpl<String, Entry> entriesByAllocator = new MultiMapHashImpl<String, Entry>();
		for (Entry e : allocations.values()) {
			// System.out.println(""+e);
			entriesByAllocator.putSingle(e.getAllocatorKey(), e);
		}
		List<Entries> ess=new ArrayList<EntryProcessor.Entries>();
		for (String key : entriesByAllocator.keySet()) {
			Entries es=new Entries();
			es.key=""+key;
			ess.add(es);
			es.entries = entriesByAllocator.get(key);
			for (Entry e : es.entries) {
				es.sum += e.getSize();
			}
		}
		Collections.sort(ess, new Comparator<Entries>() {
			@Override
			public int compare(Entries o1, Entries o2) {
				return (int)(o2.key.compareTo(o1.key));
			}
		});
		for(Entries e:ess)
		{
			out.println("\nallocator: "+e.key+"\n\tN:" + e.entries.size() + " BYTES: "
				+ formatMem(e.sum)+"\n"+e.entries.get(0).toString());
		}

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
		if (e.isFilled() && !e.isKnown()) {
			System.err.println("unknown entry: " + e);
		}
		if (e.isKnown()) {
			if (e.isAllocation()) {
				allocations.put(e.getAddress(), e);
				balance -= e.getSize();
			}
			if (e.isFree()) {
				Entry before = allocations.remove(e.getAddress());
				if (before != null) {
					balance += e.getSize();
					matching++;
					matchingSum += e.getSize();
				} else {
					beforeAllocations.put(e.getAddress(), e);
					beforeBalance += e.getSize();
					beforeN++;
				}
			}
		}
	}

}
