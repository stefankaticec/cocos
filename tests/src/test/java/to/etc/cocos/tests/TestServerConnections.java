package to.etc.cocos.tests;

import org.junit.Assert;
import org.junit.Test;
import to.etc.cocos.connectors.ConnectorState;

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
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();

		Assert.assertEquals("Connector must have gotten to connected status", ConnectorState.AUTHENTICATED, connectorState);
	}


}
