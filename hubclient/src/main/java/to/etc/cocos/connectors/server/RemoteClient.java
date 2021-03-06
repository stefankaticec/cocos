package to.etc.cocos.connectors.server;

import io.reactivex.subjects.PublishSubject;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.EvCommandError;
import to.etc.cocos.connectors.ifaces.EvCommandFinished;
import to.etc.cocos.connectors.ifaces.EvCommandOutput;
import to.etc.cocos.connectors.ifaces.IRemoteClient;
import to.etc.cocos.connectors.ifaces.IRemoteCommand;
import to.etc.cocos.connectors.ifaces.IRemoteCommandListener;
import to.etc.cocos.connectors.ifaces.ServerCommandEventBase;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

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

	//private Map<String, RemoteCommand> m_commandMap = new HashMap<>();

	private Map<String, RemoteCommand> m_commandByKeyMap = new HashMap<>();

	private final PublishSubject<ServerCommandEventBase> m_eventPublisher = PublishSubject.<ServerCommandEventBase>create();

	private CopyOnWriteArrayList<IRemoteCommandListener> m_listeners = new CopyOnWriteArrayList<>();

	public RemoteClient(HubServer server, String clientId) {
		m_hubServer= server;
		m_clientId = clientId;
		addListener(new IRemoteCommandListener() {
			@Override
			public void errorEvent(EvCommandError errorEvent) throws Exception {
				m_eventPublisher.onNext(errorEvent);
				m_hubServer.callCommandListeners(a -> a.errorEvent(errorEvent));
			}

			@Override
			public void completedEvent(EvCommandFinished ev) throws Exception {
				m_eventPublisher.onNext(ev);
				m_hubServer.callCommandListeners(a -> a.completedEvent(ev));
			}

			@Override
			public void stdoutEvent(EvCommandOutput ev) throws Exception {
				m_hubServer.callCommandListeners(a -> a.stdoutEvent(ev));
			}
		});
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
	public IRemoteCommand sendJsonCommand(String commandId, JsonPacket packet, Duration commandTimeout, @Nullable String commandKey, String description, @Nullable IRemoteCommandListener l) throws Exception {
		RemoteCommand command = new RemoteCommand(this, commandId, commandTimeout, commandKey, description);
		if(null != l)
			command.addListener(l);
		synchronized(m_hubServer) {
			if(null != commandKey) {
				if(m_commandByKeyMap.containsKey(commandKey))
					throw new IllegalStateException("The command with key=" + commandKey + " is already pending for client " + this);
			}

			//m_commandMap.put(commandId, command);
		}
		m_hubServer.sendJsonCommand(command, packet);
		return command;
	}

	@Nullable
	public RemoteCommand findCommandByKey(String key) {
		synchronized(m_hubServer) {
			return m_commandByKeyMap.get(key);
		}
	}

	public void addListener(IRemoteCommandListener l) {
		m_listeners.add(l);
	}

	public void removeListener(IRemoteCommandListener l) {
		m_listeners.remove(l);
	}

	public List<IRemoteCommandListener> getListeners() {
		return m_listeners;
	}

	@Override
	public PublishSubject<ServerCommandEventBase> getEventPublisher() {
		return m_eventPublisher;
	}
}
