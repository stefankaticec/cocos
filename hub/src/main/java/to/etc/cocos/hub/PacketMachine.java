package to.etc.cocos.hub;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.CentralSocketHandler.IPacketHandler;
import to.etc.cocos.hub.parties.BeforeClientData;
import to.etc.cocos.hub.parties.Cluster;
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
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.FatalHubException;
import to.etc.util.StringTool;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 31-03-20.
 */
final class PacketMachine {
	private final Hub m_hub;

	private final CentralSocketHandler m_socketHandler;

	private IPacketHandler m_packetState = this::psExpectHeloResponse;

	@Nullable
	private Object m_packetStateData;

	@Nullable
	private byte[] m_challenge;

	@NonNull
	final private String m_tmpClientId = StringTool.generateGUID();

	public PacketMachine(Hub hub, CentralSocketHandler socketHandler) {
		m_hub = hub;
		m_socketHandler = socketHandler;
	}

	/**
	 * This starts the dialogue for a new connection. It sends the challenge packet.
	 */
	public void sendChallenge() {
		//-- Send HELO with challenge
		byte[] challenge = m_challenge = m_hub.getChallenge();
		PacketResponseBuilder response = new PacketResponseBuilder(m_socketHandler);
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

	/**
	 * State handler that handles the packet level dialogue.
	 */
	void handlePacket(Envelope envelope, @Nullable ByteBuf payload, int length) throws Exception {
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
		if(! m_hub.checkServerSignature(clusterName, serverName, Objects.requireNonNull(m_challenge), signature))
			throw new FatalHubException(ErrorCode.authenticationFailure);

		//-- Authorized -> respond with AUTH packet.
		Cluster cluster = m_hub.getDirectory().getCluster(clusterName);
		Server server = cluster.registerServer(serverName, Arrays.asList("*"));
		m_socketHandler.setHelloInformation(sourceId, cluster, null);
		server.newConnection(m_socketHandler);
		m_socketHandler.log("new connection for server " + server.getFullId() + " in state " + server.getState());

		//-- From now on this channel services the specified server
		m_socketHandler.setConnection(server);
		setPacketState(server::packetReceived);

		//-- send back AUTH packet
		PacketResponseBuilder rb = new PacketResponseBuilder(m_socketHandler)
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
				cluster = m_hub.getDirectory().getCluster(split[0]);
				server = cluster.getRandomServer();
				if(null == server)
					throw new FatalHubException(ErrorCode.clusterNotFound, split[0]);
				orgId = null;
				break;

			case 2:
				cluster = m_hub.getDirectory().getCluster(split[1]);
				orgId = split[0];
				server = cluster.findServiceServer(orgId);
				if(null == server)
					throw new FatalHubException(ErrorCode.targetNotFound, split[0]);
				break;
		}

		//-- Forward the packet to the remote server
		String myid = envelope.getSourceId();
		m_socketHandler.setHelloInformation(myid, cluster, orgId);
		m_hub.getDirectory().registerTmpClient(m_tmpClientId, m_socketHandler);

		PacketResponseBuilder b = new PacketResponseBuilder(server.getHandler());
		b.getEnvelope()
			.setSourceId(m_tmpClientId)						// From tmp client ID
			.setTargetId(server.getFullId())				// To the selected server
			.setVersion(1)
			.setClientAuth(Hubcore.ClientAuthRequest.newBuilder()
				.setChallenge(ByteString.copyFrom(m_challenge))
				.setChallengeResponse(envelope.getHeloClient().getChallengeResponse())
				.setClientId(myid)
				.setClientVersion(envelope.getHeloClient().getClientVersion())
			);
		setPacketState(this::waitForServerAuth, new BeforeClientData(cluster, orgId, myid));
		b.send();
	}

	public void tmpGotResponseFrom(Server server, Envelope envelope) {
		//-- Expecting an AUTH or ERROR response.
		if(envelope.hasHubError()) {
			HubErrorResponse error = envelope.getHubError();
			PacketResponseBuilder b = new PacketResponseBuilder(m_socketHandler)
				.forwardTo(envelope);
			b.getEnvelope().setHubError(error);
			b.send();

			//-- REMOVE CLIENT
			unregisterTmpClient();
		} else if(envelope.hasAuth()) {
			//-- We're authenticated! Yay!
			m_socketHandler.registerClient(getPacketStateData(BeforeClientData.class));		// ORDERED
			setPacketState(this::psExpectClientInventory);					// ORDERED

			AuthResponse auth = envelope.getAuth();
			PacketResponseBuilder b = new PacketResponseBuilder(m_socketHandler)
				.forwardTo(envelope);
			b.getEnvelope().setAuth(auth);
			b.send();
		} else {
			throw new ProtocolViolationException("Expected server:auth, got " + envelope.getPayloadCase());
		}
	}

	void unregisterTmpClient() {
		m_hub.getDirectory().unregisterTmpClient(m_tmpClientId);				// Be sure not to be registered anymore
	}

	private void psExpectClientInventory(Envelope envelope, @Nullable ByteBuf payload, int length) throws IOException {
		if(! envelope.hasInventory()) {
			throw new ProtocolViolationException("Expecting inventory, got " + envelope.getPayloadCase());
		}
		m_socketHandler.log("Client inventory received");
		if(length == 0)
			throw new ProtocolViolationException("The inventory packet data is missing");
		ClientInventory inventory = envelope.getInventory();
		String dataFormat = inventory.getDataFormat();
		if(null == dataFormat || dataFormat.trim().length() == 0)
			throw new ProtocolViolationException("The inventory packet data format is missing");

		m_socketHandler.getClientConnection().updateInventory(dataFormat, Objects.requireNonNull(payload), length);

		//-- Now enter passthrough mode.
		setPacketState(m_socketHandler.getClientConnection()::packetReceived);
	}

	private void waitForServerAuth(Envelope envelope, @Nullable ByteBuf payload, int length) {
		throw new ProtocolViolationException("Not expecting a packet while waiting for client authentication by the server");
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

}
