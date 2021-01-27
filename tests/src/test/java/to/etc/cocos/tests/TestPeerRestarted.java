package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.Test;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.IRemoteClient;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.cocos.tests.framework.TestAllBase;
import to.etc.util.StringTool;

import java.time.Duration;
import java.time.temporal.ChronoUnit;


@NonNullByDefault
public class TestPeerRestarted extends TestAllBase {


	@Test
	public void testPeerRestarted() throws Exception {
		var set = createConditionSet();
		var gotCommandCondition = set.createCondition("Got command");

		getClient().registerJsonCommand(CommandTestPacket.class, () -> (ctx, packet) -> {
			gotCommandCondition.resolved();
			try {
				Thread.sleep(5000);
			} catch(InterruptedException x) {
				//-- This is a correct result when cancelling
			}
			//synchronized(this) {
			//	try {
			//		wait(15_000);
			//	} catch(Exception e) {
			//		e.printStackTrace();
			//	}
			//}
			return new JsonPacket();
		});
		CommandTestPacket p = new CommandTestPacket();
		p.setParameters("Real command");

		var expectCommandErrors = createConditionSet();
		var cancelledCommandFinished = expectCommandErrors.createCondition("Command cancelling finished");
		getServer().addServerEventListener(event -> {
			System.out.println("Got event: " + event.getType());
			if(event.getType() == ServerEventType.commandFinished) {
				cancelledCommandFinished.failed("Command should not finish");
			}
			if(event.getType() == ServerEventType.commandError) {
				cancelledCommandFinished.resolved();
			}
		});

		startAllAndAwaitSequential();

		IRemoteClient remote = getServer().getClientList().get(0);
		remote.sendJsonCommand(StringTool.generateGUID(), p, Duration.of(10, ChronoUnit.SECONDS), null, "Test command", null);
		set.await(DEFAULT_TIMEOUT);
		disconnectClient();
		startClientSync();

		expectCommandErrors.await(DEFAULT_TIMEOUT);                    // Was 10 seconds
	}
}
