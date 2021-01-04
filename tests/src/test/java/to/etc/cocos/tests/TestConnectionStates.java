package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.connectors.common.ConnectorState;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.cocos.hub.HubState;
import to.etc.cocos.tests.framework.TestAllBaseNew;
import to.etc.util.ConsoleUtil;

@NonNullByDefault
public class TestConnectionStates extends TestAllBaseNew {

	@Test
	public void testClientConnectionStates() throws Exception {
		startHubSync();
		startServerSync();
		var expectation = createConditionSet();
		expectation.createOrderedConditionValue(ConnectorState.CONNECTING);
		expectation.createOrderedConditionValue(ConnectorState.CONNECTED);
		expectation.createOrderedConditionValue(ConnectorState.AUTHENTICATED);
		getClient().addStateListener(expectation::gotValue);
		getClient().start();
		expectation.await(DEFAULT_TIMEOUT);
	}

	@Test
	public void testHubStates() throws Exception {
		var expectation = createConditionSet();
		expectation.createOrderedConditionValue(HubState.STARTING);
		expectation.createOrderedConditionValue(HubState.STARTED);

		getHub().addStateListener(hubState -> {
			expectation.gotValue(hubState);
		});
		getHub().startServer();
		expectation.await(DEFAULT_TIMEOUT);
	}

	@Test
	public void testServerStates() throws Exception {
		startHubSync();
		var expectation = createConditionSet();

		expectation.createOrderedConditionValue(ConnectorState.CONNECTING);
		expectation.createOrderedConditionValue(ConnectorState.CONNECTED);
		expectation.createOrderedConditionValue(ConnectorState.AUTHENTICATED);
		getServer().addStateListener(state -> expectation.gotValue(state));
		startServerSync();
		expectation.await(DEFAULT_TIMEOUT);
	}

	@Test
	public void testHubServerAuthFail() throws Exception {
		startHubSync();
		startServerSync();
		setClientPassword("Badpassword");
		var expectation = createConditionSet();
		expectation.createOrderedConditionValue(ConnectorState.CONNECTING);
		expectation.createOrderedConditionValue(ConnectorState.CONNECTED);
		expectation.createOrderedConditionValue(ConnectorState.RECONNECT_WAIT);
		getClient().addStateListener(state -> expectation.gotValue(state));
		getClient().start();
		expectation.await(DEFAULT_TIMEOUT);
	}

	@Test
	public void testHubInventoryAfterReconnect() throws Exception {
		startHubSync();
		startServerSync();
		startClientSync();
		//-- Now disconnect the server
		ConsoleUtil.consoleWarning("TEST", "Stopping server");
		disconnectServer();
		var set = createConditionSet();
		expectServerEvent(set, ServerEventType.clientInventoryReceived, "Inventory received");
		getServer().start();
		set.await(DEFAULT_TIMEOUT);
	}
}
