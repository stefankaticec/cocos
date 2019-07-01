package to.etc.cocos.connectors.server;

import com.google.protobuf.ByteString;
import to.etc.cocos.connectors.AbstractResponder;
import to.etc.cocos.connectors.CommandContext;
import to.etc.cocos.connectors.IHubResponder;
import to.etc.cocos.connectors.Synchronous;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public class ServerResponder extends AbstractResponder implements IHubResponder {
	private final String m_serverVersion = "1.0";

	private final String m_serverId;

	private final String m_clusterPassword;

	public ServerResponder(String id, String clusterPassword) {
		m_serverId = id;
		m_clusterPassword = clusterPassword;
	}

	/**
	 * Respond with a Server HELO response, and encode the challenge with the password.
	 */
	@Synchronous
	public void handleHELO(CommandContext cc, ArrayList<byte[]> body) throws Exception {
		System.out.println("Got HELO request");


		ByteString ba = cc.getSourceEnvelope().getChallenge().getChallenge();
		byte[] challenge = ba.toByteArray();

		String ref = m_clusterPassword + ":" + m_serverId;
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(ref.getBytes(StandardCharsets.UTF_8));
		md.update(challenge);
		byte[] digest = md.digest();

		cc.getResponseEnvelope()
			.setSourceId(m_serverId)
			.setVersion(1)
			.setTargetId("")
			.setHeloServer(Hubcore.ServerHeloResponse.newBuilder()
				.setChallengeResponse(ByteString.copyFrom(digest))
				.setServerVersion(m_serverVersion)
				.build()
			);
		cc.respond();
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
