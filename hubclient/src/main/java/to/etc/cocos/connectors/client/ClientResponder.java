package to.etc.cocos.connectors.client;

import to.etc.cocos.connectors.AbstractResponder;
import to.etc.cocos.connectors.IHubResponder;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-1-19.
 */
public class ClientResponder extends AbstractResponder implements IHubResponder {
	private final String m_clientVersion = "HubClient 1.0";

	public ClientResponder() {
	}

	//@Override public void onHelloPacket(HubConnector connector, Hubcore.Envelope envelope, List<byte[]> payload) throws Exception {
	//	Hubcore.HelloChallenge c = envelope.getChallenge();
	//	if(envelope.getVersion() != 1)
	//		throw new IllegalStateException("Cannot accept hub version " + c.getVersion());
	//	String sv = c.getServerVersion();
	//	System.out.println(">> connected to hub " + sv);
	//	byte[] challenge = c.getChallenge().toByteArray();
	//
	//	Hubcore.ClientHeloResponse response = Hubcore.ClientHeloResponse.newBuilder()
	//			.setChallengeResponse(ByteString.EMPTY)
	//			.setClientVersion(m_clientVersion)
	//			.setVersion(1)
	//			.build();
	//
	//
	//	connector.sendPacket(0x01, CommandNames.CLNT_CMD, response);
	//}
	//
	//@Override public void onAuth(HubConnector connector, Hubcore.Envelope envelope, List<byte[]> payload) throws Exception {
	//
	//}

	private byte[] encodeChallenge(byte[] challenge) {
		return challenge;
	}
}
