package to.etc.cocos.connectors.common;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore.Ack;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.Envelope.PayloadCase;
import to.etc.cocos.messages.Hubcore.HubErrorResponse;
import to.etc.cocos.messages.Hubcore.Pong;
import to.etc.function.IExecute;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.util.ByteBufferInputStream;
import to.etc.util.ClassUtil;
import to.etc.util.ConsoleUtil;
import to.etc.util.FileTool;
import to.etc.util.StringTool;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

/**
 * A connector to a Hub server. This connector keeps a single connection to the Hub server
 * and multiplexes data over that connection. It uses one or two threads depending on its connection
 * state.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-1-19.
 */
@NonNullByDefault
public abstract class HubConnectorBase<T extends Peer> {
	private final int m_id;

	private static int m_idCounter;

	private final ObjectMapper m_mapper;

	private final String m_logName;

	final private String m_server;

	final private int m_port;

	final private String m_myId;

	/** The endpoint ID */
	final private String m_targetId;

	private ConnectorState m_state = ConnectorState.STOPPED;

	private List<Consumer<ConnectorState>> m_stateListeners = new CopyOnWriteArrayList<>();

	/**
	 * The time, in seconds, between PING messages. This should correspond to the equally named value in the hub.
	 * When the receiver does not receive data for 2 * pinginterval seconds it must assume that the connection is dead
	 * and reconnect.
	 */
	private int m_pingInterval = 120;

	/** While not null the reader thread is active */
	@Nullable
	private Thread m_readerThread;

	/** While not null the writer thread is active */
	@Nullable
	private Thread m_writerThread;

	private long m_nextReconnect;

	private int m_reconnectCount;

	/** Set to the TS that the last packet was received, to check for PING timeouts */
	private long m_lastPacketReceived;

	@Nullable
	private Socket m_socket;

	@Nullable
	private InputStream m_is;

	@Nullable
	private OutputStream m_os;

	@Nullable
	private SSLSocketFactory m_socketFactory;

	private String m_serverVersion = "1.0";

	final private PacketReader m_packetReader = new PacketReader(this::isRunning, this::log);

	private final PacketWriter m_writer;

	/** Transmitter queue. Will be emptied as soon as the connection gets lost. */
	private List<PendingTxPacket> m_txQueue = new ArrayList<>();

	public enum PacketPrio {
		HUB, NORMAL, PRIO
	}

	@Nullable
	private Executor m_executor;

	private boolean m_executorWasCreated;

	@Nullable
	private ExecutorService m_eventExecutor;

	@Nullable
	private HubErrorResponse m_lastError;

	/**
	 * All (recently active) peers by their ID. This only contains the server for
	 * a client, but for a server it contains all clients.
	 */
	private final Map<String, T> m_peerMap = new HashMap<>();

	private ThreadFactory m_threadFactory = new ThreadFactory() {
		@Override
		@NonNullByDefault(false)
		public Thread newThread(Runnable r) {
			Thread t = Executors.defaultThreadFactory().newThread(r);
			t.setDaemon(true);
			return t;
		}
	};

	/**
	 * Returns the #of millis the peer needs to have been unavailable before packets sent to the peer will be failed immediately.
	 */
	public abstract long getPeerDisconnectedDuration();

	/**
	 * When T any task transmitting an ackable packet to a peer that has a full transmitter queue will block. When false
	 * the packet will be refused with an exception when the queue is full. The latter mode is used by servers.
	 */
	public abstract boolean isTransmitBlocking();

	/**
	 * Returns the max #of ackable packets that can be queued before exception or block.
	 */
	public abstract int getMaxQueuedPackets();

	protected abstract void onErrorPacket(Envelope env);

	protected abstract void handleCHALLENGE(Envelope heloPacket) throws Exception;

	protected abstract void handleAUTH(Envelope authPacket) throws Exception;

	protected abstract void handleCLAUTH(Envelope env) throws Exception;

	protected void handleCLCONN(Envelope env) throws Exception {
		throw new IllegalStateException("Unexpected packet type " + getPacketType(env));
	}

