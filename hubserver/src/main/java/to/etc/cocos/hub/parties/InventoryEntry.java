package to.etc.cocos.hub.parties;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A client inventory entry.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 18-07-19.
 */
@NonNullByDefault
final public class InventoryEntry {
	private final String m_dataFormat;

	private final byte[][] m_payload;

	public InventoryEntry(String dataFormat, byte[][] payload) {
		m_dataFormat = dataFormat;
		m_payload = payload;
	}

	public String getDataFormat() {
		return m_dataFormat;
	}

	public byte[][] getPayload() {
		return m_payload;
	}
}
