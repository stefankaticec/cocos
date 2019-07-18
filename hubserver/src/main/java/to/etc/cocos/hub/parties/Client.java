package to.etc.cocos.hub.parties;

import to.etc.cocos.hub.AbstractConnection;
import to.etc.cocos.hub.Hub;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.util.ByteBufferOutputStream;
import to.etc.util.ConsoleUtil;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a client (daemon).
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class Client extends AbstractConnection {
	private ConcurrentHashMap<String, InventoryEntry> m_inventory = new ConcurrentHashMap<>();

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

	public void updateInventory(String dataFormat, ByteBufferOutputStream payload) {
		synchronized(this) {
			InventoryEntry ie = new InventoryEntry(dataFormat, payload.getBuffers());
			m_inventory.put(dataFormat, ie);
		}
		getCluster().scheduleBroadcastEvent(server -> {
			String fullId = getFullId();
			server.sendEventClientInventory(fullId, dataFormat, payload);
		});
	}
}
