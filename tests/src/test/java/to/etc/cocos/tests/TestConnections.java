package to.etc.cocos.tests;

import org.junit.Assert;
import org.junit.Test;
import to.etc.cocos.connectors.ConnectorState;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
public class TestConnections extends TestAllBase {
	@Test
	public void testHubServerConnect() throws Exception {
		hub();
		Future<ConnectorState> fut = client().observeConnectionState()
			.filter(a -> a == ConnectorState.CONNECTED)
			.timeout(5, TimeUnit.SECONDS)
			.toFuture();
		;

		ConnectorState connectorState = fut.get();
		Assert.assertEquals("Connector must have gotten to connected status", ConnectorState.CONNECTED, connectorState);
	}






}
