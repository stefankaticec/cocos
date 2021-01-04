package to.etc.cocos.tests;

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
		startAllAndAwaitSequential();
		IRemoteClient remote = getServer().getClientList().get(0);

		UnknownCommandTestPacket p = new UnknownCommandTestPacket();
		p.setParameters("This is a test command packet");
		var set = createConditionSet();
		var condition = set.createCondition("CommandError");

		IRemoteCommand cmd = remote.sendJsonCommand(StringTool.generateGUID(), p, Duration.of(10, ChronoUnit.SECONDS), null, "Test command", new IRemoteCommandListener() {
			@Override
			public void errorEvent(EvCommandError errorEvent) throws Exception {
				if(errorEvent.getCode().equals(ErrorCode.commandNotFound.name()))
					condition.resolved();
				else
					condition.failed("Unexpected error code: " + errorEvent);
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
		set.await(DEFAULT_TIMEOUT);
	}
	@Test
	public void testSendClientCommand() throws Exception {
		var set = createConditionSet();
		var condition = set.createCondition("Command receieved");

		CommandTestPacket p = new CommandTestPacket();
		p.setParameters("Real command");

		getClient().registerJsonCommand(CommandTestPacket.class, () -> (ctx, packet) -> {
			if(p.getParameters().equals(packet.getParameters()))
				condition.resolved();
			else
				condition.failed("Packet parameters incorrect");
			return new JsonPacket();
		});

		startAllAndAwaitSequential();
		IRemoteClient remote = getServer().getClientList().get(0);


		IRemoteCommand cmd = remote.sendJsonCommand(StringTool.generateGUID(), p, Duration.of(10, ChronoUnit.SECONDS), null, "Test command", null);
		System.out.println(">> CMD=" + cmd);
		set.await(DEFAULT_TIMEOUT);
	}

	@Test
	public void testSendClientCommandWithStdout() throws Exception {
		getClient().registerJsonCommand(StdoutCommandTestPacket.class, () -> new ExecStdoutCommand());

		startAllAndAwaitSequential();
		IRemoteClient remote = getServer().getClientList().get(0);

		StdoutCommandTestPacket p = new StdoutCommandTestPacket();
		p.setParameters("Real command");
		var set = createConditionSet();
		var condition = set.createCondition("Command");
		StringBuilder stdout = new StringBuilder();

		IRemoteCommand cmd = remote.sendJsonCommand(StringTool.generateGUID(), p, Duration.of(10, ChronoUnit.SECONDS), null, "Test command", new IRemoteCommandListener() {
			@Override
			public void completedEvent(EvCommandFinished ev) throws Exception {
				if(stdout.toString().contains(OUTPUT))
					condition.resolved();
				else
					condition.failed("Command finished but output is not there");
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

		set.await(DEFAULT_TIMEOUT);

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
