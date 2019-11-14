package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * This will read a stdout stream of a process and collect the data from
 * it into a buffer. As soon as the buffer is larger than a max size, or
 * when no data has been received for T_LINGER time it will send the data
 * to the remote as a STDOUT packet.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-5-19.
 */
@NonNullByDefault
final public class StdoutPacketThread implements AutoCloseable {
	static private final int LINGERTIME_MS = 2_000;

	static private final int MAX_BUFFERED = 1024 * 100;

	private final CommandContext m_cctx;

	private final InputStream m_inputStream;

	private volatile boolean m_started;

	private volatile boolean m_terminate;

	@Nullable
	private Thread m_thread;

	private byte[] m_readBuffer = new byte[8192];

	final private CharsetDecoder m_decoder;

	final private CharBuffer m_outBuffer = CharBuffer.allocate(8192*4);

	final private ByteBuffer m_inBuffer = ByteBuffer.allocate(8192);

	private StringBuilder m_output = new StringBuilder(65536);

	/** The last time we pushed a packet */
	private long m_nextPushTime;

	public StdoutPacketThread(CommandContext cctx, InputStream inputStream, Charset streamEncoding) {
		m_cctx = cctx;
		m_inputStream = inputStream;
		m_decoder = streamEncoding.newDecoder();
	}

	public void start() {
		if(m_started)
			throw new IllegalStateException("Already started!");
		m_started = true;
		Thread thread = m_thread = new Thread(() -> mainLoop());
		thread.setDaemon(true);
		thread.setName("stdout#rd");
		thread.start();
		m_nextPushTime = System.currentTimeMillis() + LINGERTIME_MS;
	}

	/**
	 * This is the main loop of the reader thread. It tries to read the stdout stream in such
	 * a way that it sends data as soon as it arrives. For that it needs to prevent being blocked
	 * when there is actual data to send, and that is why the code is way more complex than just
	 * a stream copy. In addition, the data arrives as a byte stream, and we need to properly
	 * encode this using the console's charset to a char string to send to the portal.
	 *
	 * To prevent blocking when there is data to read we use available() to read as much as
	 * possible, and we push this to the packet buffer. Every time we push data we check whether
	 * it is time to emit a packet; we do that after LINGERTIME has passed without a push or when
	 * the buffer is too full. If there is no data available for 3 loops then we flush whatever
	 * data is present and block on a small read.
	 *
	 * The latter is actually important because otherwise we cannot detect eof, and the thread
	 * would loop forever.
	 */
	private void mainLoop() {
		try {
			int emptyLoops = 0;
			for(; ; ) {
				if(m_terminate)
					break;
				int avail = m_inputStream.available();
				if(avail > 0) {
					System.err.println("== avail " + avail);
					if(avail > m_readBuffer.length)
						avail = m_readBuffer.length;
					int read = m_inputStream.read(m_readBuffer, 0, avail);
					if(read < 0) {
						break;
					}
					if(read > 0) {
						pushData(read);
						emptyLoops = 0;
					} else {
						emptyLoops++;
					}
				} else {
					emptyLoops++;
					if(emptyLoops > 3) {
						//-- Did not get data for a while. Force a push, then do an actual read.
						appendCompleted(true);
						int read = m_inputStream.read(m_readBuffer, 0, 10);
						if(read == -1) {
							break;
						}
						pushData(read);
						emptyLoops = 0;
					}
				}
			}

			//-- Finish the decode
			m_decoder.decode(m_inBuffer, m_outBuffer, true);
			m_decoder.flush(m_outBuffer);
			m_output.append(m_outBuffer);
			if(! m_terminate)
				appendCompleted(true);								// Force sending the last packet
		} catch(Exception x) {
			x.printStackTrace();
			m_terminate = true;
		}
	}

	private void pushData(int len) {
		int off = 0;
		while(len > 0) {
			int todoBytes = m_inBuffer.capacity();
			if(todoBytes > len) {
				todoBytes = len;
			}

			//-- Put in buffer, then advance
			m_inBuffer.put(m_readBuffer, off, todoBytes);
			len -= todoBytes;
			off += todoBytes;

			//-- Convert to the correct encoding
			m_inBuffer.flip();
			m_decoder.decode(m_inBuffer, m_outBuffer, false);
			m_inBuffer.clear();
			m_outBuffer.flip();
			m_output.append(m_outBuffer);
			m_outBuffer.clear();
		}

		appendCompleted(false);
	}

	private void appendCompleted(boolean force) {
		//-- Is it time to push a packet?
		if(m_output.length() == 0) {
			return;
		}
		if(m_output.length() >= MAX_BUFFERED) {
			sendPacket(System.currentTimeMillis());
			return;
		}
		long cts = System.currentTimeMillis();
		if(cts >= m_nextPushTime || force) {
			sendPacket(cts);
		}
	}

	private void sendPacket(long cts) {
		String s = m_output.toString();
		m_output.setLength(0);
		m_nextPushTime = cts + LINGERTIME_MS;

		m_cctx.sendStdoutPacket(s);

		//System.out.println("<<packet>>");
		//System.out.println(s);
		//System.out.println("<</packet>>");
	}

	@Override public void close() throws Exception {
		if(! m_terminate) {
			//-- Wait for the thread to finish
			Thread thread = m_thread;
			if(null != thread) {
				m_thread = null;
				thread.join(120_000);							// Wait for 2 minutes max
				if(thread.isAlive()) {
					thread.interrupt();
					thread.join(15_000);
					if(thread.isAlive()) {
						System.err.println("stdout reader does not want to stop...");
					}
				}
			}
		} else {
			Thread thread = m_thread;
			if(null != thread) {
				m_thread = null;
				thread.interrupt();
				thread.join(10_000);
			}
		}

		try {
			m_inputStream.close();
		} catch(Exception x) {}
		m_started = false;
	}
}
