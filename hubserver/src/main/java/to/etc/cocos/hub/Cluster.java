package to.etc.cocos.hub;

import org.eclipse.jdt.annotation.NonNullByDefault;

import java.util.ArrayList;
import java.util.HashMap;
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
	private final ISystemContext m_systemContext;

	private final String m_clusterId;

	/**
	 * All registered servers by server name
	 */
	private Map<String, Server> m_serverMap = new ConcurrentHashMap<>();

	/**
	 * Per organisation a list of servers that could handle that organisation.
	 */
	private Map<String, List<Server>> m_targetServiceMap = new HashMap<>();

	public Cluster(ISystemContext systemContext, String clusterId) {
		m_systemContext = systemContext;
		m_clusterId = clusterId;
	}

	/**
	 * Get the server by its server name, not by services it provides.
	 */
	public Server getServer(String id, List<String> targetList) {
		synchronized(this) {
			Server server = m_serverMap.computeIfAbsent(id, a -> new Server(m_systemContext, id));
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

	public Server getServiceServer(String organisation) {
		synchronized(this) {
			List<Server> servers = m_targetServiceMap.get(organisation);
			if(null == servers) {
				servers = m_targetServiceMap.get("*");
				if(null == servers) {
					throw new UnreachableOrganisationException(organisation);
				}
			}
			return servers.stream().filter(a -> a.getState() == ConnectionState.CONNECTED).findFirst().orElseThrow(() -> new UnreachableOrganisationException(organisation));
		}
	}

}
