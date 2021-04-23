package hu.qgears.analyzelogmalloc;

import java.util.ArrayList;
import java.util.List;

import hu.qgears.analyzelogmalloc.EntryProcessor.Entries;
import hu.qgears.commons.MultiMapHashImpl;

public class ProcessedEntries {
	public MultiMapHashImpl<String, Entry> entriesByAllocator = new MultiMapHashImpl<String, Entry>();
	public List<Entries> ess=new ArrayList<EntryProcessor.Entries>();

}
