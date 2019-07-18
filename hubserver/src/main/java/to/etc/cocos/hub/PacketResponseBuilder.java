package to.etc.cocos.hub;

import io.netty.buffer.ByteBuf;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope.Builder;

/**
 * This is the normal packet builder, sending packets after
 * the connection has been established.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 1-7-19.
 */
final public class PacketResponseBuilder {
	private final Builder m_envelope = Envelope.newBuilder();

	private final AbstractConnection m_connection;

	public PacketResponseBuilder(AbstractConnection connection) {
		m_connection = connection;
	}

	/**
	 * Initialize the response envelope from the source envelope.
	 */
	public PacketResponseBuilder fromEnvelope(Envelope envelope) {
		m_envelope
			.setVersion(envelope.getVersion())
			.setCommand(envelope.getCommand())
			.setCommandId(envelope.getCommandId())
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

	public PacketResponseBuilder dataFormat(String format) {
		getEnvelope().setDataFormat(format);
		return this;
	}

	public PacketResponseBuilder targetId(String id) {
		getEnvelope().setTargetId(id);
		return this;
	}

	public PacketResponseBuilder commandId(String id) {
		getEnvelope().setCommandId(id);
		return this;
	}

	public void send() {
		TxPacket p = new TxPacket(m_envelope.build(), m_connection);
		m_connection.sendPacket(p);
	}

	public void send(ByteBuf bb) {
		TxPacket p = new TxPacket(m_envelope.build(), m_connection, new ByteBufPacketSender(bb));
		m_connection.sendPacket(p);
	}

}
