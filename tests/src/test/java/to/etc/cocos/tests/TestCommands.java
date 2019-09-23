package to.etc.cocos.tests;

import org.junit.Assert;
import org.junit.Test;
import to.etc.cocos.connectors.ifaces.EventCommandBase;
import to.etc.cocos.connectors.ifaces.EventCommandError;
import to.etc.cocos.connectors.ifaces.IRemoteClient;
import to.etc.hubserver.protocol.ErrorCode;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 22-07-19.
 */
public class TestCommands extends TestAllBase {
	@Test
	public void testSendClientCommand() throws Exception {
		waitConnected();
		IRemoteClient remote = server().getClientList().get(0);

		CommandTestPacket p = new CommandTestPacket();
		p.setParameters("This is a test command packet");

		String cmdid = remote.sendJsonCommand(p, 10 * 1000, null, "Test command", null);
		System.out.println(">> CMDID=" + cmdid);

		EventCommandBase error = remote.getEventPublisher()
			.doOnNext(a -> System.out.println(">> got cmdEvent " + a))
			.filter(a -> a instanceof EventCommandError)
			.timeout(5000, TimeUnit.SECONDS)
			.blockingFirst();

		EventCommandError err = (EventCommandError) error;
		Assert.assertEquals("Must be commandNotFound", err.getCode(), ErrorCode.commandNotFound.name());
	}
}
