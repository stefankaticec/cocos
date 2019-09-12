package to.etc.cocos.connectors.client;

import com.google.protobuf.ByteString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.HubConnectorBase;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.common.ProtocolViolationException;
import to.etc.cocos.connectors.common.Synchronous;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-1-19.
 */
@NonNullByDefault
final public class HubClient extends HubConnectorBase {
	private final String m_clientVersion = "HubClient 1.0";

	private final String m_clientPassword;

	private final String m_targetCluster;

	private final IClientPacketHandler m_clientHandler;

	private HubClient(String hubServer, int hubServerPort, IClientPacketHandler clientHandler, String clientPassword, String targetClusterAndOrg, String myId) {
		super(hubServer, hubServerPort, targetClusterAndOrg, myId, "Client");
		m_clientHandler = clientHandler;
		if(targetClusterAndOrg.indexOf('@') != -1)
			throw new IllegalStateException("The target for a client must be in the format 'organisation#cluster' or just a cluster name");
		m_clientPassword = clientPassword;
		m_targetCluster = targetClusterAndOrg;
	}

	static public HubClient create(IClientPacketHandler handler, String hubServer, int hubServerPort, String targetClusterAndOrg, String myId, String myPassword) {
		HubClient responder = new HubClient(hubServer, hubServerPort, handler, myPassword, targetClusterAndOrg, myId);
		return responder;
	}

	@Override protected void handlePacketReceived(CommandContext ctx, List<byte[]> data) throws Exception {
		switch(ctx.getSourceEnvelope().getPayloadCase()) {
			default:
				throw new ProtocolViolationException("Unexpected packet type=" + ctx.getSourceEnvelope().getPayloadCase());

			case CHALLENGE:
				handleHELO(ctx);
				break;

			case AUTH:
				handleAUTH(ctx);
				break;

			case COMMAND:
				handleCommand(ctx, data);
				break;

			case HUBERROR:
				onErrorPacket(ctx.getSourceEnvelope());
				forceDisconnect("Hub error: " + ctx.getSourceEnvelope().getHubError().getText());
				break;
		}
	}

	private void handleCommand(CommandContext ctx, List<byte[]> data) {





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

		//-- Immediately send the inventory packet
		JsonPacket inventory = m_clientHandler.getInventory();
		cc.getResponseEnvelope()
			.setInventory(Hubcore.ClientInventory.newBuilder()
				.setDataFormat(CommandNames.getJsonDataFormat(inventory))
			)
			;
		cc.respondJson(inventory);
	}

	/**
	 * Received a JSON command. The command gets executed asynchronously, and is delegated
	 * to the client command handler.
	 */
	public void handleJCMD(CommandContext cc, JsonPacket packet) throws Exception {
		m_clientHandler.executeCommand(cc, packet);
	}

	@Override protected void onErrorPacket(Envelope env) {
		// IMPLEMENT
	}

	private byte[] encodeChallenge(byte[] challenge) {
		return challenge;
	}
}
