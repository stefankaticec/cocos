package to.etc.hubserver;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import to.etc.util.ConsoleUtil;

import java.net.InetAddress;

/**
 * A Main class to start the HubServer from the command libe.
 *
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
@NonNullByDefault
final public class Main {
	static private Logger LOG = LoggerFactory.getLogger(HubServer.class);

	@Option(name = "-port", usage = "The listener port number")
	private int m_port = 9876;

	@Option(name = "-pinginterval", usage = "The #of seconds between PING messages, to keep the connection alive when idle")
	private int m_pingInterval = 120;

	@Option(name = "-listeners", usage = "The #of listener threads")
	private int m_listenerThreads = 1;

	@Nullable
	@Option(name = "-ident", usage = "Set the unique identifier for this hub")
	private String m_ident;

	@Option(name = "-nio", usage = "Use nio instead of EPoll as the connection layer")
	private boolean m_useNio;

	private volatile boolean m_terminate;

	static public void main(String[] args) throws Exception {
		new Main().run(args);
	}

	private void run(String[] args) throws Exception {
		CmdLineParser p = new org.kohsuke.args4j.CmdLineParser(this);
		try {
			//-- Decode the tasks's arguments
			p.parseArgument(args);
		} catch(CmdLineException x) {
			System.err.println("Invalid arguments: " + x.getMessage());
			System.err.println("Usage:");
			p.printUsage(System.err);
			System.exit(10);
		}

		//-- Do we have an ident?
		String ident = m_ident;
		if(ident == null) {
			String name = InetAddress.getLocalHost().getHostName();
			m_ident = ident = name;
		}
		String addr = InetAddress.getLocalHost().getHostAddress();
		System.out.println("Hub ID is " + m_ident + " at " + addr);

		HubServer server = new HubServer(m_port, ident, m_useNio);
		server.startServer();

		//-- Listen to signals to stop the thing
		if("linux".equalsIgnoreCase(System.getProperty("os.name"))) {
			Signal.handle(new Signal("HUP"), signal -> terminate(server));
		}

		ConsoleUtil.consoleLog("hub", "Server started");

		//-- Now: sleep until terminate is called
		while(! m_terminate) {
			Thread.sleep(5_000);
		}

		ConsoleUtil.consoleLog("Hub", "Main process stopping");
	}

	private void terminate(HubServer server) {
		ConsoleUtil.consoleLog("Hub", "Hangup signal received, terminate hub");
		try {
			server.terminateAndWait();
		} catch(Exception x) {
			x.printStackTrace();
		}
		m_terminate = true;
	}


}
