package to.etc.cocos.connectors.server;

import com.google.protobuf.ByteString;
import to.etc.cocos.connectors.AbstractResponder;
import to.etc.cocos.connectors.CommandContext;
import to.etc.cocos.connectors.IHubResponder;
import to.etc.cocos.connectors.Synchronous;
import to.etc.function.ConsumerEx;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ClientAuthRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public class ServerResponder extends AbstractResponder implements IHubResponder {
	private final String m_serverVersion = "1.0";

	//private final String m_serverId;

	private final String m_clusterPassword;

	private final IClientAuthenticator m_authenticator;

	private CopyOnWriteArrayList<IClientListener> m_clientListeners = new CopyOnWriteArrayList<>();

	private Map<String, IRemoteClient> m_remoteClientMap = new HashMap<>();

	public ServerResponder(String clusterPassword, IClientAuthenticator authenticator) {
		m_clusterPassword = clusterPassword;
		m_authenticator = authenticator;
	}
	/**
	 * Server authentication request from the HUB. Respond with a Server
	 * HELO response, and encode the challenge with the password.
	 */
	@Synchronous
	public void handleHELO(CommandContext cc) throws Exception {
		System.out.println("Got HELO request");

		ByteString ba = cc.getSourceEnvelope().getChallenge().getChallenge();
		byte[] challenge = ba.toByteArray();

		String ref = m_clusterPassword + ":" + cc.getConnector().getMyId();
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(ref.getBytes(StandardCharsets.UTF_8));
		md.update(challenge);
		byte[] digest = md.digest();

		cc.getResponseEnvelope()
			.setSourceId(cc.getConnector().getMyId())
			.setVersion(1)
			.setTargetId("")
			.setHeloServer(Hubcore.ServerHeloResponse.newBuilder()
				.setChallengeResponse(ByteString.copyFrom(digest))
				.setServerVersion(m_serverVersion)
				.build()
			);
		cc.respond();
	}

	@Synchronous
	public void handleCLAUTH(CommandContext cc) throws Exception {
		ClientAuthRequest clau = cc.getSourceEnvelope().getClientAuth();
		cc.log("Client authentication request from " + clau.getClientId());
		if(! m_authenticator.clientAuthenticated(clau.getClientId(), clau.getChallenge().toByteArray(), clau.getChallengeResponse().toByteArray(), clau.getClientVersion())) {
			cc.respondErrorPacket(ErrorCode.authenticationFailure, "");
			return;
		}

		//-- Respond with an AUTH packet.
		cc.getResponseEnvelope()
			.setCommand(CommandNames.AUTH_CMD)
			;
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

	public void addClientListener(IClientListener c) {
		m_clientListeners.add(c);
	}

	public void removeClientListener(IClientListener l) {
		m_clientListeners.remove(l);
	}

	private void callListeners(ConsumerEx<IClientListener> what) {
		for(IClientListener l : m_clientListeners) {
			try {
				what.accept(l);
			} catch(Exception x) {
				x.printStackTrace();
			}
		}
	}
}
