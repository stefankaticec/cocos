package to.etc.cocos.hub.parties;

import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.AbstractConnection;
import to.etc.cocos.hub.Hub;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.util.ByteBufferOutputStream;
import to.etc.util.ConsoleUtil;

/**
 * Represents a client (daemon).
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class Client extends AbstractConnection {
	@Nullable
	private ByteBufferOutputStream m_inventory;

	public Client(Cluster cluster, Hub context, String id) {
		super(cluster, context, id);
	}

	@Override
	public void log(String s) {
		ConsoleUtil.consoleLog("Hub:Server", getFullId(), s);
	}

	public void packetFromServer(Server server, Envelope envelope) {
		log("RX from server " + server.getFullId() + ": " + envelope.getCommand());
	}

	public void updateInventory(ByteBufferOutputStream payload) {
		synchronized(this) {
			m_inventory = payload;
		}
		getCluster().scheduleBroadcastEvent(server -> {
			String fullId = getFullId();
			server.sendEventClientInventory(fullId, payload);
		});
	}
}
