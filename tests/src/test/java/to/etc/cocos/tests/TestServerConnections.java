package to.etc.cocos.tests;

import org.junit.Test;
import to.etc.cocos.connectors.ConnectorState;

import java.util.concurrent.Future;
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
		Future<ConnectorState> fut = server().observeConnectionState()
			.doOnNext(a -> System.out.println(">> got state " + a))
			.filter(a -> a == ConnectorState.CONNECTED)
			.timeout(5, TimeUnit.SECONDS)
			.toFuture();
		;

		Thread.sleep(15_000);
		//ConnectorState connectorState = fut.get();
		//Assert.assertEquals("Connector must have gotten to connected status", ConnectorState.CONNECTED, connectorState);
	}


}
