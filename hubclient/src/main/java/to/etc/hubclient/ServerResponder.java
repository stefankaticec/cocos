package to.etc.hubclient;

import com.google.protobuf.ByteString;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;

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

	@Override public void onHelloPacket(HubConnector connector, BytePacket input) throws Exception {
		Hubcore.HelloChallenge c = Hubcore.HelloChallenge.parseFrom(input.getRemainingInput());
		if(c.getVersion() != 1)
			throw new IllegalStateException("Cannot accept hub version " + c.getVersion());
		String sv = c.getServerVersion();
		System.out.println(">> connected to hub server " + sv);

		Hubcore.ServerHeloResponse r = Hubcore.ServerHeloResponse.newBuilder()
				.setVersion(1)
				.setServerVersion(m_serverVersion)
				.setChallengeResponse(ByteString.EMPTY)
				.build();
		connector.sendPacket(0x01, CommandNames.SRVR_CMD, r);
	}


	@Override public void onAuth(HubConnector connector, BytePacket input) throws Exception {

	}

	/**
	 * CLIENT wants a login. Check his authentication, then send back an AUTH packet if accepted or an error
	 * packet if not.
	 */
	public void handleCLNT(HubConnector hc, BytePacket packet, Hubcore.ClientHeloResponse r) throws Exception {
		byte[] response = r.getChallengeResponse().toByteArray();

		//-- IMPLEMENT check


		//-- Send back AUTH
		PacketBuilder b = hc.allocatePacketBuilder(0x01, packet.getSourceID(), m_serverId, CommandNames.AUTH_CMD);
		hc.sendPacket(b);
	}
}
