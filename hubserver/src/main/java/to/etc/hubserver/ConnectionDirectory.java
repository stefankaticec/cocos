package to.etc.hubserver;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains the client and server connection data.
 *
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
@NonNullByDefault
final public class ConnectionDirectory {
	private final Map<String, Cluster> m_clusterMap = new HashMap<>();

	private final ISystemContext m_context;

	private Map<String, Client> m_clientMap = new HashMap<>();

	private int m_nextClientId;

	public ConnectionDirectory(ISystemContext context) {
		m_context = context;
	}

	public Server getServer(String clusterId, String serverId, List<String> targetList) {
		return getCluster(clusterId).getServer(serverId, targetList);
	}

	public Cluster getCluster(String clusterId) {
		synchronized(this) {
			Cluster cluster = m_clusterMap.computeIfAbsent(clusterId, a -> new Cluster(m_context, clusterId));
			return cluster;
		}
	}

	/**
	 * Get some server that is able to represent the specified organisation.
	 */
	public Server getOrganisationServer(String clusterId, String organisation) {
		Cluster cluster;
		synchronized(this) {
			cluster = m_clusterMap.computeIfAbsent(clusterId, a -> new Cluster(m_context, clusterId));
		}

		return cluster.getServiceServer(organisation);
	}

	public Pair<String, Client> createTempClient(String realID) {
		synchronized(this) {
			int id = m_nextClientId++;
			String randomId = "__" + id;
			Client client = new Client(m_context, realID);
			m_clientMap.put(randomId, client);
			return new Pair<>(randomId, client);
		}
	}

	/**
	 * Find - and remove - the client by temp ID.
	 */
	@Nullable
	public Client findTempClient(String tempClientID) {
		synchronized(this) {
			return m_clientMap.remove(tempClientID);
		}
	}

	public Client registerAuthorizedClient(Client tempClient) {
		synchronized(this) {
			Client client = m_clientMap.get(tempClient.getFullId());		// Already exists?
			if(null == client) {
				//-- No -> just use the temp client instance and we're done
				m_clientMap.put(tempClient.getFullId(), tempClient);
				return tempClient;
			}

			client.newConnection(tempClient);
			return client;
		}
	}
}
