package hu.qgears.analyzelogmalloc;

import hu.qgears.commons.UtilString;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.List;

public class Analyze {
	public static void main(String[] args) throws Exception {
		if(args.length!=1)
		{
			printUsage();
		}else
		{
			new Analyze().start(args);
		}
	}
	private boolean on=true;

	private static void printUsage() {
		System.out.println("logmalloc output analyser program. Usage:");
		System.out.println(" 1. Create a pipe that will transfer logmalloc output: $ mkfifo /tmp/malloc.pipe");
		System.out.println(" 2. Start analyser on the fifo file: java -jar analyze.jar /tmp/malloc.pipe");
		System.out.println(" 3. Start program to analyze with log-malloc: LD_PRELOAD=liblog-malloc2.so java -jar any.jar 1022>/tmp/malloc.pipe");
		System.out.println(" 4. Use command line to instruct analyzer:");
		System.out.println("  * off - turn analyzer off while the application is setting up (to spare CPU cycles)");
		System.out.println("  * on - turn analyzer on when the critical session is started");
		System.out.println("  * (reset - clear all log entries cached by the analyzer)");
		System.out.println("  * print/save - create logs from results since last reset");
	}

	private void start(String[] args) {
		final File input = new File(args[0]);
		new Thread("logreader thread") {
			public void run() {
				processInput(input);
			};
		}.start();
		communicateUser();
	}

	private Entry e = new Entry();
	private EntryProcessor entryProcessor=new EntryProcessor();

	private void processInput(File f) {
		try {
			Reader r = new InputStreamReader(new FileInputStream(f));
			BufferedReader br = new BufferedReader(r);
			String line;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("+")) {
					processEntry();
					e.setStartLine(line);
				} else {
					e.addLine(line);
				}
			}
			processEntry();
			System.err.println("Input closed.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void processEntry() {
		if(on)
		{
			synchronized (this) {
				entryProcessor.processEntry(e);
				e = new Entry();
			}
		}
	}

	private void communicateUser() {
		try {
			BufferedReader br=new BufferedReader(new InputStreamReader(System.in));
			printCommands(System.out);
			String line;
			while ((line = br.readLine()) != null) {
				try {
					List<String> pieces=UtilString.split(line, " \t");
					String command=pieces.get(0);
					if ("reset".equals(command)) {
						reset();
					} else if ("print".equals(command)) {
						print();
					} else if ("save".equals(command)) {
						save(pieces.get(1));
					}else if ("on".equals(command)) {
						on(true);
					}
					else if ("off".equals(command)) {
						on(false);
					}
					else
					{
						System.out.println("unknown command: '"+command+"'");
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				printCommands(System.out);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private synchronized void on(boolean b) {
		on=b;
		System.out.println("Log stream processing on: "+on+"\n");
	}

	synchronized private void save(String string) throws IOException {
		File f=new File(string);
		FileOutputStream fos=new FileOutputStream(f);
		try
		{
			PrintStream ps=new PrintStream(fos, false, "UTF-8");
			entryProcessor.processOutput(ps);
			ps.close();
		}finally
		{
			fos.close();
		}
	}

	private synchronized void print() {
		entryProcessor.processOutput(System.out);
	}

	private synchronized void reset() {
		entryProcessor = new EntryProcessor();
		System.out.println("Entry processor reset");
	}

	private void printCommands(PrintStream out) {
		out.println("reset - reset all known allocation");
		out.println("print - print current allocation status");
		out.println("save <filename> - save current allocation status to file");
		out.println("off - temporarily ignore all input from log stream");
		out.println("on - process all input from log stream from now on");
		out.println("Command: ");
	}
}
