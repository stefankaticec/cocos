package to.etc.cocos.hub.parties;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import to.etc.cocos.hub.AbstractConnection;
import to.etc.cocos.hub.ByteBufPacketSender;
import to.etc.cocos.hub.Hub;
import to.etc.cocos.hub.TxPacket;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.util.ByteBufferOutputStream;
import to.etc.util.ConsoleUtil;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a client (daemon).
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class Client extends AbstractConnection {
	private ConcurrentHashMap<String, ByteBufferPacket> m_inventory = new ConcurrentHashMap<>();

	public Client(Cluster cluster, Hub context, String id) {
		super(cluster, context, id);
	}

	@Override
	public void log(String s) {
		ConsoleUtil.consoleLog("Hub:Server", getFullId(), s);
	}

	/**
	 * Called when a server has a packet for a client. This sends the packet to the client.
	 */
	public void packetFromServer(Server server, Envelope envelope, ByteBuf payload, int length) {
		log("RX from server " + server.getFullId() + ": " + envelope.getPayloadCase());
		TxPacket p = new TxPacket(envelope, server, new ByteBufPacketSender(payload));
		ReferenceCountUtil.retain(payload);
		sendPacket(p);
	}

	public void updateInventory(String dataFormat, ByteBuf payload, int length) throws IOException {
		//-- Convert to permanent storage
		ByteBufferOutputStream bbos = new ByteBufferOutputStream();
		payload.readBytes(bbos, length);
		bbos.close();
		ByteBufferPacket p = new ByteBufferPacket(dataFormat, length, bbos.getBuffers());

		synchronized(this) {
			m_inventory.put(dataFormat, p);
		}
		getCluster().scheduleBroadcastEvent(server -> {
			String fullId = getFullId();
			server.sendEventClientInventory(fullId, p);
		});
	}
}
