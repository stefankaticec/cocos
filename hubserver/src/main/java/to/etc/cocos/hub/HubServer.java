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
import to.etc.function.FunctionEx;
import to.etc.log.EtcLoggerFactory;
import to.etc.util.ConsoleUtil;
import to.etc.util.FileTool;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 7-1-19.
 */
@NonNullByDefault
final public class HubServer implements ISystemContext {
	public static final String VERSION = "1.0";

	static public final int MAX_PACKET_SIZE = 1024 * 1024;

	final private int m_port;

	private int m_pingInterval = 120;

	private int m_listenerThreads = 1;

	final private String m_ident;

	final private boolean m_useNio;

	private final FunctionEx<String, String> m_clusterPasswordSource;

	final private SecureRandom m_random;

	final private ConnectionDirectory m_directory = new ConnectionDirectory(this);

	@Nullable
	private Channel m_serverChannel;

	@Nullable
	private ChannelFuture m_closeFuture;

	public HubServer(int port, String ident, boolean useNio, FunctionEx<String, String> clusterPasswordSource) throws Exception {
		m_port = port;
		m_ident = ident;
		m_useNio = useNio;
		m_clusterPasswordSource = clusterPasswordSource;
		m_random = SecureRandom.getInstanceStrong();
	}

	public void setListenerThreads(int listenerThreads) {
		m_listenerThreads = listenerThreads;
	}

	public void startServer() throws Exception {
		synchronized(this) {
			if(m_serverChannel != null)
				throw new IllegalStateException("The server is already running");
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
			SslContext sslContext = createSslContext();

			ServerBootstrap b = new ServerBootstrap();
			Channel serverChannel = b.group(bossGroup, workerGroup)
				.channel(channelClass)
				.handler(new LoggingHandler(LogLevel.INFO))
				.childHandler(new HubServerChannelInitializer(this, sslContext))
				.childOption(ChannelOption.AUTO_READ, true)
				//.bind(m_port).channel();
				.bind(m_port).sync().channel();

			ChannelFuture closeFuture = serverChannel.closeFuture();

			synchronized(this) {
				m_serverChannel = serverChannel;
				m_closeFuture = closeFuture;
			}
			closeFuture.addListener(future -> {
				ConsoleUtil.consoleLog("Hub", "Server closing down: releasing thread pools");
				bossGroup.shutdownGracefully();
				workerGroup.shutdownGracefully();
				synchronized(this) {
					m_closeFuture = null;
				}
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
	}

	public void terminate() {
		Channel serverChannel;
		synchronized(this) {
			serverChannel = m_serverChannel;
			if(null == serverChannel) {
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
//		SelfSignedCertificate ssc = new SelfSignedCertificate();
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

	@Override
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

	public int getPingInterval() {
		return m_pingInterval;
	}

	public void setPingInterval(int pingInterval) {
		m_pingInterval = pingInterval;
	}

	void log(String message) {
		ConsoleUtil.consoleLog("hub", message);
	}
}
