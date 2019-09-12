package to.etc.cocos.connectors.common;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.HubException;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.CommandError;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.HubErrorResponse;
import to.etc.util.ByteBufferInputStream;
import to.etc.util.ClassUtil;
import to.etc.util.ConsoleUtil;
import to.etc.util.FileTool;
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
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A connector to a Hub server. This connector keeps a single connection to the Hub server
 * and multiplexes data over that connection. It uses one or two threads depending on its connection
 * state.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-1-19.
 */
@NonNullByDefault
public abstract class HubConnectorBase {
	static private final int MAX_PACKET_SIZE = 1024 * 1024;

	static private final Logger LOG = LoggerFactory.getLogger(HubConnectorBase.class);

	private final PublishSubject<ConnectorState> m_connStatePublisher;

	private final ObjectMapper m_mapper;

	private final String m_logName;

	private boolean m_logTx = false;

	private boolean m_logRx = false;

	private int m_dumpLimit = 1024;

	final private String m_server;

	final private int m_port;

	final private String m_myId;

	/** The endpoint ID */
	final private String m_targetId;

	/**
	 * Time, in seconds, between reconnect attempts
	 */
	private int m_reconnectInterval = 60;

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

	final private PacketReader m_packetReader = new PacketReader(this::isRunning, this::log);

	private final PacketWriter m_writer;

	private List<ISendPacket> m_txQueue = new ArrayList<>();

	private List<ISendPacket> m_txPrioQueue = new ArrayList<>();

	@Nullable
	private Executor m_executor;

	@Nullable
	private ExecutorService m_createdExecutor;

	@Nullable
	private ExecutorService m_eventExecutor;

	@Nullable
	private HubErrorResponse m_lastError;

	protected abstract void onErrorPacket(Envelope env);

	protected abstract void handlePacketReceived(CommandContext ctx, List<byte[]> data) throws Exception;


	protected HubConnectorBase(String server, int port, String targetId, String myId, String logName) {
		m_server = server;
		m_port = port;
		m_myId = myId;
		m_targetId = targetId;
		m_connStatePublisher = PublishSubject.<ConnectorState>create();
		m_logName = logName;

		ObjectMapper om = m_mapper = new ObjectMapper();
		om.configure(Feature.ALLOW_MISSING_VALUES, true);
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		//SimpleModule module = new SimpleModule();
		//module.addSerializer(java.sql.Date.class, new DateSerializer());
		//om.registerModule(module);

		m_writer = new PacketWriter(om);
	}

	public ObjectMapper getMapper() {
		return m_mapper;
	}

	public String getJsonText(Object object) {
		try {
			return getMapper().writerWithDefaultPrettyPrinter().writeValueAsString(object);
		} catch(Exception x) {
			System.out.println(">> render exception " + x);
			return x.toString();
		}
	}

