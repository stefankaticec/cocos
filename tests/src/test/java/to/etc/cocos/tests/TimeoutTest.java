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
		HubServer.testOnly_setDelayPeriodAndInterval(0, 100, TimeUnit.MILLISECONDS);
		waitConnected();
		PublishSubject<StdoutCommandTestPacket> ps = PublishSubject.create();
		client().registerJsonCommand(StdoutCommandTestPacket.class, () -> (ctx, packet) -> {
			ps.onNext(packet);
			ps.onComplete();
			try {
				synchronized(this) {
					wait(3000);
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
		var observer = client.getEventPublisher().subscribe(x-> {
			System.out.println("command");
			if(x instanceof EvCommandError) {
				System.out.println(((EvCommandError) x).getMessage());
			}
			System.out.println(x.getClass().getSimpleName());
			System.out.println(x.getCommand().getDescription());
		});
		var cmd = client.sendJsonCommand(StringTool.generateGUID(), p, Duration.ofMillis(50), null, "Test command", null);
		var cancellingCommand = ps
			.delay(3, TimeUnit.SECONDS)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();

		assertEquals(RemoteCommandStatus.CANCELED, cmd.getStatus());
	}
}
