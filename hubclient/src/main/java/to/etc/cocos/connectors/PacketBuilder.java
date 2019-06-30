package to.etc.cocos.connectors;

import com.google.protobuf.Message;
import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.util.ByteArrayUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds response packets.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-1-19.
 */
@NonNullByDefault
final public class PacketBuilder {
	final private HubConnector m_connector;

	final private List<byte[]> m_data = new ArrayList<>();

	private byte[] m_currentBuffer;

	private int m_writeOffset;

	private int m_length;

	public PacketBuilder(HubConnector connector, int packetType, String targetId, String clientId, String command) {
		m_connector = connector;
		m_currentBuffer = connector.bufferAllocate();
		m_data.add(m_currentBuffer);
		m_writeOffset = 4;
		m_length = 4;
		writeByte(packetType);
		writeString(targetId);
		writeString(clientId);
		writeString(command);
	}

	public void writeString(String s) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		if(bytes.length > 255)
			throw new ProtocolViolationException("Output string too long (" + bytes.length + "): " + s);
		writeByte(bytes.length);
		writeBytes(bytes);
	}

	public void writeInt(int value) {
		writeByte((value >> 24) & 0xff );
		writeByte((value >> 16) & 0xff );
		writeByte((value >> 8) & 0xff );
		writeByte(value & 0xff );
	}

	public void writeBytes(byte[] bytes) {
		writeBytes(bytes, 0, bytes.length);
	}

	public void writeBytes(byte[] bytes, int off, int length) {
		while(length > 0) {
			int maxlen = m_currentBuffer.length - m_writeOffset;
			if(maxlen == 0) {
				allocateBuffer();
				maxlen = m_currentBuffer.length;
			}
			if(maxlen > length)
				maxlen = length;
			System.arraycopy(bytes, off, m_currentBuffer, m_writeOffset, maxlen);
			length -= maxlen;
			off += maxlen;
			m_writeOffset += maxlen;
			m_length += maxlen;
		}
	}

	public void writeByte(int type) {
		if(m_writeOffset >= m_currentBuffer.length) {
			allocateBuffer();
		}
		m_currentBuffer[m_writeOffset++] = (byte) type;
		m_length++;
	}

	private void allocateBuffer() {
		m_currentBuffer = m_connector.bufferAllocate();
		m_data.add(m_currentBuffer);
		m_writeOffset = 0;
	}

	public int length() {
		return m_length;
	}

	public byte[][] getAndReleaseBuffers() {
		if(m_data.size() == 0)
			throw new IllegalStateException("Transmit buffers have already been released");
		ByteArrayUtil.setInt(m_data.get(0), 0, length() - 4);
		byte[][] data = m_data.toArray(new byte[m_data.size()][]);
		m_data.clear();
		return data;
	}

	void releaseBuffers() {
		for(byte[] datum : m_data) {
			m_connector.bufferRelease(datum);
		}
		m_data.clear();
	}

	public void appendMessage(Message message) {
		writeBytes(message.toByteArray());
	}
}