	protected void handleCLDISC(Envelope env) throws Exception {
		throw new IllegalStateException("Unexpected packet type " + getPacketType(env));
	}

	protected void handleCLINVE(Envelope env, ArrayList<byte[]> body, Peer peer) throws Exception {
		throw new IllegalStateException("Unexpected packet type " + getPacketType(env));
	}

	protected abstract T createPeer(String peerId);

	//protected abstract void handleUnackablePackets(Envelope env, ArrayList<byte[]> body) throws Exception;

	protected abstract void handleAckable(CommandContext cc, ArrayList<byte[]> body) throws Exception;

	protected HubConnectorBase(String server, int port, String targetId, String myId, String logName) {
		m_id = nextId();
		m_server = server;
		m_port = port;
		m_myId = myId;
		m_targetId = targetId;
		m_logName = logName;

		ObjectMapper om = m_mapper = new ObjectMapper();
		om.configure(Feature.ALLOW_MISSING_VALUES, true);
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		//SimpleModule module = new SimpleModule();
		//module.addSerializer(java.sql.Date.class, new DateSerializer());
		//om.registerModule(module);

		m_writer = new PacketWriter(this, om);
	}

	private void setState(ConnectorState cs) {
		ConnectorState oldState;
		synchronized(this) {
			oldState = m_state;
			m_state = cs;
			log("setState: " + cs);
		}
		if(oldState != cs) {
			notifyStateListeners(cs);
		}
	}

	private void notifyStateListeners(ConnectorState state) {
		for(Consumer<ConnectorState> stateListener : m_stateListeners) {
			try {
				stateListener.accept(state);
			} catch(Exception x) {
				log("State listener failed: " + x);
				x.printStackTrace();
			}
		}
	}

	private static synchronized int nextId() {
		return ++m_idCounter;
	}

	public ObjectMapper getMapper() {
		return m_mapper;
	}

	public void start() {
		synchronized(this) {
			if(getState() != ConnectorState.STOPPED)
				throw new ConnectorException("The connector is in state " + getState() + ", it can only be started in STOPPED state");

			setState(ConnectorState.CONNECTING);
			m_nextReconnect = 0;
			m_reconnectCount = 0;

			Thread wt = m_writerThread = new Thread(this::writerMain, "cw-"+getClass().getSimpleName()+"#" + m_id);
			wt.setDaemon(true);
			internalStart();
			wt.start();
		}
	}

	/**
	 * Cause the client to terminate. Do not wait; to wait call terminateAndWait().
	 */
	public void terminate() {
		synchronized(this) {
			log("Received terminate request");
			if(getState() == ConnectorState.STOPPED || getState() == ConnectorState.TERMINATING)
				return;
			setState(ConnectorState.TERMINATING);
			notifyAll();
		}
	}

	public void terminateAndWait() throws Exception {
		terminate();
		Thread rt, wt;
		synchronized(this) {
			if(getState() == ConnectorState.STOPPED) {
				return;
			}
			rt = m_readerThread;
			wt = m_writerThread;

		}
		if(null != rt) {
			rt.join(1000);
			rt.interrupt();
		}
		if(null != wt) {
			wt.join(1000);
			wt.interrupt();
		}
		if(null != rt) {
			rt.join(5_000);
			if(rt.isAlive()) {
				error("Reader thread does not want to die");
			} else {
				log("Reader thread terminated.");
			}
		}
		if(null != wt) {
			wt.join(5_000);
			if(wt.isAlive()) {
				error("Writer thread does not want to die");
			} else {
				log("Writer thread terminated.");
			}
		}
	}

	public void setExecutor(Executor executor) {
		m_executor = executor;
	}

	synchronized public Executor getExecutor() {
		Executor executor = m_executor;
		if(null == executor) {
			m_executorWasCreated = true;
			executor = m_executor = Executors.newCachedThreadPool(m_threadFactory);
		}
		return executor;
	}

