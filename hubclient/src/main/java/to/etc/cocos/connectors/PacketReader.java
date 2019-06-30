package to.etc.cocos.connectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.util.ByteArrayUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This class reads packets, and after the read contains (fresh) data of the
 * packet just read.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
@NonNullByDefault
final class PacketReader {
	static private final int BUFSIZE = 8192;

	private List<byte[]> m_receiveBufferList = new ArrayList<>();

	private byte[] m_headerBuffer = new byte[8];
	private byte[] m_lenBuffer = new byte[4];

	@Nullable
	private Hubcore.Envelope m_envelope;

	@Nullable
	private Object m_body;

	private final IIsRunning m_runningFunctor;

	public PacketReader(IIsRunning runningFunctor) {
		m_runningFunctor = runningFunctor;
	}

	/**
	 * Loop on the socket input stream, reading a complete packet. On any
	 * kind of problem this throws an exception which will always terminate
	 * this connector. This is usually picked up by the client who will then
	 * reconnect.
	 */
	public void readPacket(InputStream is) throws Exception {
		m_envelope = null;
		m_body = null;
		m_receiveBufferList.clear();

		readFully(is, m_headerBuffer);

		//-- The protocol header must start with #decaf111; we use that to prevent horrible trouble when sync is lost.
		if(ByteArrayUtil.compare(m_headerBuffer, 0, CommandNames.HEADER, 0, 4) != 0)
			throw new ProtocolViolationException("Invalid packet lead-in");

		//-- Get the envelope's length
		int len = ByteArrayUtil.getInt(m_headerBuffer, 4);
		if(len < 0 || len > 1024*1024) {
			throw new ProtocolViolationException("Invalid packet envelope length: " + len);
		}
		byte[] data = new byte[len];
		readFully(is, data);
		Hubcore.Envelope c = Hubcore.Envelope.parseFrom(data);
		if(c.getVersion() != 1)
			throw new IllegalStateException("Cannot accept envelope version " + c.getVersion());

		//-- Now get the payload
		readFully(is, m_lenBuffer);
		len = ByteArrayUtil.getInt(m_lenBuffer, 0);
		if(len < 0 || len > 10*1024*1024) {
			throw new ProtocolViolationException("Invalid packet body length: " + len);
		}
		int rest = len;
		while(rest > 0) {
			int todo = rest;
			if(todo > BUFSIZE) {
				todo = BUFSIZE;
			}
			byte[] buffer = new byte[todo];
			readFully(is, buffer);
			m_receiveBufferList.add(buffer);

			rest -= todo;
		}
	}

	private void readFully(InputStream is, byte[] buffer) throws Exception {
		int off = 0;
		while(off < buffer.length) {
			if(! m_runningFunctor.isRunning())
				throw new SocketEofException("Terminating");
			int maxlen = buffer.length - off;

			int szrd = is.read(buffer, off, maxlen);
			if(szrd < 0)
				throw new SocketEofException("Cannot read fully: read " + off + " of " + buffer.length + " bytes");
			off += szrd;
		}
	}

	public Hubcore.Envelope getEnvelope() {
		Hubcore.Envelope envelope = m_envelope;
		if(null == envelope)
			throw new IllegalStateException("No packet envelope current");
		return envelope;
	}

	@Nullable
	public Object getBody() {
		return m_body;
	}

	public List<byte[]> getReceiveBufferList() {
		return m_receiveBufferList;
	}
}
