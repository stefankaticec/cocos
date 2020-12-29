package to.etc.cocos.hub;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.parties.ConnectionDirectory;
import to.etc.cocos.hub.telnetcommands.HelpTelnetCommandHandler;
import to.etc.cocos.hub.telnetcommands.ListClientsTelnetCommandHandler;
import to.etc.cocos.hub.telnetcommands.ListServerTelnetCommandHandler;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.Envelope.PayloadCase;
import to.etc.function.FunctionEx;
import to.etc.log.EtcLoggerFactory;
import to.etc.smtp.Address;
import to.etc.smtp.Message;
import to.etc.telnet.TelnetServer;
import to.etc.util.ConsoleUtil;
import to.etc.util.DateUtil;
import to.etc.util.FileTool;
import to.etc.util.TimerUtil;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 7-1-19.
 */
@NonNullByDefault
final public class Hub {
	public static final String VERSION = "1.0";

	static public final int MAX_PACKET_SIZE = 1024 * 1024;

	final private int m_port;

	private int m_pingInterval = 120;

	private int m_listenerThreads = 1;

	final private String m_ident;

	final private boolean m_useNio;

	private final FunctionEx<String, String> m_clusterPasswordSource;

	private List<Address> m_mailTo;

	final private SecureRandom m_random;

	final private ConnectionDirectory m_directory = new ConnectionDirectory(this);

	@Nullable
	private Channel m_serverChannel;

	@Nullable
	private ChannelFuture m_closeFuture;

	private ExecutorService m_eventSendingExecutor = Executors.newSingleThreadExecutor();

	@Nullable
	final private SendGridMailer m_mailer;

	private HubState m_state = HubState.STOPPED;

	public Hub(int port, String ident, boolean useNio, FunctionEx<String, String> clusterPasswordSource, @Nullable SendGridMailer mailer, List<Address> mailTo) throws Exception {
		m_port = port;
		m_ident = ident;
		m_useNio = useNio;
		m_clusterPasswordSource = clusterPasswordSource;
		m_mailTo = mailTo;
		m_random = SecureRandom.getInstanceStrong();
		m_mailer = mailer;
	}

	public void setListenerThreads(int listenerThreads) {
		m_listenerThreads = listenerThreads;
	}

	public void startServer() throws Exception {
		synchronized(this) {
			if(m_state != HubState.STOPPED)
				throw new IllegalStateException("The server is already running");
			m_state = HubState.STARTING;
		}

		EtcLoggerFactory.getSingleton().initializeFromResource(EtcLoggerFactory.DEFAULT_CONFIG_FILENAME, null);
		ConsoleUtil.consoleLog("Hub", "Starting server");
		EventLoopGroup bossGroup;
		EventLoopGroup workerGroup;
		Class<? extends ServerChannel> channelClass;
		if(m_useNio) {
			bossGroup = new NioEventLoopGroup(m_listenerThreads);
			workerGroup = new NioEventLoopGroup();
			channelClass = NioServerSocketChannel.class;
		} else {
			bossGroup = new EpollEventLoopGroup(m_listenerThreads);
			workerGroup = new EpollEventLoopGroup();
			channelClass = EpollServerSocketChannel.class;
		}

		boolean failed = true;
		try {
			TimerUtil.scheduleAtFixedRate(60, 60, TimeUnit.SECONDS, () -> tick());
			SslContext sslContext = createSslContext();

			ServerBootstrap b = new ServerBootstrap();
			Channel serverChannel = b.group(bossGroup, workerGroup)
				.channel(channelClass)
				.handler(new LoggingHandler(LogLevel.INFO))
				.childHandler(new HubChannelInitializer(this, sslContext))
				.childOption(ChannelOption.AUTO_READ, true)
				//.bind(m_port).channel();
				.bind(m_port).sync().channel();

			ChannelFuture closeFuture = serverChannel.closeFuture();

			synchronized(this) {
				m_serverChannel = serverChannel;
				m_closeFuture = closeFuture;
				m_state = HubState.RUNNING;
			}
			closeFuture.addListener(future -> {
				ConsoleUtil.consoleLog("Hub", "Server closing down: releasing thread pools");
				bossGroup.shutdownGracefully();
				workerGroup.shutdownGracefully();
				TimerUtil.shutdownNow();
				synchronized(this) {
					m_closeFuture = null;
					m_state = HubState.STOPPED;
				}
				log("Hub terminated");
			});
			failed = false;
			//closeFuture.sync();
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			if(failed) {
				bossGroup.shutdownGracefully();
				workerGroup.shutdownGracefully();
			}
		}

		var ts = TelnetServer.createServer(7171);
		ts.addCommandHandler(new HelpTelnetCommandHandler());
		ts.addCommandHandler(new ListClientsTelnetCommandHandler(this));
		ts.addCommandHandler(new ListServerTelnetCommandHandler(this));
	}

