package to.etc.cocos.hub.parties;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.CentralSocketHandler;
import to.etc.cocos.hub.Hub;

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

	private final Hub m_context;

	private Map<String, CentralSocketHandler> m_tmpClientMap = new HashMap<>();

	public ConnectionDirectory(Hub context) {
		m_context = context;
	}

	public Server getServer(String clusterId, String serverId, List<String> targetList) {
		return getCluster(clusterId).registerServer(serverId, targetList);
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
	@Nullable
	public Server findOrganisationServer(String clusterId, String organisation) {
		Cluster cluster;
		synchronized(this) {
			cluster = m_clusterMap.computeIfAbsent(clusterId, a -> new Cluster(m_context, clusterId));
		}

		return cluster.findServiceServer(organisation);
	}

	@Nullable
	public synchronized CentralSocketHandler findTempClient(String clientId) {
		return m_tmpClientMap.get(clientId);
	}

	///**
	// * Find - and remove - the client by temp ID.
	// */
	//@Nullable
	//public Client findTempClient(String tempClientID) {
	//	synchronized(this) {
	//		return m_clientMap.remove(tempClientID);
	//	}
	//}

	public synchronized void registerTmpClient(String tmpClientId, CentralSocketHandler centralSocketHandler) {
		m_tmpClientMap.put(tmpClientId, centralSocketHandler);
	}

	public synchronized void unregisterTmpClient(String tmpClientId) {
		m_tmpClientMap.remove(tmpClientId);
	}
}
