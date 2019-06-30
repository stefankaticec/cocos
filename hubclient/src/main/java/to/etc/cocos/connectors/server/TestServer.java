package to.etc.cocos.connectors.server;

import to.etc.cocos.connectors.HubConnector;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * This test server mimics the server side of the protocol.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-1-19.
 */
public class TestServer {
	@Option(name = "-server", usage = "The IP address or host name for the daemon exchange to connect to", required = true)
	private String m_server;

	@Option(name = "-port", aliases = {"-p"}, usage = "The port number to connect to")
	private int m_port = 9876;

	@Option(name = "-id", usage = "The server ID (in the format server@cluster)")
	private String m_serverId;

	private HubConnector m_connector;

	static public void main(String[] args) throws Exception {
		new TestServer().run(args);
	}

	private void run(String[] args) throws Exception {
		CmdLineParser p = new CmdLineParser(this);
		try {
			//-- Decode the tasks's arguments
			p.parseArgument(args);
		} catch(CmdLineException x) {
			System.err.println("Invalid arguments: " + x.getMessage());
			System.err.println("Usage:");
			p.printUsage(System.err);
			System.exit(10);
		}

		m_connector = new HubConnector(m_server, m_port, "", m_serverId, new ServerResponder(m_serverId));
		m_connector.start();

		for(;;) {
			int c = System.in.read();
			if(c == 'Q') {
				return;
			}
		}
	}
}
