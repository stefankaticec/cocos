package to.etc.cocos.tests;

import to.etc.cocos.hub.HubServer;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
public class TestAllBase {
	private HubServer m_hub;

	public HubServer hub() throws Exception {
		HubServer hub = m_hub;
		if(null == hub) {
			m_hub = hub = new HubServer(9890, "testHUB", false);
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
