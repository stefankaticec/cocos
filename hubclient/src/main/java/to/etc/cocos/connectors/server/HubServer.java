package to.etc.cocos.connectors.server;

import com.google.protobuf.ByteString;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.HubConnectorBase;
import to.etc.cocos.connectors.common.ISendPacket;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.common.PacketWriter;
import to.etc.cocos.connectors.common.ProtocolViolationException;
import to.etc.cocos.connectors.common.Synchronous;
import to.etc.cocos.connectors.ifaces.IClientAuthenticator;
import to.etc.cocos.connectors.ifaces.IDaemonCommand;
import to.etc.cocos.connectors.ifaces.IRemoteClient;
import to.etc.cocos.connectors.ifaces.IRemoteClientHub;
import to.etc.cocos.connectors.ifaces.IRemoteClientListener;
import to.etc.cocos.connectors.ifaces.IServerEvent;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.AuthResponse;
import to.etc.cocos.messages.Hubcore.ClientAuthRequest;
import to.etc.cocos.messages.Hubcore.CommandError;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.function.ConsumerEx;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
@NonNullByDefault
final public class HubServer extends HubConnectorBase implements IRemoteClientHub {
	private final String m_serverVersion = "1.0";

	private final String m_clusterPassword;

	private final IClientAuthenticator m_authenticator;

	private CopyOnWriteArrayList<IRemoteClientListener> m_clientListeners = new CopyOnWriteArrayList<>();

	private Map<String, RemoteClient> m_remoteClientMap = new HashMap<>();

	private final PublishSubject<IServerEvent> m_serverEventSubject;

	private final Map<String, RemoteCommand> m_commandMap = new HashMap<>();

	private final Map<String, RemoteCommand> m_commandByKeyMap = new HashMap<>();

