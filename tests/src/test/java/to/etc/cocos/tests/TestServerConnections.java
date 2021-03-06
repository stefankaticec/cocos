package to.etc.cocos.tests;

import org.junit.Assert;
import org.junit.Test;
import to.etc.cocos.connectors.common.ConnectorState;
import to.etc.cocos.messages.Hubcore.HubErrorResponse;
import to.etc.hubserver.protocol.ErrorCode;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
public class TestServerConnections extends TestAllBase {

	/**
	 * The server should reach AUTH state.
	 */
	@Test
	public void testHubServerConnect() throws Exception {
		hub();
		ConnectorState connectorState = server().observeConnectionState()
			.doOnNext(a -> System.out.println(">> got state " + a))
			.filter(a -> a == ConnectorState.AUTHENTICATED)
			.timeout(5000, TimeUnit.SECONDS)
			.blockingFirst();

		Assert.assertEquals("Connector must have gotten to connected status", ConnectorState.AUTHENTICATED, connectorState);
	}

	@Test
	public void testHubServerIncorrectAuth() throws Exception {
		hub();
		setServerPassword("invalid_pass");
		ConnectorState connectorState = server().observeConnectionState()
			.doOnNext(a -> System.out.println(">> got state " + a))
			.filter(a -> a == ConnectorState.RECONNECT_WAIT)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();
		Assert.assertEquals("Connector must have gotten to RECONNECT_WAIT status", ConnectorState.RECONNECT_WAIT, connectorState);
		HubErrorResponse lastError = server().getLastError();
		Assert.assertNotNull("There must be a HUB error that is returned", lastError);
		Assert.assertNotNull("The hub error must have code " + ErrorCode.authenticationFailure.name(), lastError.getCode());
	}

}
