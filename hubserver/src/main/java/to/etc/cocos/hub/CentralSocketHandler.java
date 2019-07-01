package to.etc.cocos.hub;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.parties.AbstractConnection;
import to.etc.cocos.hub.parties.ConnectionDirectory;
import to.etc.cocos.hub.parties.Server;
import to.etc.cocos.hub.problems.ProtocolViolationException;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ClientHeloResponse;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.HelloChallenge;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ServerHeloResponse;
import to.etc.util.ConsoleUtil;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class CentralSocketHandler extends SimpleChannelInboundHandler<ByteBuf> {
	@NonNull
	private final HubServer m_central;

	@NonNull
	private final SocketChannel m_channel;

	@Nullable
	private AbstractConnection m_connection;

	@NonNull
	private String m_clientId = "";

	@Nullable
	private byte[] m_challenge;

	private ByteBuf m_intBuf;

	private byte[] m_lenBuf = new byte[4];

	private int m_length;

	private byte[] m_envelopeBuffer;

	private int m_envelopeOffset;

	private Envelope m_envelope;

	/**
	 * The state for reading a packet.
	 */
	private IReadHandler m_readState = this::readHeaderLong;

	private IPacketHandler m_packetState = this::expectHeloResponse;

	public CentralSocketHandler(HubServer central, SocketChannel socketChannel) {
		m_central = central;
		m_channel = socketChannel;
	}

	@Override protected void channelRead0(ChannelHandlerContext context, ByteBuf data) throws Exception {
		//-- Keep reading data from the buffer until empty and handle it.

		try {
			while(data.readableBytes() > 0) {
				m_readState.handleRead(context, data);
			}
		} catch(ProtocolViolationException px) {
			throw px;
		} catch(Exception x) {
			x.printStackTrace();
			throw new ProtocolViolationException(x.toString());
		}
	}

	@Override public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		super.handlerAdded(ctx);
		m_intBuf = ctx.alloc().buffer(4);
	}

	@Override public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		super.handlerRemoved(ctx);
		m_intBuf.release();
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Packet reader states.										*/
	/*----------------------------------------------------------------------*/

	/**
	 * Packet state: read the header and check it once read.
	 */
	private void readHeaderLong(ChannelHandlerContext context, ByteBuf source) {
		m_intBuf.writeBytes(source);
		if(m_intBuf.readableBytes() >= 4) {
			//-- Compare against header
			for(byte b : CommandNames.HEADER) {
				if(b != m_intBuf.readByte()) {
					throw new ProtocolViolationException("Packet header incorrect");
				}
			}

			//-- It worked. Next thing is the envelope length.
			m_readState = this::readEnvelopeLength;
		}
	}

	/**
	 * Read the length bytes for the envelope.
	 */
	private void readEnvelopeLength(ChannelHandlerContext context, ByteBuf source) {
		m_intBuf.writeBytes(source);
		if(m_intBuf.readableBytes() >= 4) {
			int length = m_intBuf.readInt();
			if(length < 0 || length >= CommandNames.MAX_ENVELOPE_LENGTH) {
				throw new ProtocolViolationException("Envelope length " + length + " is out of limits");
			}
			m_length = length;
			m_envelopeBuffer = new byte[length];
			m_envelopeOffset = 0;
			m_readState = this::readEnvelope;
		}
	}

	/**
	 * With the length from the previous step, collect the envelope data into a byte array
	 * and when finished convert it into the Envelope class.
	 */
	private void readEnvelope(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
		int available = byteBuf.readableBytes();
		if(available == 0)
			return;
		int todo = m_length - m_envelopeOffset;
		if(todo > available) {
			todo = available;
		}

		byteBuf.readBytes(m_envelopeBuffer, m_envelopeOffset, todo);
		m_envelopeOffset += todo;

		//-- All data read?
		if(m_envelopeOffset < m_length)
			return;

		//-- Create the Envelope
		try {
			m_envelope = Hubcore.Envelope.parseFrom(m_envelopeBuffer);
		} finally {
			m_envelopeBuffer = null;
		}

		m_readState = this::readPayloadLength;
	}

	private void readPayloadLength(ChannelHandlerContext channelHandlerContext, ByteBuf source) throws Exception {
		m_intBuf.writeBytes(source);
		if(m_intBuf.readableBytes() >= 4) {
			int length = m_intBuf.readInt();
			if(length < 0 || length >= CommandNames.MAX_ENVELOPE_LENGTH) {
				throw new ProtocolViolationException("Envelope length " + length + " is out of limits");
			}
			m_length = length;

			/*
			 * We now have all info to decide what to do with the data. The data payload will not be read
			 * until the envelope has been decoded and handled. If decode decides that the data needs to be
			 * copied it will schedule that here.
			 */
			if(length == 0) {
				//-- Nothing to do: we're just set for another packet.
				m_readState = this::readHeaderLong;
			} else {
				m_readState = this::bodyReadError;
			}

			handlePacket();
		}
	}

	/**
	 * This state means that a body was present but the packet handler did not decide to read it proper. It
	 * reports an error, then terminates the connection.
	 */
	private void bodyReadError(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) {
		throw new ProtocolViolationException("The packet handler did not cause the payload for the packet to be read");
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Command state handler.										*/
	/*----------------------------------------------------------------------*/
	/**
	 *
	 */
	private void handlePacket() throws Exception {
		m_packetState.handlePacket(m_envelope);
	}

	private void expectHeloResponse(Hubcore.Envelope envelope) throws Exception {
		if(envelope.hasHeloClient()) {
			handleClientHello(envelope, envelope.getHeloClient());
		} else if(envelope.hasHeloServer()) {
			handleServerHello(envelope, envelope.getHeloServer());
		} else
			throw new ProtocolViolationException("No client nor server part in HELO response");
	}

	/**
	 * The server helo response contains the challenge response for authorisation.
	 */
	private void handleServerHello(Envelope envelope, ServerHeloResponse heloServer) throws Exception {
		String sourceId = envelope.getSourceId();
		String[] split = sourceId.split("@");
		if(split.length != 2)
			throw new ProtocolViolationException("The server ID is invalid");
		String serverName = split[0];
		String clusterName = split[1];

		byte[] signature = heloServer.getChallengeResponse().toByteArray();
		if(! m_central.checkServerSignature(clusterName, serverName, Objects.requireNonNull(m_challenge), signature))
			throw new ProtocolViolationException("Authorization failure");

		//-- Authorized -> respond with AUTH packet.
		Server server = getDirectory().getServer(clusterName, serverName, Arrays.asList("*"));				// Server id includes cluster id always
		server.newConnection(m_channel, this);
		log("new connection for server " + server.getFullId() + " in state " + server.getState());

		//-- send back AUTH packet
		m_connection = server;
		m_packetState = server::packetReceived;

		ResponseBuilder rb = new ResponseBuilder(this)
			.fromEnvelope(envelope)
			;
		rb.getEnvelope().setCommand(CommandNames.AUTH_CMD);				// Authorized
		rb.send();
	}

	private void handleClientHello(Envelope envelope, ClientHeloResponse heloClient) {
		throw new ProtocolViolationException("Client helo not implemented yet");
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Other listeners for channel events.							*/
	/*----------------------------------------------------------------------*/

	/**
	 * Called when the channel has just been opened. This sends an HELO packet to the client.
	 */
	@Override public void channelActive(ChannelHandlerContext ctx) throws Exception {
		ctx.channel().closeFuture().addListener(future -> {
			remoteDisconnected(ctx);
		});

		//-- Send HELO with challenge
		byte[] challenge = m_challenge = m_central.getChallenge();
		ResponseBuilder response = new ResponseBuilder(this);
		response.getEnvelope()
			.setCommand(CommandNames.HELO_CMD)
			.setSourceId("")							// from HUB
			.setTargetId("unknown-client")				// We have no client ID yet
			.setDataFormat(CommandNames.BODY_BYTES)		// Zero body bytes, actually
			.setCommandId("*")
			.setVersion(1)
			.setChallenge(
				HelloChallenge.newBuilder()
					.setChallenge(ByteString.copyFrom(challenge))
					.setServerVersion(HubServer.VERSION)
			)
			;
		m_packetState = this::expectHeloResponse;
		response.send();
	}

	private void remoteDisconnected(ChannelHandlerContext ctx) {
		AbstractConnection connection = m_connection;
		if(null != connection) {
			log("Channel disconnected");
			connection.channelClosed();
		}
	}

	@Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		error("Connection exception: " + cause);
		ctx.close();
	}


	//private void disconnectedState(ChannelHandlerContext ctx, HubPacket packet) {
	//	throw new IllegalStateException("We should be disconnected");
	//}

	///**
	// * Called when the HELO answer arrives, this decodes the answer depending on the
	// * response.
	// */
	//private void heloAnswerState(ChannelHandlerContext context, HubPacket packet) throws Exception {
	//	if(packet.getType() != 0x01) {
	//		throw new ProtocolViolationException("HELO response bad packet type " + Integer.toString(packet.getType(), 16));
	//	}
	//	try {
	//		String name = packet.getCommand();
	//		if(name.equals(CommandNames.CLNT_CMD)) {
	//			log("Got CLNT response");
	//			heloHandleClient(context, packet);
	//		} else if(name.equals(CommandNames.SRVR_CMD)) {
	//			log("Got SRVR response");
	//			heloHandleServer(context, packet);
	//		} else {
	//			throw new ProtocolViolationException("HELO response invalid: expecting CLNT or SRVR, got '" + name + "'");
	//		}
	//	} catch(FatalHubException fhx) {
	//		sendHubErrorAndDisconnect(packet.getCommand(), fhx.getCode(), fhx.getParameters());
	//	} catch(HubException hx) {
	//		sendHubError(packet.getCommand(), null, hx.getCode(), hx.getParameters());
	//	}
	//}

	///**
	// * Packet format: SRVR response packet.
	// */
	//private void heloHandleServer(ChannelHandlerContext context, HubPacket packet) throws Exception {
	//	//-- Get server and cluster ID
	//	String s = packet.getSourceId();
	//	int ix = s.indexOf("@");
	//	if(ix == -1)
	//		throw new ProtocolViolationException("Source ID " + s + " does not contain @");
	//	String serverId = s.substring(0, ix);
	//	String clusterId = s.substring(ix + 1);
	//	if(clusterId.length() == 0 || serverId.length() == 0)
	//		throw new ProtocolViolationException("Source ID " + s + " has invalid names");
	//
	//	Hubcore.ServerHeloResponse r = Hubcore.ServerHeloResponse.parseFrom(packet.getRemainingStream());
	//	byte[] signature = r.getChallengeResponse().toByteArray();
	//
	//	if( ! m_central.checkServerSignature(signature, Objects.requireNonNull(m_challenge))) {
	//		sendHubErrorAndDisconnect(packet.getCommand(), ErrorCode.invalidSignature);
	//		return;
	//	}
	//
	//	Server server = getDirectory().getServer(clusterId, s, Arrays.asList("*"));				// Server id includes cluster id always
	//	server.newConnection(context.channel(), this);
	//	log("new connection for server " + server.getFullId() + " in state " + server.getState());
	//
	//	//-- send back AUTH packet
	//	m_connection = server;
	//	m_state = server::newPacket;
	//
	//	Hubcore.AuthResponse auth = Hubcore.AuthResponse.newBuilder()
	//			.build();
	//	sendHubMessage(0x01, CommandNames.AUTH_CMD, auth, null);
	//	log("AUTH sent");
	//}

	//private void heloHandleClient(ChannelHandlerContext context, HubPacket packet) throws Exception {
	//	String clientId = packet.getSourceId();
	//	String s = packet.getTargetId();
	//	int ix = s.indexOf("@");
	//	if(ix == -1)
	//		throw new ProtocolViolationException("Target ID " + s + " does not contain @");
	//	String organisationId = s.substring(0, ix);
	//	String clusterId = s.substring(ix + 1);
	//
	//	Hubcore.ClientHeloResponse r = Hubcore.ClientHeloResponse.parseFrom(packet.getRemainingStream());
	//	if(1 != r.getVersion())
	//		throw new ProtocolViolationException("Invalid packet version " + r.getVersion());
	//	byte[] signature = r.getChallengeResponse().toByteArray();
	//
	//	//-- We need to send a request for a "pending" client. Find a server that handles the destination
	//	Server server = getDirectory().getOrganisationServer(clusterId, organisationId);
	//	if(server.getState() != ConnectionState.CONNECTED)
	//		throw new UnreachableOrganisationException(server.getFullId() + " in state " + server.getState());
	//
	//	/*
	//	 * We have at least a tentative connection, but before we can really register the client we need to
	//	 * authenticate; only when that works can we become the "real" client. This is necessary to prevent
	//	 * denial of service attacks: if we would replace the client connection already any attacker could
	//	 * cause connections to drop like flies.
	//	 */
	//	Pair<String, Client> pair = getDirectory().createTempClient(packet.getSourceId());
	//	Client client = pair.get2();
	//	client.newConnection(context.channel(), this);			// Associate with new client
	//	log("Unauthenticated client " + packet.getSourceId() + " mapped to temp id=" + client.getFullId());
	//
	//	//-- Send a client authentication request to the server we've just found.
	//	server.sendMessage(packet.getType(), pair.get1(), packet.getCommand(), r, null);
	//}

	void sendResponse(ResponseBuilder r) {
		log("Sending response packet: " + r.getEnvelope().getCommand());
		//-- Convert the data into a response packet.
		ByteBuf buf = new PacketBuilder(m_channel.alloc())
			.appendMessage(r.getEnvelope().build())
			.emptyBody()
			.getCompleted()
			;
		ChannelFuture future = m_channel.writeAndFlush(buf);
		future.addListener((ChannelFutureListener) f -> {
			if(! f.isSuccess())
				m_channel.disconnect();
		});
	}

	public void sendPing() {
		ResponseBuilder response = new ResponseBuilder(this);
		response.getEnvelope()
			.setCommand(CommandNames.PING_CMD)
			.setSourceId("")							// from HUB
			.setTargetId(m_clientId)					// Whatever is known
			.setDataFormat(CommandNames.BODY_BYTES)		// Zero body bytes, actually
			.setCommandId("*")
			.setVersion(1)
		;
		response.send();
		log("sent ping");
	}

	///**
	// * Send a message that originates from the hub itself. These have an empty "sourceID" as it is the hub.
	// */
	//public void sendHubMessage(int packetCode, String command, @Nullable Message message, @Nullable RunnableEx after) {
	//	PacketBuilder pb = new PacketBuilder(m_channel.alloc(), (byte)packetCode, m_clientId, "", command);
	//	if(null != message)
	//		pb.appendMessage(message);
	//	ChannelFuture future = m_channel.writeAndFlush(pb.getCompleted());
	//	future.addListener((ChannelFutureListener) f -> {
	//		if(! f.isSuccess())
	//			m_channel.disconnect();
	//		else if(after != null){
	//			after.run();
	//		}
	//	});
	//}
	//
	///**
	// * Send an error message originating from the HUB (with an empty sourceID).
	// */
	//public void sendHubError(String failedCommand, @Nullable RunnableEx after, ErrorCode errorCode, Object... parameters) {
	//	Hubcore.ErrorResponse err = Hubcore.ErrorResponse.newBuilder()
	//			.setCode(errorCode.name())
	//			.setText(MessageFormat.format(errorCode.getText(), parameters))
	//			.build();
	//	sendHubMessage(0x02, failedCommand, err, after);
	//}
	//
	//public void sendHubErrorAndDisconnect(String failedCommand, ErrorCode errorCode, Object... parameters) {
	//	Hubcore.ErrorResponse err = Hubcore.ErrorResponse.newBuilder()
	//			.setCode(errorCode.name())
	//			.setText(MessageFormat.format(errorCode.getText(), parameters))
	//			.build();
	//	sendHubMessage(0x03, failedCommand, err, () -> {
	//		m_state = this::disconnectedState;
	//		m_channel.disconnect();
	//	});
	//}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Messages from a specific source.							*/
	/*----------------------------------------------------------------------*/
	///**
	// * Send a message that originates from the hub itself. These have an empty "sourceID" as it is the hub.
	// */
	//public void sendMessage(int packetCode, String sourceID, String command, Message message, @Nullable RunnableEx after) {
	//	PacketBuilder pb = new PacketBuilder(m_channel.alloc(), (byte)packetCode, m_clientId, sourceID, command);
	//	pb.appendMessage(message);
	//	ChannelFuture future = m_channel.writeAndFlush(pb.getCompleted());
	//	future.addListener((ChannelFutureListener) f -> {
	//		if(! f.isSuccess())
	//			m_channel.disconnect();
	//		else if(after != null){
	//			after.run();
	//		}
	//	});
	//}
	//
	///**
	// * Send an error message originating from the HUB (with an empty sourceID).
	// */
	//public void sendError(String sourceID, String failedCommand, @Nullable RunnableEx after, ErrorCode errorCode, Object... parameters) {
	//	Hubcore.ErrorResponse err = Hubcore.ErrorResponse.newBuilder()
	//			.setCode(errorCode.name())
	//			.setText(MessageFormat.format(errorCode.getText(), parameters))
	//			.build();
	//	sendMessage(0x02, sourceID, failedCommand, err, after);
	//}
	//
	//public void sendErrorAndDisconnect(String sourceID, String failedCommand, ErrorCode errorCode, Object... parameters) {
	//	Hubcore.ErrorResponse err = Hubcore.ErrorResponse.newBuilder()
	//			.setCode(errorCode.name())
	//			.setText(MessageFormat.format(errorCode.getText(), parameters))
	//			.build();
	//	sendMessage(0x03, sourceID, failedCommand, err, () -> {
	//		m_state = this::disconnectedState;
	//		m_channel.disconnect();
	//	});
	//}

	void log(String log) {
		ConsoleUtil.consoleLog("hub", "csh " + log);
	}
	void error(String log) {
		ConsoleUtil.consoleError("hub", "csh " + log);
	}


	//public void forwardPacket(HubPacket packet) {
	//	ByteArrayUtil.setInt(m_lenBuf, 0, packet.getData().length);
	//	ChannelFuture future = m_channel.write(m_lenBuf);
	//	future.addListener((ChannelFutureListener) f -> {
	//		if(! f.isSuccess())
	//			m_channel.disconnect();
	//	});
	//
	//	future = m_channel.writeAndFlush(packet.getData());
	//	future.addListener((ChannelFutureListener) f -> {
	//		if(! f.isSuccess())
	//			m_channel.disconnect();
	//	});
	//}



	private ConnectionDirectory getDirectory() {
		return m_central.getDirectory();
	}

	interface IReadHandler {
		void handleRead(ChannelHandlerContext context, ByteBuf source) throws Exception;
	}

	interface IPacketHandler {
		void handlePacket(Hubcore.Envelope envelope) throws Exception;
	}
}