	public void terminate() {
		Channel serverChannel;
		synchronized(this) {
			if(m_state != HubState.RUNNING) {
				throw new IllegalStateException("Cant terminate, not running.");
			}
			m_state = HubState.TERMINATING;
			serverChannel = m_serverChannel;
			if(null == serverChannel) {
				log("Sever channel is null.");
				return;
			}
			m_serverChannel = null;
		}
		serverChannel.close();
	}

	public void terminateAndWait() throws Exception {
		terminate();
		ChannelFuture closeFuture;
		synchronized(this) {
			closeFuture = m_closeFuture;
			if(null == closeFuture)
				return;
		}
		closeFuture.sync();
	}

	private X509Certificate getServerCertificate() throws Exception {
		CertificateFactory fact = CertificateFactory.getInstance("X.509");
		try(InputStream is = getClass().getResourceAsStream("/secure/server.crt")) {
			X509Certificate cer = (X509Certificate) fact.generateCertificate(is);
			return cer;
		}
	}

	private PrivateKey getServerKey() throws Exception {
		try(InputStream is = getClass().getResourceAsStream("/secure/server.pkcs8.key")) {
			String text = FileTool.readStreamAsString(is, "utf-8");
			String privKeyPEM = text.replace("-----BEGIN RSA PRIVATE KEY-----\n", "");
			privKeyPEM = privKeyPEM.replace("-----END RSA PRIVATE KEY-----", "").trim();
			privKeyPEM = privKeyPEM.replace("-----END PRIVATE KEY-----", "").trim();
			privKeyPEM = privKeyPEM.replace("-----BEGIN PRIVATE KEY-----", "").trim();

//			System.out.println(privKeyPEM);
			privKeyPEM = privKeyPEM.replace("\n", "");

			byte[] encoded = Base64.getDecoder().decode(privKeyPEM);

			// PKCS8 decode the encoded RSA private key
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
			KeyFactory kf = KeyFactory.getInstance("RSA");
			PrivateKey privKey = kf.generatePrivate(keySpec);
//			System.out.println(privKey);
			return privKey;
		}
	}

	private SslContext createSslContext() throws Exception {
		X509Certificate ssc = getServerCertificate();
		PrivateKey pk = getServerKey();

		SslContext sslCtx = SslContextBuilder.forServer(pk, "", ssc)
				.build();
		return sslCtx;
	}

	@NonNull public String getIdent() {
		return m_ident;
	}

	byte[] getChallenge() {
		return m_random.generateSeed(10);
	}

	public ConnectionDirectory getDirectory() {
		return m_directory;
	}

	/**
	 * Check the server supplied signature
	 */
	public boolean checkServerSignature(String clusterName, String serverName, byte[] challenge, byte[] signature) throws Exception {
		String password = m_clusterPasswordSource.apply(clusterName);
		if(null == password)
			return false;
		String str = password + ":" + serverName + "@" + clusterName;
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(str.getBytes(StandardCharsets.UTF_8));
		md.update(challenge);
		byte[] digest = md.digest();
		return Arrays.equals(digest, signature);
	}

	public void addEvent(Runnable run) {
		m_eventSendingExecutor.submit(run);
	}

	public int getPingInterval() {
		return m_pingInterval;
	}

	public void setPingInterval(int pingInterval) {
		m_pingInterval = pingInterval;
	}

	void log(String message) {
		ConsoleUtil.consoleLog("hub", message);
	}

	public synchronized HubState getState() {
		return m_state;
	}

	static public String getPacketType(Envelope env) {
		if(env.getPayloadCase() == PayloadCase.ACKABLE)
			return env.getAckable().getPayloadCase().name();
		return env.getPayloadCase().name();
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Error reporting.											*/
	/*----------------------------------------------------------------------*/

	/**
	 * Called when a send fails, this checks whether the cause is a SSL handshake timeout. If so
	 * it checks how many of those occurred in the last minute and reports if those errors last
	 * for > 2 minutes.
	 */
	public void registerFailure(@NonNull Throwable cause) {
		if(! cause.toString().contains("SslHandshakeTimeoutException"))
			return;

		Date now = new Date();
		registerError(now);
	}

	public void registerError(Date now) {
		incrementMinuteCounter(now);
		incrementHourCounter(now);
	}

	private synchronized ErrorCounter incrementMinuteCounter(Date ts) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(ts);
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		Date minute = cal.getTime();

		if(m_errorCounterMinuteList.size() > 0) {
			ErrorCounter errorCounter = m_errorCounterMinuteList.get(0);
			if(! errorCounter.getStart().before(minute)) {
				if(errorCounter.getEnd().after(minute)) {
					errorCounter.m_count++;
					return errorCounter;
				}
			}
		}

		ErrorCounter errorCounter = new ErrorCounter(minute, DateUtil.addMinutes(minute, 1));
		m_errorCounterMinuteList.add(0, errorCounter);
		errorCounter.m_count = 1;
		return errorCounter;
	}

