package to.etc.cocos.hub;

import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope.Builder;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 1-7-19.
 */
final public class ImmediateResponseBuilder {
	private final CentralSocketHandler m_handler;

	private final Builder m_envelope = Envelope.newBuilder();

	public ImmediateResponseBuilder(CentralSocketHandler handler) {
		m_handler = handler;
	}

	/**
	 * Initialize the response envelope from the source envelope.
	 */
	public ImmediateResponseBuilder fromEnvelope(Envelope envelope) {
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

	public ImmediateResponseBuilder sourceId(String id) {
		getEnvelope().setSourceId(id);
		return this;
	}

	public ImmediateResponseBuilder targetId(String id) {
		getEnvelope().setTargetId(id);
		return this;
	}

	public ImmediateResponseBuilder commandId(String id) {
		getEnvelope().setCommandId(id);
		return this;
	}

	public void send() {
		m_handler.immediateSendResponse(this);
	}
}