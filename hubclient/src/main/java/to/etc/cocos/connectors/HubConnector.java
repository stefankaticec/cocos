package to.etc.cocos.connectors;

import com.google.protobuf.Message;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import to.etc.function.ConsumerEx;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.util.ByteArrayUtil;
import to.etc.util.StringTool;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * A connector to a Hub server. This connector keeps a single connection to the Hub server
 * and multiplexes data over that connection. It uses one or two threads depending on its connection
 * state.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-1-19.
 */
@NonNullByDefault
final public class HubConnector {
	private boolean m_logTx = true;

	private boolean m_logRx = true;

	private int m_dumpLimit = 1024;

	static private final int MAX_PACKET_SIZE = 1024 * 1024;

	static private final Logger LOG = LoggerFactory.getLogger(HubConnector.class);

	final private String m_server;

	final private int m_port;

	final private String m_clientId;

	/** The endpoint ID */
	final private String m_targetId;
	private final IHubResponder m_responder;

	/**
	 * Time, in seconds, between reconnect attempts
	 */
	final private int m_reconnectInterval = 60;

	private ConnectorState m_state = ConnectorState.STOPPED;

	/** While not null the reader thread is active */
	@Nullable
	private Thread m_readerThread;

	/** While not null the writer thread is active */
	@Nullable
	private Thread m_writerThread;

	private long m_nextReconnect;

	private int m_reconnectCount;

	@Nullable
	private Socket m_socket;

	@Nullable
	private InputStream m_is;

	@Nullable
	private OutputStream m_os;

	@Nullable
	private SSLSocketFactory m_socketFactory;

	private String m_serverVersion = "1.0";

	private final class TxPacket implements Runnable {
		final private byte[][] m_buffers;

		final private int m_length;

		TxPacket(PacketBuilder b) {
			m_buffers = b.getAndReleaseBuffers();
			m_length = b.length();
		}

		public byte[][] getBuffers() {
			return m_buffers;
		}

		public int getLength() {
			return m_length;
		}

		@Override public void run() {
			transmitBytes(this);
		}
	}

	private List<TxPacket> m_txQueue = new ArrayList<>();

	private List<TxPacket> m_txPrioQueue = new ArrayList<>();

	public HubConnector(String server, int port, String targetId, String clientId, IHubResponder responder) {
		m_server = server;
		m_port = port;
		m_clientId = clientId;
		m_targetId = targetId;
		m_responder = responder;
	}

	public void start() {
		synchronized(this) {
			if(m_state != ConnectorState.STOPPED)
				throw new ConnectorException("The connector is in state " + m_state + ", it can only be started in STOPPED state");

			m_state = ConnectorState.STARTING;
			m_nextReconnect = 0;
			m_reconnectCount = 0;

			Thread wt = m_writerThread = new Thread(this::writerMain, "conn#writer");
			wt.setDaemon(true);
			wt.setDaemon(true);
			wt.start();
		}
	}

	/**
	 * Cause the client to terminate. Do not wait; to wait call terminateAndWait().
	 */
	public void terminate() {
		synchronized(this) {
			if(m_state == ConnectorState.STOPPED || m_state == ConnectorState.TERMINATING)
				return;
			m_state = ConnectorState.TERMINATING;
			notifyAll();
		}
	}

