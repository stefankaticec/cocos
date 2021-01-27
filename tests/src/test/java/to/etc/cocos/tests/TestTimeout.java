package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import to.etc.cocos.connectors.client.IJsonCommandHandler;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.EvCommandError;
import to.etc.cocos.connectors.ifaces.EvCommandFinished;
import to.etc.cocos.connectors.ifaces.EvCommandOutput;
import to.etc.cocos.connectors.ifaces.IRemoteCommandListener;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.ifaces.ServerCommandEventBase;
import to.etc.cocos.connectors.packets.CancelReasonCode;
import to.etc.cocos.tests.framework.TestAllBase;
import to.etc.util.StringTool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@NonNullByDefault
public class TestTimeout extends TestAllBase {

	/**
	 * Test that a command is properly cancelled when its timeout expires, and that
	 * all side effects of the cancellation (like calling the listeners) work correctly.
	 */
	@Test
	public void testTimeout() throws Exception {
		setBeforeServerStart(server -> {
			server.testOnly_setDelayPeriodAndInterval(0, 300, TimeUnit.MILLISECONDS);
		});

		startAllAndAwaitSequential();

		IJsonCommandHandler<StdoutCommandTestPacket> handler = new IJsonCommandHandler<StdoutCommandTestPacket>() {
			@Nullable
			private Thread m_thread;

			private boolean m_cancelled;

			@Override
			public JsonPacket execute(CommandContext ctx, StdoutCommandTestPacket packet) throws Exception {

				synchronized(this) {
					m_thread = Thread.currentThread();
				}
				try {
					Thread.sleep(15000);
					return new JsonPacket();
				} catch(InterruptedException x) {
					throw new CancellationException("Command cancelled");
				} finally {
					synchronized(this) {
						m_thread = null;
					}
				}
			}

			;

			@Override
			public void cancel(CommandContext ctx, CancelReasonCode code, @Nullable String cancelReason) throws Exception {
				synchronized(this) {
					Thread thread = m_thread;
					if(null == thread) {
						throw new IllegalStateException("Command not running while cancelling");
					}
					m_cancelled = true;
					thread.interrupt();
				}
			}
		};

		getClient().registerJsonCommandAsync(StdoutCommandTestPacket.class, () -> handler);
		var client = getServer().getClientList().get(0);

		StdoutCommandTestPacket p = new StdoutCommandTestPacket();
		p.setParameters("Sleepy command");

		List<ServerCommandEventBase> eventList = new ArrayList<>();

		var set = createConditionSet();
		var jsonResultC = set.createCondition("jsonResult Received");

		IRemoteCommandListener l = new IRemoteCommandListener() {
			@Override
			public void errorEvent(EvCommandError errorEvent) throws Exception {
				eventList.add(errorEvent);
				jsonResultC.resolved();
			}

			@Override
			public void completedEvent(EvCommandFinished ev) throws Exception {
				eventList.add(ev);
				jsonResultC.resolved();
			}

			@Override
			public void stdoutEvent(EvCommandOutput ev) throws Exception {
				// We do not care about these
			}
		};

		var cmd = client.sendJsonCommand(StringTool.generateGUID(), p, Duration.ofMillis(500), null, "Test command", l);

		set.await(Duration.ofSeconds(15));

		assertEquals(RemoteCommandStatus.FAILED, cmd.getStatus());
		assertEquals("Listener must have one result", 1, eventList.size());
		assertEquals("Command result must be a failed event", EvCommandError.class, eventList.get(0).getClass());
	}
}
