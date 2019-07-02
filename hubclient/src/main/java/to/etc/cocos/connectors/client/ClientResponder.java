package to.etc.cocos.connectors.client;

import com.google.protobuf.ByteString;
import to.etc.cocos.connectors.AbstractResponder;
import to.etc.cocos.connectors.CommandContext;
import to.etc.cocos.connectors.IHubResponder;
import to.etc.cocos.connectors.Synchronous;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-1-19.
 */
public class ClientResponder extends AbstractResponder implements IHubResponder {
	private final String m_clientVersion = "HubClient 1.0";

	private final String m_clientPassword;

	private final String m_targetCluster;

	public ClientResponder(String clientPassword, String targetClusterAndOrg) {
		if(targetClusterAndOrg.indexOf('#') == -1)
			throw new IllegalStateException("The target for a client must be in the format 'organisation#cluster'");
		m_clientPassword = clientPassword;
		m_targetCluster = targetClusterAndOrg;
	}

	/**
	 * Respond with a Client HELO response, and encode the challenge with the password.
	 */
	@Synchronous
	public void handleHELO(CommandContext cc) throws Exception {
		System.out.println("Got HELO request");
		ByteString ba = cc.getSourceEnvelope().getChallenge().getChallenge();
		byte[] challenge = ba.toByteArray();

		String ref = m_clientPassword + ":" + cc.getConnector().getMyId();
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(ref.getBytes(StandardCharsets.UTF_8));
		md.update(challenge);
		byte[] digest = md.digest();

		cc.getResponseEnvelope()
			.setSourceId(cc.getConnector().getMyId())
			.setVersion(1)
			.setTargetId("@" + m_targetCluster)
			.setHeloClient(Hubcore.ClientHeloResponse.newBuilder()
				.setChallengeResponse(ByteString.copyFrom(digest))
				.setClientVersion(m_clientVersion)
				.build()
			);
		cc.respond();
	}

	/**
	 * If the authorization was successful we receive this; move to AUTHORIZED status.
	 */
	@Synchronous
	public void handleAUTH(CommandContext cc) throws Exception {
		cc.getConnector().authorized();
		cc.log("Authenticated successfully");
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
