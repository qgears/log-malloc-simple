package hu.qgears.analyzelogmalloc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import hu.qgears.commons.UtilString;
import joptsimple.annot.AnnotatedClass;
import joptsimple.annot.JOHelp;

/**
 * log-malloc-simple analyser tool. Command line tool that reads input from an process instrumented
 * with log-malloc-simple and processes it to show usable information.
 * 
 * @author rizsi
 *
 */
public class Analyze {
	public static class Args
	{
		@JOHelp("TCP host of server port")
		public String host=null;
		@JOHelp("TCP port of server port")
		public int port=0;
		@JOHelp("Pipe input (or file input)")
		public File pipe;
		@JOHelp("Compare to this later state")
		public File compare;
		@JOHelp("If set then create a copy of the incoming stream into this file.")
		public File tee;
		public OutputStream openTee() throws FileNotFoundException {
			if(tee!=null)
			{
				System.out.println("Tee to: "+tee.getAbsolutePath());
				return new FileOutputStream(tee);
			}
			return null;
		}
		@JOHelp("In compare mode write all instances (instead of a single example) of allocations that contain this string (in any of the stack trace)")
		public List<String> printAllIfContains=new ArrayList<String>();
	}
	/**
	 * Analysation is running.
	 * When false then input is ignored.
	 */
	private boolean on=true;
	/**
	 * All processed bytes of the log - never zeroes. Useful to track whether anything happens at all
	 * and to estimate required bandwidth (RAM, pipe, TCP, etc. depending on the setup) of logging and processing.
	 * 
	 * In real it is the number of UTF characters and not bytes but that should be the same in stack traces
	 * and for estimation purposes that must be enough.
	 */
	private volatile long processedBytes=0;
	/**
	 * The current entry read from log-malloc-simple input data strem.
	 * The object is updated with data read from input until the next object header is read. Then this object is processed.
	 */
	private Entry e = new Entry();
	private EntryProcessor entryProcessor=new EntryProcessor();
	public static void main(String[] args) throws Exception {
		Args a=new Args();
		AnnotatedClass ac=new AnnotatedClass();
		ac.parseAnnotations(a);
		ac.parseArgs(args);
		if(a.host==null && a.pipe==null)
		{
			printUsage(ac);
		}else
		{
			new Analyze().start(a);
		}
	}

