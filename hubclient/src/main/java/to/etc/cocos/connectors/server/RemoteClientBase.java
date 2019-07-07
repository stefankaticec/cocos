package to.etc.cocos.connectors.server;

import to.etc.cocos.connectors.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 07-07-19.
 */
public class RemoteClientBase implements IRemoteClient {
	final private String m_clientId;

	public RemoteClientBase(String clientId) {
		m_clientId = clientId;
	}

	@Override public String getClientKey() {
		return m_clientId;
	}

	@Override public <I extends JsonPacket> I getInventory(Class<I> packetClass) {
		throw new UnsupportedOperationException();
	}
}
