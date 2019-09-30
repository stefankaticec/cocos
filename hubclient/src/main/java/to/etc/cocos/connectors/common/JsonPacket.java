package to.etc.cocos.connectors.common;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 06-07-19.
 */
public class JsonPacket {
	/** We need a single property to prevent a jackson serialization error when we just return a JsonPacket. */
	private int m_version = 1;

	public int getVersion() {
		return m_version;
	}

	public void setVersion(int version) {
		m_version = version;
	}
}
