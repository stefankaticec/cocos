package to.etc.cocos.hub.parties;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-09-19.
 */
@NonNullByDefault
final public class ByteBufferPacket {
	private final String m_dataFormat;

	private final int m_length;

	private final byte[][] m_buffers;

	public ByteBufferPacket(String dataFormat, int length, byte[][] buffers) {
		m_dataFormat = dataFormat;
		m_length = length;
		m_buffers = buffers;
	}

	public String getDataFormat() {
		return m_dataFormat;
	}

	public int getLength() {
		return m_length;
	}

	public byte[][] getBuffers() {
		return m_buffers;
	}
}
