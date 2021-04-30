package to.etc.cocos.hub;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.parties.Cluster;
import to.etc.cocos.hub.parties.ConnectionDirectory;
import to.etc.cocos.hub.parties.ConnectionState;
import to.etc.cocos.hub.problems.PartyNotConnectedException;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.util.ConsoleUtil;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
@NonNullByDefault
abstract public class AbstractConnection {
	static private final long DUPLICATE_COUNT = 3;
	static private final long DUPLICATE_INTERVAL = 1000 * 60 * 2;

	private final Cluster m_cluster;

	@Nullable
	private CentralSocketHandler m_handler;

	private final Hub m_systemContext;

	final private String m_fullId;

	/** The #of times that existing connections were disconnected within the duplicate timeout */
	private int m_dupConnCount;

	private long m_tsFirstDuplicate;

	private long m_tsLastDuplicate;

	private ConnectionState m_state = ConnectionState.DISCONNECTED;

	final public void log(String s) {
		CentralSocketHandler handler = m_handler;
		String id = handler == null ? "(disconnected)" : handler.getId();
		ConsoleUtil.consoleLog("Hub:" + getClass().getSimpleName(), id, getFullId(), s);
	}

	public AbstractConnection(Cluster cluster, Hub systemContext, String id) {
		m_cluster = cluster;
		m_systemContext = systemContext;
		m_fullId = id;
	}

	public String getFullId() {
		return m_fullId;
	}

	public Cluster getCluster() {
		return m_cluster;
	}

	public synchronized ConnectionState getState() {
		return m_state;
	}


	public synchronized boolean isUsable() {
		return m_state == ConnectionState.CONNECTED;
	}

	/**
	 * Accepts a new connection to this, and possibly discards a previous one. If
	 * this returns false the new connection is refused, and disconnected.
	 */
	public boolean newConnection(CentralSocketHandler handler) {
		synchronized(this) {
			log("New connection from " + handler.getRemoteAddress() + " id=" + handler.getId());
			CentralSocketHandler oldChannel = m_handler;
			if(null == oldChannel) {
				//-- No disconnects - reset duplicate state
				m_dupConnCount = 0;
				m_state = ConnectionState.CONNECTED;
				log("No disconnects. Resetting duplicate state");
			} else {
				// jal 20201209 We can have address changes when coming from a cloud. Do not test those.
				//-- If the new address differs from the current one prefer the current one
				//if(! oldChannel.getRemoteAddress().equals(handler.getRemoteAddress())) {
				//	log("New connection from " + handler.getRemoteAddress() + " on channel "+handler.getId()+" discarded: preferring existing connection from " + oldChannel.getRemoteAddress() + " on channel "+ oldChannel.getId());
				//	handler.disconnectOnly("Refused because a connection already exists");
				//	return false;
				//}

				//-- Most certainly disconnect the old, existing connection
				disconnectOnly("New connection came in from " + handler.getRemoteAddress());

				//-- Duplicate detection...
				long ts = System.currentTimeMillis();
				if(m_dupConnCount == 0) {
					m_tsFirstDuplicate = ts;
					m_tsLastDuplicate = ts;
					m_dupConnCount = 1;
					m_state = ConnectionState.POSSIBLY_DUPLICATE;
				} else {
					if(ts - m_tsLastDuplicate < DUPLICATE_INTERVAL) {
						//-- Within the timeout
						m_tsLastDuplicate = ts;					// Set last occurrence
						m_dupConnCount++;						// Count as duplicate
						if(m_dupConnCount >= DUPLICATE_COUNT) {
							m_state = ConnectionState.DUPLICATE;
						} else {
							m_state = ConnectionState.POSSIBLY_DUPLICATE;
						}
					} else {
						//-- No, outside -> reset to normal
						m_state = ConnectionState.CONNECTED;
						m_dupConnCount = 0;
					}
				}
			}
			m_handler = handler;
		}
		return true;
	}

	/**
	 * Called to disconnect an attached handler without changing any other state; the server or client is not deregistered.
	 */
	public void disconnectOnly(String why) {
		CentralSocketHandler handler;
		synchronized(this) {
			handler = m_handler;
			m_handler = null;
			m_state = ConnectionState.DISCONNECTED;
		}
		log("called disconnectOnly " + why + " with handler=" + handler);
		if(null != handler) {
			handler.disconnectOnly(why);
		}
	}

	public synchronized void channelClosed() {
		m_state = ConnectionState.DISCONNECTED;
		m_handler = null;
		CentralSocketHandler handler = m_handler;
		m_cluster.unregister(this);
		log("channel closed " + (handler == null ? "(null channel)" : handler.getId()));
	}

	@NonNull public CentralSocketHandler getHandler() {
		CentralSocketHandler handler = m_handler;
		if(null == handler)
			throw new PartyNotConnectedException(getFullId());
		return handler;
	}


	/*----------------------------------------------------------------------*/
	/*	CODING:	Buffered packet transmitter.								*/
	/*----------------------------------------------------------------------*/

	/**
	 * Schedule a packet to be sent with normal priority.
	 */
	public void sendPacket(TxPacket packet) {
		CentralSocketHandler handler;
		synchronized(this) {
			handler = m_handler;
			if(null == handler) {
				throw new PartyNotConnectedException(getFullId());
			}
			handler.immediateSendPacket(packet);
		}
	}

	public Hub getSystemContext() {
		return m_systemContext;
	}

	public ConnectionDirectory getDirectory() {
		return getSystemContext().getDirectory();
	}

	public void onPacketForward(AbstractConnection connection, Envelope envelope) {

	}

	public PacketResponseBuilder packetBuilder() {
		return new PacketResponseBuilder(this);
	}
}
