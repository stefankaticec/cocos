package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.connectors.common.ConnectorState;
import to.etc.cocos.tests.framework.TestAllBaseNew;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;

@NonNullByDefault
public class TestClientConnectionStates extends TestAllBaseNew {

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
}
