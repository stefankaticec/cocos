package to.etc.cocos.hub;

import io.netty.buffer.ByteBuf;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.Envelope.Builder;

import java.util.Objects;

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

	@Nullable
	public IExecute getOnAfter() {
		return m_onAfter;
	}

	public void send() {
		AbstractConnection connection = Objects.requireNonNull(m_connection, "No connection present when sending packet");

		if(getEnvelope().hasAckable()) {
			getEnvelope().getAckableBuilder().setSequence(connection.nextSequence());
		}

		CentralSocketHandler socketHandler = m_socketHandler;
		if(null != socketHandler) {
			TxPacket p = new TxPacket(m_envelope.build(), connection, m_bodySender, m_onAfter);
			socketHandler.immediateSendPacket(p);
			//socketHandler.immediateSendResponse(this, m_onAfter);
		} else {
			TxPacket p = new TxPacket(m_envelope.build(), connection, m_bodySender, m_onAfter);
			connection.sendPacket(p);
		}
	}

	public PacketResponseBuilder forwardTo(Envelope envelope) {
		m_envelope
			.setVersion(envelope.getVersion())
			.setSourceId(envelope.getSourceId())			// Swap src and dest
			.setTargetId(envelope.getTargetId())
		;

		return this;
	}
}
