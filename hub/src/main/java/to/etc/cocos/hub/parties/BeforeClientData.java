package to.etc.cocos.hub.parties;

/**
 * Should be removed, functionally unused.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 07-07-19.
 */
@Deprecated
public class BeforeClientData {
	private final Cluster m_cluster;

	private final String m_organisationId;

	private final String m_clientId;

	public BeforeClientData(Cluster cluster, String organisationId, String clientId) {
		m_cluster = cluster;
		m_organisationId = organisationId;
		m_clientId = clientId;
	}

	public String getOrganisationId() {
		return m_organisationId;
	}

	public String getClientId() {
		return m_clientId;
	}

	public Cluster getCluster() {
		return m_cluster;
	}
}
