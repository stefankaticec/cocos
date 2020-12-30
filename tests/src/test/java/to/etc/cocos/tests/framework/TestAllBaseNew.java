package to.etc.cocos.tests.framework;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import to.etc.cocos.connectors.client.HubClient;
import to.etc.cocos.connectors.client.IClientAuthenticationHandler;
import to.etc.cocos.connectors.common.ConnectorState;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.IClientAuthenticator;
import to.etc.cocos.connectors.server.HubServer;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.cocos.hub.Hub;
import to.etc.cocos.hub.HubState;
import to.etc.cocos.tests.InventoryTestPacket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

import static java.util.Objects.requireNonNull;

@NonNullByDefault
public class TestAllBaseNew {
	public static final int HUBPORT = 9890;

	public static final String CLUSTERNAME = "junit";

	public static final String SERVERNAME = "rmtserver";

	public static final String CLIENTID = "testDaemon";

	public static final String CLIENTPASSWORD = "tokodoko";

	public static final String CLUSTERPASSWORD = "inujit";


	@Nullable
	private Hub m_hub;

	@Nullable
	private HubServer m_server;

	@Nullable
	private HubClient m_client;

	@Nullable
	private String m_serverPassword;

	@Nullable
	private String m_clientPassword;

	private String m_allClientPassword = CLIENTPASSWORD;


	@Before
	public void setup() throws Exception {
		m_hub = new Hub(HUBPORT, "testHUB", false, a -> CLUSTERPASSWORD, null, Collections.emptyList(), false);
		m_server = createServer();
		m_client = createClient();
	}

	@After
	public void tearDown() throws Exception {
		var closeSet = createConditionSet(Duration.ofSeconds(10));

		var hub = m_hub;
		if(hub != null){
			var hubStoppedCondition = closeSet.createCondition("Hub stopped");
			hub.addStateListener(state-> {
				if(state == HubState.STOPPED) {
					hubStoppedCondition.resolved();
				}
			});
			hub.terminateAndWait();
		}
		var server = m_server;
		if(server != null) {
			var serverStoppedCondition = closeSet.createCondition("Server stopped");
			server.addStateListener(state-> {
				if(state == ConnectorState.STOPPED) {
					serverStoppedCondition.resolved();
				}
			});
			server.terminateAndWait();
		}
		var client = m_client;
		if(client != null) {
			var clientStoppedCondition = closeSet.createCondition("Client stopped");
			client.addStateListener(state -> {
				if(state == ConnectorState.STOPPED) {
					clientStoppedCondition.resolved();
				}
			});
			client.terminateAndWait();
		}

		closeSet.await();
	}

	public TestConditionSet createAllConnectedSet() {
		var set = createConditionSet(Duration.ofSeconds(5));
		var hubUpCondition = set.createCondition("Hub is up");
		getHub().addStateListener(state -> {
			if(state == HubState.RUNNING) {
				hubUpCondition.resolved();
			}
		});
		expectServerState(set, ConnectorState.AUTHENTICATED, "Server is up");
		expectClientState(set, ConnectorState.AUTHENTICATED, "Client is up");
		expectServerEvent(set, ServerEventType.peerRestarted, "PeerRestartedEvent");
		return set;
	}

	/**
	 * this starts a huh, a server and a client. They race to connect.
	 * @return
	 * @throws Exception
	 */
	public TestAllBaseNew startAndWaitConnected() throws Exception {
		var set = createAllConnectedSet();
		startAll();
		set.await();
		return this;
	}

	public TestAllBaseNew startAll() throws Exception {
		getHub().startServer();
		getServer().start();
		getClient().start();
		return this;
	}

	@NonNull
	public Hub getHub() {
		return requireNonNull(m_hub, "Hub not connected!");
	}

	@NonNull
	public HubServer getServer() {
		return requireNonNull(m_server, "Server not connected!");
	}

	@NonNull
	public HubClient getClient() {
		return requireNonNull(m_client, "Client not connected!");
	}

	public TestConditionSet createConditionSet(Duration duration) {
		return new TestConditionSet(duration);
	}


	private HubServer createServer() {
		String id = SERVERNAME + "@" + CLUSTERNAME;
		IClientAuthenticator au = new IClientAuthenticator() {
			@Override public boolean clientAuthenticated(String clientId, byte[] challenge, byte[] challengeResponse, String clientVersion) throws Exception {
				return authenticateClient(clientId, challenge, challengeResponse);
			}
		};
		String pw = m_serverPassword != null ? m_serverPassword : CLUSTERPASSWORD;
		return HubServer.create(au, "localhost", HUBPORT, pw, id);
	}

	private boolean authenticateClient(String clientId, byte[] challenge, byte[] response) throws Exception {
		String ref = m_allClientPassword + ":" + clientId;
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(ref.getBytes(StandardCharsets.UTF_8));
		md.update(challenge);
		byte[] digest = md.digest();
		return Arrays.equals(digest, response);

	}

	private HubClient createClient() {
		String password = m_clientPassword == null ? CLIENTPASSWORD : m_clientPassword;
		IClientAuthenticationHandler ph = new IClientAuthenticationHandler() {
			@Override public JsonPacket getInventory() {
				return new InventoryTestPacket();
			}

			@Override
			public byte[] createAuthenticationResponse(String clientId, byte[] challenge) throws Exception {
				String ref = password + ":" + clientId;
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				md.update(ref.getBytes(StandardCharsets.UTF_8));
				md.update(challenge);
				byte[] digest = md.digest();
				return digest;
			}
		};

		return HubClient.create(ph, "localhost", HUBPORT, CLUSTERNAME, CLIENTID);
	}

	public void disconnectClient() throws Exception {
		var set = createConditionSet(Duration.ofSeconds(10));
		expectClientState(set, ConnectorState.STOPPED, "Client disconnected");
		getClient().terminateAndWait();
		set.await();
		m_client = null;
	}

	public void connectClient(TestConditionSet set) throws Exception {
		m_client = createClient();
		var client = m_client = createClient();
		expectServerEvent(set, ServerEventType.peerRestarted, "Client connected");
		client.start();
	}

	public TestConditionSet expectServerEvent(Duration duration, ServerEventType type, String name) throws Exception {
		var set = createConditionSet(duration);
		return expectServerEvent(set, type, name);
	}

	public TestConditionSet expectServerEvent(TestConditionSet set, ServerEventType type, String name){
		var condition = set.createCondition(name);
		getServer().addServerEventListener(event -> {
			if(event.getType() == type) {
				condition.resolved();
			}
		});
		return set;
	}

	public TestConditionSet expectServerState(TestConditionSet set, ConnectorState state, String name) {
		var condition = set.createCondition(name);
		getServer().addStateListener(s -> {
			if(state == s) {
				condition.resolved();
			}
		});
		return set;
	}
	public TestConditionSet expectClientState(TestConditionSet set, ConnectorState expectedState, String name) {
		var co = set.createCondition(name);
		getClient().addStateListener(state-> {
			if(state == expectedState) {
				co.resolved();
			}
		});
		return set;
	}

}
