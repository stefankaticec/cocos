package to.etc.cocos.hub;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Signal;
import to.etc.smtp.Address;
import to.etc.util.ConsoleUtil;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * A Main class to start the HubServer from the command libe.
 *
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
@NonNullByDefault
final public class Main {
	static private Logger LOG = LoggerFactory.getLogger(Hub.class);

	@Option(name = "-port", usage = "The listener port number")
	private int m_port = 8987;

	@Option(name = "-pinginterval", usage = "The #of seconds between PING messages, to keep the connection alive when idle")
	private int m_pingInterval = 120;

	@Option(name = "-listeners", usage = "The #of listener threads")
	private int m_listenerThreads = 1;

	@Nullable
	@Option(name = "-ident", usage = "Set the unique identifier for this hub")
	private String m_ident;

	@Option(name = "-nio", usage = "Use nio instead of EPoll as the connection layer")
	private boolean m_useNio;

	@Nullable
	@Option(name = "-sendgridkey", aliases = "-sk", usage = "The key for sending warning emails through SendGrid")
	private String m_mailerKey;

	@Nullable
	@Option(name = "-mailfrom", aliases = "-mf", usage = "The from address used when sending mails")
	private String m_mailFrom;

	@Option(name = "-mailTo", aliases = "-mt", usage = "The to address(es) for emails")
	private List<String> m_mailTo = new ArrayList<>();

	@Option(name = "-notelnet", usage = "Skip starting telnet server")
	private boolean m_noTelnet = false;

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
		ConsoleUtil.consoleLog("hub", "Hub ID is " + m_ident + " at " + addr  + ":" + m_port);

		//-- Do we want to have emails?
		String mailerKey = m_mailerKey;
		String mailFrom = m_mailFrom;
		List<Address> to = new ArrayList<>();
		SendGridMailer mailer = null;
		if(m_mailTo.size() > 0 && mailFrom != null && mailerKey != null) {
			mailer = new SendGridMailer(mailerKey, mailFrom);
			m_mailTo.forEach(a -> to.add(new Address(a)));
		}

		Hub server = new Hub(m_port, ident, m_useNio, clusterName -> "prutbzlael", mailer, to, m_noTelnet);
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

		ConsoleUtil.consoleLog("Hub", "Main process stopped");
	}

	private void terminate(Hub server) {
		ConsoleUtil.consoleLog("Hub", "Hangup signal received, terminate hub");
		try {
			server.terminateAndWait();
		} catch(Exception x) {
			x.printStackTrace();
		}
		m_terminate = true;
	}


}
