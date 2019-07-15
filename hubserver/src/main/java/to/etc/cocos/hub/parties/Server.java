package to.etc.cocos.hub.parties;

import io.netty.buffer.ByteBuf;
import to.etc.cocos.hub.AbstractConnection;
import to.etc.cocos.hub.CentralSocketHandler;
import to.etc.cocos.hub.Hub;
import to.etc.cocos.hub.PacketResponseBuilder;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.FatalHubException;
import to.etc.hubserver.protocol.HubException;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.util.ByteBufferOutputStream;
import to.etc.util.ConsoleUtil;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public class Server extends AbstractConnection {
	public Server(Cluster cluster, Hub systemContext, String id) {
		super(cluster, systemContext, id);
	}

	public void packetReceived(Envelope envelope) {
		if(!isUsable())
			throw new FatalHubException(ErrorCode.serverDisconnected);
		log("Packet received: " + envelope.getCommand());

		String targetId = envelope.getTargetId();
		if(targetId.length() == 0) {
			handleHubCommand(envelope);
		} else {
			Client client = getCluster().findClient(targetId);
			if(null != client) {
				client.packetFromServer(this, envelope);
			} else {
				CentralSocketHandler tmpClient = getDirectory().findTempClient(targetId);
				if(null == tmpClient) {
					throw new HubException(ErrorCode.clientNotFound, targetId);
				}
				tmpClient.tmpGotResponseFrom(this, envelope);
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

	public PacketResponseBuilder packetBuilder(String clientID, String command) {
		PacketResponseBuilder b = packetBuilder();
		b.getEnvelope()
			.setCommand(command)
			.setSourceId(clientID)
			.setTargetId(getFullId())
			.setVersion(1)
			.setDataFormat("")
			;
		return b;
	}


	/**
	 * Send a "client unregistered" packet to the remote.
	 */
	public void sendEventClientUnregistered(String fullId) {
		PacketResponseBuilder b = packetBuilder(fullId, CommandNames.CLIENT_DISCONNECTED);
		b.send();
	}

	public void sendEventClientRegistered(String clientId) {
		PacketResponseBuilder b = packetBuilder(clientId, CommandNames.CLIENT_CONNECTED);
		b.send();
	}

	public void sendEventClientInventory(String clientId, ByteBufferOutputStream payload) {
		ByteBuf buffer = getHandler().alloc().buffer();
		for(byte[] bb : payload.getBuffers()) {
			buffer.writeBytes(bb);
		}

		packetBuilder(clientId, CommandNames.CLIENT_CONNECTED)
			.sourceId(clientId)
			.send(buffer);
	}
}
