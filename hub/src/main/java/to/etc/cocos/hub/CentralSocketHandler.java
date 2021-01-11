package to.etc.cocos.hub;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.parties.BeforeClientData;
import to.etc.cocos.hub.parties.Client;
import to.etc.cocos.hub.parties.Cluster;
import to.etc.cocos.hub.parties.ConnectionDirectory;
import to.etc.cocos.hub.parties.Server;
import to.etc.cocos.hub.problems.ProtocolViolationException;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.HubErrorResponse;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.FatalHubException;
import to.etc.hubserver.protocol.HubException;
import to.etc.util.ConsoleUtil;
import to.etc.util.StringTool;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
@NonNullByDefault
final public class CentralSocketHandler extends SimpleChannelInboundHandler<ByteBuf> {
	@NonNull
	private final Hub m_hub;

	final private PacketAssemblyMachine m_packetAssembler;

	final private PacketMachine m_packetStateMachine;

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

	/**
	 * The state of the handler, used to prevent spurious errors when
	 * a disconnect is received while transmits are in flight.
	 */
	private volatile SocketState m_state = SocketState.CONNECTED;

	public CentralSocketHandler(Hub hub, SocketChannel socketChannel) {
		m_hub = hub;
		m_channel = socketChannel;
		m_remoteAddress = socketChannel.remoteAddress().getAddress().getHostAddress();
		m_packetStateMachine = new PacketMachine(hub, this);
		m_packetAssembler = new PacketAssemblyMachine(m_packetStateMachine::handlePacket, socketChannel.alloc());
	}

