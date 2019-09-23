package to.etc.cocos.hub;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.parties.Cluster;
import to.etc.cocos.hub.parties.ConnectionDirectory;
import to.etc.cocos.hub.parties.ConnectionState;
import to.etc.cocos.hub.problems.ProtocolViolationException;
import to.etc.cocos.messages.Hubcore.Envelope;

import java.util.LinkedList;
import java.util.List;

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

	/** Normal priority packets to send. */
	private List<TxPacket> m_txPacketQueue = new LinkedList<>();

	/** Priority packets to send */
	private List<TxPacket> m_txPacketQueuePrio = new LinkedList<>();

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
	 * Accepts a new connection to this, and possibly discards a previous one.
	 */
	public void newConnection(CentralSocketHandler handler) {
		synchronized(this) {
			CentralSocketHandler oldChannel = m_handler;
			if(null == oldChannel) {
				//-- No disconnects - reset duplicate state
				m_dupConnCount = 0;
				m_state = ConnectionState.CONNECTED;
			} else {
				//-- Most certainly disconnect the old, existing connection
				disconnectOnly();

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
	}

	/**
	 * Called to disconnect an attached handler without changing any other state; the server or client is not deregistered.
	 */
	public void disconnectOnly() {
		CentralSocketHandler handler;
		synchronized(this) {
			handler = m_handler;
			m_handler = null;
			m_state = ConnectionState.DISCONNECTED;
		}
		if(null != handler) {
			handler.disconnectOnly();
		}
	}

	public synchronized void channelClosed() {
		m_state = ConnectionState.DISCONNECTED;
		m_handler = null;
		m_cluster.unregister(this);
		//log("channel closed");
	}

	abstract public void log(String s);

	//public void forwardPacket(HubPacket packet) {
	//	getHandler().forwardPacket(packet);
	//}

	@NonNull public CentralSocketHandler getHandler() {
		CentralSocketHandler handler = m_handler;
		if(null == handler)
			throw new ProtocolViolationException("The connection to " + getFullId() + " is not currently alive");
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
			m_txPacketQueue.add(packet);					// Will be picked up when current packet tx finishes.
			handler = m_handler;
		}
		if(null != handler) {
			handler.tryScheduleSend(this, packet);			// If the transmitter is empty start it
		}
	}

	public void sendPacketPrio(TxPacket packet) {
		CentralSocketHandler handler;
		synchronized(this) {
			m_txPacketQueuePrio.add(packet);
			handler = m_handler;
		}
		if(null != handler) {
			handler.tryScheduleSend(this, packet);				// If the transmitter is empty start it
		}
	}

	/**
	 * Select the next packet to send, and make it the current packet.
	 */
	@Nullable
	synchronized TxPacket getNextPacketToTransmit() {
		if(m_txPacketQueuePrio.size() > 0) {
			TxPacket txPacket = m_txPacketQueuePrio.get(0);
			synchronized(this) {
				m_txPacketQueuePrio.remove(txPacket);
			}
			return txPacket;
		} else if(m_txPacketQueue.size() > 0) {
			TxPacket txPacket = m_txPacketQueue.get(0);
			synchronized(this) {
				m_txPacketQueue.remove(txPacket);
			}
			return txPacket;
		} else {
			return null;
		}
	}

	//public void sendHubMessage(int packetCode, String command, @Nullable Message message, @Nullable RunnableEx after) {
	//	getHandler().sendHubMessage(packetCode, command, message, after);
	//}
	//
	//public void sendHubError(String failedCommand, ErrorCode errorCode, @Nullable RunnableEx after) {
	//	getHandler().sendHubError(failedCommand, after, errorCode);
	//}
	//
	//public void sendHubErrorAndDisconnect(String failedCommand, ErrorCode errorCode) {
	//	getHandler().sendHubErrorAndDisconnect(failedCommand, errorCode);
	//}
	//
	//public void sendMessage(int packetCode, String sourceID, String command, Message message, @Nullable RunnableEx after) {
	//	getHandler().sendMessage(packetCode, sourceID, command, message, after);
	//}
	//
	//public void sendError(String sourceID, String failedCommand, @Nullable RunnableEx after, ErrorCode errorCode, Object... parameters) {
	//	getHandler().sendError(sourceID, failedCommand, after, errorCode, parameters);
	//}
	//
	//public void sendErrorAndDisconnect(String sourceID, String failedCommand, ErrorCode errorCode, Object... parameters) {
	//	getHandler().sendErrorAndDisconnect(sourceID, failedCommand, errorCode, parameters);
	//}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Generic packet handling.									*/
	/*----------------------------------------------------------------------*/
	//protected <T extends Message> T parseBuffer(Class<T> clz, HubPacket packet) throws Exception {
	//	T instance = (T) clz.getMethod("getDefaultInstance").invoke(null);
	//	return (T) instance.getParserForType().parseFrom(packet.getRemainingStream());
	//}

	/**
	 * A handler method is a method with a name "handleCOMMAND", with one
	 * parameter of type HubPacket and optionally a second packet which is
	 * a protobuf Message of a specific type.
	 */
	//@Nullable
	//protected Method findHandlerMethod(String command) {
	//	String name = "handle" + command;
	//	for(Method method : getClass().getMethods()) {
	//		if(method.getName().equals(name)) {
	//			if(Modifier.isPublic(method.getModifiers())) {
	//				if(method.getParameterCount() >= 1 && method.getParameterCount() <= 2) {
	//					Class<?>[] pt = method.getParameterTypes();
	//					if(pt[0] == HubPacket.class) {
	//						return method;
	//					}
	//				}
	//			}
	//		}
	//	}
	//	return null;
	//}
	//
	//protected void callPacketMethod(HubPacket packet) throws Exception {
	//	Method m = findHandlerMethod(packet.getCommand());
	//	if(null == m) {
	//		throw new ProtocolViolationException("No handler for packet command " + packet.getCommand());
	//	}
	//
	//	Object[] args = new Object[m.getParameterCount()];
	//	args[0] = packet;
	//
	//	if(m.getParameterCount() > 1) {
	//		Class<?> bufferClass = m.getParameterTypes()[2];
	//		if(! MessageOrBuilder.class.isAssignableFrom(bufferClass))
	//			throw new ProtocolViolationException(m.getName() + " has an unknown packet buffer parameter " + bufferClass.getName());
	//		Class<Message> mbc = (Class<Message>) bufferClass;
	//		Message message = parseBuffer(mbc, packet);
	//		args[1] = message;
	//	}
	//
	//	try {
	//		m.invoke(this, args);
	//	} catch(InvocationTargetException itx) {
	//		Throwable tx = itx.getTargetException();
	//		if(tx instanceof RuntimeException) {
	//			throw (RuntimeException) tx;
	//		} else if(tx instanceof Error) {
	//			throw (Error) tx;
	//		} else if(tx instanceof Exception) {
	//			throw (Exception) tx;
	//		} else {
	//			throw itx;
	//		}
	//	}
	//}

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

	public PacketResponseBuilder packetBuilder(Envelope source) {
		PacketResponseBuilder b = new PacketResponseBuilder(this);
		b.fromEnvelope(source);
		return b;
	}

}
