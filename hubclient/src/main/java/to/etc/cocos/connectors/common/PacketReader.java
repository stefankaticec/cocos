package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.util.ByteArrayUtil;
import to.etc.util.StringTool;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
	private Envelope m_envelope;

	@Nullable
	private Object m_body;

	private final IIsRunning m_runningFunctor;

	@Nullable
	private final Consumer<String> m_logger;

	private boolean m_logPackets = false;

	@Nullable
	private ByteArrayOutputStream m_baos;

	public PacketReader(IIsRunning runningFunctor, @Nullable Consumer<String> logger) {
		m_runningFunctor = runningFunctor;
		m_logger = logger;
		if(m_logPackets) {
			m_baos = new ByteArrayOutputStream();
		}
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
		ByteArrayOutputStream baos = m_baos;
		if(null != baos)
			baos.reset();

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
		Envelope c = Envelope.parseFrom(data);
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

		Consumer<String> logger = m_logger;
		if(baos != null && logger != null) {
			StringBuilder sb = new StringBuilder();
			sb.append("\n");
			byte[] bytes = baos.toByteArray();
			StringTool.dumpData(sb, bytes, 0, bytes.length, "r> ");

			logger.accept(sb.toString());
		}
		m_envelope = c;
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
			ByteArrayOutputStream baos = m_baos;
			if(null != baos) {
				baos.write(buffer, off, maxlen);
			}

			off += szrd;
		}
	}

	public Envelope getEnvelope() {
		Envelope envelope = m_envelope;
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
