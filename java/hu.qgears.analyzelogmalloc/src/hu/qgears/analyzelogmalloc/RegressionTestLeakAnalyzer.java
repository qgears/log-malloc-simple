package hu.qgears.analyzelogmalloc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

public class RegressionTestLeakAnalyzer {
	private Map<String, ByteArrayOutputStream> snapshots=new HashMap<String, ByteArrayOutputStream>();
	public void snapshot(Analyze a) throws IOException {
		snapshot("orig", a);
	}
	public void snapshot(String name, Analyze a) throws IOException {
		ByteArrayOutputStream snapshot=new ByteArrayOutputStream();
		a.snapshot(snapshot);
		snapshots.put(name, snapshot);
		prevName=name;
	}
	private long leaked=0;
	private String prevName;
	public long countLeak(String name0, String name1, PrintStream out) throws IOException {
		ByteArrayOutputStream snapshot0=snapshots.get(name0);
		ByteArrayOutputStream snapshot1=snapshots.get(name1);
		return countLeak(snapshot0.toByteArray(), snapshot1.toByteArray(), out);
	}
	public long countLeak(byte[] snapshot0, byte[] snapshot1, PrintStream out) throws IOException {
		leaked=0;
		try(Analyze a=new Analyze())
		{
			Analyze.Args args=new Analyze.Args();
			args.hideIfContains.add("libjvm.so");
			args.hideIfContains.add("libz.so.1");
			args.hideIfContains.add("libz.so");
			args.hideIfContains.add("libzip.so");
			args.modeInteractive=false;
			leaked=0;
			args.compareDiffEntryEvent.addListener(e->{
				leaked+=e.diffSize;
			});
			a.executeCompare(new ByteArrayInputStream(snapshot0),
					new ByteArrayInputStream(snapshot1), out, args);
		}
		return leaked;
	}
	public long snapshotDiff(Analyze a, PrintStream out) throws IOException {
		ByteArrayOutputStream prev=snapshots.remove(prevName);
		snapshot(a);
		long ret=countLeak(prev.toByteArray(), snapshots.get(prevName).toByteArray(), out);
		return ret;
	}
}
