package to.etc.cocos.connectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * This is the server-side proxy of a remote client.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 18-07-19.
 */
@NonNullByDefault
final public class RemoteClient {
	final private String m_clientId;

	private final HubServer m_hubServer;

	private Map<Class<?>, JsonPacket> m_inventoryMap = new HashMap<>();

	public RemoteClient(HubServer server, String clientId) {
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

	/**
	 * Retrieve the specified inventory type from the client.
	 */
	@Nullable
	public <I extends JsonPacket> I getInventory(Class<I> packetClass) {
		JsonPacket jsonPacket = m_inventoryMap.get(packetClass);
		return (I) jsonPacket;
	}

	///**
	// * Send a command to the client.
	// */
	//String sendJsonCommand(JsonPacket packet, long commandTimeout, @Nullable String commandKey, String description, IRemoteCommandListener l) throws Exception {
	//	return m_hubServer.sendJsonCommand(packet, commandTimeout, commandKey, description, l);
	//
	//
	//}
}
