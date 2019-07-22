package to.etc.cocos.tests;

import org.junit.Test;
import to.etc.cocos.connectors.RemoteClient;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 22-07-19.
 */
public class TestCommands extends TestAllBase {
	@Test
	public void testSendClientCommand() throws Exception {
		waitConnected();
		RemoteClient remote = server().getClientList().get(0);

		CommandTestPacket p = new CommandTestPacket();
		p.setParameters("This is a test command packet");

		String cmdid = remote.sendJsonCommand(p, 10 * 1000, null, "Test command", null);
		System.out.println(">> CMDID=" + cmdid);

		Thread.sleep(10_000);
	}
}
