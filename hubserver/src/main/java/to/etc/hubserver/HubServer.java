package to.etc.hubserver;

import io.netty.bootstrap.ServerBootstrap;
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
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import to.etc.log.EtcLoggerFactory;
import to.etc.util.FileTool;

import java.io.InputStream;
import java.net.InetAddress;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 *
 * Used documents:
 * https://medium.com/@maanadev/netty-with-https-tls-9bf699e07f01
 *
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 7-1-19.
 */
@NonNullByDefault
final public class HubServer implements ISystemContext {
	public static final String VERSION = "1.0";

	static public final int MAX_PACKET_SIZE = 1024 * 1024;

	static private Logger LOG = LoggerFactory.getLogger(HubServer.class);

	@Option(name = "-port", usage = "The listener port number")
	private int m_port = 9876;

	@Option(name = "-pinginterval", usage = "The #of seconds between PING messages, to keep the connection alive when idle")
	private int m_pingInterval = 120;

	@Option(name = "-listeners", usage = "The #of listener threads")
	private int m_listenerThreads = 1;

	@Nullable
	@Option(name = "-ident", usage = "Set the unique identifier for this server")
	private String m_ident;

	@Option(name = "-nio", usage = "Use nio instead of EPoll as the connection layer")
	private boolean m_useNio;

	private SecureRandom m_random;

	private ConnectionDirectory m_directory = new ConnectionDirectory(this);

	private HubServer() throws Exception {
		m_random = SecureRandom.getInstanceStrong();
	}

	static public void main(String[] args) throws Exception {
		new HubServer().run(args);
	}

	private void run(String[] args) throws Exception {
		CmdLineParser p = new org.kohsuke.args4j.CmdLineParser(this);
		try {
			//-- Decode the tasks's arguments
			p.parseArgument(args);
		} catch(CmdLineException x) {
			System.err.println("Invalid arguments: " + x.getMessage());
			System.err.println("Usage:");
			p.printUsage(System.err);
			System.exit(10);
		}

		//-- Do we have an ident?
		if(m_ident == null) {
			String name = InetAddress.getLocalHost().getHostName();
			m_ident = name;
		}
		String addr = InetAddress.getLocalHost().getHostAddress();
		System.out.println("Server ID is " + m_ident + " at " + addr);

		startServer();
	}

	private void startServer() throws Exception {
		EtcLoggerFactory.getSingleton().initializeFromResource(EtcLoggerFactory.DEFAULT_CONFIG_FILENAME, null);
		LOG.info("Starting server");
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

		try {
			SslContext sslContext = createSslContext();

			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup)
					.channel(channelClass)
					.handler(new LoggingHandler(LogLevel.INFO))
					.childHandler(new HubServerChannelInitializer(this, sslContext))
					.childOption(ChannelOption.AUTO_READ, true)
					.bind(m_port).sync().channel().closeFuture().sync();
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
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
		return Objects.requireNonNull(m_ident);
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
	public boolean checkServerSignature(byte[] signature, byte[] challenge) {
		return signature.length == 0;
	}

	public int getPingInterval() {
		return m_pingInterval;
	}

	public void setPingInterval(int pingInterval) {
		m_pingInterval = pingInterval;
	}
}
