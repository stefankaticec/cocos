package to.etc.cocos.hub;

import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import io.netty.channel.Channel;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.function.RunnableEx;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
@NonNullByDefault
class AbstractConnection {
	static private final long DUPLICATE_COUNT = 3;
	static private final long DUPLICATE_INTERVAL = 1000 * 60 * 2;

	@Nullable
	private Channel m_channel;

	@Nullable
	private CentralSocketHandler m_handler;

	private final ISystemContext m_systemContext;

	final private String m_fullId;

	/** The #of times that existing connections were disconnected within the duplicate timeout */
	private int m_dupConnCount;

	private long m_tsFirstDuplicate;

	private long m_tsLastDuplicate;


	private ConnectionState m_state = ConnectionState.DISCONNECTED;

	public AbstractConnection(ISystemContext systemContext, String id) {
		m_systemContext = systemContext;
		m_fullId = id;
	}

	public String getFullId() {
		return m_fullId;
	}

	public synchronized ConnectionState getState() {
		return m_state;
	}

	/**
	 * Accept a new connection from another instance (pass ownership).
	 */
	void newConnection(AbstractConnection from) {
		Channel channel = Objects.requireNonNull(from.m_channel);
		CentralSocketHandler handler = Objects.requireNonNull(from.m_handler);
		from.m_channel = null;
		from.m_handler = null;
		newConnection(channel, handler);
	}

	/**
	 * Accepts a new connection to this, and possibly discards a previous one.
	 */
	public void newConnection(Channel channel, CentralSocketHandler handler) {
		synchronized(this) {
			Channel oldChannel = m_channel;
			if(null == oldChannel) {
				//-- No disconnects - reset duplicate state
				m_dupConnCount = 0;
				m_state = ConnectionState.CONNECTED;
			} else {
				//-- Most certainly disconnect the channel
				disconnect();

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

			m_channel = channel;
			m_handler = handler;
		}
	}

	public void disconnect() {
		Channel channel;
		synchronized(this) {
			channel = m_channel;
			m_channel = null;
			m_state = ConnectionState.DISCONNECTED;
		}

		if(null != channel)
			channel.disconnect();
	}

	public synchronized void channelClosed() {
		m_state = ConnectionState.DISCONNECTED;
		m_channel = null;
		m_handler = null;
		log("channel closed");
	}

	public void log(String s) {
		System.out.println(m_fullId + ": " + s);
	}

	public void forwardPacket(HubPacket packet) {
		getHandler().forwardPacket(packet);
	}

	@NonNull public CentralSocketHandler getHandler() {
		CentralSocketHandler handler = m_handler;
		if(null == handler)
			throw new ProtocolViolationException("The connection to " + getFullId() + " is not currently alive");
		return handler;
	}

	public void sendHubMessage(int packetCode, String command, @Nullable Message message, @Nullable RunnableEx after) {
		getHandler().sendHubMessage(packetCode, command, message, after);
	}

	public void sendHubError(String failedCommand, ErrorCode errorCode, @Nullable RunnableEx after) {
		getHandler().sendHubError(failedCommand, after, errorCode);
	}

	public void sendHubErrorAndDisconnect(String failedCommand, ErrorCode errorCode) {
		getHandler().sendHubErrorAndDisconnect(failedCommand, errorCode);
	}

	public void sendMessage(int packetCode, String sourceID, String command, Message message, @Nullable RunnableEx after) {
		getHandler().sendMessage(packetCode, sourceID, command, message, after);
	}

	public void sendError(String sourceID, String failedCommand, @Nullable RunnableEx after, ErrorCode errorCode, Object... parameters) {
		getHandler().sendError(sourceID, failedCommand, after, errorCode, parameters);
	}

	public void sendErrorAndDisconnect(String sourceID, String failedCommand, ErrorCode errorCode, Object... parameters) {
		getHandler().sendErrorAndDisconnect(sourceID, failedCommand, errorCode, parameters);
	}

	/*----------------------------------------------------------------------*/
	/*	CODING:	Generic packet handling.									*/
	/*----------------------------------------------------------------------*/
	protected <T extends Message> T parseBuffer(Class<T> clz, HubPacket packet) throws Exception {
		T instance = (T) clz.getMethod("getDefaultInstance").invoke(null);
		return (T) instance.getParserForType().parseFrom(packet.getRemainingStream());
	}

	/**
	 * A handler method is a method with a name "handleCOMMAND", with one
	 * parameter of type HubPacket and optionally a second packet which is
	 * a protobuf Message of a specific type.
	 */
	@Nullable
	protected Method findHandlerMethod(String command) {
		String name = "handle" + command;
		for(Method method : getClass().getMethods()) {
			if(method.getName().equals(name)) {
				if(Modifier.isPublic(method.getModifiers())) {
					if(method.getParameterCount() >= 1 && method.getParameterCount() <= 2) {
						Class<?>[] pt = method.getParameterTypes();
						if(pt[0] == HubPacket.class) {
							return method;
						}
					}
				}
			}
		}
		return null;
	}

	protected void callPacketMethod(HubPacket packet) throws Exception {
		Method m = findHandlerMethod(packet.getCommand());
		if(null == m) {
			throw new ProtocolViolationException("No handler for packet command " + packet.getCommand());
		}

		Object[] args = new Object[m.getParameterCount()];
		args[0] = packet;

		if(m.getParameterCount() > 1) {
			Class<?> bufferClass = m.getParameterTypes()[2];
			if(! MessageOrBuilder.class.isAssignableFrom(bufferClass))
				throw new ProtocolViolationException(m.getName() + " has an unknown packet buffer parameter " + bufferClass.getName());
			Class<Message> mbc = (Class<Message>) bufferClass;
			Message message = parseBuffer(mbc, packet);
			args[1] = message;
		}

		try {
			m.invoke(this, args);
		} catch(InvocationTargetException itx) {
			Throwable tx = itx.getTargetException();
			if(tx instanceof RuntimeException) {
				throw (RuntimeException) tx;
			} else if(tx instanceof Error) {
				throw (Error) tx;
			} else if(tx instanceof Exception) {
				throw (Exception) tx;
			} else {
				throw itx;
			}
		}
	}

	public ISystemContext getSystemContext() {
		return m_systemContext;
	}

	public ConnectionDirectory getDirectory() {
		return getSystemContext().getDirectory();
	}
}
