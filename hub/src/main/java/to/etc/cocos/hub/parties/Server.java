package to.etc.cocos.hub.parties;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.AbstractConnection;
import to.etc.cocos.hub.ByteBufPacketSender;
import to.etc.cocos.hub.CentralSocketHandler;
import to.etc.cocos.hub.Hub;
import to.etc.cocos.hub.PacketResponseBuilder;
import to.etc.cocos.hub.TxPacket;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.AckableMessage;
import to.etc.cocos.messages.Hubcore.CommandError;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.FatalHubException;
import to.etc.hubserver.protocol.HubException;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
@NonNullByDefault
public class Server extends AbstractConnection {
	public Server(Cluster cluster, Hub systemContext, String id) {
		super(cluster, systemContext, id);
	}

	/**
	 * Packet received from the remote server.
	 */
	public void packetReceived(Envelope envelope, @Nullable ByteBuf payload, int length) {
		if(!isUsable())
			throw new FatalHubException(ErrorCode.serverDisconnected);
		log("Packet received(S): " + Hub.getPacketType(envelope));

		String targetId = envelope.getTargetId();
		if(targetId.length() == 0) {
			handleHubCommand(envelope);
		} else {
			Client client = getCluster().findClient(targetId);
			if(null != client) {
				//-- Forward the packet to the client.
				client.packetFromServer(this, envelope, payload, length);
			} else {
				CentralSocketHandler tmpClient = getDirectory().findTempClient(targetId);
				if(null == tmpClient) {
					handleClientMissing(envelope);
					return;
				}
				tmpClient.tmpGotResponseFrom(this, envelope, payload, length);
			}
		}
	}

	private void handleClientMissing(Envelope env) {
		switch(env.getPayloadCase()) {
			default:
				log("Can't send " + env.getPayloadCase() + " , client " + env.getTargetId() + " no longer connected.");
				return;

			case ACKABLE:
				handleClientMissingAckable(env);
				return;
		}
	}

	private void handleClientMissingAckable(Envelope env) {
		AckableMessage ackable = env.getAckable();
		switch(ackable.getPayloadCase()){
			default:
				throw new HubException(ErrorCode.clientNotFound, env.getTargetId());

			case CMD:
				//-- Just reply with a command error
				CommandError error = CommandError.newBuilder()
					.setCode(ErrorCode.clientNotFound.name())
					.setMessage("Client " + env.getTargetId() + " not found")
					.build();
				PacketResponseBuilder pb = packetBuilder(env.getTargetId());
				pb.getEnvelope().setAckable(AckableMessage.newBuilder().setCommandError(error).build());
				pb.send();
				return;
		}
	}

	private void handleHubCommand (Envelope envelope){
		if(envelope.hasPong())
			return;
		log("HUB command packet received: " + Hub.getPacketType(envelope));
	}

	public PacketResponseBuilder packetBuilder(String clientID) {
		PacketResponseBuilder b = packetBuilder();
		b.getEnvelope()
			.setSourceId(clientID)
			.setTargetId(getFullId())
			.setVersion(1)
			;
		return b;
	}

	public PacketResponseBuilder packetBuilderAckable(String clientID) {
		PacketResponseBuilder b = packetBuilder();
		b.getEnvelope()
			.setSourceId(clientID)
			.setTargetId(getFullId())
			.setVersion(1)
		;
		return b;
	}


	/**
	 * Send a "client unregistered" packet to the remote.
	 */
	public void sendEventClientUnregistered(String fullId) {
		PacketResponseBuilder b = packetBuilder(fullId);
		b.getEnvelope()
			.setClientDisconnected(Hubcore.ClientDisconnected.getDefaultInstance()
			);
		b.send();
	}

	public void sendEventClientRegistered(String clientId) {
		PacketResponseBuilder b = packetBuilder(clientId);
		b.getEnvelope()
			.setClientConnected(Hubcore.ClientConnected.getDefaultInstance()
			);
		b.send();
	}

	public void sendEventClientInventory(String clientId, ByteBufferPacket packet) {
		ByteBuf buffer = getHandler().alloc().buffer();
		//System.out.println("Payload = " + payload);
		//System.out.println(" buffers= " + payload.getBuffers());
		for(byte[] bb : packet.getBuffers()) {
			buffer.writeBytes(bb);
		}

		PacketResponseBuilder b = packetBuilder(clientId)
			.sourceId(clientId);
		b.getEnvelope().setInventory(
			Hubcore.ClientInventory.newBuilder()
				.setDataFormat(packet.getDataFormat())
		);
		b.body(buffer).send();
	}

	public void packetFromClient(Client client, Envelope envelope, @Nullable ByteBuf payload, int length) {
		log("RX from client " + client.getFullId() + ": " + Hub.getPacketType(envelope));
		TxPacket p = new TxPacket(envelope, client, null == payload ? null : new ByteBufPacketSender(payload), null);
		if(null != payload)
			ReferenceCountUtil.retain(payload);
		sendPacket(p);
	}

	/**
	 * When a new server has connected this will send the current client
	 * set to it.
	 */
	public void startInventorySend() throws Exception {
		new InventorySender(this, this::inventorySendCompleted).start();
	}

	private void inventorySendCompleted() {



	}
}
