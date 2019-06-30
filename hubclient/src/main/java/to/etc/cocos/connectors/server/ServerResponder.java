package to.etc.cocos.connectors.server;

import to.etc.cocos.connectors.AbstractResponder;
import to.etc.cocos.connectors.IHubResponder;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public class ServerResponder extends AbstractResponder implements IHubResponder {
	private final String m_serverVersion = "1.0";

	private final String m_serverId;

	public ServerResponder(String id) {
		m_serverId = id;
	}

	//@Override public void onHelloPacket(HubConnector connector, Hubcore.Envelope envelope, List<byte[]> payload) throws Exception {
	//	Hubcore.HelloChallenge c = envelope.getChallenge();
	//	if(envelope.getVersion() != 1)
	//		throw new IllegalStateException("Cannot accept hub version " + c.getVersion());
	//	String sv = c.getServerVersion();
	//	System.out.println(">> connected to hub " + sv);
	//
	//	Hubcore.ServerHeloResponse r = Hubcore.ServerHeloResponse.newBuilder()
	//			.setVersion(1)
	//			.setServerVersion(m_serverVersion)
	//			.setChallengeResponse(ByteString.EMPTY)
	//			.build();
	//	connector.sendPacket(0x01, CommandNames.SRVR_CMD, r);
	//}
	//
	//@Override public void onAuth(HubConnector connector, Hubcore.Envelope envelope, List<byte[]> payload) throws Exception {
	//
	//}
	//
	///**
	// * CLIENT wants a login. Check his authentication, then send back an AUTH packet if accepted or an error
	// * packet if not.
	// */
	//public void handleCLNT(HubConnector hc, Hubcore.Envelope envelope, List<byte[]> payload) throws Exception {
	//	byte[] response = r.getChallengeResponse().toByteArray();
	//
	//	////-- IMPLEMENT check
	//	//
	//	//
	//	////-- Send back AUTH
	//	//PacketBuilder b = hc.allocatePacketBuilder(0x01, packet.getSourceID(), m_serverId, CommandNames.AUTH_CMD);
	//	//hc.sendPacket(b);
	//}
}