	public void start() {
		synchronized(this) {
			if(m_state != ConnectorState.STOPPED)
				throw new ConnectorException("The connector is in state " + m_state + ", it can only be started in STOPPED state");

			m_state = ConnectorState.CONNECTING;
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
		log("Received terminate request");
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

	public void setExecutor(Executor executor) {
		m_executor = executor;
	}

	synchronized public Executor getExecutor() {
		Executor executor = m_executor;
		if(null == executor) {
			executor = m_executor = m_createdExecutor = Executors.newCachedThreadPool();
		}
		return executor;
	}

	public synchronized Executor getEventExecutor() {
		ExecutorService eventExecutor = m_eventExecutor;
		if(null == eventExecutor) {
			m_eventExecutor = eventExecutor = Executors.newSingleThreadExecutor();
		}
		return eventExecutor;
	}

	private void cleanupAfterTerminate() {
		m_connStatePublisher.onComplete();
		ExecutorService service;
		ExecutorService eventExecutor;
		synchronized(this) {
			service = m_createdExecutor;
			eventExecutor = m_eventExecutor;
			m_createdExecutor = null;
		}
		if(null != service)
			service.shutdownNow();
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

			m_connStatePublisher.onNext(oldState);
			for(;;) {
				boolean doContinue = doWriteAction();
				ConnectorState state = getState();
				if(state != oldState) {
					m_connStatePublisher.onNext(state);
					oldState = state;
				}
				if(! doContinue)
					break;
			}
		} catch(Exception x) {
			if(isRunning())
				error(x, "Writer terminated with exception: " + x);
		} finally {
			synchronized(this) {
				m_writerThread = null;
			}
			forceDisconnect("Writer terminating");
			ConnectorState state = getState();
			if(state != oldState) {
				m_connStatePublisher.onNext(state);
				oldState = state;
			}
			cleanupAfterTerminate();
			log("Writer has terminated");
		}
	}

	private synchronized boolean isRunning() {
		return m_state == ConnectorState.CONNECTED || m_state == ConnectorState.AUTHENTICATED;
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

				case AUTHENTICATED:
				case CONNECTED:
					//-- We need to transmit packets when available
					ISendPacket sender;
					if(m_txPrioQueue.size() > 0) {
						sender = m_txPrioQueue.remove(0);
					} else if(m_txQueue.size() > 0) {
						sender = m_txQueue.remove(0);
					} else {
						sleepWait(10_000L);
						return true;
					}
					action = () -> transmitPacket(sender);
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
	 * Transmit the packet. If sending fails we disconnect state.
	 */
	private void transmitPacket(ISendPacket sender) {
		try {
			OutputStream os;
			synchronized(this) {
				os = m_os;
				if(null == os)
					throw new SocketEofException("Sender socket is null");
			}
			m_writer.setOs(os);
			sender.send(m_writer);
		} catch(Exception x) {
			error("Send for packet " + sender + " failed: " + x);
			forceDisconnect("Packet send failed");
		}
	}

	public void sendPacket(ISendPacket packetSender) {
		synchronized(this) {
			if(m_state == ConnectorState.STOPPED || m_state == ConnectorState.TERMINATING) {
				throw new IllegalStateException("Cannot send packets when connector is " + m_state);
			}
			m_txQueue.add(packetSender);
			notify();
		}
	}

	public void sendPacketPrio(ISendPacket packetSender) {
		synchronized(this) {
			if(m_state == ConnectorState.STOPPED || m_state == ConnectorState.TERMINATING) {
				throw new IllegalStateException("Cannot send packets when connector is " + m_state);
			}
			m_txPrioQueue.add(packetSender);
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
			if(m_state != ConnectorState.RECONNECT_WAIT && m_state != ConnectorState.CONNECTING) {
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
			log(sb.toString());

			Thread th = m_readerThread = new Thread(this::readerMain, "conn#reader");
			th.setDaemon(true);
			th.start();
			synchronized(this) {
				m_state = ConnectorState.CONNECTED;
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
			for(;;) {
				InputStream is;
				synchronized(this) {
					is = m_is;
					if(null == is)
						break;
				}
				m_packetReader.readPacket(is);
				executePacket();
			}
		} catch(Exception x) {
			//log("state " + getState());
			if(isRunning()) {
				error("reader terminated because of " + x);
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
		if(env.hasHubError()) {
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

			//-- Disconnect.
			//forceDisconnect("HUB error: " + error.getCode());
			return;
		}

		log("Received packet: " + env.getPayloadCase());
		CommandContext ctx = new CommandContext(this, env);
		try {
			packetReceived(ctx, new ArrayList<>(m_packetReader.getReceiveBufferList()));
			//m_responder.acceptPacket(ctx, new ArrayList<>(m_packetReader.getReceiveBufferList()));
		} catch(CommandFailedException cfx) {
			log("Command failed: " + cfx);
			sendHubErrorPacket(ctx, cfx);
		} catch(Exception px) {
			log("Fatal command handler exception: " + px);
			forceDisconnect(px.toString());
		}
	}

	private void sendHubErrorPacket(CommandContext ctx, CommandFailedException cfx) {

		ctx.getResponseEnvelope().getHubErrorBuilder()
			.setText(cfx.getMessage())
			.setCode("command.exception")
			.setDetails(StringTool.strStacktrace(cfx))
			;
		ISendPacket sp = new ISendPacket() {
			@Override public void send(PacketWriter os) throws Exception {
				os.send(ctx.getResponseEnvelope().build(), null);
			}
		};
		sendPacket(sp);
	}

	public void sendCommandErrorPacket(CommandContext ctx, String code, String message, @Nullable String details) {
		CommandError cmdE = CommandError.newBuilder()
			.setId(ctx.getSourceEnvelope().getCmd().getId())
			.setName(ctx.getSourceEnvelope().getCmd().getName())
			.setCode(code)
			.setMessage(message)
			.setDetails(details)
			.build();
		ctx.getResponseEnvelope().setCommandError(cmdE);
		ISendPacket sp = new ISendPacket() {
			@Override public void send(PacketWriter os) throws Exception {
				os.send(ctx.getResponseEnvelope().build(), null);
			}
		};
		sendPacket(sp);
	}

	public void sendCommandErrorPacket(CommandContext ctx, ErrorCode code, Object... params) {
		String message = MessageFormat.format(code.getText(), params);
		CommandError cmdE = CommandError.newBuilder()
			.setId(ctx.getSourceEnvelope().getCmd().getId())
			.setName(ctx.getSourceEnvelope().getCmd().getName())
			.setCode(code.name())
			.setMessage(message)
			//.setDetails(details)
			.build();
		ctx.getResponseEnvelope().setCommandError(cmdE);
		ISendPacket sp = new ISendPacket() {
			@Override public void send(PacketWriter os) throws Exception {
				os.send(ctx.getResponseEnvelope().build(), null);
			}
		};
		sendPacket(sp);
	}

	public void sendCommandErrorPacket(CommandContext ctx, Throwable t) {
		String message = "Exception in command: " + t.toString();
		CommandError cmdE = CommandError.newBuilder()
			.setId(ctx.getSourceEnvelope().getCmd().getId())
			.setName(ctx.getSourceEnvelope().getCmd().getName())
			.setCode(ErrorCode.commandException.name())
			.setMessage(message)
			.setDetails(StringTool.strStacktrace(t))
			.build();
		ctx.getResponseEnvelope().setCommandError(cmdE);
		ISendPacket sp = new ISendPacket() {
			@Override public void send(PacketWriter os) throws Exception {
				os.send(ctx.getResponseEnvelope().build(), null);
			}
		};
		sendPacket(sp);
	}



	/**
	 * Force disconnect and enter the next appropriate state, depending on
	 * state and m_terminate. This is a no-op if the disconnection is
	 * already a fact. In that case no message will be reported either.
	 */
	protected void forceDisconnect(String why) {
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

			switch(m_state) {
				default:
					throw new IllegalStateException("Unexpected state: " + m_state);

				case TERMINATING:
					/*
					 * If we are terminating having a disconnected socket means we're IDLE.
					 */
					if(m_readerThread == null && m_writerThread == null) {
						m_state = ConnectorState.STOPPED;
					}
					//m_state = ConnectorState.IDLE;
					break;

				case AUTHENTICATED:
				case CONNECTED:
				case CONNECTING:
					/*
					 * Connection failed, or reconnect attempt failed -> enter wait.
					 */
					m_state = ConnectorState.RECONNECT_WAIT;
					int count = m_reconnectCount++;
					int delta = count < 3 ? 2000 :
						count < 6 ? 5000 :
							count < 10 ? 30000 :
								60000;
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

	public Observable<ConnectorState> observeConnectionState() {
		return m_connStatePublisher;
	}

	@Nullable
	public synchronized HubErrorResponse getLastError() {
		return m_lastError;
	}

	void log(String s) {
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
			if(m_state == ConnectorState.CONNECTED) {
				m_state = ConnectorState.AUTHENTICATED;
				notifyAll();								// Release the wr
			}
		}
	}


	/*----------------------------------------------------------------------*/
	/*	CODING:	Command handling											*/
	/*----------------------------------------------------------------------*/
	static private final byte[] NULLBODY = new byte[0];

	private void packetReceived(CommandContext ctx, List<byte[]> data) throws Exception {
		try {
			handlePacketReceived(ctx, data);
		} catch(Exception x) {
			unwrapAndRethrowException(ctx, x);
		}
		//Object body = decodeBody(ctx.getConnector(), ctx.getSourceEnvelope().getDataFormat(), data);
		//Method m = findHandlerMethod(ctx.getSourceEnvelope().getCommand(), body);
		//if(null == m) {
		//	throw new ProtocolViolationException("No handler for packet command " + ctx.getSourceEnvelope().getCommand() + " with body type " + bodyType(body));
		//}
		//
		//if(m.getAnnotation(Synchronous.class) != null) {
		//	invokeCall(ctx, body, m);
		//} else {
		//	invokeCallAsync(ctx, body, m);
		//}
	}

	private String bodyType(@Nullable Object body) {
		return null == body ? "(void)" : body.getClass().getName();
	}


	//private void invokeCallAsync(CommandContext ctx, @Nullable Object body, Method m) {
	//	ctx.getConnector().getExecutor().execute(() -> {
	//		try {
	//			invokeCall(ctx, body, m);
	//		} catch(Exception x) {
	//			ctx.log("Failed to execute " + m.getName() + ": " + x);
	//			try {
	//				unwrapAndRethrowException(ctx, x);
	//			} catch(Exception xx) {
	//				ctx.log("Could not return protocol error: " + xx);
	//			}
	//		}
	//	});
	//}
	//
	//private void invokeCall(CommandContext ctx, @Nullable Object body, Method m) throws Exception {
	//	try {
	//		if(null == body)
	//			m.invoke(this, ctx);
	//		else
	//			m.invoke(this, ctx, body);
	//	} catch(InvocationTargetException itx) {
	//		unwrapAndRethrowException(ctx, itx);
	//	}
	//}

	static public void unwrapAndRethrowException(CommandContext cc, Throwable t) throws Exception {
		while(t instanceof InvocationTargetException) {
			t = ((InvocationTargetException)t).getTargetException();
		}

		if(t instanceof HubException) {
			cc.respondWithHubErrorPacket((HubException) t);
		}  if(t instanceof RuntimeException) {
			throw (RuntimeException) t;
		} else if(t instanceof Error) {
			throw (Error) t;
		} else if(t instanceof Exception) {
			throw (Exception) t;
		} else {
			throw new RuntimeException(t);
		}
	}


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
}
