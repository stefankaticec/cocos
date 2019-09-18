package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 15-5-19.
 */
@NonNullByDefault
final public class DaemonKey {
	private final String m_clientId;

	private final String m_serverId;

	public DaemonKey(String clientId, String serverId) {
		m_clientId = clientId;
		m_serverId = serverId;
	}

	public String getClientId() {
		return m_clientId;
	}

	public String getServerId() {
		return m_serverId;
	}

	@Override public boolean equals(@Nullable Object o) {
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;

		DaemonKey serverKey = (DaemonKey) o;

		if(!m_clientId.equals(serverKey.m_clientId))
			return false;
		return m_serverId.equals(serverKey.m_serverId);

	}

	@Override public int hashCode() {
		int result = m_clientId.hashCode();
		result = 31 * result + m_serverId.hashCode();
		return result;
	}

	@Override public String toString() {
		return m_clientId + ":" + m_serverId;
	}

	/**
	 * Decode a string in the format serverid@organisation.
	 */
	static public DaemonKey decode(String in) {
		if(in == null)
			throw new IllegalArgumentException("Null input");
		int pos = in.indexOf('@');
		if(pos != -1) {
			String name = in.substring(0, pos);
			String org = in.substring(pos + 1);
			if(name.length() > 0 && org.length() > 0)
				return new DaemonKey(org, name);
		}
		throw new IllegalArgumentException("The daemon database reference '" + in + "' is invalid. Use the format dbhandle@organisationid:daemonname");
	}
}
