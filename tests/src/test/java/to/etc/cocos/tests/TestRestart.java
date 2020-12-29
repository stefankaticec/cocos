package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.connectors.server.ServerEventType;

import java.util.concurrent.TimeUnit;


@NonNullByDefault
public class TestRestart extends TestAllBase {


	@Test
	public void testRestart() throws Exception {
		waitConnected();
		clientDisconnect();
		client();

		server().observeServerEvents()
			.doOnNext(x-> System.out.println(">> got type: " +x.getType()))
			.filter(x->x.getType() == ServerEventType.peerRestarted)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();
	}
}
