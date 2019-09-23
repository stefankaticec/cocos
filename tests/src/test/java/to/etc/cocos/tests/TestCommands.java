package to.etc.cocos.tests;

import io.reactivex.subjects.PublishSubject;
import org.junit.Assert;
import org.junit.Test;
import to.etc.cocos.connectors.common.JsonPacket;
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
	public void testSendUnknownClientCommand() throws Exception {
		waitConnected();
		IRemoteClient remote = server().getClientList().get(0);

		UnknownCommandTestPacket p = new UnknownCommandTestPacket();
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

	@Test
	public void testSendClientCommand() throws Exception {
		PublishSubject<CommandTestPacket> ps = PublishSubject.create();

		client().registerJsonCommand(CommandTestPacket.class, (ctx, packet) -> {
			System.out.println(">> Got comand! " + packet.getParameters());
			ps.onNext(packet);
			ps.onComplete();
			return new JsonPacket();
		});

		waitConnected();
		IRemoteClient remote = server().getClientList().get(0);

		CommandTestPacket p = new CommandTestPacket();
		p.setParameters("Real command");

		String cmdid = remote.sendJsonCommand(p, 10 * 1000, null, "Test command", null);
		System.out.println(">> CMDID=" + cmdid);

		CommandTestPacket ctp = ps
			.filter(a -> a instanceof CommandTestPacket)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();

		//EventCommandBase error = remote.getEventPublisher()
		//	.doOnNext(a -> System.out.println(">> got cmdEvent " + a))
		//	.filter(a -> a instanceof EventCommand)
		//	.timeout(5000, TimeUnit.SECONDS)
		//	.blockingFirst();

		Assert.assertEquals("Must be the packet we sent", p.getParameters(), ctp.getParameters());
	}

}