	private static void printUsage(AnnotatedClass ac) throws IOException {
		System.out.println("logmalloc output analyser program. Usage:");
		System.out.println(" 1. Create a pipe that will transfer logmalloc output: $ mkfifo /tmp/malloc.pipe");
		System.out.println(" 2. Start analyser on the fifo file: java -jar analyze.jar /tmp/malloc.pipe");
		System.out.println(" 3. Start program to analyze with log-malloc: LD_PRELOAD=liblog-malloc2.so java -jar any.jar 1022>/tmp/malloc.pipe");
		System.out.println(" 4. Use command line to instruct analyzer:");
		printCommands(System.out);
		ac.printHelpOn(System.out);
	}
	/**
	 * Start analyser on a separate thread and do user communication on the caller thread.
	 * Returns when communicateUser returns.
	 * @param args
	 */
	private void start(final Args args) {
		if(args.compare!=null)
		{
			System.out.println("Compare mode: "+args.pipe.getAbsolutePath()+" "+args.compare.getAbsolutePath());
			executeCompare(args);
			return;
		} else if(args.pipe!=null)
		{
			if(args.host!=null)
			{
				throw new IllegalArgumentException("--pipe and --host used at the same time is illegal");
			}
			new Thread("logreader thread") {
				public void run() {
					try {
						try(FileInputStream fis=new FileInputStream(args.pipe))
						{
							processInput(fis, args.openTee());
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				};
			}.start();
		}else if(args.host!=null)
		{
			startTCPServer(args);
		}
		communicateUser();
	}
	private void executeCompare(Args args) {
		try {
			try(FileInputStream fis=new FileInputStream(args.pipe))
			{
				processInput(fis, null);
			}
			EntryProcessor prev=entryProcessor;
			entryProcessor=new EntryProcessor();
			try(FileInputStream fis=new FileInputStream(args.compare))
			{
				processInput(fis, null);
			}
			entryProcessor.processCompare(System.out, prev, args);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void startTCPServer(final Args args) {
		new Thread("TCP listen thread") {
			public void run() {
				try {
					try(ServerSocket ss=new ServerSocket())
					{
						ss.bind(new InetSocketAddress(args.host, args.port));
						System.out.println("TCP server port opened: "+args.host+":"+ss.getLocalPort());
						Socket s=ss.accept();
						System.out.println("TCP client connected...");
						new Thread("TCP read thread")
						{
							@Override
							public void run() {
								try {
									try
									{
										processInput(s.getInputStream(), args.openTee());
									}finally
									{
										s.close();
									}
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
						.start();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			};
		}.start();
	}
	/**
	 * Do processing of input stream in by reading it line by line in a blocking manner.
	 * @param outputStream 
	 * @param f
	 */
	private void processInput(InputStream in, OutputStream outputStream) {
		try {
			Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
			BufferedReader br = new BufferedReader(r);
			OutputStreamWriter osw=null;
			if(outputStream!=null)
			{
				osw=new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
			}
			try
			{
				String line;
				while ((line = br.readLine()) != null) {
					if(osw!=null)
					{
						osw.write(line);
						osw.write("\n");
					}
					processedBytes+=line.length()+1;
					if (line.startsWith("+")) {
						// Log entry starts. Close previous log entry and setup new object.
						processEntry();
						e.setStartLine(line);
					} else if (line.startsWith("-")) {
						// Log entry finished. Process current log entry.
						processEntry();
					} else {
						e.addLine(line);
					}
				}
				processEntry();
			} finally
			{
				br.close();
				if(osw!=null)
				{
					osw.close();
				}
				if(outputStream!=null)
				{
					outputStream.close();
				}
			}
			System.err.println("Input closed.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Process the current entry that is being updated right now by input.
	 * In case the current entry is empty then does nothing.
	 * In case the current entry was opened with a "+..." line then store it into the entryprocessor
	 */
	private void processEntry() {
		if(on)
		{
			synchronized (this) {
				entryProcessor.processEntry(e);
			}
			e = new Entry();
		}
	}
	/**
	 * Read input commands from the user and process them.
	 */
	private void communicateUser() {
		try {
			BufferedReader br=new BufferedReader(new InputStreamReader(System.in));
			printCommands(System.out);
			System.out.println("Command: ");
			String line;
			while ((line = br.readLine()) != null) {
				try {
					List<String> pieces=UtilString.split(line, " \t");
					String command=pieces.get(0);
					switch (command) {
					case "reset":
						reset();
						break;
					case "print":
						print();
						break;
					case "save":
						save(pieces.get(1));
						break;
					case "on":
						on(true);
						break;
					case "off":
						on(false);
						break;
					case "snapshot":
						snapshot(pieces.get(1));
						break;
					default:
						System.out.println("unknown command: '"+command+"'");
						break;
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				printCommands(System.out);
				System.out.println("Command: ");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	synchronized private void snapshot(String filePath) throws IOException {
		File f=new File(filePath);
		FileOutputStream fos=new FileOutputStream(f);
		try
		{
			PrintStream ps=new PrintStream(fos, false, "UTF-8");
			entryProcessor.snapshot(ps);
			ps.close();
		}finally
		{
			fos.close();
		}
	}

	/**
	 * Enable processing of input data.
	 * @param b
	 */
	private synchronized void on(boolean b) {
		on=b;
		System.out.println("Log stream processing on: "+on+"\n");
	}
	/**
	 * save the current state of the processor into a file.
	 * @param string
	 * @throws IOException
	 */
	synchronized private void save(String string) throws IOException {
		File f=new File(string);
		FileOutputStream fos=new FileOutputStream(f);
		try
		{
			PrintStream ps=new PrintStream(fos, false, "UTF-8");
			processOutput(ps);
			ps.close();
		}finally
		{
			fos.close();
		}
	}
	private void processOutput(PrintStream ps) {
		ps.println("Processed bytes: "+processedBytes);
		entryProcessor.processOutput(ps);
	}

	/**
	 * Print the current state of the processor.
	 */
	private synchronized void print() {
		processOutput(System.out);
	}
	/**
	 * Reset the current state of the processor. Forgets all events that are logged up to now.
	 */
	private synchronized void reset() {
		entryProcessor = new EntryProcessor();
		System.out.println("Entry processor reset");
	}
	/**
	 * Print possible commands to the user.
	 * @param out
	 */
	private static void printCommands(PrintStream out) {
		out.println(" * off - turn analyzer off while the application is setting up (to spare CPU cycles when analysation is not required - eg initialization of the program)");
		out.println(" * on - turn analyzer on when the critical session is started (default is on)");
		out.println(" * (reset - clear all log entries cached by the analyzer)");
		out.println(" * print - print current allocation status (since last reset/on) to stdout");
		out.println(" * save <filename> - print current allocation status (since last reset/on) to file");
		out.println(" * snapshot <filename> - save all current stored allocations into a file");
	}
}