	private HubServer(String hubServer, int hubServerPort, String clusterPassword, IClientAuthenticator authenticator, String id) {
		super(hubServer, hubServerPort, "", id, "Server");
		m_clusterPassword = clusterPassword;
		m_authenticator = authenticator;
		m_serverEventSubject = PublishSubject.create();

		addClientListener(new IRemoteClientListener() {
			@Override public void clientConnected(IRemoteClient client) throws Exception {
				m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.clientConnected, client));
			}

			@Override public void clientDisconnected(IRemoteClient client) throws Exception {
				m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.clientDisconnected, client));
			}

			@Override public void clientInventoryPacketReceived(RemoteClient client, JsonPacket packet) {
				m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.clientInventoryReceived, client));
			}
		});
		observeConnectionState()
			.subscribe(connectorState -> {
				switch(connectorState) {
					default:
						return;

					case AUTHENTICATED:
						m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.serverConnected));
						break;

					case RECONNECT_WAIT:
					case STOPPED:
						m_serverEventSubject.onNext(new ServerEventBase(ServerEventType.serverDisconnected));
						break;
				}
			});
	}

	static public HubServer create(IClientAuthenticator au, String hubServer, int hubServerPort, String hubPassword, String id) {
		if(id.indexOf('@') == -1)
			throw new IllegalArgumentException("The server ID must be in the format servername@clustername");

		HubServer responder = new HubServer(hubServer, hubServerPort, hubPassword, au, id);
		return responder;
	}

	public Observable<IServerEvent> observeServerEvents() {
		return m_serverEventSubject;
	}

	@Override protected void onErrorPacket(Envelope env) {
		// IMPLEMENT
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

			case CLIENTAUTH:
				handleCLAUTH(ctx);
				break;

			case CLIENTCONNECTED:
				handleCLCONN(ctx);
				break;

			case CLIENTDISCONNECTED:
				handleCLDISC(ctx);
				break;

			case INVENTORY:
				handleCLINVE(ctx, data);
				break;

			case COMMANDERROR:
				handleCommandError(ctx);
				break;
		}
	}

	private void handleCommandError(CommandContext ctx) {
		CommandError err = ctx.getSourceEnvelope().getCommandError();
		ctx.log("Client " + ctx.getSourceEnvelope().getSourceId() + " command error: " + err.getCode() + " " + err.getMessage());
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Authentication.												*/
	/*----------------------------------------------------------------------*/

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

	/**
	 * If the server's authorization was successful we receive this; move to AUTHORIZED status.
	 */
	@Synchronous
	public void handleAUTH(CommandContext cc) throws Exception {
		cc.getConnector().authorized();
		cc.log("Authenticated successfully");
	}

	/**
	 * Client authentication request.
	 */
	@Synchronous
	public void handleCLAUTH(CommandContext cc) throws Exception {
		ClientAuthRequest clau = cc.getSourceEnvelope().getClientAuth();
		cc.log("Client authentication request from " + clau.getClientId());
		if(! m_authenticator.clientAuthenticated(clau.getClientId(), clau.getChallenge().toByteArray(), clau.getChallengeResponse().toByteArray(), clau.getClientVersion())) {
			cc.respondWithHubErrorPacket(ErrorCode.authenticationFailure, "");
			return;
		}

		//-- Respond with an AUTH packet.
		cc.getResponseEnvelope()
			.setAuth(AuthResponse.newBuilder().build())
			;
		cc.respond();
	}

	/**
	 * Client connected event. Add the client, then start sending events.
	 */
	@Synchronous
	public void handleCLCONN(CommandContext cc) throws Exception {
		String id = cc.getSourceEnvelope().getSourceId();
		synchronized(this) {
			RemoteClient rc = m_remoteClientMap.computeIfAbsent(id, a -> new RemoteClient(this, id));
			cc.getConnector().getEventExecutor().execute(() -> callListeners(a -> a.clientConnected(rc)));
		}
		cc.log("Client (re)connected: " + id);
	}

	/**
	 * Client disconnected event. Remove the client, then start sending events.
	 */
	@Synchronous
	public void handleCLDISC(CommandContext cc) throws Exception {
		String id = cc.getSourceEnvelope().getSourceId();
		synchronized(this) {
			RemoteClient rc = m_remoteClientMap.remove(id);
			if(null == rc) {
				cc.error("Unexpected disconnected event for unknown client " + id);
				return;
			}
			cc.getConnector().getEventExecutor().execute(() -> callListeners(a -> a.clientDisconnected(rc)));
		}
		cc.log("Client disconnected: " + id);
	}

	/**
	 * Client Inventory: a client has updated its inventory.
	 */
	@Synchronous
	public void handleCLINVE(CommandContext cc, List<byte[]> data) throws Exception {
		String dataFormat = cc.getSourceEnvelope().getInventory().getDataFormat();
		if(! CommandNames.isJsonDataFormat(dataFormat))
			throw new ProtocolViolationException("Inventory packet must be in JSON format");
		Object o = decodeBody(dataFormat, data);
		if(! (o instanceof JsonPacket))
			throw new ProtocolViolationException("Inventory packet does not extend JsonPacket");
		JsonPacket packet = (JsonPacket) o;

		cc.log("Got client inventory packet " + packet);
		String id = cc.getSourceEnvelope().getSourceId();
		synchronized(this) {
			RemoteClient rc = m_remoteClientMap.get(id);
			if(null == rc) {
				cc.error("Unexpected client inventory event for unknown client " + id);
				return;
			}
			rc.inventoryReceived(packet);
			cc.getConnector().getEventExecutor().execute(() -> callListeners(a -> a.clientInventoryPacketReceived(rc, packet)));
		}
	}

	public void addClientListener(IRemoteClientListener c) {
		m_clientListeners.add(c);
	}

	public void removeClientListener(IRemoteClientListener l) {
		m_clientListeners.remove(l);
	}

	private void callListeners(ConsumerEx<IRemoteClientListener> what) {
		for(IRemoteClientListener l : m_clientListeners) {
			try {
				what.accept(l);
			} catch(Exception x) {
				x.printStackTrace();
			}
		}
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Interface.													*/
	/*----------------------------------------------------------------------*/

	@Override
	public synchronized List<IRemoteClient> getClientList() {
		return new ArrayList<>(m_remoteClientMap.values());
	}

	@Override
	@Nullable
	public synchronized RemoteClient findClient(String clientId) {
		return m_remoteClientMap.get(clientId);
	}

	@Override
	public List<String> getClientIdList() {
		return getClientList().stream()
			.map(a -> a.getClientID())
			.collect(Collectors.toList());
	}

	public void sendJsonCommand(RemoteCommand command, JsonPacket packet) {
		synchronized(this) {
			m_commandMap.put(command.getCommandId(), command);
		}

		Envelope jcmd = Envelope.newBuilder()
			.setSourceId(getMyId())
			.setTargetId(command.getClientKey())
			.setVersion(1)
			.setCmd(Hubcore.Command.newBuilder()
				.setDataFormat(CommandNames.getJsonDataFormat(packet))
				.setId(command.getCommandId())
				.setName(packet.getClass().getName())
			)
			.build();
		sendPacket(new ISendPacket() {
			@Override public void send(PacketWriter os) throws Exception {
				os.send(jcmd, packet);
			}
		});
	}


	@Override
	public void close() throws Exception {
		terminateAndWait();
	}

	@Override
	public void addListener(IRemoteClientListener l) {

	}

	@Override
	public void removeListener(IRemoteClientListener l) {

	}

	@Nullable
	@Override
	public IDaemonCommand findCommand(String code) {
		return null;
	}

	@Nullable
	@Override
	public IDaemonCommand findCommand(String clientId, String commandKey) {
		return null;
	}

}
