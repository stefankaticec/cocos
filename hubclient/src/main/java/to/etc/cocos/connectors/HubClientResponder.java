package to.etc.cocos.connectors;

import com.google.protobuf.ByteString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.client.IClientPacketHandler;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-1-19.
 */
@NonNullByDefault
public class HubClientResponder extends AbstractResponder implements IHubResponder {
	private final String m_clientVersion = "HubClient 1.0";

	private final String m_clientPassword;

	private final String m_targetCluster;

	private final IClientPacketHandler m_clientHandler;

	public HubClientResponder(IClientPacketHandler clientHandler, String clientPassword, String targetClusterAndOrg) {
		m_clientHandler = clientHandler;
		if(targetClusterAndOrg.indexOf('@') != -1)
			throw new IllegalStateException("The target for a client must be in the format 'organisation#cluster' or just a cluster name");
		m_clientPassword = clientPassword;
		m_targetCluster = targetClusterAndOrg;
	}

	/**
	 * Respond with a Client HELO response. This send the inventory
	 * packet, and encodes the challenge with the password.
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
			.setTargetId(m_targetCluster)
			.setHeloClient(Hubcore.ClientHeloResponse.newBuilder()
				.setChallengeResponse(ByteString.copyFrom(digest))
				.setClientVersion(m_clientVersion)
				.build()
			);
		cc.respond();
		//cc.respondJson(m_clientHandler.getInventory());
	}

	/**
	 * If the authorization was successful we receive this; move to AUTHORIZED status.
	 */
	@Synchronous
	public void handleAUTH(CommandContext cc) throws Exception {
		cc.getConnector().authorized();
		cc.log("Authenticated successfully");
	}

	private byte[] encodeChallenge(byte[] challenge) {
		return challenge;
	}
}