	public void terminateAndWait() throws Exception {
		terminate();
		Thread rt, wt;
		synchronized(this) {
			if(m_state == ConnectorState.STOPPED) {
				return;
			}
			rt = m_readerThread;
			wt = m_writerThread;

		}
		if(null != rt)
			rt.join();
		if(null != wt)
			wt.join();
		synchronized(this) {
			m_state = ConnectorState.STOPPED;
		}
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Writer thread handler.										*/
	/*----------------------------------------------------------------------*/

	private void writerMain() {
		try {
			log("Writer started");
			while(doWriteAction()) {
			}
		} catch(Exception x) {
			LOG.error("Writer terminated with exception: " + x, x);
		} finally {
			synchronized(this) {
				m_writerThread = null;
			}
			forceDisconnect("Writer terminating");
		}
	}

	private boolean doWriteAction() {
		Runnable action;
		synchronized(this) {
			ConnectorState state = m_state;

			switch(state) {
				default:
					log("Illegal state in writer: " + state);
					throw new IllegalStateException("Illegal state in writer: " + state);

				case TERMINATING:
					return false;

				case WAIT_HELO:
				case CONNECTED:
					//-- We need to transmit packets when available
					if(m_txPrioQueue.size() > 0) {
						action = m_txPrioQueue.remove(0);
					} else if(m_txQueue.size() > 0) {
						action = m_txQueue.remove(0);
					} else {
						sleepWait(10_000L);
						return true;
					}
					break;

				case STARTING:
					action = this::reconnect;
					break;

				case RECONNECT_WAIT:
					long ets = m_nextReconnect;

					long cts = System.currentTimeMillis();
					if(cts >= ets) {
						action = this::reconnect;
						break;
					} else {
						long delta = ets - cts;
						log("wait_reconnect: " + delta +  " ms left");
						sleepWait(delta);
					}
					return true;
			}
		}
		action.run();
		return true;
	}

	private void transmitBytes(TxPacket packet) {
		OutputStream os;
		synchronized(this) {
			os = m_os;
			if(null == os)
				return;
		}

		StringBuilder sb = m_logTx ? new StringBuilder() : null;
		try {
			int szleft = packet.getLength();
			int dumpLeft = m_dumpLimit;
			if(sb != null)
				sb.append("Writing ").append(szleft).append(" bytes\n");
			for(byte[] buffer : packet.getBuffers()) {
				int maxlen = szleft;
				if(maxlen > buffer.length)
					maxlen = buffer.length;

				if(sb != null) {
					int dumpSz = maxlen;
					if(dumpSz > dumpLeft)
						dumpSz = dumpLeft;
					if(dumpSz > 0) {
						StringTool.dumpData(sb, buffer, 0, dumpSz, "w> ");
						dumpLeft -= dumpSz;
					}
				}
				os.write(buffer, 0, maxlen);

				szleft -= maxlen;
				if(szleft <= 0)
					return;
			}
		} catch(Exception x) {
			if(null != sb) {
				System.out.println(sb.toString());
				sb = null;
			}

			//-- Any error in the transmitter leads to disconnect and retry
			x.printStackTrace();
			forceDisconnect("Transmitter failed: " + x.toString());
		} finally {
			if(null != sb) {
				System.out.println(sb.toString());
				sb = null;
			}
		}
	}

	public void sendPacket(int packetCode, String command, @Nullable Message message) {
		PacketBuilder b = allocatePacketBuilder(packetCode, command);
		if(null != message)
			b.appendMessage(message);
		sendPacket(b);
	}

	public void sendPacketPrio(int packetCode, String command, @Nullable Message message) {
		PacketBuilder b = allocatePacketBuilder(packetCode, command);
		if(null != message)
			b.appendMessage(message);
		sendPacketPrio(b);
	}

	public void sendPacket(PacketBuilder b) {
		TxPacket packet = new TxPacket(b);
		releasePacket(b);
		synchronized(this) {
			m_txQueue.add(packet);
			notify();
		}
	}

	private void sendPacketPrio(PacketBuilder b) {
		TxPacket packet = new TxPacket(b);
		releasePacket(b);
		synchronized(this) {
			m_txPrioQueue.add(packet);
			notify();
		}
	}


	private void sleepWait(long ms) {
		try {
			wait(ms);
		} catch(InterruptedException x) {
		}
	}

	/**
	 * (re)connect to the daemon.
	 */
	private void reconnect() {
		synchronized(this) {
			if(m_state != ConnectorState.RECONNECT_WAIT && m_state != ConnectorState.STARTING) {
				return;
			}
			m_state = ConnectorState.CONNECTING;
		}

		try {
			SSLSocketFactory ssf = getSocketFactory();
			SSLSocket s = (SSLSocket) ssf.createSocket(m_server, m_port);
			s.startHandshake();

			m_socket = s;
			m_is = s.getInputStream();
			m_os = s.getOutputStream();
			log("Connected to " + m_server + ":" + m_port);
			SSLSession session = s.getSession();
			Certificate[] cchain = session.getPeerCertificates();
			System.out.println("The Certificates used by peer");
			for(int i = 0; i < cchain.length; i++) {
				System.out.println("- subject DN " + ((X509Certificate) cchain[i]).getSubjectDN());
			}
			System.out.println("Peer host is " + session.getPeerHost());
			System.out.println("Cipher is " + session.getCipherSuite());
			System.out.println("Protocol is " + session.getProtocol());
			System.out.println("ID is " + new BigInteger(session.getId()));
			System.out.println("Session created in " + session.getCreationTime());
			System.out.println("Session accessed in "+ session.getLastAccessedTime());

			Thread th = m_readerThread = new Thread(this::readerMain, "conn#reader");
			th.setDaemon(true);
			th.start();
			synchronized(this) {
				m_state = ConnectorState.WAIT_HELO;
			}
		} catch(Exception x) {
			forceDisconnect("Connection failed: " + x);
		}
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Reader part..												*/
	/*----------------------------------------------------------------------*/
	private int m_bufferSize = 8192;

	private int m_allocatedSize = 0;

	private int m_maxBufferSize = 20*1024*1024;

	private int m_bufferWaits = 0;

	/** Bufferpool: byte buffers used for collecting data */
	final private List<byte[]> m_bufferPool = new ArrayList<>();

	private ConsumerEx<BytePacket> m_packetState = this::respondHelo;

	/**
	 * The reader collects packets from the remote and pushes completed packets to the receive handler. The reader
	 * has two states:
	 * <ul>
	 *	<li>waitLength means it is reading bytes to determine the length of the next packet. The
	 *		length will always be at the start of a new buffer. Once the length is known it
	 *		will move to the next state to read the data content of the packet.</li>
	 *	<li>waitData means it is reading the remaining bytes of a single packet in whatever
	 *		data buffer is current</li>
	 * </ul>
	 * The two states alternate.
	 */
	private void readerMain() {
		String why = "Unknown reason";
		try {
			log("Reader started");
			readerLoop();
		} catch(Exception x) {
			why = x.toString();
			x.printStackTrace();
		} finally {
			forceDisconnect("Reader terminating: " + why);
			synchronized(this) {
				m_readerThread = null;
			}
		}
	}

	private void readerLoop() throws Exception {
		InputStream is;
		synchronized(this) {
			is = m_is;
			if(! inState(ConnectorState.WAIT_HELO, ConnectorState.CONNECTED) || is == null)
				return;
		}

		m_packetState = this::respondHelo;
		byte[] buffer = null;
		try {
			byte[] lenBuf = new byte[4];
			for(; ; ) {
				readFully(is, lenBuf, 4);                        // Read buffer length
				int length = ByteArrayUtil.getInt(lenBuf, 0);
				if(length <= 0 || length > MAX_PACKET_SIZE) {
					throw new ProtocolViolationException("Packet size unacceptable: " + length);
				}
				//-- Read a set of packets.
				BytePacket packet = allocateReceivePacket(length);
				while(length > 0) {
					buffer = bufferAllocate();
					int mx = m_bufferSize;
					if(mx > length)
						mx = length;
					readFully(is, buffer, mx);
					length -= mx;
					packet.addBuffer(buffer);
					buffer = null;
				}
				packet.finish();
				pushPacket(packet);
				if(! inState(ConnectorState.CONNECTED, ConnectorState.WAIT_HELO))
					return;
			}
		} finally {
			if(null != buffer)
				bufferRelease(buffer);

		}
	}

	private void readFully(InputStream is, byte[] data, int length) throws IOException {
		if(null == data)
			throw new IllegalAccessError("NULL DATA BUFFER");
		int offset = 0;
		while(length > 0) {
			int szrd = is.read(data, offset, length);
			if(szrd < 0)
				throw new ConnectorDisconnectedException();
			length -= szrd;
			offset += szrd;
		}
	}

	private void pushPacket(BytePacket packet) throws Exception {
		try {
			if(m_logRx)
				packet.dump();
			if(packet.getType() == 0x00 && CommandNames.PING_CMD.equals(packet.getCommand())) {
				sendPacketPrio(0x01, CommandNames.PONG_CMD, null);
			} else if(packet.getType() == 0x03) {
				//-- Server fatal error
				handleServerFatal(packet);
			} else {
				m_packetState.accept(packet);
			}
		} finally {
			releasePacket(packet);
		}
	}

	private void handleServerFatal(BytePacket packet) throws IOException {
		Hubcore.ErrorResponse response = Hubcore.ErrorResponse.parseFrom(packet.getRemainingInput());
		throw new FatalServerException("Server sent a fatal error: " + response.getCode() + ": " + response.getText());
	}

	/**
	 * Create a HELO response packet.
	 */
	private void respondHelo(BytePacket input) throws Exception {
		if(input.getType() != 0x00 || ! input.getCommand().equals(CommandNames.HELO_CMD)) {
			throw new ProtocolViolationException("Expecting HELO packet");
		}
		m_responder.onHelloPacket(this, input);
		m_packetState = this::respondAuth;
	}

	/**
	 * Wait for AUTH.
	 */
	private void respondAuth(BytePacket input) throws Exception {
		if(input.getType() != 0x01 || !input.getCommand().equals(CommandNames.AUTH_CMD)) {
			throw new ProtocolViolationException("Expecting AUTH packet");
		}
		m_responder.onAuth(this, input);
		synchronized(this) {
			m_state = ConnectorState.CONNECTED;
			m_reconnectCount = 0;
		}
		m_packetState = packet -> m_responder.acceptPacket(this, packet);
	}

	/**
	 * Force disconnect and enter the next appropriate state, depending on
	 * state and m_terminate. This is a no-op if the disconnection is
	 * already a fact. In that case no message will be reported either.
	 */
	private void forceDisconnect(String why) {
		log("forceDisconnect: " + why);
		Socket socket;
		InputStream is;
		OutputStream os;
		synchronized(this) {
			socket = m_socket;
			is = m_is;
			os = m_os;
			m_socket = null;
			m_is = null;
			m_os = null;

//			if(socket == null && is == null && os == null) {
//				//-- Already fully disconnected; assume state is OK
//				return;
//			}

			//-- Are we terminating?
			if(m_state != ConnectorState.TERMINATING && m_state != ConnectorState.RECONNECT_WAIT) {
				m_state = ConnectorState.RECONNECT_WAIT;
				int count = m_reconnectCount++;
				int delta = count < 3 ? 2000 :
						count < 6 ? 5000 :
								count < 10 ? 30000 :
										60000;
				m_nextReconnect = System.currentTimeMillis() + delta;
				m_state = ConnectorState.RECONNECT_WAIT;
			}
			if(m_readerThread == null && m_writerThread == null) {
				m_state = ConnectorState.STOPPED;
			}
			notifyAll();
		}
		try {
			if(is != null)
				is.close();
		} catch(Exception x) {
		}

		try {
			if(os != null)
				os.close();
		} catch(Exception x) {
		}
		try {
			if(socket != null)
				socket.close();
		} catch(Exception x) {
		}
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Buffer management											*/
	/*----------------------------------------------------------------------*/

	/**
	 * Allocate a read buffer.
	 */
	byte[] bufferAllocate() {
		synchronized(m_bufferPool) {
			if(m_bufferPool.size() > 0) {
				return m_bufferPool.remove(m_bufferPool.size() - 1);        // Get available buffer
			}

			//-- Can we allocate?
			if(m_allocatedSize < m_maxBufferSize) {
				m_allocatedSize += m_bufferSize;
				return new byte[m_bufferSize];
			}

			//-- We're out of them- we need to wait.
			m_bufferWaits++;
			for(; ; ) {
				try {
					m_bufferPool.wait(1000);
				} catch(InterruptedException x) {
					throw new TerminationException();
				}
				if(!inReceivingState())
					throw new TerminationException();
				if(m_bufferPool.size() > 0) {
					return m_bufferPool.remove(m_bufferPool.size() - 1);        // Get available buffer
				}
			}
		}
	}

	void bufferRelease(byte[] buffer) {
		if(null == buffer)
			throw new IllegalStateException("NULL DATA BUFFER");
		synchronized(m_bufferPool) {
			m_bufferPool.add(buffer);
			m_bufferPool.notify();
		}
	}

	private synchronized boolean inReceivingState() {
		return m_state == ConnectorState.CONNECTED || m_state == ConnectorState.WAIT_HELO;
	}

	private BytePacket allocateReceivePacket(int len) {
		BytePacket packet = new BytePacket();
		packet.reset(len);
		return packet;
	}

	private void releasePacket(BytePacket packet) {
		byte[][] buffers = packet.getBuffers();
		for(int i = 0, length = buffers.length; i < length; i++) {
			byte[] buffer = buffers[i];
			if(buffer == null)
				return;
			buffers[i] = null;
			bufferRelease(buffer);
		}
	}

	public PacketBuilder allocatePacketBuilder(int packetType, String targetId, String clientId, String command) {
		return new PacketBuilder(this, packetType, targetId, clientId, command);
	}

	public PacketBuilder allocatePacketBuilder(int packetType, String command) {
		return new PacketBuilder(this, packetType, m_targetId, m_clientId, command);
	}


	public void releasePacket(PacketBuilder b) {
		b.releaseBuffers();
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	SSL and security related code								*/
	/*----------------------------------------------------------------------*/

	/*
	 * https://deliciousbrains.com/ssl-certificate-authority-for-local-https-development/
	 * https://stackoverflow.com/questions/18889058/programmatically-import-ca-trust-cert-into-existing-keystore-file-without-using
	 * https://www.baeldung.com/java-keystore
	 */
	private TrustManager createTrustManager() throws Exception {
		//-- Load the server's certificate
		X509Certificate certificate = getServerCertificate();

		//-- Load a keystore with just the cert needed
		KeyStore ks = KeyStore.getInstance("pkcs12");
		char[] pass = "".toCharArray();
		ks.load(null, pass);

		ks.setCertificateEntry("puzzler-daemon", certificate);

		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ks);

		TrustManager[] managers = tmf.getTrustManagers();
		return managers[0];
	}

	private X509Certificate getServerCertificate() throws Exception {
		CertificateFactory fact = CertificateFactory.getInstance("X.509");
		try(InputStream is = getClass().getResourceAsStream("/secure/puzzlerCA.crt")) {
			X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
			return cer;
		}
	}

	private SSLSocketFactory getSocketFactory() throws Exception {
		SSLSocketFactory factory = m_socketFactory;
		if(null == factory) {
			SSLContext sslCtx = SSLContext.getInstance("TLS");
			sslCtx.init(null, new TrustManager[]{createTrustManager()}, new SecureRandom());
			factory = m_socketFactory = sslCtx.getSocketFactory();
		}
		return factory;
	}

	public synchronized ConnectorState getState() {
		return m_state;
	}

	public synchronized boolean inState(ConnectorState... stt) {
		for(ConnectorState state : stt) {
			if(m_state == state)
				return true;
		}
		return false;
	}

	private void log(String s) {
		System.out.println("cc: " + s);
	}
}
