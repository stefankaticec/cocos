package to.etc.cocos.tests;

import io.reactivex.rxjava3.subjects.Subject;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.connectors.server.ServerEventBase;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.cocos.hub.HubState;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;

@NonNullByDefault
public class TestPeerRestarted extends TestAllBase {

	@Test
	public void testRestart() throws Exception {
		//var sce = new ConditionSet(Duration.of(5, ChronoUnit.SECONDS));
		//var condition = sce.createCondition("Name");
		//var hubStartedListener = (HubState state)-> {
		//	if(state== HubState.RUNNING) {
		//		condition.resolved();
		//	}
		//};
		//
		waitConnected();

		clientDisconnect();
		server().observeServerEvents()
			.filter(x->x.getType() == ServerEventType.clientDisconnected)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();
		client();

		ServerEventBase fakeDisconnect = new ServerEventBase(ServerEventType.serverDisconnected);
		var subject = Subject.just(fakeDisconnect);
		var serverDisconnect = server().observeServerEvents()
			.filter(x->x.getType() == ServerEventType.serverDisconnected)
			.timeout(5, TimeUnit.SECONDS, subject)
			.blockingFirst();
		server().observeServerEvents()
			.doOnNext(x-> System.out.println(">> got event type: " +x.getType()))
			.filter(x->x.getType() == ServerEventType.peerRestarted)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();

		assertEquals(fakeDisconnect, serverDisconnect);
	}
}