	@NonNullByDefault(false)
	@Override
	protected void channelRead0(ChannelHandlerContext context, ByteBuf data) throws Exception {
		//-- Keep reading data from the buffer until empty and handle it.
		if(m_hub.getState() != HubState.STARTED || m_state != SocketState.CONNECTED) {
			return;
		}
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
	 * Called when the channel has just been opened. This sends a CHALLENGE packet to the client.
	 */
	@NonNullByDefault(false)
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		if(m_hub.getState() != HubState.STARTED) {
			return;
		}
		ctx.channel().closeFuture().addListener(future -> {
			remoteDisconnected(ctx);
		});
		m_packetStateMachine.sendChallenge();
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

	public synchronized AbstractConnection getConnection() {
		AbstractConnection connection = m_connection;
		if(null == connection)
			throw new IllegalStateException("The party is no longer connected to this connection.");
		return connection;
	}

	synchronized void setConnection(AbstractConnection connection) {
		m_connection = connection;
	}

	Client getClientConnection() {
		return (Client) getConnection();
	}

	Server getServerConnection() {
		return (Server) getConnection();
	}

	/**
	 * Called only when we're a temp client, this checks whether the server
	 * accepted our auth request.
	 */
	public void tmpGotResponseFrom(Server server, Envelope envelope, @Nullable ByteBuf payload, int length) {
		m_packetStateMachine.tmpGotResponseFrom(server, envelope);
	}

	synchronized void registerClient(BeforeClientData data) {
		log("CLIENT authenticated!!");
		var connection = getCluster().registerAuthorizedClient(this);
		if(connection != null) {
			m_connection = connection;
		} else {
			log("New connection was refused on channel with id: " + getId() + ", client IP: "+ getRemoteAddress());
		}
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	All kinds of disconnect handling							*/
	/*----------------------------------------------------------------------*/

	/**
	 * Just disconnects the channel and make sure it is unusable.
	 */
	public synchronized void disconnectOnly(String why) {
		log("internal disconnect requested: " + why + ", client IP: "+ getRemoteAddress());
		synchronized(this) {
			m_connection = null;
			m_cluster = null;
			m_txPacketQueue.clear();
			m_txPacketQueuePrio.clear();
			m_txCurrentPacket = null;
			m_state = SocketState.DISCONNECTED;
			for(ByteBuf byteBuf : m_txBufferList) {
				byteBuf.release();
			}
			m_txBufferList.clear();
		}
		try {
			m_channel.disconnect();
		} catch(Exception x) {
			if(m_hub.getState() == HubState.STARTED)
				log("NETTY Disconnect failed (ignoring): " + x);
		}
	}

	/**
	 * The channel disconnected, possibly because of a remote disconnect.
	 */
	private void remoteDisconnected(ChannelHandlerContext ctx) {
		remoteDisconnected(ctx, "remote disconnect received");
	}

	void remoteDisconnected(ChannelHandlerContext ctx, String why) {
		log(why + " (id=" + ctx.channel().id() + ")");
		AbstractConnection connection;
		Cluster cluster;
		synchronized(this) {
			if(m_state != SocketState.CONNECTED)
				return;
			connection = m_connection;
			cluster = m_cluster;
			m_connection = null;
			m_cluster = null;
			m_state = SocketState.DISCONNECTED;

			if(null != connection) {
				log("Channel " + ctx.channel().id() + " disconnected");
				connection.channelClosed();                    // Deregister from hub and post event
			}
		}

		//-- Clean up
		m_packetStateMachine.unregisterTmpClient();
		if(null != cluster)
			cluster.runEvents();
	}

	public String getId() {
		return m_channel.id().toString();
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Other listeners for channel events.							*/
	/*----------------------------------------------------------------------*/
	@NonNullByDefault(false)
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		if(m_hub.getState() == HubState.STARTED && m_state == SocketState.CONNECTED) {
			error("Connection exception: " + cause + ". Client IP: "+ getRemoteAddress());
		}
		try {
			ctx.close();
		} catch(Exception x) {
			if(m_hub.getState() == HubState.STARTED && m_state == SocketState.CONNECTED) {
				error("Connection close exception: " + x);
			}
		}
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Sending data to this channel's remote.						*/
	/*----------------------------------------------------------------------*/

	/**
	 * Initiate sending of a packet, by converting the packet into a list
	 * of send buffers and starting the transmit.
	 */
	void initiatePacketSending(@Nullable TxPacket packet) {
		if(m_hub.getState() != HubState.STARTED)
			return;

		//ConsoleUtil.consoleLog("XX", ">> initiatePacketSending entry packet " + packet);
		int txfailCount = 0;
		for(; ; ) {
			if(null == packet) {
				return;
			}
			synchronized(this) {
				if(m_txCurrentPacket != null)                // Transmitter busy?
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
					if(m_hub.getState() == HubState.STARTED)
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
			if(m_txBufferList.size() == 0) {
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
				txHandleFailedSend(cause);
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
					throw new IllegalStateException("Null current txpacket at end of send on " + getLogInd() + " channel " + m_channel.id());
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
	private void txHandleFailedSend(@Nullable Throwable cause) {
		// If we're stopping or stopped, or disconnected, it's logical sends fail, ignore
		if(m_hub.getState() != HubState.STARTED || m_state != SocketState.CONNECTED)
			return;
		TxPacket packet;
		synchronized(this) {
			packet = m_txCurrentPacket;							// Will be null when we're terminating/disconnecting
			if(null == packet) {
				return;
				//throw new IllegalStateException("current packet is null while transmitting");
			}

			//-- We tell the Party nothing, this will cause it to resent the packet once a new connection has been made.
			m_txCurrentPacket = null;
			releaseTxBuffers();
		}

		String why;
		if(null == cause) {
			why = "Unknown failure?";
		} else {
			why = cause.toString();
			log("Send failure: " + cause);
			cause.printStackTrace();
		}
		disconnectOnly("failed send " + packet + ": " + why);
		if(null != cause)
			m_hub.registerFailure(cause);
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
			m_txPacketQueue.add(packet);                    // Will be picked up when current packet tx finishes.
			packet.setPacketRemoveFromQueue(() -> {
				synchronized(this) {
					m_txPacketQueue.remove(packet);
				}
			}, TxPacketType.HUB);
		}
		initiatePacketSending(packet);
	}

	void immediateSendHubException(Envelope source, HubException x) {
		if(m_hub.getState() != HubState.STARTED || m_state != SocketState.CONNECTED)
			return;
		log("sending hub exception " + x);

		PacketResponseBuilder rb = new PacketResponseBuilder(this)
			.fromEnvelope(source);
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
				log("send failed, disconnecting channel " + m_channel.id());
				m_channel.disconnect();
			});
		}
		rb.send();
	}

	public void sendPing() {
		if(m_hub.getState() != HubState.STARTED)
			return;
		PacketResponseBuilder response = new PacketResponseBuilder(this);
		response.getEnvelope()
			.setSourceId("")                            // from HUB
			.setTargetId(getMyID())                        // Whatever is known
			.setVersion(1)
			.setPing(Hubcore.Ping.newBuilder().build())
		;
		response.send();
		log("sent ping");
	}

	private String getLogInd() {
		String myId = m_myId;
		AbstractConnection connection = m_connection;
		if(myId == null) {
			return "newClient";
		}
		if(m_connection instanceof Server) {
			return "S:" + myId;
		} else {
			return "C:" + m_myId;
		}
	}

	void log(String log) {
		ConsoleUtil.consoleLog("Hub", getId(), getLogInd(), log);
	}

	void error(String log) {
		ConsoleUtil.consoleError("Hub", getId(), getLogInd(), log);
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Info on this connection.									*/
	/*----------------------------------------------------------------------*/

	/**
	 * The source ID for this connection, as identified by the initial HELO packet.
	 */
	public synchronized String getMyID() {
		String myId = m_myId;
		if(null == myId)
			throw new IllegalStateException("The connection's ID is not yet known - HELO packet response has not yet been received?");
		return myId;
	}

	public synchronized Cluster getCluster() {
		Cluster cluster = m_cluster;
		if(null == cluster)
			throw new IllegalStateException("The connection's ID is not yet known - HELO packet response has not yet been received?");
		return cluster;
	}

	synchronized void setHelloInformation(String clientId, Cluster cluster, @Nullable String resourceId) {
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
	@NonNullByDefault(false)
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		m_packetAssembler.destroy();
	}

	public ByteBufAllocator alloc() {
		return m_channel.alloc();
	}


	private ConnectionDirectory getDirectory() {
		return m_hub.getDirectory();
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
