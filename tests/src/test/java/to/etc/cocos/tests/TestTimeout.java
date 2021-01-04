package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.server.HubServer;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.cocos.tests.framework.TestAllBaseNew;
import to.etc.util.StringTool;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@NonNullByDefault
public class TestTimeout extends TestAllBaseNew {

	@Test
	public void testTimeout() throws Exception {
		HubServer.testOnly_setDelayPeriodAndInterval(0, 300, TimeUnit.MILLISECONDS);

		getClient().registerJsonCommand(StdoutCommandTestPacket.class, () -> (ctx, packet) -> {
			synchronized(this) {
				wait(300);
			}
			return new JsonPacket();
		});
		startAllAndAwaitSequential();
		var client = getServer().getClientList().get(0);

		StdoutCommandTestPacket p = new StdoutCommandTestPacket();
		p.setParameters("Real command");

		var cmd = client.sendJsonCommand(StringTool.generateGUID(), p, Duration.ofMillis(50), null, "Test command", null);
		var set = expectServerEvent(ServerEventType.cancelFinished, "Command cancelled");
		set.await(DEFAULT_TIMEOUT);					// was 15 seconds

		assertEquals(RemoteCommandStatus.CANCELED, cmd.getStatus());
	}
}
