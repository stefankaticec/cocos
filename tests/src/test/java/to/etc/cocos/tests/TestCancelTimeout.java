package to.etc.cocos.tests;

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
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.util.StringTool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 26-01-21.
 */
public class TestCancelTimeout extends TestAllBase {

	/**
	 * Test that a cancelled command that does NOT terminate on a cancel
	 * is still marked as failed on the server after the cancel response
	 * timeout has passed.
	 */
	@Test
	public void testCancelTimeout() throws Exception {
		setBeforeServerStart(server -> {
			server.testOnly_setDelayPeriodAndInterval(0, 300, TimeUnit.MILLISECONDS);
			server.testOnly_setCancelResponseTimeout(500);
		});
		waitConnected();

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
					Thread.sleep(5000);
					return new JsonPacket();
				} catch(InterruptedException x) {
					throw new CancellationException("Command cancelled");
				} finally {
					synchronized(this) {
						m_thread = null;
					}
				}
			};

			@Override
			public void cancel(CommandContext ctx, CancelReasonCode code, @Nullable String cancelReason) throws Exception {
				// Do nothing
			}
		};

		client().registerJsonCommandAsync(StdoutCommandTestPacket.class, () -> handler);
		var client = server().getClientList().get(0);

		StdoutCommandTestPacket p = new StdoutCommandTestPacket();
		p.setParameters("Sleepy command");

		List<ServerCommandEventBase> eventList = new ArrayList<>();

		IRemoteCommandListener l = new IRemoteCommandListener() {
			@Override
			public void errorEvent(EvCommandError errorEvent) throws Exception {
				eventList.add(errorEvent);
			}

			@Override
			public void completedEvent(EvCommandFinished ev) throws Exception {
				eventList.add(ev);
			}

			@Override
			public void stdoutEvent(EvCommandOutput ev) throws Exception {
				// We do not care about these
			}
		};

		var cmd = client.sendJsonCommand(StringTool.generateGUID(), p, Duration.ofMillis(10000), null, "Test command", l);

		Thread.sleep(250);
		cmd.cancel(CancelReasonCode.USER, "Test cancel");

		var cancellingCommand = server().observeServerEvents()
			.filter(x->x.getType() == ServerEventType.cancelFinished)
			.timeout(15, TimeUnit.SECONDS)
			.blockingFirst();

		assertEquals(RemoteCommandStatus.FAILED, cmd.getStatus());
		assertEquals("Listener must have one result", 1, eventList.size());
		assertEquals("Command result must be a failed event", EvCommandError.class, eventList.get(0).getClass());
	}

}
