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
import to.etc.cocos.hub.parties.BeforeClientData;
import to.etc.cocos.hub.parties.Cluster;
import to.etc.cocos.hub.parties.ConnectionDirectory;
import to.etc.cocos.hub.parties.Server;
import to.etc.cocos.hub.problems.ProtocolViolationException;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.FatalHubException;
import to.etc.hubserver.protocol.HubException;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ClientHeloResponse;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ErrorResponse;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.HelloChallenge;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ServerHeloResponse;
import to.etc.util.ByteBufferOutputStream;
import to.etc.util.ConsoleUtil;
import to.etc.util.StringTool;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class CentralSocketHandler extends SimpleChannelInboundHandler<ByteBuf> {
	@NonNull
	private final Hub m_central;

	@NonNull
	private final SocketChannel m_channel;

	@Nullable
	private AbstractConnection m_connection;

	@Nullable
	private String m_myId;

	@Nullable
	private Cluster m_cluster;

	@Nullable
	private String m_resourceId;

	@NonNull
	final private String m_tmpClientId = StringTool.generateGUID();

	@Nullable
	private byte[] m_challenge;

	private ByteBuf m_intBuf;

	@NonNull
	final private byte[] m_lenBuf = new byte[4];

	private int m_length;

	@Nullable
	private byte[] m_envelopeBuffer;

	private int m_envelopeOffset;

	private Envelope m_envelope;

	@NonNull
	private PayloadState m_payloadState = PayloadState.EMPTY;

	@Nullable
	private ByteBufferOutputStream m_payloadOutputStream;

	/**
	 * The state for reading a packet.
	 */
	private IReadHandler m_readState = this::readHeaderLong;

	private IPacketHandler m_packetState = this::expectHeloResponse;

	@Nullable
	private Object m_packetStateData;

	public CentralSocketHandler(Hub central, SocketChannel socketChannel) {
		m_central = central;
		m_channel = socketChannel;
	}

	@Override protected void channelRead0(ChannelHandlerContext context, ByteBuf data) throws Exception {
		//-- Keep reading data from the buffer until empty and handle it.

		try {
			while(data.readableBytes() > 0) {
				m_readState.handleRead(context, data);
			}
		} catch(HubException x) {
			sendHubException(x);
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
		ResponseBuilder response = new ResponseBuilder(this);
		response.getEnvelope()
			.setCommand(CommandNames.HELO_CMD)
			.setSourceId("")							// from HUB
			.setTargetId("unknown-client")				// We have no client ID yet
			.setDataFormat("")							// Zero body bytes, actually
			.setCommandId("*")
			.setVersion(1)
			.setChallenge(
				HelloChallenge.newBuilder()
					.setChallenge(ByteString.copyFrom(challenge))
					.setServerVersion(Hub.VERSION)
			)
		;
		setPacketState(this::expectHeloResponse);
		response.send();
	}

	@Override public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		super.handlerAdded(ctx);
		m_intBuf = ctx.alloc().buffer(4);
	}

	@Override public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		super.handlerRemoved(ctx);
		m_intBuf.release();
	}

	public String getTmpClientId() {
		return m_tmpClientId;
	}

	public synchronized AbstractConnection getConnection() {
		return m_connection;
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
			m_payloadOutputStream = null;

			/*
			 * We now have all info to decide what to do with the data. The data payload will not be read
			 * until the envelope has been decoded and handled. If decode decides that the data needs to be
			 * copied it will schedule that here.
			 */
			if(length == 0) {
				//-- Nothing to do: we're just set for another packet.
				m_readState = this::readHeaderLong;
				m_payloadState = PayloadState.EMPTY;
			} else {
				m_readState = this::bodyReadError;
				m_payloadState = PayloadState.UNREAD;
				m_payloadOutputStream = new ByteBufferOutputStream();
			}

			handlePacket();
		}
	}

	/**
	 * Usable after readPayloadLength, this actually reads the payload into a
	 * payload buffer set.
	 */
	private void readPayloadBodyBytes(ChannelHandlerContext channelHandlerContext, ByteBuf source) throws Exception {
		switch(m_payloadState) {
			default:
				throw new IllegalStateException(m_payloadState + "??");

			case EMPTY:
				m_payloadOutputStream = new ByteBufferOutputStream();
				m_payloadState = PayloadState.READ;
				return;

			case READ:
				throw new IllegalStateException("Attempt to read payload when it has already been read");

			case UNREAD:
				int available = source.readableBytes();
				if(available == 0)
					return;
				int todo = m_length;
				if(todo > available) {
					todo = available;
				}
				ByteBufferOutputStream pos = Objects.requireNonNull(m_payloadOutputStream);
				source.readBytes(pos, todo);
				m_length -= todo;
				if(m_length < 0)
					throw new IllegalStateException();
				else if(m_length == 0) {
					m_payloadState = PayloadState.READ;
					handlePacket();
				}
				break;
		}
	}



	/**
	 * This state means that a body was present but the packet handler did not decide to read it proper. It
	 * reports an error, then terminates the connection.
	 */
	private void bodyReadError(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) {
		throw new ProtocolViolationException("The packet handler did not cause the payload for the packet to be read");
	}

	private int getPayloadLength() {
		switch(m_payloadState) {
			default:
				throw new IllegalStateException(m_payloadState + ": ??");
			case EMPTY:
				return 0;
			case UNREAD:
				throw new IllegalStateException("The payload has not yet been read");
			case READ:
				return Objects.requireNonNull(m_payloadOutputStream).getSize();
		}
	}

	private ByteBufferOutputStream getPayload() {
		switch(m_payloadState) {
			default:
				throw new IllegalStateException(m_payloadState + ": ??");
			case UNREAD:
				throw new IllegalStateException("The payload has not yet been read");
			case READ:
			case EMPTY:
				return Objects.requireNonNull(m_payloadOutputStream);
		}
	}

	private boolean isPayloadLoaded() {
		switch(m_payloadState) {
			default:
				throw new IllegalStateException(m_payloadState + ": ??");
			case EMPTY:
				m_payloadOutputStream = new ByteBufferOutputStream();
				return true;
			case UNREAD:
				m_readState = this::readPayloadBodyBytes;
				return false;
			case READ:
				return true;
		}
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Command state handler.										*/
	/*----------------------------------------------------------------------*/
	/**
	 *
	 */
	private void handlePacket() throws Exception {
		IPacketHandler packetState;
		synchronized(this) {
			packetState = m_packetState;
		}
		packetState.handlePacket(m_envelope);
	}

	private synchronized void setPacketState(IPacketHandler handler) {
		m_packetState = handler;
		m_packetStateData = null;
	}

	private synchronized void setPacketState(IPacketHandler handler, Object data) {
		m_packetState = handler;
		m_packetStateData = data;
	}

	private synchronized <T> T getPacketStateData(Class<T> clz) {
		Object data = m_packetStateData;
		if(null == data) {
			throw new IllegalStateException("Invalid packet state data: expected " + clz.getClass().getName() + " but got null");
		}
		if(clz.isInstance(data))
			return (T) data;
		throw new IllegalStateException("Invalid packet state data: expected " + clz.getClass().getName() + " but got " + data.getClass().getName());
	}

	private void expectHeloResponse(Hubcore.Envelope envelope) throws Exception {
		if(envelope.hasHeloClient()) {
			//-- The client expects a JSON inventory in the body, so first start reading that
			if(isPayloadLoaded()) {
				handleClientHello(envelope, envelope.getHeloClient());
			}
		} else if(envelope.hasHeloServer()) {
			handleServerHello(envelope, envelope.getHeloServer());
		} else
			throw new ProtocolViolationException("No client nor server part in HELO response");
	}

	/**
	 * The server helo response contains the challenge response for authorisation.
	 */
	private void handleServerHello(Envelope envelope, ServerHeloResponse heloServer) throws Exception {
		//-- We must have an empty body
		if(getPayloadLength() != 0)
			throw new ProtocolViolationException("Non-empty payload on server hello");

		String sourceId = envelope.getSourceId();
		String[] split = sourceId.split("@");
		if(split.length != 2)
			throw new ProtocolViolationException("The server ID is invalid");
		String serverName = split[0];
		String clusterName = split[1];

		byte[] signature = heloServer.getChallengeResponse().toByteArray();
		if(! m_central.checkServerSignature(clusterName, serverName, Objects.requireNonNull(m_challenge), signature))
			throw new FatalHubException(ErrorCode.authenticationFailure);

		//-- Authorized -> respond with AUTH packet.
		Cluster cluster = getDirectory().getCluster(clusterName);
		Server server = cluster.registerServer(serverName, Arrays.asList("*"));
		setHelloInformation(sourceId, cluster, null);
		server.newConnection(this);
		log("new connection for server " + server.getFullId() + " in state " + server.getState());

		//-- send back AUTH packet
		m_connection = server;
		setPacketState(server::packetReceived);

		ResponseBuilder rb = new ResponseBuilder(this)
			.fromEnvelope(envelope)
			;
		rb.getEnvelope().setCommand(CommandNames.AUTH_CMD);				// Authorized
		rb.send();
	}

	/**
	 * Handles the client HELO response. It stores the inventory packet, and
	 * then forwards the HELO request as an CLAUTH command to the remote server.
	 */
	private void handleClientHello(Envelope envelope, ClientHeloResponse heloClient) {
		//-- We must have an empty body
		if(getPayloadLength() != 0)
			throw new ProtocolViolationException("Non-empty payload on client hello");

		/*
		 * Format is either clusterid or resource#clusterid.
		 */
		String targetId = envelope.getTargetId();
		String[] split = targetId.split("#");
		Server server;
		Cluster cluster;
		String orgId;
		switch(split.length) {
			default:
				throw new IllegalStateException("Invalid client target: " + targetId);

			case 1:
				cluster = getDirectory().getCluster(split[0]);
				server = cluster.getRandomServer();
				if(null == server)
					throw new FatalHubException(ErrorCode.clusterNotFound, split[0]);
				orgId = null;
				break;
			case 2:
				cluster = getDirectory().getCluster(split[1]);
				orgId = split[0];
				server = cluster.findServiceServer(orgId);
				if(null == server)
					throw new FatalHubException(ErrorCode.targetNotFound, split[0]);
				break;
		}

		//-- Forward the packet to the remote server
		setHelloInformation(m_envelope.getSourceId(), cluster, orgId);
		getDirectory().registerTmpClient(m_tmpClientId, this);

		Envelope tgtEnvelope = Envelope.newBuilder()
			.setCommand(CommandNames.CLAUTH_CMD)
			.setSourceId(m_tmpClientId)						// From tmp client ID
			.setTargetId(server.getFullId())				// To the selected server
			.setDataFormat("")								// Zero body bytes, actually
			.setCommandId("")
			.setVersion(1)
			.setClientAuth(Hubcore.ClientAuthRequest.newBuilder()
				.setChallenge(ByteString.copyFrom(m_challenge))
				.setChallengeResponse(envelope.getHeloClient().getChallengeResponse())
				.setClientId(m_myId)
				.setClientVersion(envelope.getHeloClient().getClientVersion())
			)
			.build();
		setPacketState(this::expectClientServerAuth, new BeforeClientData(cluster, orgId, m_envelope.getSourceId()));
		server.getHandler().sendEnvelopeAndEmptyBody(tgtEnvelope);
	}

	private void expectClientServerAuth(Envelope envelope) {
		throw new ProtocolViolationException("Not expecting a packet while waiting for client authentication by the server");
	}

	/**
	 * Called only when we're a temp client, this checks whether the server
	 * accepted our auth request.
	 */
	public void tmpGotResponseFrom(Server server, Envelope envelope) {
		//-- Expecting an AUTH or ERROR response.
		if(envelope.hasError()) {
			ErrorResponse error = envelope.getError();
			sendEnvelopeAndEmptyBody(envelope, true);		// Return the error verbatim

			//-- REMOVE CLIENT
		} else if(envelope.getCommand().equals(CommandNames.AUTH_CMD)) {
			//-- We're authenticated! Yay!
			log("CLIENT authenticated!!");
			sendEnvelopeAndEmptyBody(envelope);							// Forward AUTH to client
			registerClient(getPacketStateData(BeforeClientData.class));
		} else {
			throw new ProtocolViolationException("Expected server:auth, got " + envelope.getCommand());
		}
	}

	private void registerClient(BeforeClientData data) {
		getCluster().registerAuthorizedClient(this);
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	All kinds of disconnect handling							*/
	/*----------------------------------------------------------------------*/

	/**
	 * Just disconnects the channel and make sure it is unusable.
	 */
	public synchronized void disconnectOnly() {
		log("internal disconnect requested");
		synchronized(this) {
			m_connection = null;
			m_cluster = null;
		}
		m_channel.disconnect();
	}

	/**
	 * The channel disconnected, possibly because of a remote disconnect.
	 */
	private void remoteDisconnected(ChannelHandlerContext ctx) {
		AbstractConnection connection;
		Cluster cluster;
		synchronized(this) {
			connection = m_connection;
			cluster = m_cluster;
			m_connection = null;
			m_cluster = null;

			if(null != connection) {
				log("Channel disconnected");
				connection.channelClosed();					// Deregister from hub and post event
			}
		}

		//-- Clean up
		getDirectory().unregisterTmpClient(this);				// Be sure not to be registered anymore
		if(null != cluster)
			cluster.runEvents();
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Other listeners for channel events.							*/
	/*----------------------------------------------------------------------*/
	@Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		error("Connection exception: " + cause);
		ctx.close();
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Sending data to this channel's remote.						*/
	/*----------------------------------------------------------------------*/

	public ResponseBuilder packetBuilder(String command) {
		ResponseBuilder responseBuilder = new ResponseBuilder(this);
		responseBuilder.getEnvelope()
			.setDataFormat("")
			.setVersion(1)
			.setCommand(command)
			.setTargetId(getMyID())
			.setSourceId("")
			;

		return responseBuilder;
	}

	/**
	 *
	 */
	void sendResponse(ResponseBuilder r) {
		log("Sending response packet: " + r.getEnvelope().getCommand());
		sendEnvelopeAndEmptyBody(r.getEnvelope().build());
	}

	private void sendEnvelopeAndEmptyBody(Hubcore.Envelope envelope) {
		sendEnvelopeAndEmptyBody(envelope, false);
	}

	private void sendEnvelopeAndEmptyBody(Envelope envelope, boolean andDisconnect) {
		ByteBuf buf = new PacketBuilder(m_channel.alloc())
			.appendMessage(envelope)
			.emptyBody()
			.getCompleted()
			;
		ChannelFuture future = m_channel.writeAndFlush(buf);
		future.addListener((ChannelFutureListener) f -> {
			if(! f.isSuccess() || andDisconnect)
				m_channel.disconnect();
		});

	}

	private void sendHubException(HubException x) {
		log("sending hub exception " + x);

		ResponseBuilder rb = new ResponseBuilder(this)
			.fromEnvelope(m_envelope)
			;
		rb.getEnvelope()
			.setDataFormat("")
			.setError(ErrorResponse.newBuilder()
				.setCode(x.getCode().name())
				.setText(x.getMessage())
				.setDetails(StringTool.strStacktrace(x))
				.build()
			);

		//-- Convert the data into a response packet.
		ByteBuf buf = new PacketBuilder(m_channel.alloc())
			.appendMessage(rb.getEnvelope().build())
			.emptyBody()
			.getCompleted()
			;
		boolean isFatal = x instanceof FatalHubException;
		ChannelFuture future = m_channel.writeAndFlush(buf);
		future.addListener((ChannelFutureListener) f -> {
			if(! f.isSuccess() || isFatal)
				m_channel.disconnect();
		});
	}

	public void sendPing() {
		ResponseBuilder response = new ResponseBuilder(this);
		response.getEnvelope()
			.setCommand(CommandNames.PING_CMD)
			.setSourceId("")							// from HUB
			.setTargetId(getMyID())						// Whatever is known
			.setDataFormat("")							// Zero body bytes, actually
			.setCommandId("*")
			.setVersion(1)
		;
		response.send();
		log("sent ping");
	}

	private String getLogInd() {
		String myId = m_myId;
		AbstractConnection connection = m_connection;
		if(myId == null)
			return "newClient";
		if(m_connection instanceof Server) {
			return "S:" + myId;
		} else {
			return "C:" + m_myId;
		}
	}

	void log(String log) {
		ConsoleUtil.consoleLog("Hub", getLogInd(), log);
	}

	void error(String log) {
		ConsoleUtil.consoleError("Hub", getLogInd(), log);
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Info on this connection.									*/
	/*----------------------------------------------------------------------*/

	/**
	 * The source ID for this connection, as identified by the initial HELO packet.
	 */
	public String getMyID() {
		String myId = m_myId;
		if(null == myId)
			throw new IllegalStateException("The connection's ID is not yet known - HELO packet response has not yet been received?");
		return myId;
	}

	public Cluster getCluster() {
		Cluster cluster = m_cluster;
		if(null == cluster)
			throw new IllegalStateException("The connection's ID is not yet known - HELO packet response has not yet been received?");
		return m_cluster;
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

	private synchronized void setHelloInformation(String clientId, Cluster cluster, String resourceId) {
		if(m_myId != null || m_cluster != null || m_resourceId != null) {
			throw new IllegalStateException("Client, cluster or resource ID already defined!!");
		}
		m_myId = clientId;
		m_cluster = cluster;
		m_resourceId = resourceId;
	}


	private ConnectionDirectory getDirectory() {
		return m_central.getDirectory();
	}

	interface IReadHandler {
		void handleRead(ChannelHandlerContext context, ByteBuf source) throws Exception;
	}

	interface IPacketHandler {
		void handlePacket(Hubcore.Envelope envelope) throws Exception;
	}

	private enum PayloadState {
		EMPTY,
		UNREAD,
		READ;
	}
}
