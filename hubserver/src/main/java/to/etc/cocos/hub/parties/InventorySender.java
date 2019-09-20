package to.etc.cocos.hub.parties;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.hub.IExecute;
import to.etc.cocos.hub.PacketResponseBuilder;
import to.etc.cocos.messages.Hubcore;

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

	private List<ByteBufferPacket> m_inventoryList = Collections.emptyList();

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

				if(m_phase > 1) {
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
				b.send();


				break;

			case 1:
				//-- send inventory packet list
				break;
		}
	}


}
