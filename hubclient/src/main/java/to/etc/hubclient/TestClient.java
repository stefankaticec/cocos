package to.etc.hubclient;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-1-19.
 */
public class TestClient {
	@Option(name = "-server", usage = "The IP address or host name for the daemon exchange to connect to", required = true)
	private String m_server;

	@Option(name = "-port", aliases = {"-p"}, usage = "The port number to connect to")
	private int m_port = 9876;

	@Option(name = "-id", usage = "The daemon ID to use, which defaults to a generated id")
	private String m_daemonId;

	@Option(name = "-target", usage = "The target we want to reach")
	private String m_targetId = "bloomville@Puzzler";

	private HubConnector m_client;

	static public void main(String[] args) throws Exception {
		new TestClient().run(args);
	}

	private void run(String[] args) throws Exception {
		loadOptions();
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

		m_client = new HubConnector(m_server, m_port, m_targetId, m_daemonId, new ClientResponder());
		m_client.start();

		for(;;) {
			int c = System.in.read();
			if(c == 'Q') {
				return;
			}
		}

	}

	private void loadOptions() {
		m_daemonId = "1234";

	}


}
