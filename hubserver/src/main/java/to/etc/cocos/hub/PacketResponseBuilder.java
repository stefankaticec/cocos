package to.etc.cocos.hub;

import io.netty.buffer.ByteBuf;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.Envelope.Builder;

/**
 * This is the normal packet builder, sending packets after
 * the connection has been established.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 1-7-19.
 */
@NonNullByDefault
final public class PacketResponseBuilder {
	private final Builder m_envelope = Envelope.newBuilder();

	@Nullable
	private final AbstractConnection m_connection;

	@Nullable
	private final CentralSocketHandler m_socketHandler;

	@Nullable
	private IPacketBodySender m_bodySender;

	@Nullable
	private IExecute m_onAfter;

	public PacketResponseBuilder(AbstractConnection connection) {
		m_connection = connection;
		m_socketHandler = null;
	}

	public PacketResponseBuilder(CentralSocketHandler handler) {
		m_connection = null;
		m_socketHandler = handler;
	}

	/**
	 * Initialize the response envelope from the source envelope.
	 */
	public PacketResponseBuilder fromEnvelope(Envelope envelope) {
		m_envelope
			.setVersion(envelope.getVersion())
			.setSourceId(envelope.getTargetId())			// Swap src and dest
			.setTargetId(envelope.getSourceId())
			;
		return this;
	}

	public Builder getEnvelope() {
		return m_envelope;
	}

	public PacketResponseBuilder sourceId(String id) {
		getEnvelope().setSourceId(id);
		return this;
	}

	public PacketResponseBuilder body(ByteBuf bb) {
		m_bodySender = new ByteBufPacketSender(bb);
		return this;
	}

	public PacketResponseBuilder after(IExecute after) {
		m_onAfter = after;
		return this;
	}

	public PacketResponseBuilder targetId(String id) {
		getEnvelope().setTargetId(id);
		return this;
	}

	//public PacketResponseBuilder commandId(String id) {
	//	getEnvelope().setCommandId(id);
	//	return this;
	//}

	public void send() {
		CentralSocketHandler socketHandler = m_socketHandler;
		AbstractConnection connection = m_connection;
		if(null != socketHandler) {
			socketHandler.immediateSendResponse(this, m_onAfter);
		} else if(null != connection) {
			TxPacket p = new TxPacket(m_envelope.build(), connection, m_bodySender, m_onAfter);
			connection.sendPacket(p);
		} else {
			throw new IllegalStateException("Response builder not attached to anything");
		}
	}

	//public void send(ByteBuf bb) {
	//	TxPacket p = new TxPacket(m_envelope.build(), m_connection, new ByteBufPacketSender(bb));
	//	m_connection.sendPacket(p);
	//}

}
