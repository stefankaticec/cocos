package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 24-5-19.
 */
@NonNullByDefault
final public class DaemonDbKey {
	private final DaemonKey m_serverKey;

	private final String m_databaseHandle;

	public DaemonDbKey(DaemonKey serverKey, String databaseHandle) {
		m_serverKey = serverKey;
		m_databaseHandle = databaseHandle;
	}

	public DaemonKey getServerKey() {
		return m_serverKey;
	}

	public String getDatabaseHandle() {
		return m_databaseHandle;
	}

	static public DaemonDbKey decode(String in) {
		if(in == null)
			throw new IllegalArgumentException("Null input");
		int pos = in.indexOf(':');
		if(pos != -1) {
			String dkey = in.substring(0, pos);
			String db = in.substring(pos + 1);
			return new DaemonDbKey(DaemonKey.decode(dkey), db);
		}
		throw new IllegalArgumentException("The daemon database reference '" + in + "' is invalid. Use the format daemonname@organisationid:database");
	}

	@Override public String toString() {
		return m_databaseHandle + "@" + m_serverKey;
	}
}
