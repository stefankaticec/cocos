package to.etc.cocos.hub.parties;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.CentralSocketHandler;
import to.etc.cocos.hub.HubServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cluster is a collection of servers that all handle the same tasks. All servers always belong to
 * a cluster.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
@NonNullByDefault
final public class Cluster {
	private final HubServer m_systemContext;

	private final String m_clusterId;

	private Map<String, Client> m_clientMap = new HashMap<>();

	/**
	 * All registered servers by server name
	 */
	private Map<String, Server> m_serverMap = new ConcurrentHashMap<>();

	/**
	 * Per organisation a list of servers that could handle that organisation.
	 */
	private Map<String, List<Server>> m_targetServiceMap = new HashMap<>();

	final private LinkedList<Runnable> m_orderedActionList = new LinkedList<>();

	public Cluster(HubServer systemContext, String clusterId) {
		m_systemContext = systemContext;
		m_clusterId = clusterId;
	}

	@Nullable
	public synchronized Client findClient(String clientId) {
		return m_clientMap.get(clientId);
	}

	public Client registerAuthorizedClient(CentralSocketHandler socketHandler) {
		Client client;
		synchronized(this) {
			client = m_clientMap.computeIfAbsent(socketHandler.getMyID(), a -> new Client(this, m_systemContext, a));
			client.newConnection(socketHandler);
			m_orderedActionList.add(() -> sendClientRegisteredEvent(socketHandler.getMyID()));
		}
		runEvents();
		return client;
	}

	@Nullable
	public synchronized Server getRandomServer() {
		for(Server server : m_serverMap.values()) {
			if(server.isUsable())
				return server;
		}
		return null;
	}

	private synchronized List<Server> getAllServers() {
		return new ArrayList<>(m_serverMap.values());
	}

	/**
	 * Get the server by its server name, not by services it provides.
	 */
	public Server registerServer(String id, List<String> targetList) {
		synchronized(this) {
			Server server = m_serverMap.computeIfAbsent(id, a -> new Server(this, m_systemContext, id));
			removeServerFromServiceMap(server);				// Remove old registrations
			for(String service : targetList) {
				List<Server> servers = m_targetServiceMap.computeIfAbsent(service, a -> new ArrayList<>());
				servers.add(server);
			}
			return server;
		}
	}

	private synchronized void removeServerFromServiceMap(Server server) {
		for(List<Server> value : m_targetServiceMap.values()) {
			value.remove(server);
		}
	}

	@Nullable
	public Server findServiceServer(String organisation) {
		synchronized(this) {
			List<Server> servers = m_targetServiceMap.get(organisation);
			if(null == servers) {
				servers = m_targetServiceMap.get("*");
				if(null == servers) {
					return null;
				}
			}
			return servers.stream().filter(a -> a.getState() == ConnectionState.CONNECTED).findFirst().orElse(null);
		}
	}

	public synchronized void unregister(AbstractConnection abstractConnection) {
		if(abstractConnection instanceof Client) {
			m_clientMap.remove(abstractConnection.getFullId());
			m_orderedActionList.add(() -> sendClientUnregisteredEvents(abstractConnection.getFullId()));
		} else if(abstractConnection instanceof Server) {
			m_serverMap.remove(abstractConnection.getFullId());
		} else
			throw new IllegalStateException();
	}

	/**
	 * Send events in order.
	 */
	public synchronized void runEvents() {
		while(m_orderedActionList.size() > 0) {
			Runnable runnable = m_orderedActionList.removeFirst();
			m_systemContext.addEvent(runnable);
		}
	}

	/**
	 * Send all servers in the cluster the "client has left" event.
	 */
	private void sendClientUnregisteredEvents(String clientId) {
		for(Server server : getAllServers()) {
			try {
				server.sendEventClientUnregistered(clientId);
			} catch(Exception x) {
				x.printStackTrace();
			}
		}
	}

	private void sendClientRegisteredEvent(String clientId) {
		for(Server server : getAllServers()) {
			try {
				server.sendEventClientRegistered(clientId);
			} catch(Exception x) {
				x.printStackTrace();
			}
		}
	}

}
