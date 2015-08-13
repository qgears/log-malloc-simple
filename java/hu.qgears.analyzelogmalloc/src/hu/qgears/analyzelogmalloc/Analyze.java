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

/**
 * log-malloc-simple analyser tool. Command line tool that reads input from an process instrumented
 * with log-malloc-simple and processes it to show usable information.
 * 
 * @author rizsi
 *
 */
public class Analyze {
	/**
	 * Analysation is running.
	 * When false then input is ignored.
	 */
	private boolean on=true;
	/**
	 * The current entry read from log-malloc-simple input data strem.
	 * The object is updated with data read from input until the next object header is read. Then this object is processed.
	 */
	private Entry e = new Entry();
	private EntryProcessor entryProcessor=new EntryProcessor();
	public static void main(String[] args) throws Exception {
		if(args.length!=1)
		{
			printUsage();
		}else
		{
			new Analyze().start(args);
		}
	}

	private static void printUsage() {
		System.out.println("logmalloc output analyser program. Usage:");
		System.out.println(" 1. Create a pipe that will transfer logmalloc output: $ mkfifo /tmp/malloc.pipe");
		System.out.println(" 2. Start analyser on the fifo file: java -jar analyze.jar /tmp/malloc.pipe");
		System.out.println(" 3. Start program to analyze with log-malloc: LD_PRELOAD=liblog-malloc2.so java -jar any.jar 1022>/tmp/malloc.pipe");
		System.out.println(" 4. Use command line to instruct analyzer:");
		printCommands(System.out);
	}
	/**
	 * Start analyser on a separate thread and do user communication on the caller thread.
	 * Returns when communicateUser returns.
	 * @param args
	 */
	private void start(String[] args) {
		final File input = new File(args[0]);
		new Thread("logreader thread") {
			public void run() {
				processInput(input);
			};
		}.start();
		communicateUser();
	}
	/**
	 * Do processing of input stream in by reading it line by line in a blocking manner.
	 * @param f
	 */
	private void processInput(File f) {
		try {
			Reader r = new InputStreamReader(new FileInputStream(f));
			BufferedReader br = new BufferedReader(r);
			try
			{
				String line;
				while ((line = br.readLine()) != null) {
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
			}
			System.err.println("Input closed.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * Process the current entry that is being updated right now by input.
	 * In case the current entry is empty then does nothing.
	 * In case the current entry was opened with a "+..." line then store it into the entryproceesor
	 */
	private void processEntry() {
		if(on)
		{
			synchronized (this) {
				entryProcessor.processEntry(e);
				e = new Entry();
			}
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
				System.out.println("Command: ");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
			entryProcessor.processOutput(ps);
			ps.close();
		}finally
		{
			fos.close();
		}
	}
	/**
	 * Print the current state of the processor.
	 */
	private synchronized void print() {
		entryProcessor.processOutput(System.out);
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
	}
}
