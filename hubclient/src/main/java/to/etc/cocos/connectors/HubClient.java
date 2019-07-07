package to.etc.cocos.connectors;

import io.reactivex.Observable;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.client.IClientPacketHandler;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ErrorResponse;

/**
 * Wrapper for a hub client, encapsulating the client responder and
 * the hubConnector.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 07-07-19.
 */
@NonNullByDefault
final public class HubClient {
	private final HubConnector m_connector;

	private final HubClientResponder m_responder;

	private HubClient(HubConnector connector, HubClientResponder responder) {
		m_connector = connector;
		m_responder = responder;
	}

	static public HubClient create(IClientPacketHandler handler, String hubServer, int hubServerPort, String targetClusterAndOrg, String myId, String myPassword) {
		HubClientResponder responder = new HubClientResponder(handler, myPassword, targetClusterAndOrg);
		HubConnector client = new HubConnector(hubServer, hubServerPort, targetClusterAndOrg, myId, responder, "Client");
		return new HubClient(client, responder);
	}

	public void start() {
		m_connector.start();
	}

	public void terminate() {
		m_connector.terminate();
	}

	public void terminateAndWait() throws Exception {
		m_connector.terminateAndWait();
	}

	public ConnectorState getState() {
		return m_connector.getState();
	}

	public Observable<ConnectorState> observeConnectionState() {
		return m_connector.observeConnectionState();
	}

	@Nullable public ErrorResponse getLastError() {
		return m_connector.getLastError();
	}
}
