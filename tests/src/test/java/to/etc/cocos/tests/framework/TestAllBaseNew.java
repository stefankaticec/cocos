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

	@NonNull
	private Hub createHub() throws Exception {
		return new Hub(HUBPORT, "testHUB", false, a -> CLUSTERPASSWORD, null, Collections.emptyList(), false);
	}

	@After
	public void tearDown() throws Exception {
		var closeSet = createConditionSet(Duration.ofSeconds(10));

		var hub = m_hub;
		if(hub != null){
			expectHubState(closeSet, HubState.STOPPED, "Hub stopped");
			hub.terminateAndWait();
			m_hub = null;
		}

		var server = m_server;
		if(server != null) {
			expectServerState(closeSet, ConnectorState.STOPPED, "Server stopped");
			server.terminateAndWait();
			m_server = null;
		}

		var client = m_client;
		if(client != null) {
			expectClientState(closeSet, ConnectorState.STOPPED, "Client stopped");
			client.terminateAndWait();
			m_client = null;
		}

		closeSet.await();
	}

	public TestConditionSet createAllConnectedSet() throws Exception {
		var set = createConditionSet(Duration.ofSeconds(5));
		expectHubState(set, HubState.RUNNING, "Hub is up");
		expectServerState(set, ConnectorState.AUTHENTICATED, "Server is up");
		expectClientState(set, ConnectorState.AUTHENTICATED, "Client is up");
		expectPeerRestarted(set);

		return set;
	}

	/**
	 * this starts a hub, a server and a client. They race to connect to each other
	 * @return
	 * @throws Exception
	 */
	public TestAllBaseNew startAndWaitConnected() throws Exception {
		var set = createAllConnectedSet();
		startAll();
		set.await();

		return this;
	}

	/**
	 * This starts a hub, then a server, then a client in order. One operation waits for the other.
	 * @return this
	 * @throws Exception
	 */
	public TestAllBaseNew startAndAwaitSequential() throws Exception {
		startHubSync();
		startServerSync();
		startClientSync();

		return this;
	}

	public TestAllBaseNew startAll() throws Exception {
		getHub().startServer();
		getServer().start();
		getClient().start();

		return this;
	}

	@NonNull
	public Hub getHub() throws Exception{
		var hub = m_hub;
		if(hub == null) {
			hub = m_hub = createHub();
		}
		return hub;
	}

	@NonNull
	public HubServer getServer() {
		var server = m_server;
		if(server == null) {
			server = m_server = createServer();
		}
		return server;
	}

	@NonNull
	public HubClient getClient() {
		var client = m_client;
		if(client == null) {
			client = m_client = createClient();
		}
		return client;
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
	public void disconnectServer() throws Exception {
		var set = createConditionSet(Duration.ofSeconds(10));
		expectServerState(set, ConnectorState.STOPPED, "Server disconnected");
		getServer().terminateAndWait();
		set.await();
		m_server = null;
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

	public TestConditionSet expectServerState(Duration duration, ConnectorState state, String name) {
		var set = createConditionSet(duration);
		return expectServerState(set, state, name);
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
	public TestConditionSet expectHubState(TestConditionSet set, HubState expectedState, String name) throws Exception {
		var condition = set.createCondition(name);
		getHub().addStateListener(state->{
			if(state == expectedState) {
				condition.resolved();
			}
		});
		return set;
	}

	public Hub startHubSync() throws Exception {
		var set = createConditionSet(Duration.ofSeconds(5));
		expectHubState(set, HubState.RUNNING, "Hub started");
		getHub().startServer();
		set.await();
		return getHub();
	}

	public HubServer startServerSync() throws Exception {
		var set = createConditionSet(Duration.ofSeconds(5));
		expectServerState(set, ConnectorState.AUTHENTICATED, "Server started");
		getServer().start();
		set.await();
		return getServer();
	}

	public HubClient startClientSync() throws Exception {
		var set = createConditionSet(Duration.ofSeconds(5));
		expectPeerRestarted(set);
		expectClientState(set, ConnectorState.AUTHENTICATED, "Client started");
		getClient().start();
		set.await();
		return getClient();
	}

	private void expectPeerRestarted(TestConditionSet set) {
		expectServerEvent(set, ServerEventType.peerRestarted, "Peer restarted");
	}

	public void setServerPassword(@Nullable String serverPassword) {
		m_serverPassword = serverPassword;
	}

	public void setClientPassword(@Nullable String clientPassword) {
		m_clientPassword = clientPassword;
	}
}
