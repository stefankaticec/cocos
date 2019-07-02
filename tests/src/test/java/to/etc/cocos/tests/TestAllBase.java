package to.etc.cocos.tests;

import org.junit.After;
import to.etc.cocos.connectors.ConnectorState;
import to.etc.cocos.connectors.HubConnector;
import to.etc.cocos.connectors.client.ClientResponder;
import to.etc.cocos.connectors.server.ServerResponder;
import to.etc.cocos.hub.HubServer;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
public class TestAllBase {
	public static final int HUBPORT = 9890;

	public static final String CLUSTERNAME = "junit";

	public static final String SERVERNAME = "rmtserver";

	public static final String CLIENTID = "testDaemon";
	public static final String CLIENTPASSWORD = "tokodoko";

	public static final String CLUSTERPASSWORD = "inujit";

	private HubServer m_hub;

	private HubConnector m_client;

	private HubConnector m_server;

	private String m_serverPassword;

	public HubConnector client() {
		HubConnector client = m_client;
		if(null == client) {
			m_client = client = new HubConnector("localhost", HUBPORT, CLUSTERNAME, CLIENTID, new ClientResponder(CLIENTPASSWORD, CLUSTERNAME));
			client.start();
		}
		return client;
	}

	public HubConnector server() {
		HubConnector server = m_server;
		if(null == server) {
			String id = SERVERNAME + "@" + CLUSTERNAME;
			m_server = server = new HubConnector("localhost", HUBPORT, "", id, new ServerResponder(m_serverPassword != null ? m_serverPassword : CLUSTERPASSWORD));
			server.start();
		}
		return server;
	}

	public HubConnector serverConnected() {
		server().observeConnectionState()
			.doOnNext(a -> System.out.println(">> got state " + a))
			.filter(a -> a == ConnectorState.AUTHENTICATED)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();
		return server();
	}

	public HubServer hub() throws Exception {
		HubServer hub = m_hub;
		if(null == hub) {
			m_hub = hub = new HubServer(HUBPORT, "testHUB", false, a -> CLUSTERPASSWORD);
			hub.startServer();
		}
		return hub;
	}

	@After
	public void tearDown() throws Exception {
		HubServer hub = m_hub;
		if(null != hub) {
			m_hub = null;
			hub.terminateAndWait();
		}

		HubConnector server = m_server;
		if(null != server) {
			m_server = null;
			server.terminateAndWait();
		}
		m_serverPassword = null;
	}

	protected void setServerPassword(String assword) {
		m_serverPassword = assword;
	}
}
