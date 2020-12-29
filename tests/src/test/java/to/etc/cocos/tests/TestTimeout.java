package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.connectors.common.ConnectorState;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.server.HubServer;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.util.StringTool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@NonNullByDefault
public class TestTimeout extends TestAllBase {

	@Test
	public void testOrderOfClientStates() throws Exception {
		hub();

		List<ConnectorState> order = new ArrayList<>(List.of(ConnectorState.CONNECTING, ConnectorState.CONNECTED, ConnectorState.AUTHENTICATED));
		serverConnected();
		var state = client().observeConnectionState()			// This is a race always.
			.doOnNext(connectorState -> System.out.println("> got " + connectorState))
			.map(a -> {
				if(order.size() == 0)
					return true;
				if(order.get(0) == a) {
					order.remove(0);
				}
				return order.size() == 0;
			})
			.filter(a -> a)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();
	}

	@Test
	public void testTimeout() throws Exception {
		HubServer.testOnly_setDelayPeriodAndInterval(0, 300, TimeUnit.MILLISECONDS);
		waitConnected();
		client().registerJsonCommand(StdoutCommandTestPacket.class, () -> (ctx, packet) -> {
			synchronized(this) {
				wait(300);
			}
			return new JsonPacket();
		});
		var client = server().getClientList().get(0);

		StdoutCommandTestPacket p = new StdoutCommandTestPacket();
		p.setParameters("Real command");

		var cmd = client.sendJsonCommand(StringTool.generateGUID(), p, Duration.ofMillis(50), null, "Test command", null);
		var cancellingCommand = server().observeServerEvents()
			.filter(x->x.getType() == ServerEventType.cancelFinished)
			.timeout(15, TimeUnit.SECONDS)
			.blockingFirst();

		assertEquals(RemoteCommandStatus.CANCELED, cmd.getStatus());
	}
}
