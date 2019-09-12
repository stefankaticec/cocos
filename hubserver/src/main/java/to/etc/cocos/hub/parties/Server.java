package to.etc.cocos.hub.parties;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.AbstractConnection;
import to.etc.cocos.hub.ByteBufPacketSender;
import to.etc.cocos.hub.CentralSocketHandler;
import to.etc.cocos.hub.Hub;
import to.etc.cocos.hub.PacketResponseBuilder;
import to.etc.cocos.hub.TxPacket;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.FatalHubException;
import to.etc.hubserver.protocol.HubException;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.util.ConsoleUtil;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
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
		log("Packet received: " + envelope.getPayloadCase());

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
					throw new HubException(ErrorCode.clientNotFound, targetId);
				}
				tmpClient.tmpGotResponseFrom(this, envelope, payload, length);
			}
		}
	}

	@Override
	public void log (String s){
		ConsoleUtil.consoleLog("Hub:Server", getFullId(), s);
	}


	private void handleHubCommand (Envelope envelope){
		log("HUB command packet received");

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


	/**
	 * Send a "client unregistered" packet to the remote.
	 */
	public void sendEventClientUnregistered(String fullId) {
		PacketResponseBuilder b = packetBuilder(fullId);
		b.getEnvelope().setClientDisconnected(Hubcore.ClientDisconnected.getDefaultInstance());
		b.send();
	}

	public void sendEventClientRegistered(String clientId) {
		PacketResponseBuilder b = packetBuilder(clientId);
		b.getEnvelope().setClientConnected(Hubcore.ClientConnected.getDefaultInstance());
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
		b.send(buffer);
	}

	public void packetFromClient(Client client, Envelope envelope, ByteBuf payload, int length) {
		log("RX from client " + client.getFullId() + ": " + envelope.getPayloadCase());
		TxPacket p = new TxPacket(envelope, client, new ByteBufPacketSender(payload));
		ReferenceCountUtil.retain(payload);
		sendPacket(p);
	}
}
