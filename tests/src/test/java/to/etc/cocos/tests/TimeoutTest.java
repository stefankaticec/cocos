package to.etc.cocos.tests;

import io.reactivex.rxjava3.subjects.PublishSubject;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.connectors.common.ConnectorState;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.EvCommandError;
import to.etc.cocos.connectors.ifaces.IRemoteCommandListener;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.server.HubServer;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.util.StringTool;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;

@NonNullByDefault
public class TimeoutTest extends TestAllBase {

	@Test
	public void testOrderOfClientStates() throws Exception {
		hub();
		serverConnected();
		var state = client().observeConnectionState()
			.timeout(5000, TimeUnit.SECONDS)
			.blockingFirst();
		assertEquals(ConnectorState.CONNECTING, state);
		state = client().observeConnectionState()
			.timeout(5000, TimeUnit.SECONDS)
			.blockingFirst();

		assertEquals(ConnectorState.CONNECTED, state);
		state = client().observeConnectionState()
			.timeout(5000, TimeUnit.SECONDS)
			.blockingFirst();
		assertEquals(ConnectorState.AUTHENTICATED, state);
	}

	@Test
	public void testTimeout() throws Exception {
		HubServer.testOnly_setDelayPeriodAndInterval(0, 1000, TimeUnit.MILLISECONDS);
		waitConnected();
		client().registerJsonCommand(StdoutCommandTestPacket.class, () -> (ctx, packet) -> {
			try {
				synchronized(this) {
					wait(15_000);
				}
			}catch(InterruptedException e) {
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			return new JsonPacket();
		});
		var client = server().getClientList().get(0);

		StdoutCommandTestPacket p = new StdoutCommandTestPacket();
		p.setParameters("Real command");

		var cmd = client.sendJsonCommand(StringTool.generateGUID(), p, Duration.ofMillis(50), null, "Test command", null);

		client.getEventPublisher().subscribe(x-> {
			System.out.println("cmd");
			System.out.println(x.getCommand().getDescription());
		});
		var cancellingCommand = server().observeServerEvents()
			.doOnNext(a -> System.out.println(">> got state " + a.getType()))
			.filter(x->x.getType() == ServerEventType.cancelFinished)
			.timeout(1500, TimeUnit.SECONDS)
			.blockingFirst();

		assertEquals(RemoteCommandStatus.CANCELED, cmd.getStatus());
	}
}
