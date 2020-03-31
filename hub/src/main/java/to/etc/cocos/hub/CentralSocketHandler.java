package to.etc.cocos.hub;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.parties.BeforeClientData;
import to.etc.cocos.hub.parties.Client;
import to.etc.cocos.hub.parties.Cluster;
import to.etc.cocos.hub.parties.ConnectionDirectory;
import to.etc.cocos.hub.parties.Server;
import to.etc.cocos.hub.problems.ProtocolViolationException;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.AuthResponse;
import to.etc.cocos.messages.Hubcore.ClientHeloResponse;
import to.etc.cocos.messages.Hubcore.ClientInventory;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.HelloChallenge;
import to.etc.cocos.messages.Hubcore.HubErrorResponse;
import to.etc.cocos.messages.Hubcore.ServerHeloResponse;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.FatalHubException;
import to.etc.hubserver.protocol.HubException;
import to.etc.util.ConsoleUtil;
import to.etc.util.StringTool;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class CentralSocketHandler extends SimpleChannelInboundHandler<ByteBuf> {
	@NonNull
	private final Hub m_central;

	private PacketAssemblyMachine m_packetAssembler;

	@NonNull
	private final SocketChannel m_channel;

	private final String m_remoteAddress;

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


	private IPacketHandler m_packetState = this::psExpectHeloResponse;

	@Nullable
	private Object m_packetStateData;

	/*
	 * Packet transmitter buffers.
	 */
	/** Immediate-level  priority packets to send. */
	private List<TxPacket> m_txPacketQueue = new LinkedList<>();

	/** Priority packets to send */
	private List<TxPacket> m_txPacketQueuePrio = new LinkedList<>();

	/** Buffers to send for the current packet */
	private List<ByteBuf> m_txBufferList = new LinkedList<>();

	@Nullable
	private TxPacket m_txCurrentPacket;


	public CentralSocketHandler(Hub central, SocketChannel socketChannel) {
		m_central = central;
		m_channel = socketChannel;
		m_remoteAddress = socketChannel.remoteAddress().getAddress().getHostAddress();
		m_packetAssembler = new PacketAssemblyMachine(this::handlePacket, socketChannel.alloc());
	}

	@Override protected void channelRead0(ChannelHandlerContext context, ByteBuf data) throws Exception {
		//-- Keep reading data from the buffer until empty and handle it.
		try {
			while(data.readableBytes() > 0) {
				m_packetAssembler.handleRead(context, data);
			}
		//} catch(HubException x) {			// cannot do that here: we might not have a full packet.
		//	immediateSendHubException(x);
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
		PacketResponseBuilder response = new PacketResponseBuilder(this);
		response.getEnvelope()
			.setSourceId("")							// from HUB
			.setTargetId("unknown-client")				// We have no client ID yet
			.setVersion(1)
			.setChallenge(
				HelloChallenge.newBuilder()
					.setChallenge(ByteString.copyFrom(challenge))
					.setServerVersion(Hub.VERSION)
			)
		;
		setPacketState(this::psExpectHeloResponse);
		response.send();
	}

	//@Override public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
	//	super.handlerAdded(ctx);
	//	m_intBuf = ctx.alloc().buffer(4);
	//}
	//
	//@Override public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
	//	super.handlerRemoved(ctx);
	//	m_intBuf.release();
	//}

	public String getTmpClientId() {
		return m_tmpClientId;
	}

	public synchronized AbstractConnection getConnection() {
		AbstractConnection connection = m_connection;
		if(null == connection)
			throw new IllegalStateException("The party is no longer connected to this connection.");
		return connection;
	}

	Client getClientConnection() {
		return (Client) getConnection();
	}

	Server getServerConnection() {
		return (Server) getConnection();
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Packet reader states.										*/
	/*----------------------------------------------------------------------*/
	/*----------------------------------------------------------------------*/
	/*	CODING:	Command state handler.										*/
	/*----------------------------------------------------------------------*/
	/**
	 * Starting state: the remote must send a HELO command to start off the protocol.
	 */
	private void psExpectHeloResponse(Hubcore.Envelope envelope, ByteBuf payload, int length) throws Exception {
		if(envelope.hasHeloClient()) {
			handleClientHello(envelope, envelope.getHeloClient(), payload, length);
		} else if(envelope.hasHeloServer()) {
			handleServerHello(envelope, envelope.getHeloServer(), payload, length);
		} else
			throw new ProtocolViolationException("No client nor server part in CHALLENGE response, got " + envelope.getPayloadCase());
	}

	/**
	 * The server helo response contains the challenge response for authorisation.
	 */
	private void handleServerHello(Envelope envelope, ServerHeloResponse heloServer, ByteBuf payload, int length) throws Exception {
		//-- We must have an empty body
		if(length != 0)
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

		//-- From now on this channel services the specified server
		m_connection = server;
		setPacketState(server::packetReceived);

		//-- send back AUTH packet
		PacketResponseBuilder rb = new PacketResponseBuilder(this)
			.fromEnvelope(envelope)
			;
		rb.getEnvelope().getAuthBuilder()
			.build();
		rb.after(server::startInventorySend).send();
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Client handler.												*/
	/*----------------------------------------------------------------------*/
	/**
	 * Handles the client HELO response. It stores the inventory packet, and
	 * then forwards the HELO request as an CLAUTH command to the remote server.
	 */
	private void handleClientHello(Envelope envelope, ClientHeloResponse heloClient, ByteBuf payload, int length) {
		//-- We must have an empty body
		if(length != 0)
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
				throw new FatalHubException(ErrorCode.targetNotFound, targetId);

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
		setHelloInformation(envelope.getSourceId(), cluster, orgId);
		getDirectory().registerTmpClient(m_tmpClientId, this);

		PacketResponseBuilder b = new PacketResponseBuilder(server.getHandler());
		b.getEnvelope()
			.setSourceId(m_tmpClientId)						// From tmp client ID
			.setTargetId(server.getFullId())				// To the selected server
			.setVersion(1)
			.setClientAuth(Hubcore.ClientAuthRequest.newBuilder()
				.setChallenge(ByteString.copyFrom(m_challenge))
				.setChallengeResponse(envelope.getHeloClient().getChallengeResponse())
				.setClientId(m_myId)
				.setClientVersion(envelope.getHeloClient().getClientVersion())
			);
		setPacketState(this::waitForServerAuth, new BeforeClientData(cluster, orgId, envelope.getSourceId()));
		b.send();
	}

	private void waitForServerAuth(Envelope envelope, @Nullable ByteBuf payload, int length) {
		throw new ProtocolViolationException("Not expecting a packet while waiting for client authentication by the server");
	}

	/**
	 * Called only when we're a temp client, this checks whether the server
	 * accepted our auth request.
	 */
	public void tmpGotResponseFrom(Server server, Envelope envelope, ByteBuf payload, int length) {
		//-- Expecting an AUTH or ERROR response.
		if(envelope.hasHubError()) {
			HubErrorResponse error = envelope.getHubError();
			PacketResponseBuilder b = new PacketResponseBuilder(this)
				.forwardTo(envelope);
			b.getEnvelope().setHubError(error);
			b.send();

			//-- REMOVE CLIENT
			getDirectory().unregisterTmpClient(this);
		} else if(envelope.hasAuth()) {
			//-- We're authenticated! Yay!
			log("CLIENT authenticated!!");
			registerClient(getPacketStateData(BeforeClientData.class));		// ORDERED
			setPacketState(this::psExpectClientInventory);					// ORDERED

			AuthResponse auth = envelope.getAuth();
			PacketResponseBuilder b = new PacketResponseBuilder(this)
				.forwardTo(envelope);
			b.getEnvelope().setAuth(auth);
			b.send();
		} else {
			throw new ProtocolViolationException("Expected server:auth, got " + envelope.getPayloadCase());
		}
	}

	private void psExpectClientInventory(Envelope envelope, @Nullable ByteBuf payload, int length) throws IOException {
		if(! envelope.hasInventory()) {
			throw new ProtocolViolationException("Expecting inventory, got " + envelope.getPayloadCase());
		}
		log("Client inventory received");
		if(length == 0)
			throw new ProtocolViolationException("The inventory packet data is missing");
		ClientInventory inventory = envelope.getInventory();
		String dataFormat = inventory.getDataFormat();
		if(null == dataFormat || dataFormat.trim().length() == 0)
			throw new ProtocolViolationException("The inventory packet data format is missing");

		getClientConnection().updateInventory(dataFormat, Objects.requireNonNull(payload), length);

		//-- Now enter passthrough mode.
		setPacketState(getClientConnection()::packetReceived);
	}

	private synchronized void registerClient(BeforeClientData data) {
		m_connection = getCluster().registerAuthorizedClient(this);
	}

	/**
	 * State handler that handles the packet level dialogue.
	 */
	private void handlePacket(Envelope envelope, @Nullable ByteBuf payload, int length) throws Exception {
		IPacketHandler packetState;
		synchronized(this) {
			packetState = m_packetState;
		}
		try {
			packetState.handlePacket(envelope, payload, length);
		} finally {
			if(null != payload) {
				payload.release();
			}
		}
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


	/*----------------------------------------------------------------------*/
	/*	CODING:	All kinds of disconnect handling							*/
	/*----------------------------------------------------------------------*/

	/**
	 * Just disconnects the channel and make sure it is unusable.
	 */
	public synchronized void disconnectOnly(String why) {
		log("internal disconnect requested: " + why);
		synchronized(this) {
			m_connection = null;
			m_cluster = null;
			m_txPacketQueue.clear();
			m_txPacketQueuePrio.clear();
			m_txCurrentPacket = null;
			for(ByteBuf byteBuf : m_txBufferList) {
				byteBuf.release();
			}
			m_txBufferList.clear();
		}
		m_channel.disconnect();
	}

	/**
	 * The channel disconnected, possibly because of a remote disconnect.
	 */
	private void remoteDisconnected(ChannelHandlerContext ctx) {
		log("remote disconnect received");
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

	void tryScheduleSend(AbstractConnection conn, TxPacket packet) {
		initiatePacketSending(packet);
	}

	/**
	 * Initiate sending of a packet, by converting the packet into a list
	 * of send buffers and starting the transmit.
	 */
	private void initiatePacketSending(@Nullable TxPacket packet) {
		//ConsoleUtil.consoleLog("XX", ">> initiatePacketSending entry packet " + packet);
		int txfailCount = 0;
		for(;;) {
			if(null == packet) {
				return;
			}
			synchronized(this) {
				if(m_txCurrentPacket != null)				// Transmitter busy?
					return;
				m_txCurrentPacket = packet;
			}

			try {
				List<ByteBuf> bufferList = new LinkedList<>();
				ByteBuf buffer = m_channel.alloc().buffer(1024);
				bufferList.add(buffer);
				buffer.writeBytes(CommandNames.HEADER);
				try(BufferWriter bw = new BufferWriter(bufferList, buffer)) {
					byte[] bytes = packet.getEnvelope().toByteArray();
					bw.getHeaderBuf().writeInt(bytes.length);
					bw.getHeaderBuf().writeBytes(bytes);
					packet.getBodySender().sendBody(bw);
				}

				//-- Start the transmitter.
				startSendingBuffers(bufferList);
				return;
			} catch(Exception x) {
				log("prepare transmit failed: " + x);
				if(txfailCount++ > 20) {
					disconnectOnly("TX failed after " + txfailCount + " retries");
					x.printStackTrace();
					throw new IllegalStateException("TOO MANY RETRIES INITIATING SEND");
				}

				try {
					AbstractConnection onBehalfOf = packet.getOnBehalfOf();
					if(null != onBehalfOf) {
						onBehalfOf.onPacketForward(Objects.requireNonNull(m_connection), packet.getEnvelope());
					}
				} catch(Exception xx) {
					xx.printStackTrace();
				}

				/*
				 * As it is the PREPARE that failed we just notify the source and try the next packet
				 */
				AbstractConnection conn;
				synchronized(this) {
					releaseTxBuffers();
					m_txCurrentPacket = null;
					conn = m_connection;
					if(null == conn)
						return;
				}
				packet = getNextPacketToTransmit();
			}
		}
	}

	/**
	 * First check local packets; if nothing there try connection packets.
	 */
	@Nullable
	private synchronized TxPacket getNextPacketToTransmit() {
		AbstractConnection connection;
		synchronized(this) {
			if(m_txPacketQueuePrio.size() > 0) {
				TxPacket txPacket = m_txPacketQueuePrio.get(0);
				return txPacket;
			} else if(m_txPacketQueue.size() > 0) {
				TxPacket txPacket = m_txPacketQueue.get(0);
				return txPacket;
			} else {
				connection = m_connection;
			}
		}
		if(null != connection)
			return connection.getNextPacketToTransmit();
		return null;
	}

	/**
	 * Called when the sender is idle and there is data to send. This
	 * starts sending the first buffer.
	 */
	private void startSendingBuffers(List<ByteBuf> bufferList) {
		ByteBuf buf;
		synchronized(this) {
			m_txBufferList = bufferList;
			if(m_txBufferList.size()  == 0) {
				throw new IllegalStateException("Starting the transmitter without buffers to send");
			}
			buf = m_txBufferList.remove(0);
		}
		txBuffer(buf);
	}

	private void txBuffer(ByteBuf buf) {
		//System.out.println("> txbuffer " + buf);
		ChannelFuture future = m_channel.writeAndFlush(buf);
		future.addListener((ChannelFutureListener) f -> {
			if(f.isSuccess()) {
				txHandleSuccessfulSend();
			} else {
				Throwable cause = f.cause();
				if(null != cause)
					cause.printStackTrace();
				txHandleFailedSend();
			}
		});
	}

	/**
	 * Called when a send buffer has been fully transmitted, this sends the next buffer or
	 * starts sending the next packet.
	 */
	private void txHandleSuccessfulSend() {
		ByteBuf byteBuf;
		TxPacket packetToFinish;
		TxPacket packet = null;
		synchronized(this) {
			//System.out.println(">> txHandleSuccessful: " + m_txBufferList.size() + " buffers " + m_txCurrentPacket);
			if(m_txBufferList.size() > 0) {
				byteBuf = m_txBufferList.remove(0);
				packetToFinish = null;
			} else {
				byteBuf = null;

				//-- We need to send a new packetdisconnect
				packetToFinish = m_txCurrentPacket;
				if(null == packetToFinish)
					throw new IllegalStateException("Null current txpacket at end of send");
				m_txCurrentPacket = null;
			}
			//System.out.println(">>> packetToFinish "+ packetToFinish + ", bytebuf=" + byteBuf + " currentpacket=" + m_txCurrentPacket);
		}

		if(packetToFinish != null) {
			synchronized(this) {
				Runnable ftor = packetToFinish.getPacketRemoveFromQueue();
				if(null == ftor)
					throw new IllegalStateException("Packet in transmitter does not have a queue assigned to it");
				ftor.run();
			}
			packetToFinish.getSendFuture().complete(packetToFinish);
		}

		if(byteBuf != null) {
			txBuffer(byteBuf);
			return;
		}
		initiatePacketSending(getNextPacketToTransmit());
	}

	/**
	 * Send failed. Requeue the failed packet on the prio queue, then disconnect. When the remote reconnects
	 * the packet is retried (unless we have a tx timeout).
	 */
	private void txHandleFailedSend() {
		TxPacket packet;
		synchronized(this) {
			packet = m_txCurrentPacket;
			if(null == packet) {
				throw new IllegalStateException("current packet is null while transmitting");
			}

			//-- We tell the Party nothing, this will cause it to resent the packet once a new connection has been made.
			m_txCurrentPacket = null;
			releaseTxBuffers();
		}
		disconnectOnly("failed send " + packet);
	}

	private synchronized void releaseTxBuffers() {
		for(ByteBuf byteBuf : m_txBufferList) {
			byteBuf.release();
		}
		m_txBufferList.clear();
	}

	/**
	 * Schedule a packet to be sent with normal priority.
	 */
	public void immediateSendPacket(TxPacket packet) {
		synchronized(this) {
			m_txPacketQueue.add(packet);					// Will be picked up when current packet tx finishes.
			packet.setPacketRemoveFromQueue(() -> {
				synchronized(this) {
					m_txPacketQueue.remove(packet);
				}
			}, TxPacketType.HUB);
		}
		initiatePacketSending(packet);
	}

	private void immediateSendHubException(Envelope source, HubException x) {
		log("sending hub exception " + x);

		PacketResponseBuilder rb = new PacketResponseBuilder(this)
			.fromEnvelope(source)
			;
		rb.getEnvelope()
			.setHubError(HubErrorResponse.newBuilder()
				.setCode(x.getCode().name())
				.setText(x.getMessage())
				.setDetails(StringTool.strStacktrace(x))
				.build()
			);

		boolean isFatal = x instanceof FatalHubException;
		if(isFatal) {
			rb.after(() -> {
				log("send failed, disconnecting");
				m_channel.disconnect();
			});
		}
		rb.send();
	}

	public void sendPing() {
		PacketResponseBuilder response = new PacketResponseBuilder(this);
		response.getEnvelope()
			.setSourceId("")							// from HUB
			.setTargetId(getMyID())						// Whatever is known
			.setVersion(1)
			.setPing(Hubcore.Ping.newBuilder().build())
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

	private synchronized void setHelloInformation(String clientId, Cluster cluster, String resourceId) {
		if(m_myId != null || m_cluster != null || m_resourceId != null) {
			throw new IllegalStateException("Client, cluster or resource ID already defined!!");
		}
		m_myId = clientId;
		m_cluster = cluster;
		m_resourceId = resourceId;
	}

	/**
	 * This channel has become inactive and reached the end of its life. Release all resources.
	 */
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		m_packetAssembler.destroy();
	}

	public ByteBufAllocator alloc() {
		return m_channel.alloc();
	}


	private ConnectionDirectory getDirectory() {
		return m_central.getDirectory();
	}

	public String getRemoteAddress() {
		return m_remoteAddress;
	}

	interface IReadHandler {
		void handleRead(ChannelHandlerContext context, ByteBuf source) throws Exception;
	}

	interface IPacketHandler {
		void handlePacket(Hubcore.Envelope envelope, @Nullable ByteBuf payload, int length) throws Exception;
	}
}
