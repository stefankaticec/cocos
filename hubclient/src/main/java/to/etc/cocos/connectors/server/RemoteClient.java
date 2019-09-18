package to.etc.cocos.connectors.server;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.IRemoteCommandListener;
import to.etc.cocos.connectors.ifaces.IRemoteClient;
import to.etc.util.StringTool;

import java.util.HashMap;
import java.util.Map;

/**
 * This is the server-side proxy of a remote client.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 18-07-19.
 */
@NonNullByDefault
final public class RemoteClient implements IRemoteClient {
	final private String m_clientId;

	private final HubServer m_hubServer;

	private Map<Class<?>, JsonPacket> m_inventoryMap = new HashMap<>();

	private Map<String, RemoteCommand> m_commandMap = new HashMap<>();
	private Map<String, RemoteCommand> m_commandByKeyMap = new HashMap<>();

	public RemoteClient(HubServer server, String clientId) {
		m_hubServer= server;
		m_clientId = clientId;
	}

	void inventoryReceived(JsonPacket inventoryPacket) {
		synchronized(this) {
			m_inventoryMap.put(inventoryPacket.getClass(), inventoryPacket);
		}
	}

	@Override
	public String getClientID() {
		return m_clientId;
	}

	/**
	 * Retrieve the specified inventory type from the client.
	 */
	@Override
	@Nullable
	public <I extends JsonPacket> I getInventory(Class<I> packetClass) {
		JsonPacket jsonPacket = m_inventoryMap.get(packetClass);
		return (I) jsonPacket;
	}

	/**
	 * Send a command to the client.
	 */
	@Override
	public String sendJsonCommand(JsonPacket packet, long commandTimeout, @Nullable String commandKey, String description, @Nullable IRemoteCommandListener l) throws Exception {
		String commandId = StringTool.generateGUID();
		RemoteCommand command = new RemoteCommand(commandId, getClientID(), commandTimeout, commandKey, description);
		if(null != l)
			command.addListener(l);
		synchronized(m_hubServer) {
			if(null != commandKey) {
				if(m_commandByKeyMap.containsKey(commandKey))
					throw new IllegalStateException("The command with key=" + commandKey + " is already pending for client " + this);
			}

			m_commandMap.put(commandId, command);
		}
		m_hubServer.sendJsonCommand(command, packet);
		return commandId;
	}
}
