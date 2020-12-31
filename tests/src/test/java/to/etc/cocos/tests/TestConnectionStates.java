package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.connectors.common.ConnectorState;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.cocos.hub.HubState;
import to.etc.cocos.tests.framework.TestAllBaseNew;
import to.etc.util.ConsoleUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;

@NonNullByDefault
public class TestConnectionStates extends TestAllBaseNew {

	@Test
	public void testClientConnectionStates() throws Exception {
		startHubSync();
		startServerSync();
		var expectation = createConditionSet(Duration.ofSeconds(5));
		var condition = expectation.createCondition("Expected states");

		var expectedOrder = new ArrayList<>(Arrays.asList(ConnectorState.CONNECTING, ConnectorState.CONNECTED, ConnectorState.AUTHENTICATED));
		getClient().addStateListener(state -> {
			if(condition.getState().isResolved()){
				return;
			}
			var nextState = expectedOrder.remove(0);
			if(nextState != state) {
				condition.failed("Expected " + nextState + " but got " + state);
			}
			if(expectedOrder.isEmpty()) {
				condition.resolved();
			}

		});
		getClient().start();
		expectation.await();
	}

	@Test
	public void testHubStates() throws Exception {
		var expectedStates = new ArrayList<>(Arrays.asList(HubState.STARTING, HubState.RUNNING));
		var expectation = createConditionSet(Duration.ofSeconds(5));
		var cond = expectation.createCondition("Expected states");
		getHub().addStateListener(hubState -> {
			if(cond.getState().isResolved()) {
				return;
			}
			var nextState = expectedStates.remove(0);
			if(nextState != hubState){
				cond.failed("Expected " + nextState + " but got " + hubState);
			}
			if(expectedStates.isEmpty()) {
				cond.resolved();
			}
		});
		getHub().startServer();
		expectation.await();
	}

	@Test
	public void testServerStates() throws Exception {
		startHubSync();
		var expectation = createConditionSet(Duration.ofSeconds(5));
		var condition = expectation.createCondition("Expected states");

		var expectedOrder = new ArrayList<>(Arrays.asList(ConnectorState.CONNECTING, ConnectorState.CONNECTED, ConnectorState.AUTHENTICATED));
		getServer().addStateListener(state -> {
			if(condition.getState().isResolved()){
				return;
			}
			var nextState = expectedOrder.remove(0);
			if(nextState != state) {
				condition.failed("Expected " + nextState + " but got " + state);
			}
			if(expectedOrder.isEmpty()) {
				condition.resolved();
			}
		});
		startServerSync();
		expectation.await();
	}

	@Test
	public void testHubServerAuthFail() throws Exception {
		startHubSync();
		startServerSync();
		setClientPassword("Badpassword");
		var set = createConditionSet(Duration.ofSeconds(5));
		var condition = set.createCondition("Connect");
		var expectedOrder = new ArrayList<>(Arrays.asList(ConnectorState.CONNECTING, ConnectorState.CONNECTED, ConnectorState.RECONNECT_WAIT));
		getClient().addStateListener(state -> {
			if(condition.getState().isResolved()){
				return;
			}
			var nextState = expectedOrder.remove(0);
			if(nextState != state) {
				condition.failed("Expected " + nextState + " but got " + state);
			}
			if(expectedOrder.isEmpty()) {
				condition.resolved();
			}

		});
		getClient().start();
		set.await();
	}

	@Test
	public void testHubInventoryAfterReconnect() throws Exception {
		startHubSync();
		startServerSync();
		startClientSync();
		//-- Now disconnect the server
		ConsoleUtil.consoleWarning("TEST", "Stopping server");
		disconnectServer();
		var set = createConditionSet(Duration.ofSeconds(5));
		expectServerEvent(set, ServerEventType.clientInventoryReceived, "Inventory received");
		getServer().start();
		set.await();
	}
}
