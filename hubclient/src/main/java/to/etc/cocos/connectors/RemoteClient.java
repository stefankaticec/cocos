package to.etc.cocos.connectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 18-07-19.
 */
@NonNullByDefault
final public class RemoteClient {
	final private String m_clientId;

	private final HubServerResponder m_hubServer;

	private Map<Class<?>, JsonPacket> m_inventoryMap = new HashMap<>();

	public RemoteClient(HubServerResponder server, String clientId) {
		m_hubServer= server;
		m_clientId = clientId;
	}

	void inventoryReceived(JsonPacket inventoryPacket) {
		synchronized(this) {
			m_inventoryMap.put(inventoryPacket.getClass(), inventoryPacket);
		}
	}

	public String getClientKey() {
		return m_clientId;
	}

	@Nullable
	public <I extends JsonPacket> I getInventory(Class<I> packetClass) {
		JsonPacket jsonPacket = m_inventoryMap.get(packetClass);
		return (I) jsonPacket;
	}
}
