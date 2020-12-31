package to.etc.cocos.tests;

import org.junit.Assert;
import org.junit.Test;
import to.etc.cocos.connectors.client.JsonSystemCommand;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.EvCommandError;
import to.etc.cocos.connectors.ifaces.EvCommandFinished;
import to.etc.cocos.connectors.ifaces.EvCommandOutput;
import to.etc.cocos.connectors.ifaces.IRemoteClient;
import to.etc.cocos.connectors.ifaces.IRemoteCommand;
import to.etc.cocos.connectors.ifaces.IRemoteCommandListener;
import to.etc.cocos.tests.framework.TestAllBaseNew;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.util.StringTool;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 22-07-19.
 */
public class TestCommands extends TestAllBaseNew {

	@Test
	public void testSendUnknownClientCommand() throws Exception {
		startAndAwaitSequential();
		IRemoteClient remote = getServer().getClientList().get(0);

		UnknownCommandTestPacket p = new UnknownCommandTestPacket();
		p.setParameters("This is a test command packet");
		var set = createConditionSet(Duration.ofSeconds(5));
		var condition = set.createCondition("CommandError");

		IRemoteCommand cmd = remote.sendJsonCommand(StringTool.generateGUID(), p, Duration.of(10, ChronoUnit.SECONDS), null, "Test command", new IRemoteCommandListener() {
			@Override
			public void errorEvent(EvCommandError errorEvent) throws Exception {
				condition.resolved(errorEvent);
			}

			@Override
			public void completedEvent(EvCommandFinished ev) throws Exception {
				condition.failed("Expected command to fail");
			}

			@Override
			public void stdoutEvent(EvCommandOutput ev) throws Exception {
				condition.failed("Expected command to fail");

			}
		});
		System.out.println(">> CMD=" + cmd);
		set.await();
		EvCommandError err = condition.getResult();
		Assert.assertEquals("Must be commandNotFound", err.getCode(), ErrorCode.commandNotFound.name());
	}
	@Test
	public void testSendClientCommand() throws Exception {
		var set = createConditionSet(Duration.ofSeconds(5));
		var condition = set.createCondition("Command receieved");

		getClient().registerJsonCommand(CommandTestPacket.class, () -> (ctx, packet) -> {
			condition.resolved(packet);
			return new JsonPacket();
		});

		startAndAwaitSequential();
		IRemoteClient remote = getServer().getClientList().get(0);

		CommandTestPacket p = new CommandTestPacket();
		p.setParameters("Real command");

		IRemoteCommand cmd = remote.sendJsonCommand(StringTool.generateGUID(), p, Duration.of(10, ChronoUnit.SECONDS), null, "Test command", null);
		System.out.println(">> CMD=" + cmd);

		set.await();

		CommandTestPacket ctp = condition.getResult();

		//EventCommandBase error = remote.getEventPublisher()
		//	.doOnNext(a -> System.out.println(">> got cmdEvent " + a))
		//	.filter(a -> a instanceof EventCommand)
		//	.timeout(5000, TimeUnit.SECONDS)
		//	.blockingFirst();

		Assert.assertEquals("Must be the packet we sent", p.getParameters(), ctp.getParameters());
	}

	@Test
	public void testSendClientCommandWithStdout() throws Exception {
		getClient().registerJsonCommand(StdoutCommandTestPacket.class, () -> new ExecStdoutCommand());

		startAndAwaitSequential();
		IRemoteClient remote = getServer().getClientList().get(0);

		StdoutCommandTestPacket p = new StdoutCommandTestPacket();
		p.setParameters("Real command");
		var set = createConditionSet(Duration.ofSeconds(5));
		var condition = set.createCondition("Command");
		StringBuilder stdout = new StringBuilder();

		IRemoteCommand cmd = remote.sendJsonCommand(StringTool.generateGUID(), p, Duration.of(10, ChronoUnit.SECONDS), null, "Test command", new IRemoteCommandListener() {
			@Override
			public void completedEvent(EvCommandFinished ev) throws Exception {
				condition.resolved(ev);
			}

			@Override
			public void stdoutEvent(EvCommandOutput ev) throws Exception {
				stdout.append(ev.getOutput());
			}

			@Override
			public void errorEvent(EvCommandError errorEvent) throws Exception {
				condition.failed("Expected command to finish, got error");
			}
		});
		System.out.println(">> CMD=" + cmd);

		set.await();
		EvCommandFinished event = condition.getResult();

		Assert.assertTrue("Must be command completed", event instanceof EvCommandFinished);
		Assert.assertTrue("Output must be correct", stdout.toString().contains(OUTPUT));

	}
	static private final String OUTPUT = "The hills are alive with the sound of Metallica";


	public class ExecStdoutCommand extends JsonSystemCommand<StdoutCommandTestPacket> {
		@Override
		public JsonPacket execute(CommandContext ctx, StdoutCommandTestPacket packet) throws Exception {
			List<String> args = new ArrayList<>();
			args.add("/bin/bash");
			args.add("-c");
			args.add("echo '" + OUTPUT +"'");
			runRemoteCommand(ctx, args);
			return new JsonPacket();
		}
	}


}
