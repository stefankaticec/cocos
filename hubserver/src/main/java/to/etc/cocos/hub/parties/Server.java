package to.etc.cocos.hub.parties;

import io.netty.channel.ChannelHandlerContext;
import to.etc.cocos.hub.HubPacket;
import to.etc.cocos.hub.ISystemContext;
import to.etc.cocos.hub.problems.ProtocolViolationException;
import to.etc.hubserver.protocol.CommandNames;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public class Server extends AbstractConnection {
	public Server(ISystemContext systemContext, String id) {
		super(systemContext, id);
	}

	public void newPacket(ChannelHandlerContext ctx, HubPacket packet) throws Exception {
		if(packet.getType() == 0x01) {
			callPacketMethod(packet);
		} else {
			throw new ProtocolViolationException("Unexpected packet: " + packet);
		}
	}

	/**
	 * AUTH received from the server means the client is valid. This locates the client using its temp ID, then
	 * tells it that it's valid.
	 */
	public void handleAUTH(HubPacket packet) {
		String clientId = packet.getTargetId();
		Client client = getDirectory().findTempClient(clientId);	// Find the temp client
		if(client == null) {
			log("AUTH for nonexistent client id=" + clientId);
			return;
		}

		//-- Now register the client under its real ID, or re-register.
		Client newClient = getDirectory().registerAuthorizedClient(client);
		newClient.sendHubMessage(0x01, CommandNames.AUTH_CMD, null, null);
		log("Connected authorized client " + newClient.getFullId());

	}

}