	public synchronized Executor getEventExecutor() {
		ExecutorService eventExecutor = m_eventExecutor;
		if(null == eventExecutor) {
			m_eventExecutor = eventExecutor = Executors.newSingleThreadExecutor(m_threadFactory);
		}
		return eventExecutor;
	}

	private void cleanupAfterTerminate() {
		ExecutorService executor;
		ExecutorService eventExecutor;
		synchronized(this) {
			executor = m_executorWasCreated ? (ExecutorService) m_executor : null;
			eventExecutor = m_eventExecutor;
			m_executorWasCreated = false;
			m_eventExecutor = null;
			m_executor = null;
		}
		if(null != executor)
			executor.shutdownNow();
		if(null != eventExecutor)
			eventExecutor.shutdown();
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Writer thread handler.										*/
	/*----------------------------------------------------------------------*/

	private void writerMain() {
		ConnectorState oldState = getState();
		try {
			log("Writer started for id=" + m_myId + " targeting " + m_targetId + " on hub server " + m_server + ":" + m_port);

			for(;;) {
				boolean doContinue = doWriteAction();
				if(! doContinue)
					break;
			}
		} catch(Exception x) {
			if(isRunning())
				error(x, "Writer terminated with exception: " + x);
		} finally {
			Thread readerThread;
			synchronized(this) {
				m_writerThread = null;
				readerThread = m_readerThread;
				m_readerThread = null;
			}
			forceDisconnect("Writer terminating");

			//-- We must wait for the reader to terminate too
			if(readerThread != null && readerThread.isAlive()) {
				log("stopping: waiting for reader to terminate");
				try {
					readerThread.join(60_000);
				} catch(Exception x) {
					x.printStackTrace();
				}
				if(readerThread.isAlive())
					log("stopping: reader thread did not want to stop 8-/");
			}
			setState(ConnectorState.STOPPED);
			//synchronized(this) {
			//	m_state = ConnectorState.STOPPED;
			//}
			//notifyStateListeners(ConnectorState.STOPPED);
			cleanupAfterTerminate();
			log("Writer has terminated");
		}
	}

	private synchronized boolean isRunning() {
		return getState() == ConnectorState.CONNECTED || getState() == ConnectorState.AUTHENTICATED;
	}

	private boolean doWriteAction() {
		Runnable action;

		synchronized(this) {
			ConnectorState state = getState();
			switch(state) {
				default:
					log("Illegal state in writer: " + state);
					throw new IllegalStateException("Illegal state in writer: " + state);

				case TERMINATING:
					return false;

				case AUTHENTICATED:
				case CONNECTED:
					//-- We need to transmit packets when available
					IPacketTransmitter transmitter;
					if(m_txQueue.size() > 0) {
						PendingTxPacket pp = m_txQueue.remove(0);
						action = () -> transmitPacket(pp);
					} else {
						if(checkPingTimeout())
							return true;
						sleepWait(10_000L);
						return true;
					}
					break;

				case CONNECTING:
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

	/**
	 * Called regularly in CONNECTED mode when there is nothing to TX, this
	 * checks whether we received a packet within the ping timeout period. If
	 * not the connection is terminated with an error.
	 *
	 * @return true if there was a timeout and the connection was closed
	 *
	 */
	private synchronized boolean checkPingTimeout() {
		long fence = System.currentTimeMillis() - (m_pingInterval * 2 * 1000);		// Data must have been received after this
		if(m_lastPacketReceived < fence) {
			log("Ping timeout: no data received for " + (m_pingInterval * 2) + " seconds - disconnecting");
			forceDisconnect("Ping timeout");
			return true;
		}
		return false;
	}

	/**
	 * Transmit the packet. If sending fails we disconnect state. This gets
	 * called on the writer thread. If the send fails with an IOException and
	 * pushbackQueue is not null then the failing packet is requeued first
	 * on that queue, so that it will be retransmitted as soon as the connection
	 * is re-established. If the send has failed the channel will have been
	 * disconnected.
	 */
	private void transmitPacket(PendingTxPacket pp) {
		try {
			log("Transmitting packet "  + pp);
			OutputStream os;
			synchronized(this) {
				os = m_os;
				if(null == os)
					throw new SocketEofException("Sender socket is null");
			}
			m_writer.setOs(os);
			m_writer.sendEnvelope(pp.getEnvelope());
			m_writer.sendBody(pp.getBodyTransmitter());
			os.flush();
		} catch(Exception x) {
			String why;
			synchronized(this) {
				if(isConnectionCloseException(x)) {
					why = "Remote disconnect (" + x.toString() + ")";
				} else {
					ConnectorState state = getState();
					switch(state){
						default:
							break;

						case CONNECTING:
						case CONNECTED:
						case AUTHENTICATED:
							error("Transmit for packet " + pp + " failed: " + x + " in state " + state);
							if(x.getMessage().contains("rethrowing"))
								x.printStackTrace();
							break;
					}
				}
				why = "Packet send failed (" + x + ")";
			}

			forceDisconnect(why);
			m_txQueue.clear();
		}
	}

	/**
	 * Remote connection termination is a mess: lots of different exceptions can
	 * occur because of that. Detect the common ones.
	 */
	private boolean isConnectionCloseException(Exception x) {
		if(x instanceof SSLException)
			return true;
		if(x instanceof SocketException)
			return true;
		return false;
	}

	/**
	 * Put a packet into the transmitter queue, to be sent as soon as the transmitter is free.
	 */
	protected void sendPacketPrimitive(Envelope envelope, @Nullable IBodyTransmitter bodyTransmitter, IExecute onSendFailure) {
		sendPacketPrimitive(new PendingTxPacket(envelope, bodyTransmitter, onSendFailure, null));
	}

	/**
	 * Put a packet in the transmitter queue. Drop it if the queue gets too full.
	 */
	protected void sendPacketPrimitive(PendingTxPacket pp) {
		synchronized(this) {
			if(getState() == ConnectorState.STOPPED || getState() == ConnectorState.TERMINATING) {
				throw new IllegalStateException("Cannot send packets when connector is " + getState());
			}

			if(m_txQueue.size() > 20_000) {
				log("Transmitter queue full, dropping packet");
				return;
			}
			m_txQueue.add(pp);
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
			if(getState() != ConnectorState.RECONNECT_WAIT && getState() != ConnectorState.CONNECTING) {
				return;
			}
			setState(ConnectorState.CONNECTING);
		}

		try {
			SSLSocketFactory ssf = getSocketFactory();
			SSLSocket s = (SSLSocket) ssf.createSocket(m_server, m_port);
			s.setSoTimeout(m_pingInterval * 2 * 1000);						// If we do not receive anything for PINGINTERVAL seconds timeout
			log("Starting SSL handshake");
			s.startHandshake();

			m_socket = s;
			m_is = s.getInputStream();
			m_os = s.getOutputStream();
			StringBuilder sb = new StringBuilder();
			sb.append("Connected to ").append(m_server).append(':').append(m_port).append("\n");
			SSLSession session = s.getSession();
			Certificate[] cchain = session.getPeerCertificates();
			sb.append("The Certificates used by peer\n");
			for(int i = 0; i < cchain.length; i++) {
				sb.append("- subject DN ").append(((X509Certificate) cchain[i]).getSubjectDN()).append("\n");
			}
			sb.append("Peer host is ").append(session.getPeerHost()).append("\n");
			sb.append("Cipher is ").append(session.getCipherSuite()).append("\n");
			sb.append("Protocol is ").append(session.getProtocol()).append("\n");
			sb.append("ID is ").append(new BigInteger(session.getId())).append("\n");
			sb.append("Session created in ").append(session.getCreationTime()).append("\n");
			sb.append("Session accessed in ").append(session.getLastAccessedTime()).append("\n");
			if(getState() != ConnectorState.RECONNECT_WAIT && getState() != ConnectorState.CONNECTING){
				try {
					s.close();
				}catch(Exception e) {

				}
				return;
			}
			log(sb.toString());

			Thread th = m_readerThread = new Thread(this::readerMain, "cr-"+getClass().getSimpleName()+"#" + m_id);
			th.setDaemon(true);
			th.start();
			synchronized(this) {
				setState(ConnectorState.CONNECTED);
				m_lastPacketReceived = System.currentTimeMillis();
			}
		} catch(Exception x) {
			forceDisconnect("Connection failed: " + x);
		}
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Reader part..												*/
	/*----------------------------------------------------------------------*/
	/**
	 * This is the reader thread. It reads packet data from the server, and calls packetReceived for every
	 * packet found. The reader thread terminates on every communications error and disconnects the socket
	 * at that time. It is the responsibility of the writer thread to try to reconnect.
	 */
	private void readerMain() {
		String disconnectReason = "Normal termination";
		try {
			for(; ; ) {
				InputStream is;
				synchronized(this) {
					is = m_is;
					if(null == is)
						break;
				}
				m_packetReader.readPacket(is);
				synchronized(this) {
					m_lastPacketReceived = System.currentTimeMillis();
				}
				executePacket();
			}
		} catch(SocketEofException | SSLException eofx) {
			System.out.println("Got exception, my state is: "+ getState());
			if(isRunning()) {
				ConsoleUtil.consoleLog("reader terminated because of eof: " + eofx.getMessage());
				eofx.printStackTrace();
				disconnectReason = "Server disconnect";
			}
		} catch(Exception x) {
			//log("state " + getState());
			if(isRunning()) {
				error("reader terminated because of " + x);
				x.printStackTrace();
				disconnectReason = x.toString();
			}
		} finally {
			synchronized(this) {
				m_readerThread = null;
			}
			forceDisconnect(disconnectReason);
			log("reader terminated");
		}
	}

	private void executePacket() {
		Envelope env = m_packetReader.getEnvelope();
		log("Received packet: " + getPacketType(env));

		if(env.getSourceId().equals(getMyId()))
			throw new IllegalStateException("?? Packet source is myself?");

		try {
			ArrayList<byte[]> body = new ArrayList<>(m_packetReader.getReceiveBufferList());
			switch(env.getPayloadCase()) {
				default:
					throw new IllegalStateException("Unexpected packet type " + getPacketType(env));
					//handleUnackablePackets(env, body);
					//break;

				case HUBERROR:
					HubErrorResponse error = env.getHubError();

					log("Received HUB ERROR packet: " + error.getCode() + " " + error.getText());
					synchronized(this) {
						m_lastError = error;
					}

					try {
						onErrorPacket(env);
					} catch(Exception x) {
						log("Unexpected exception while handling error packet: " + x);
						x.printStackTrace();;
					}
					return;

				case CHALLENGE:
					handleCHALLENGE(env);
					break;

				case AUTH:
					handleAUTH(env);
					break;

				case CLIENTAUTH:
					handleCLAUTH(env);
					break;

				case CLIENTCONNECTED:
					handleCLCONN(env);
					break;

				case CLIENTDISCONNECTED:
					handleCLDISC(env);
					break;

				case ACK:
					handleAckPacket(env);
					break;

				case ACKABLE:
					handleAckablePacket(env, body);
					break;

				case PING:
					respondWithPong(env);
					break;

				case INVENTORY:
					handleCLINVE(env, body, getOrCreatePeer(env.getSourceId()));
					break;
			}
		} catch(Exception px) {
			Throwable t = px;
			while(t instanceof InvocationTargetException) {
				t = ((InvocationTargetException)t).getTargetException();
			}
			log("Fatal Packet Execute exception: " + t);
			t.printStackTrace();
			forceDisconnect(t.toString());
		}
	}

	/**
	 * Ack whatever was sent by the appropriate peer.
	 */
	private void handleAckPacket(Envelope env) {
		Peer peer;
		synchronized(this) {
			peer = m_peerMap.get(env.getSourceId());
		}
		if(null == peer) {
			log("Ack received for unknown peer=" + env.getSourceId());
			return;
		}
		peer.ackReceived(env.getAck().getSequence());
	}

	private void handleAckablePacket(Envelope env, ArrayList<byte[]> body) {
		respondWithAck(env);						// Always ack the packet as we've seen it

		Peer peer = getOrCreatePeer(env.getSourceId());
		if(peer.seen(env.getAckable().getSequence())) {
			log("Ignoring repeated packet with sequence# " + env.getAckable().getSequence() + " " + CommandNames.getPacketName(env));
			return;
		}

		peer.setConnected();
		CommandContext ctx = new CommandContext(this, env, peer);
		try {
			handleAckable(ctx, body);
		//} catch(CommandFailedException cfx) {
		//	sendHubErrorPacket(ctx, cfx);
		} catch(Exception x) {
			Throwable t = x;
			while(t instanceof InvocationTargetException) {
				t = ((InvocationTargetException)t).getTargetException();
			}
			log("Command exception: " + t);

			//-- If this was a command then respond with a command exception
			if(env.getAckable().hasCmd()) {
				peer.sendCommandErrorPacket(ctx.getSourceEnvelope(), t);
			} else {
				sendHubErrorPacket(env, t);
			}
		}
	}

	protected T getOrCreatePeer(String id) {
		if(id.isBlank())
			throw new IllegalStateException("Invalid peer ID");
		synchronized(this) {
			return m_peerMap.computeIfAbsent(id, a -> createPeer(id));
		}
	}

	/**
	 * Send a HUB error packet.
	 */
	protected void sendHubErrorPacket(Envelope src, Throwable cfx) {
		Envelope response = responseEnvelope(src, src.getSourceId())
			.setHubError(HubErrorResponse.newBuilder()
				.setText(cfx.getMessage())
				.setCode("command.exception")
				.setDetails(StringTool.strStacktrace(cfx))
				.build()
			).build();
		sendPacketPrimitive(response, null, () -> {});
	}

	/**
	 * Send a HUB error packet.
	 */
	protected void sendHubErrorPacket(Envelope src, ErrorCode code, Object... params) {
		String message = MessageFormat.format(code.getText(), params);
		Envelope response = responseEnvelope(src, src.getSourceId())
			.setHubError(HubErrorResponse.newBuilder()
				.setText(message)
				.setCode(code.name())
				.build()
			).build();
		sendPacketPrimitive(response, null, () -> {});
	}

	/**
	 * Force disconnect and enter the next appropriate state, depending on
	 * state and m_terminate. This is a no-op if the disconnection is
	 * already a fact. In that case no message will be reported either.
	 */
	protected void forceDisconnect(@Nullable String why) {
		Socket socket;
		InputStream is;
		OutputStream os;
		synchronized(this) {
			if(null != why)
				log("forceDisconnect: " + why);

			socket = m_socket;
			is = m_is;
			os = m_os;
			m_socket = null;
			m_is = null;
			m_os = null;

			switch(getState()) {
				default:
					throw new IllegalStateException("Unexpected state: " + getState());

				case TERMINATING:
					//if(m_readerThread == null && m_writerThread == null) {
					//	/*
					//	 * JAL Questionable: this should not be possible really; this gets called
					//	 * on either the reader thread OR the writer thread, so both of these cannot
					//	 * be null..
					//	 */
					//	setState(ConnectorState.STOPPED);
					//	System.err.println("BUG BUG BUG BUG: forceDisconnect called but both threads seem to have terminated??");
					//}
					////m_state = ConnectorState.IDLE;
					break;

				case AUTHENTICATED:
					m_reconnectCount = 0;
					/*FALL_THROUGH*/

				case CONNECTED:
				case CONNECTING:
					/*
					 * Connection failed, or reconnect attempt failed -> enter wait.
					 */
					setState(ConnectorState.RECONNECT_WAIT);
					int count = m_reconnectCount++;
					int delta = count < 3 ? 250 :
						count < 6 ? 2000 :
						10000;
					m_nextReconnect = System.currentTimeMillis() + delta;
					break;

				case STOPPED:
					break;

				case RECONNECT_WAIT:
					break;
				//throw new IllegalStateException("We should not need to disconnect when we're waiting to disconnect");

			}
			notifyAll();
		}
		FileTool.closeAll(is, os, socket);
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

	@Nullable
	public synchronized HubErrorResponse getLastError() {
		return m_lastError;
	}

	protected void log(String s) {
		ConsoleUtil.consoleLog(m_logName, s);
	}

	public void error(String s) {
		ConsoleUtil.consoleError(m_logName, s);
	}

	private void error(Throwable t, String s) {
		ConsoleUtil.consoleError(m_logName, s);
		t.printStackTrace();
	}

	public String getMyId() {
		return m_myId;
	}

	public void authorized() {
		synchronized(this) {
			if(getState() == ConnectorState.CONNECTED) {
				setState(ConnectorState.AUTHENTICATED);
				notifyAll();								// Release the wr
			}
		}
	}


	/*----------------------------------------------------------------------*/
	/*	CODING:	Command handling											*/
	/*----------------------------------------------------------------------*/
	static private final byte[] NULLBODY = new byte[0];

	private void respondWithPong(Envelope src) {
		Envelope response = responseEnvelope(src, "")
			.setPong(Pong.newBuilder())
			.build();
		sendPacketPrimitive(response, null, () -> {
			log("Pong send failed!?");
		});
	}

	private void respondWithAck(Envelope src) {
		Envelope response = responseEnvelope(src, src.getSourceId())
			.setAck(Ack.newBuilder().setSequence(src.getAckable().getSequence()))
			.build();
		sendPacketPrimitive(response, null, () -> {
			log("Ack send failed -> disconnecting");
			forceDisconnect("Ack send failed");
		});
	}

	protected Envelope.Builder responseEnvelope(Envelope src, String targetId) {
		if(m_myId == null || m_myId.length() == 0)
			throw new IllegalStateException("Missing MyID");
		return Envelope.newBuilder()
			.setVersion(1)
			.setTargetId(targetId)
			.setSourceId(m_myId)
			;
	}

	protected String getPacketType(Envelope env) {
		if(env.getPayloadCase() == PayloadCase.ACKABLE)
			return env.getAckable().getPayloadCase().name();
		return env.getPayloadCase().name();
	}

	private String bodyType(@Nullable Object body) {
		return null == body ? "(void)" : body.getClass().getName();
	}

	//static private void unwrapAndRethrowException(CommandContext cc, Throwable t) throws Exception {
	//	while(t instanceof InvocationTargetException) {
	//		t = ((InvocationTargetException)t).getTargetException();
	//	}
	//
	//	if(t instanceof HubException) {
	//		cc.respondWithHubErrorPacket(PacketPrio.HUB, (HubException) t);
	//	}  if(t instanceof RuntimeException) {
	//		throw (RuntimeException) t;
	//	} else if(t instanceof Error) {
	//		throw (Error) t;
	//	} else if(t instanceof Exception) {
	//		throw (Exception) t;
	//	} else {
	//		throw new RuntimeException(t);
	//	}
	//}


	@Nullable
	public Object decodeBody(String bodyType, List<byte[]> data) throws IOException {
		switch(bodyType) {
			case CommandNames.BODY_BYTES:
				return data;

			case "":
				return null;
		}

		int pos = bodyType.indexOf(':');
		if(pos == -1)
			throw new ProtocolViolationException("Unknown body type " + bodyType);
		String clzz = bodyType.substring(pos + 1);
		String sub = bodyType.substring(0, pos);

		switch(sub) {
			default:
				throw new ProtocolViolationException("Unknown body type " + bodyType);

			case CommandNames.BODY_JSON:
				Class<?> bodyClass = ClassUtil.loadClass(getClass().getClassLoader(), clzz);
				return getMapper().readerFor(bodyClass).readValue(new ByteBufferInputStream(data.toArray(new byte[data.size()][])));
		}
	}

	protected Map<String, T> getPeerMap() {
		return m_peerMap;
	}

	protected void internalStart() {}

	public void addStateListener(Consumer<ConnectorState> listener) {
		m_stateListeners.add(listener);
	}
}
