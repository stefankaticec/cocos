package to.etc.cocos.connectors.client;

import to.etc.cocos.connectors.AbstractResponder;
import to.etc.cocos.connectors.BytePacket;
import to.etc.cocos.connectors.HubConnector;
import to.etc.cocos.connectors.IHubResponder;
import com.google.protobuf.ByteString;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-1-19.
 */
public class ClientResponder extends AbstractResponder implements IHubResponder {
	private final String m_clientVersion = "HubClient 1.0";

	public ClientResponder() {
	}

	@Override public void onHelloPacket(HubConnector connector, BytePacket input) throws Exception {
		Hubcore.HelloChallenge c = Hubcore.HelloChallenge.parseFrom(input.getRemainingInput());
		if(c.getVersion() != 1)
			throw new IllegalStateException("Cannot accept hub version " + c.getVersion());
		String sv = c.getServerVersion();
		System.out.println(">> connected to hub server " + sv);
		byte[] challenge = c.getChallenge().toByteArray();

		Hubcore.ClientHeloResponse response = Hubcore.ClientHeloResponse.newBuilder()
				.setChallengeResponse(ByteString.EMPTY)
				.setClientVersion(m_clientVersion)
				.setVersion(1)
				.build();
		connector.sendPacket(0x01, CommandNames.CLNT_CMD, response);
	}

	@Override public void onAuth(HubConnector connector, BytePacket input) throws Exception {

	}

	private byte[] encodeChallenge(byte[] challenge) {
		return challenge;
	}
}
