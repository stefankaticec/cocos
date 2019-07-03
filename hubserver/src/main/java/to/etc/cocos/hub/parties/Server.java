package to.etc.cocos.hub.parties;

import to.etc.cocos.hub.ISystemContext;
import to.etc.cocos.hub.problems.FatalHubException;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.util.ConsoleUtil;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public class Server extends AbstractConnection {
	public Server(ISystemContext systemContext, String id) {
		super(systemContext, id);
	}

	public void packetReceived(Envelope envelope) {
		if(! isUsable())
			throw new FatalHubException(ErrorCode.serverDisconnected);





		log("Packet received: " + envelope.getCommand());
	}

	//public void newPacket(ChannelHandlerContext ctx, HubPacket packet) throws Exception {
	//	if(packet.getType() == 0x01) {
	//		callPacketMethod(packet);
	//	} else {
	//		throw new ProtocolViolationException("Unexpected packet: " + packet);
	//	}
	//}
	//
	///**
	// * AUTH received from the server means the client is valid. This locates the client using its temp ID, then
	// * tells it that it's valid.
	// */
	//public void handleAUTH(HubPacket packet) {
	//	String clientId = packet.getTargetId();
	//	Client client = getDirectory().findTempClient(clientId);	// Find the temp client
	//	if(client == null) {
	//		log("AUTH for nonexistent client id=" + clientId);
	//		return;
	//	}
	//
	//	//-- Now register the client under its real ID, or re-register.
	//	Client newClient = getDirectory().registerAuthorizedClient(client);
	//	newClient.sendHubMessage(0x01, CommandNames.AUTH_CMD, null, null);
	//	log("Connected authorized client " + newClient.getFullId());
	//
	//}

	@Override
	public void log(String s) {
		ConsoleUtil.consoleLog("Hub:Server", getFullId(), s);
	}
}
