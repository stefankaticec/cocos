package to.etc.cocos.tests;

import org.junit.Assert;
import org.junit.Test;
import to.etc.cocos.connectors.ConnectorState;
import to.etc.cocos.connectors.server.IRemoteClient;
import to.etc.cocos.connectors.server.IServerEvent;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ErrorResponse;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
public class TestClientConnections extends TestAllBase {
	@Test
	public void testHubServerConnect() throws Exception {
		hub();
		serverConnected();
		ConnectorState connectorState = client().observeConnectionState()
			.doOnNext(a -> System.out.println(">> got state " + a))
			.filter(a -> a == ConnectorState.AUTHENTICATED)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();

		Assert.assertEquals("Connector must have gotten to connected status", ConnectorState.AUTHENTICATED, connectorState);
	}

	@Test
	public void testHubClientEvent() throws Exception {
		hub();
		serverConnected();
		client();
		IServerEvent event = server().observeServerEvents()
			.doOnNext(a -> System.out.println(">> got event " + a.getType()))
			.filter(a -> a.getType() == ServerEventType.clientConnected)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();


		IRemoteClient client = event.getClient();
		Assert.assertNotNull(client);
		Assert.assertEquals("Connector must have gotten client connected event", CLIENTID, client.getClientKey());
	}

	@Test
	public void testHubInventory() throws Exception {
		hub();
		serverConnected();
		client();
		IServerEvent event = server().observeServerEvents()
			.doOnNext(a -> System.out.println(">> got event " + a.getType()))
			.filter(a -> a.getType() == ServerEventType.clientInventoryReceived)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();


		IRemoteClient client = event.getClient();
		Assert.assertNotNull(client);
		Assert.assertEquals("Connector must have gotten client inventory event", CLIENTID, client.getClientKey());
	}

	/**
	 * Connect to the hub with an incorrect password, and check that we indeed
	 * get an error message saying so.
	 */
	@Test
	public void testHubServerAuthFail() throws Exception {
		hub();
		serverConnected();
		setClientPassword("Badpassword");
		ConnectorState connectorState = client().observeConnectionState()
			.doOnNext(a -> System.out.println(">> got state " + a))
			.filter(a -> a == ConnectorState.RECONNECT_WAIT)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();

		Assert.assertEquals("Connector must have gotten to disconnected status", ConnectorState.RECONNECT_WAIT, connectorState);
		ErrorResponse lastError = client().getLastError();
		Assert.assertNotNull("There must be a HUB error that is returned", lastError);
		Assert.assertNotNull("The hub error must have code " + ErrorCode.authenticationFailure.name(), lastError.getCode());
	}
}
