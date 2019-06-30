package to.etc.cocos.hub;

import com.google.protobuf.Message;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.function.BiConsumerEx;
import to.etc.function.RunnableEx;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.util.ByteArrayUtil;
import to.etc.util.Pair;
import to.etc.util.StringTool;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final class CentralSocketHandler extends SimpleChannelInboundHandler<byte[]> {
	@NonNull
	private final HubServer m_central;

	@NonNull
	private final SocketChannel m_channel;

	@Nullable
	private AbstractConnection m_connection;

	@NonNull
	private BiConsumerEx<ChannelHandlerContext, HubPacket> m_state = this::newState;

	@NonNull
	private String m_clientId = "";

	@Nullable
	private byte[] m_challenge;

	private byte[] m_lenBuf = new byte[4];

	public CentralSocketHandler(HubServer central, SocketChannel socketChannel) {
		m_central = central;
		m_channel = socketChannel;
	}

	private ConnectionDirectory getDirectory() {
		return m_central.getDirectory();
	}

	@Override protected void channelRead0(ChannelHandlerContext context, byte[] bytes) throws Exception {
		StringBuilder sb = new StringBuilder();
		StringTool.dumpData(sb, bytes, 0, bytes.length, "r> ");
		System.out.println(sb.toString());

		try {
			HubPacket packet = new HubPacket(bytes);
			m_state.accept(context, packet);
		} catch(ProtocolViolationException px) {
			throw px;
		} catch(Exception x) {
			x.printStackTrace();
			throw new ProtocolViolationException(x.toString());
		}
	}

	/**
	 * Called when the channel has just been opened. This sends an HELO packet to the client.
	 */
	@Override public void channelActive(ChannelHandlerContext ctx) throws Exception {
		ctx.channel().closeFuture().addListener(future -> {
			remoteDisconnected(ctx);
		});

		//-- Send HELO with challenge
		byte[] challenge = m_challenge = m_central.getChallenge();
		Hubcore.HelloChallenge helo = Hubcore.HelloChallenge.newBuilder()
				.setChallenge(com.google.protobuf.ByteString.copyFrom(challenge))
				.setVersion(1)
				.build();
		m_state = this::heloAnswerState;
		sendHubMessage(0x0, CommandNames.HELO_CMD, helo, null);
	}

	private void remoteDisconnected(ChannelHandlerContext ctx) {
		AbstractConnection connection = m_connection;
		if(null != connection) {
			log("Channel disconnected");
			connection.channelClosed();
		}
	}

	@Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		System.out.println("Connection exception: " + cause);
		ctx.close();
	}

	/**
	 * NEW state: data should not be received in this state, so disconnect.
	 */
	private void newState(ChannelHandlerContext ctx, HubPacket packet) {
		sendHubError("", () -> m_channel.disconnect(), ErrorCode.noDataExpected);
	}

	private void disconnectedState(ChannelHandlerContext ctx, HubPacket packet) {
		throw new IllegalStateException("We should be disconnected");
	}

	/**
	 * Called when the HELO answer arrives, this decodes the answer depending on the
	 * response.
	 */
	private void heloAnswerState(ChannelHandlerContext context, HubPacket packet) throws Exception {
		if(packet.getType() != 0x01) {
			throw new ProtocolViolationException("HELO response bad packet type " + Integer.toString(packet.getType(), 16));
		}
		try {
			String name = packet.getCommand();
			if(name.equals(CommandNames.CLNT_CMD)) {
				System.out.println("Got CLNT response");
				heloHandleClient(context, packet);
			} else if(name.equals(CommandNames.SRVR_CMD)) {
				System.out.println("Got SRVR response");
				heloHandleServer(context, packet);
			} else {
				throw new ProtocolViolationException("HELO response invalid: expecting CLNT or SRVR, got '" + name + "'");
			}
		} catch(FatalHubException fhx) {
			sendHubErrorAndDisconnect(packet.getCommand(), fhx.getCode(), fhx.getParameters());
		} catch(HubException hx) {
			sendHubError(packet.getCommand(), null, hx.getCode(), hx.getParameters());
		}
	}

	/**
	 * Packet format: SRVR response packet.
	 */
	private void heloHandleServer(ChannelHandlerContext context, HubPacket packet) throws Exception {
		//-- Get server and cluster ID
		String s = packet.getSourceId();
		int ix = s.indexOf("@");
		if(ix == -1)
			throw new ProtocolViolationException("Source ID " + s + " does not contain @");
		String serverId = s.substring(0, ix);
		String clusterId = s.substring(ix + 1);
		if(clusterId.length() == 0 || serverId.length() == 0)
			throw new ProtocolViolationException("Source ID " + s + " has invalid names");

		Hubcore.ServerHeloResponse r = Hubcore.ServerHeloResponse.parseFrom(packet.getRemainingStream());
		byte[] signature = r.getChallengeResponse().toByteArray();

		if( ! m_central.checkServerSignature(signature, Objects.requireNonNull(m_challenge))) {
			sendHubErrorAndDisconnect(packet.getCommand(), ErrorCode.invalidSignature);
			return;
		}

		Server server = getDirectory().getServer(clusterId, s, Arrays.asList("*"));				// Server id includes cluster id always
		server.newConnection(context.channel(), this);
		log("new connection for server " + server.getFullId() + " in state " + server.getState());

		//-- send back AUTH packet
		m_connection = server;
		m_state = server::newPacket;

		Hubcore.AuthResponse auth = Hubcore.AuthResponse.newBuilder()
				.build();
		sendHubMessage(0x01, CommandNames.AUTH_CMD, auth, null);
		System.out.println("AUTH sent");
	}

	private void heloHandleClient(ChannelHandlerContext context, HubPacket packet) throws Exception {
		String clientId = packet.getSourceId();
		String s = packet.getTargetId();
		int ix = s.indexOf("@");
		if(ix == -1)
			throw new ProtocolViolationException("Target ID " + s + " does not contain @");
		String organisationId = s.substring(0, ix);
		String clusterId = s.substring(ix + 1);

		Hubcore.ClientHeloResponse r = Hubcore.ClientHeloResponse.parseFrom(packet.getRemainingStream());
		if(1 != r.getVersion())
			throw new ProtocolViolationException("Invalid packet version " + r.getVersion());
		byte[] signature = r.getChallengeResponse().toByteArray();

		//-- We need to send a request for a "pending" client. Find a server that handles the destination
		Server server = getDirectory().getOrganisationServer(clusterId, organisationId);
		if(server.getState() != ConnectionState.CONNECTED)
			throw new UnreachableOrganisationException(server.getFullId() + " in state " + server.getState());

		/*
		 * We have at least a tentative connection, but before we can really register the client we need to
		 * authenticate; only when that works can we become the "real" client. This is necessary to prevent
		 * denial of service attacks: if we would replace the client connection already any attacker could
		 * cause connections to drop like flies.
		 */
		Pair<String, Client> pair = getDirectory().createTempClient(packet.getSourceId());
		Client client = pair.get2();
		client.newConnection(context.channel(), this);			// Associate with new client
		log("Unauthenticated client " + packet.getSourceId() + " mapped to temp id=" + client.getFullId());

		//-- Send a client authentication request to the server we've just found.
		server.sendMessage(packet.getType(), pair.get1(), packet.getCommand(), r, null);
	}

	/**
	 * Send a message that originates from the hub itself. These have an empty "sourceID" as it is the hub.
	 */
	void sendHubMessage(int packetCode, String command, @Nullable Message message, @Nullable RunnableEx after) {
		PacketBuilder pb = new PacketBuilder(m_channel.alloc(), (byte)packetCode, m_clientId, "", command);
		if(null != message)
			pb.appendMessage(message);
		ChannelFuture future = m_channel.writeAndFlush(pb.getCompleted());
		future.addListener((ChannelFutureListener) f -> {
			if(! f.isSuccess())
				m_channel.disconnect();
			else if(after != null){
				after.run();
			}
		});
	}

	/**
	 * Send an error message originating from the HUB (with an empty sourceID).
	 */
	void sendHubError(String failedCommand, @Nullable RunnableEx after, ErrorCode errorCode, Object... parameters) {
		Hubcore.ErrorResponse err = Hubcore.ErrorResponse.newBuilder()
				.setCode(errorCode.name())
				.setText(MessageFormat.format(errorCode.getText(), parameters))
				.build();
		sendHubMessage(0x02, failedCommand, err, after);
	}

	void sendHubErrorAndDisconnect(String failedCommand, ErrorCode errorCode, Object... parameters) {
		Hubcore.ErrorResponse err = Hubcore.ErrorResponse.newBuilder()
				.setCode(errorCode.name())
				.setText(MessageFormat.format(errorCode.getText(), parameters))
				.build();
		sendHubMessage(0x03, failedCommand, err, () -> {
			m_state = this::disconnectedState;
			m_channel.disconnect();
		});
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Messages from a specific source.							*/
	/*----------------------------------------------------------------------*/
	/**
	 * Send a message that originates from the hub itself. These have an empty "sourceID" as it is the hub.
	 */
	void sendMessage(int packetCode, String sourceID, String command, Message message, @Nullable RunnableEx after) {
		PacketBuilder pb = new PacketBuilder(m_channel.alloc(), (byte)packetCode, m_clientId, sourceID, command);
		pb.appendMessage(message);
		ChannelFuture future = m_channel.writeAndFlush(pb.getCompleted());
		future.addListener((ChannelFutureListener) f -> {
			if(! f.isSuccess())
				m_channel.disconnect();
			else if(after != null){
				after.run();
			}
		});
	}

	/**
	 * Send an error message originating from the HUB (with an empty sourceID).
	 */
	void sendError(String sourceID, String failedCommand, @Nullable RunnableEx after, ErrorCode errorCode, Object... parameters) {
		Hubcore.ErrorResponse err = Hubcore.ErrorResponse.newBuilder()
				.setCode(errorCode.name())
				.setText(MessageFormat.format(errorCode.getText(), parameters))
				.build();
		sendMessage(0x02, sourceID, failedCommand, err, after);
	}

	void sendErrorAndDisconnect(String sourceID, String failedCommand, ErrorCode errorCode, Object... parameters) {
		Hubcore.ErrorResponse err = Hubcore.ErrorResponse.newBuilder()
				.setCode(errorCode.name())
				.setText(MessageFormat.format(errorCode.getText(), parameters))
				.build();
		sendMessage(0x03, sourceID, failedCommand, err, () -> {
			m_state = this::disconnectedState;
			m_channel.disconnect();
		});
	}

	void log(String log) {
		System.out.println("ch: " + log);
	}

	public void forwardPacket(HubPacket packet) {
		ByteArrayUtil.setInt(m_lenBuf, 0, packet.getData().length);
		ChannelFuture future = m_channel.write(m_lenBuf);
		future.addListener((ChannelFutureListener) f -> {
			if(! f.isSuccess())
				m_channel.disconnect();
		});

		future = m_channel.writeAndFlush(packet.getData());
		future.addListener((ChannelFutureListener) f -> {
			if(! f.isSuccess())
				m_channel.disconnect();
		});
	}
}
