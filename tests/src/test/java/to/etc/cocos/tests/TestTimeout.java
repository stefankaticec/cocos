package to.etc.cocos.tests;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import to.etc.cocos.connectors.client.IJsonCommandHandler;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.ConnectorState;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.ifaces.EvCommandError;
import to.etc.cocos.connectors.ifaces.EvCommandFinished;
import to.etc.cocos.connectors.ifaces.EvCommandOutput;
import to.etc.cocos.connectors.ifaces.IRemoteCommandListener;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.ifaces.ServerCommandEventBase;
import to.etc.cocos.connectors.packets.CancelReasonCode;
import to.etc.cocos.connectors.server.HubServer;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.util.StringTool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

@NonNullByDefault
public class TestTimeout extends TestAllBase {

	@Test
	public void testOrderOfClientStates() throws Exception {
		hub();

		List<ConnectorState> order = new ArrayList<>(List.of(ConnectorState.CONNECTING, ConnectorState.CONNECTED, ConnectorState.AUTHENTICATED));
		serverConnected();
		var state = client().observeConnectionState()			// This is a race always.
			.doOnNext(connectorState -> System.out.println("> got " + connectorState))
			.map(a -> {
				if(order.size() == 0)
					return true;
				if(order.get(0) == a) {
					order.remove(0);
				}
				return order.size() == 0;
			})
			.filter(a -> a)
			.timeout(5, TimeUnit.SECONDS)
			.blockingFirst();
	}

	@Test
	public void testTimeout() throws Exception {
		HubServer.testOnly_setDelayPeriodAndInterval(0, 300, TimeUnit.MILLISECONDS);
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
					Thread.sleep(15000);
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

		var cmd = client.sendJsonCommand(StringTool.generateGUID(), p, Duration.ofMillis(500), null, "Test command", l);
		var cancellingCommand = server().observeServerEvents()
			.filter(x->x.getType() == ServerEventType.cancelFinished)
			.timeout(15, TimeUnit.SECONDS)
			.blockingFirst();

		assertEquals(RemoteCommandStatus.FAILED, cmd.getStatus());
		assertEquals("Listener must have one result", 1, eventList.size());
		assertEquals("Command result must be a failed event", EvCommandError.class, eventList.get(0).getClass());
	}
}
