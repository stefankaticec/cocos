package to.etc.cocos.connectors;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.server.IClientAuthenticator;
import to.etc.cocos.connectors.server.IClientListener;
import to.etc.cocos.connectors.server.IServerEvent;
import to.etc.cocos.connectors.server.ServerEventBase;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ErrorResponse;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 07-07-19.
 */
@NonNullByDefault
final public class HubServer {
	private final HubConnector m_connector;

	private final HubServerResponder m_responder;

	private final PublishSubject<IServerEvent> m_serverEventSubject;

	private HubServer(HubConnector connector, HubServerResponder responder) {
		m_connector = connector;
		m_responder = responder;
		m_serverEventSubject = PublishSubject.create();
	}

	static public HubServer create(IClientAuthenticator au, String hubServer, int hubServerPort, String hubPassword, String id) {
		if(id.indexOf('@') == -1)
			throw new IllegalArgumentException("The server ID must be in the format servername@clustername");

		HubServerResponder responder = new HubServerResponder(hubPassword, au);
		HubConnector server = new HubConnector(hubServer, hubServerPort, "", id, responder, "Server");

		HubServer hs = new HubServer(server, responder);

		responder.addClientListener(new IClientListener() {
			@Override public void clientConnected(RemoteClient client) throws Exception {
				hs.m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.clientConnected, client));
			}

			@Override public void clientDisconnected(RemoteClient client) throws Exception {
				hs.m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.clientDisconnected, client));
			}

			@Override public void clientInventoryPacketReceived(RemoteClient client, JsonPacket packet) {
				hs.m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.clientInventoryReceived, client));
			}
		});
		server.observeConnectionState()
			.subscribe(connectorState -> {
				switch(connectorState) {
					default:
						return;

					case AUTHENTICATED:
						hs.m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.serverConnected));
						break;

					case RECONNECT_WAIT:
					case STOPPED:
						hs.m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.serverDisconnected));
						break;
				}
			});

		return hs;
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

	public void addClientListener(IClientListener c) {
		m_responder.addClientListener(c);
	}

	public void removeClientListener(IClientListener l) {
		m_responder.removeClientListener(l);
	}

	public Observable<IServerEvent> observeServerEvents() {
		return m_serverEventSubject;
	}
}

