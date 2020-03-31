package to.etc.cocos.hub.parties;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCountUtil;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.AbstractConnection;
import to.etc.cocos.hub.ByteBufPacketSender;
import to.etc.cocos.hub.Hub;
import to.etc.cocos.hub.TxPacket;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.FatalHubException;
import to.etc.util.ByteBufferOutputStream;
import to.etc.util.ConsoleUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a client (daemon).
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
@NonNullByDefault
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
	public void packetFromServer(Server server, Envelope envelope, @Nullable ByteBuf payload, int length) {
		log("RX from server " + server.getFullId() + ": " + Hub.getPacketType(envelope));
		TxPacket p = new TxPacket(envelope, server, null == payload ? null : new ByteBufPacketSender(payload), null);
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

	public void packetReceived(Envelope envelope, @Nullable ByteBuf payload, int length) {
		if(!isUsable())
			throw new IllegalStateException("Received data from a defunct client??");
		log("Packet received(C): " + Hub.getPacketType(envelope));

		String targetId = envelope.getTargetId();
		if(targetId.length() == 0) {
			handleHubCommand(envelope);
		} else {
			Server server = decodeServerTargetID(envelope.getTargetId());
			server.packetFromClient(this, envelope, payload, length);
		}
	}

	/**
	 * Format is either clusterid, resource#clusterid, server@clusterid.
	 */
	private Server decodeServerTargetID(String targetId) {
		String[] split = targetId.split("#");
		Server server;
		Cluster cluster;
		String orgId;
		switch(split.length) {
			default:
				throw new FatalHubException(ErrorCode.targetNotFound, targetId);

			case 1:
				//-- Can be server@clusterid
				split = targetId.split("@");
				switch(split.length) {
					default:
						throw new FatalHubException(ErrorCode.targetNotFound, targetId);

					case 1:
						//-- Cluster ID only
						cluster = getDirectory().getCluster(split[0]);
						server = cluster.getRandomServer();
						if(null == server)
							throw new FatalHubException(ErrorCode.clusterNotFound, split[0]);
						return server;

					case 2:
						cluster = getDirectory().getCluster(split[1]);
						server = cluster.findServer(split[0]);
						if(null == server)
							throw new FatalHubException(ErrorCode.targetNotFound, targetId);
						return server;
				}

			case 2:
				cluster = getDirectory().getCluster(split[1]);
				orgId = split[0];
				server = cluster.findServiceServer(orgId);
				if(null == server)
					throw new FatalHubException(ErrorCode.targetNotFound, split[0]);
				return server;
		}
	}



	private void handleHubCommand(Envelope envelope) {


	}

	public synchronized List<ByteBufferPacket> getInventoryPacketList() {
		return new ArrayList<>(m_inventory.values());
	}
}
