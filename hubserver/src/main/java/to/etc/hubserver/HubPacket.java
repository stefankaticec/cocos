package to.etc.hubserver;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Received packet on the hub. While this currently encapsulates
 * its data as a byte[] a later implementation will use packet
 * receive buffers to build the packet as we go.
 *
 * The packet encapsulates the mandatory core structure of
 * a packet:
 * <ul>
 *     <li>The packet type byte</li>
 *     <li>The target server ID</li>
 *     <li>the source server ID</li>
 *     <li>The command</li>
 * </ul>
 * and is followed by a byte array usually used to read a protobuf from.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 9-1-19.
 */
final public class HubPacket {
	static private final String EMPTY = "".intern();

	final private byte[] m_data;

	private final byte m_type;

	final private String m_targetId;

	final private String m_sourceId;

	private final String m_command;

	private int m_readIndex;

	public HubPacket(byte[] data) {
		int len = data.length;
		if(len < 4)
			throw new ProtocolViolationException("Too small a packet (" + len + ") received");
		m_data = data;
		m_type = data[0];
		m_readIndex = 1;
		m_targetId = readString();
		m_sourceId = readString();
		m_command = readString();
	}

	/**
	 * Read the next String.
	 */
	private String readString() {
		try {
			int len = m_data[m_readIndex++] & 0xff;
			if(len == 0)
				return EMPTY;
			String res = new String(m_data, m_readIndex, len, StandardCharsets.UTF_8);
			m_readIndex += len;
			return res;
		} catch(Exception x) {
			throw new ProtocolViolationException("Read buffer data access exception");
		}
	}

//	public int readByte() {
//		try {
//			return m_data[m_readIndex++] & 0xff;
//		} catch(Exception x) {
//			throw new ProtocolViolationException("Read buffer data access exception");
//		}
//	}

	public InputStream getRemainingStream() {
		return new ByteArrayInputStream(m_data, m_readIndex, m_data.length - m_readIndex);
	}

	public String getCommand() {
		return m_command;
	}

	final public byte getType() {
		return m_type;
	}

	final public int getLength() {
		return m_data.length;
	}

	public String getSourceId() {
		return m_sourceId;
	}

	public String getTargetId() {
		return m_targetId;
	}

	public byte[] getData() {
		return m_data;
	}
}
