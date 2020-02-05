package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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

	private volatile boolean m_finished;

	@Nullable
	private Thread m_readerThread;

	@Nullable
	private Thread m_writerThread;

	private byte[] m_readBuffer = new byte[8192];

	final private CharsetDecoder m_decoder;

	final private CharBuffer m_outBuffer = CharBuffer.allocate(8192*4);

	final private ByteBuffer m_inBuffer = ByteBuffer.allocate(8192);

	private StringBuilder m_output = new StringBuilder(65536);

	///** The last time we pushed a packet */
	//private long m_nextPushTime;

	private final ReentrantLock m_lock = new ReentrantLock();

	private Condition m_dataAvailable;

	private Condition m_bufferEmptied;

	public StdoutPacketThread(CommandContext cctx, InputStream inputStream, Charset streamEncoding) {
		m_cctx = cctx;
		m_inputStream = inputStream;
		m_decoder = streamEncoding.newDecoder();
		m_dataAvailable = m_lock.newCondition();
		m_bufferEmptied = m_lock.newCondition();
	}

	public void start() {
		if(m_started)
			throw new IllegalStateException("Already started!");
		m_started = true;
		Thread thread = m_readerThread = new Thread(() -> readerLoop());
		thread.setDaemon(true);
		thread.setName("stdout#rd");
		thread.start();

		thread = m_writerThread = new Thread(() -> writerLoop());
		thread.setDaemon(true);
		thread.setName("stdout#wr");
		thread.start();
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
	private void readerLoop() {
		try {
			int emptyLoops = 0;
			for(; ; ) {
				if(m_terminate)
					break;
				int avail = m_inputStream.available();
				if(avail > 0) {
					//System.err.println("== avail " + avail);
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
						//appendCompleted(true);
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
			m_inBuffer.flip();
			//System.err.println(">> finish: inBuffer size=" + m_inBuffer.position() + " limit=" + m_inBuffer.limit() + ", " + m_inBuffer.remaining());
			//System.err.println(">> finish: outBuffer size=" + m_outBuffer.length() + " limit=" + m_outBuffer.limit() + ", " + m_outBuffer.remaining() + ", pos " + m_outBuffer.position());
			m_decoder.decode(m_inBuffer, m_outBuffer, true);
			//System.err.println(">> finish: outBuffer size=" + m_outBuffer.length() + " limit=" + m_outBuffer.limit() + ", " + m_outBuffer.remaining() + ", pos " + m_outBuffer.position());

			m_decoder.flush(m_outBuffer);
			m_outBuffer.flip();
			//System.err.println(">> finish: outBuffer size=" + m_outBuffer.length() + " limit=" + m_outBuffer.limit() + ", " + m_outBuffer.remaining() + ", pos " + m_outBuffer.position());
			//m_output.append(m_outBuffer);
			appendOutput(m_outBuffer);
			m_finished = true;
		} catch(Exception x) {
			x.printStackTrace();
			m_terminate = true;
		}
	}

	private void pushData(int len) throws Exception {
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
			appendOutput(m_outBuffer);
			//m_output.append(m_outBuffer);
			m_outBuffer.clear();
		}
		//appendCompleted(false);
	}

	private long m_lastOutputTime;

	private long m_lastPacketTime;

	private void appendOutput(CharBuffer data) throws Exception {
		m_lock.lock();
		try {
			while(m_output.length() > 1024*1024) {
				m_bufferEmptied.await();
			}
			int l = m_output.length();
			m_output.append(data);
			int count = 0;
			while(l < m_output.length()) {
				if(0 == m_output.charAt(l++)) {
					count++;
				}
			}
			if(count > 0)
				System.err.println(">> ADDED " + count + " NUL CHARS");
			m_lastOutputTime = System.currentTimeMillis();
			m_dataAvailable.signal();						// There is data
		} finally {
			m_lock.unlock();
		}
	}

	//private void appendCompleted(boolean force) {
	//	//-- Is it time to push a packet?
	//	if(m_output.length() == 0) {
	//		return;
	//	}
	//	if(m_output.length() >= MAX_BUFFERED) {
	//		sendPacket(System.currentTimeMillis());
	//		return;
	//	}
	//	long cts = System.currentTimeMillis();
	//	if(cts >= m_nextPushTime || force) {
	//		sendPacket(cts);
	//	}
	//}

	private void writerLoop() {
		while(! m_terminate) {

			String packetData = null;
			m_lock.lock();
			try {
				//-- 1. Do we have output?
				int length = m_output.length();
				if(length > 0) {
					long cts = System.currentTimeMillis();

					//-- Is it time to send the packet?
					if(isPacketSendTime(cts, length)) {
						packetData = m_output.toString();
						m_output.setLength(0);
						m_lastPacketTime = cts;
						m_bufferEmptied.signal();
					} else {
						try {
							m_dataAvailable.await(1, TimeUnit.SECONDS);
						} catch(InterruptedException x) {
						}
					}
				} else if(m_finished) {
					return;
				}
			} finally {
				m_lock.unlock();
			}

			if(packetData != null) {
				if(packetData.contains("\u0000"))
					System.err.println("OUTPUT BUFFER CONTAINS NUL CHARACTERS");
				m_cctx.sendStdoutPacket(packetData);
			}
		}
	}

	private boolean isPacketSendTime(long cts, int length) {
		if(length > 10_000 || m_finished)
			return true;

		//-- If the last output is < 500ms ago then wait for more
		if(m_lastOutputTime >= cts - 500)
			return false;

		////-- Is the last time we sent a packet > 1 second ago?
		//if(m_lastPacketTime < cts - 1000)
		//	return true;

		return true;
	}

	//private void sendPacket(long cts) {
	//	String s = m_output.toString();
	//	m_output.setLength(0);
	//	m_nextPushTime = cts + LINGERTIME_MS;
	//
	//	m_cctx.sendStdoutPacket(s);
	//}

	@Override public void close() throws Exception {
		Thread rt = m_readerThread;
		Thread wt = m_writerThread;
		if(! m_terminate) {
			//-- Wait for the thread to finish
			if(null != rt && wt != null) {
				m_readerThread = null;
				m_writerThread = null;
				rt.join(120_000);							// Wait for 2 minutes max
				wt.join(10_000);
				if(wt.isAlive())
					wt.interrupt();
				if(rt.isAlive())
					rt.interrupt();
				rt.join(15_000);
				wt.join(15_000);
				if(rt.isAlive()) {
					System.err.println("stdout reader does not want to stop...");
				}
				if(wt.isAlive()) {
					System.err.println("stdout writer does not want to stop...");
				}
			}
		} else {
			if(null != rt && null != wt) {
				m_readerThread = null;
				m_writerThread = null;
				rt.interrupt();
				wt.interrupt();
				rt.join(10_000);
				wt.join(10_000);
			}
		}

		try {
			m_inputStream.close();
		} catch(Exception x) {}
		m_started = false;
	}
}
