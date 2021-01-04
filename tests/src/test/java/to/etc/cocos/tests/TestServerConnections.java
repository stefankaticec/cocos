package to.etc.cocos.tests;

import org.junit.Assert;
import org.junit.Test;
import to.etc.cocos.connectors.common.ConnectorState;
import to.etc.cocos.messages.Hubcore.HubErrorResponse;
import to.etc.cocos.tests.framework.TestAllBaseNew;
import to.etc.hubserver.protocol.ErrorCode;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
public class TestServerConnections extends TestAllBaseNew {

	/**
	 * The server should reach AUTH state.
	 */
	@Test
	public void testHubServerConnect() throws Exception {
		startHubSync();
		var set = expectServerState(ConnectorState.AUTHENTICATED, "Authenticated");
		getServer().start();
		set.await(DEFAULT_TIMEOUT);
	}

	@Test
	public void testHubServerIncorrectAuth() throws Exception {
		startHubSync();
		setServerPassword("invalid_pass");
		var set = createConditionSet();
		var condition = set.createCondition("Expect reconnect wait");
		var expectedOrder = new ArrayList<>(Arrays.asList(ConnectorState.CONNECTING, ConnectorState.CONNECTED, ConnectorState.RECONNECT_WAIT));
		getServer().addStateListener(state -> {
			if(condition.isResolved()) {
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
		getServer().start();
		set.await(DEFAULT_TIMEOUT);
		HubErrorResponse lastError = getServer().getLastError();
		Assert.assertNotNull("There must be a HUB error that is returned", lastError);
		Assert.assertNotNull("The hub error must have code " + ErrorCode.authenticationFailure.name(), lastError.getCode());
	}

}
