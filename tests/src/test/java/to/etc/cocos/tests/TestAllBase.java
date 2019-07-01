package to.etc.cocos.tests;

import to.etc.cocos.connectors.HubConnector;
import to.etc.cocos.connectors.client.ClientResponder;
import to.etc.cocos.connectors.server.ServerResponder;
import to.etc.cocos.hub.HubServer;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
public class TestAllBase {
	public static final int HUBPORT = 9890;

	public static final String CLUSTERNAME = "junit";

	public static final String SERVERNAME = "rmtserver";

	public static final String CLIENTID = "testDaemon";

	public static final String CLUSTERPASSWORD = "inujit";

	private HubServer m_hub;

	private HubConnector m_client;

	private HubConnector m_server;

	public HubConnector client() {
		HubConnector client = m_client;
		if(null == client) {
			m_client = client = new HubConnector("localhost", HUBPORT, CLUSTERNAME, CLIENTID, new ClientResponder());
			client.start();
		}
		return client;
	}

	public HubConnector server() {
		HubConnector server = m_server;
		if(null == server) {
			String id = SERVERNAME + "@" + CLUSTERNAME;
			m_server = server = new HubConnector("localhost", HUBPORT, "", id, new ServerResponder(id, CLUSTERPASSWORD));
			server.start();
		}
		return server;
	}

	public HubServer hub() throws Exception {
		HubServer hub = m_hub;
		if(null == hub) {
			m_hub = hub = new HubServer(HUBPORT, "testHUB", false, a -> CLUSTERPASSWORD);
			hub.startServer();
		}
		return hub;
	}

	public void tearDown() throws Exception {
		HubServer hub = m_hub;
		if(null != hub) {
			m_hub = null;
			hub.terminateAndWait();
		}
	}
}
