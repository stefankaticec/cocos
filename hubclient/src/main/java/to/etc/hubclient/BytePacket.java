package to.etc.hubclient;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.util.StringTool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-1-19.
 */
@NonNullByDefault
final public class BytePacket {
	private byte[][] m_bufList = new byte[10][];

	private int m_bufferSize;

	private int m_length;

	private int m_bufCount;

	private int m_readOffset;

	private String m_targetID = "";

	private String m_sourceID = "";

	private String m_command = "";

	void reset(int length) {
		m_bufCount = 0;
		m_length = length;
		m_readOffset = 1;
	}

	void addBuffer(byte[] buffer) {
		if(m_bufCount >= m_bufList.length) {
			byte[][] nw = new byte[m_bufCount + 60][];
			System.arraycopy(m_bufList, 0, nw, 0, m_bufCount);
			m_bufList = nw;
		}
		m_bufList[m_bufCount++] = buffer;
		m_bufferSize = buffer.length;
	}

	public void dump() {
		int offset = 0;
		StringBuilder sb = new StringBuilder();
		sb.append("Packet buffer: length=").append(m_length).append("\n");
		for(int i = 0; i < m_bufCount; i++) {
			byte[] buf = m_bufList[i];

			int buflen = m_length;
			if(buflen > buf.length)
				buflen = buf.length;
			try {
				StringTool.dumpData(sb, buf, 0, buflen, "r> ");
			} catch(IOException x) {
				// morons
			}
			offset += m_length;
		}
		System.out.println(sb.toString());
	}

	/**
	 * Called when all buffers have been added.
	 */
	void finish() {
		m_targetID = readString();
		m_sourceID = readString();
		m_command = readString();
	}

	/**
	 * Read the next short string.
	 */
	private String readString() {
		try {
			int len = readByte();

			int bx = m_readOffset / m_bufferSize;
			int bo = m_readOffset % m_bufferSize;

			//-- String fits the buffer?
			if(bo + len < m_bufferSize) {
				byte[] buffer = m_bufList[bx];
				m_readOffset += len;
				return new String(buffer, bo, len, StandardCharsets.UTF_8);
			} else {
				byte[] ar = collectBytes(len);
				return new String(ar, StandardCharsets.UTF_8);
			}
		} catch(Exception x) {
			throw new ProtocolViolationException("Packet buffer format error: " + x);
		}
	}

	/**
	 * Read the next short byte array.
	 */
	private byte[] readBytes() {
		try {
			int len = readByte();
			return collectBytes(len);
		} catch(Exception x) {
			throw new ProtocolViolationException("Packet buffer format error: " + x);
		}
	}

	/**
	 * Read the next byte.
	 */
	private int readByte() {
		try {
			int bx = m_readOffset / m_bufferSize;
			int bo = m_readOffset % m_bufferSize;
			byte[] buffer = m_bufList[bx];
			m_readOffset++;
			return buffer[bo] & 0xff;
		} catch(Exception x) {
			throw new ProtocolViolationException("Packet buffer format error: " + x);
		}
	}

	private byte[] collectBytes(int len) {
		int bx = m_readOffset / m_bufferSize;
		int bo = m_readOffset % m_bufferSize;

		byte[] res = new byte[len];
		int destoff = 0;
		while(len > 0) {
			int todo = m_bufferSize - bo;
			if(todo > len)
				todo = len;
			System.arraycopy(m_bufList[bx], bo, res, destoff, todo);
			m_readOffset += todo;
			len -= todo;
			if(len <= 0)
				return res;

			destoff += todo;
			bo = 0;
			bx++;
		}
		return res;
	}

	@NonNullByDefault(false)
	public InputStream getRemainingInput() {
		return new InputStream() {
			private int m_offset = m_readOffset;

			@Override public int read(byte[] out, int off, int len) throws IOException {
				int bytesleft = m_length - m_offset;
				if(bytesleft <= 0)
					return -1;
				if(len > bytesleft)
					len = bytesleft;
				int bx = m_offset / m_bufferSize;
				int bo = m_offset % m_bufferSize;
				int nread = 0;
				while(len > 0) {
					int todo = m_bufferSize - bo;
					if(todo > len)
						todo = len;
					if(todo > bytesleft)
						todo = bytesleft;

					System.arraycopy(m_bufList[bx], bo, out, off, todo);
					len -= todo;
					m_offset += todo;
					nread += todo;
					if(len <= 0)
						return nread;

					//-- Buffer must have expired, so goto next
					bytesleft -= todo;
					off += todo;
					bo = 0;
					bx++;
				}
				return nread;
			}

			@Override public int read() {
				if(m_offset >= m_length)
					return -1;
				int bx = m_offset / m_bufferSize;
				int bo = m_offset % m_bufferSize;
				m_offset++;
				return m_bufList[bx][bo] & 0xff;
			}
		};
	}

	byte[][] getBuffers() {
		return m_bufList;
	}

	public int getType() {
		return m_bufList[0][0] & 0xff;
	}


	public String getSourceID() {
		return m_sourceID;
	}

	public String getTargetID() {
		return m_targetID;
	}

	public String getCommand() {
		return m_command;
	}
}
