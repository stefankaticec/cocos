package to.etc.cocos.hub.parties;

import io.netty.buffer.ByteBuf;
import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.hub.IExecute;
import to.etc.cocos.hub.PacketResponseBuilder;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.ClientInventory;

import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 20-09-19.
 */
@NonNullByDefault
final class InventorySender {
	final private Server m_server;

	private final IExecute m_onFinished;

	private List<Client> m_clientList = Collections.emptyList();

	private int m_index;

	private int m_phase;

	InventorySender(Server server, IExecute onFinished) {
		m_server = server;
		m_onFinished = onFinished;
	}

	public void start() throws Exception {
		m_clientList = m_server.getCluster().getAllClients();
		sendNextPacket();
	}

	private void sendNextPacket() throws Exception {
		Client client;
		int phase;
		synchronized(this) {
			if(m_index >= m_clientList.size()) {
				m_phase++;
				m_index = 0;

				switch(m_phase) {
					default:
						throw new IllegalStateException(m_phase + " unknown phase");

					case 1:
						break;

					case 2:
						m_onFinished.execute();
						return;
				}
			}
			client = m_clientList.get(m_index++);
			phase = m_phase;
		}

		switch(phase) {
			default:
				throw new IllegalStateException(phase + ": unexpected phase");

			case 0:
				//-- send client connected
				PacketResponseBuilder b = m_server.packetBuilder(client.getFullId());
				b.getEnvelope().setClientConnected(Hubcore.ClientConnected.getDefaultInstance());
				b.after(this::sendNextPacket);
				b.send();
				break;

			case 1:
				//-- send inventory packet list
				List<ByteBufferPacket> inventoryPacketList = client.getInventoryPacketList();
				while(inventoryPacketList.size() == 0) {
					synchronized(this) {
						if(m_index >= m_clientList.size()) {
							m_phase++;
							return;
						}
						client = m_clientList.get(m_index++);
						inventoryPacketList = client.getInventoryPacketList();
					}
				}
				for(int i = inventoryPacketList.size() - 1; i >= 0; i--) {
					ByteBufferPacket packet = inventoryPacketList.get(i);
					ByteBuf buffer = m_server.getHandler().alloc().buffer();
					for(byte[] bb : packet.getBuffers()) {
						buffer.writeBytes(bb);
					}

					b = m_server.packetBuilder(client.getFullId());
					b.getEnvelope().setInventory(ClientInventory.getDefaultInstance());
					b.body(buffer);
					if(i == 0)
						b.after(this::sendNextPacket);
					b.send();
				}
				break;
		}
	}


}