	private synchronized ErrorCounter incrementHourCounter(Date ts) {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		Date hour = cal.getTime();

		if(m_errorCounterHourList.size() > 0) {
			ErrorCounter errorCounter = m_errorCounterHourList.get(0);
			if(! errorCounter.getStart().before(hour)) {
				if(errorCounter.getEnd().after(hour)) {
					errorCounter.m_count++;
					return errorCounter;
				}
			}
		}

		ErrorCounter errorCounter = new ErrorCounter(hour, DateUtil.addMinutes(hour, 60));
		m_errorCounterHourList.add(0, errorCounter);
		errorCounter.m_count = 1;
		return errorCounter;
	}

	public enum ErrorState {
		NONE,
		FAILED,
		FAILING,
		RECOVERING
	}

	private void checkErrorCounters() {
		checkErrorCountersForTest(new Date(), 6, 2);
	}

	public ErrorState checkErrorCountersForTest(Date when, int amount, int minutes) {
		List<ErrorCounter> res = new ArrayList<>();

		Calendar cal = Calendar.getInstance();
		cal.setTime(when);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.MINUTE, -10);
		Date fence = cal.getTime();

		String message = null;
		String subject = null;
		ErrorState newState = null;
		synchronized(this) {
			//-- Remove all error counters older than 10 minutes
			for(int i = m_errorCounterMinuteList.size() - 1; i >= 0; i--) {
				ErrorCounter errorCounter = m_errorCounterMinuteList.get(i);
				if(errorCounter.getStart().before(fence)) {
					//-- Old record- delete
					m_errorCounterMinuteList.remove(i);
				}
			}
			res.addAll(m_errorCounterMinuteList);

			//-- Remove everything older than 24 hours from the hour counter list.
			Date now = new Date();
			cal.setTime(now);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			cal.add(Calendar.HOUR, -24);
			fence = cal.getTime();
			for(int i = m_errorCounterHourList.size() - 1; i >= 0; i--) {
				ErrorCounter errorCounter = m_errorCounterHourList.get(i);
				if(errorCounter.getStart().before(fence)) {
					//-- Old record- delete
					m_errorCounterMinuteList.remove(i);
				}
			}

			//-- Now: how many errors did we have in the last n minutes?
			int errors = getErrorsInLastMinutes(when, minutes);
			if(errors >= amount) {
				//-- We are in error. Did we already report?
				if(m_errorWindowStarted != null) {
					return ErrorState.FAILED;							// Already notified; exit
				}

				m_errorWindowStarted = now;

				message = "SSL Timeout errors are occurring: " + errors + " already at " + now + " in the last " + minutes + " minutes";
				subject = "[cocos] SSL handshake errors on hub";
				newState = ErrorState.FAILING;
			} else if(errors == 0) {
				//-- We have no errors -> leave error state
				if(m_errorWindowStarted == null) {
					return ErrorState.NONE;
				}
				m_errorWindowStarted = null;

				message = "The SSL problems have magically disappeared at " + now + ", they started at " + m_errorWindowStarted;
				subject = "[cocos] SSL Handshake fixed";
				newState = ErrorState.RECOVERING;
			} else {
				return m_errorWindowStarted == null ? ErrorState.NONE : ErrorState.FAILED;
			}
		}

		if(message != null && subject != null) {
			System.err.println("------- message -----------");
			System.err.println(subject);
			System.err.println(message);

			SendGridMailer mailer = m_mailer;
			if(null != mailer) {
				Message m = new Message();
				m.setSubject(subject);
				m.setBody(message);
				m_mailTo.forEach(m::addTo);
				try {
					mailer.send(m);
				} catch(Exception x) {
					x.printStackTrace();
				}
			}
		}
		return newState;
	}

	private synchronized int getErrorsInLastMinutes(Date from, int minutes) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(from);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.MINUTE, -minutes);
		Date fence = cal.getTime();

		int errors = 0;
		for(ErrorCounter errorCounter : m_errorCounterMinuteList) {
			if(! errorCounter.getStart().before(fence)) {
				errors += errorCounter.m_count;
			}
		}
		return errors;
	}

	private void tick() {
		try {
			checkErrorCounters();
		} catch(Exception x) {
			x.printStackTrace();
		}
	}

	public synchronized List<ErrorCounter> getErrorCounterHourList() {
		return new ArrayList<>(m_errorCounterHourList);
	}

	public synchronized List<ErrorCounter> getErrorCounterMinuteList() {
		return new ArrayList<>(m_errorCounterMinuteList);
	}

	private List<ErrorCounter> m_errorCounterMinuteList = new ArrayList<>();

	private List<ErrorCounter> m_errorCounterHourList = new ArrayList<>();

	@Nullable
	private Date m_errorWindowStarted;

	static public class ErrorCounter {
		private final Date m_start;

		private final Date m_end;

		private int m_count;

		public ErrorCounter(Date start, Date end) {
			m_start = start;
			m_end = end;
		}

		public Date getStart() {
			return m_start;
		}

		public Date getEnd() {
			return m_end;
		}

		public int getCount() {
			return m_count;
		}
	}
}
