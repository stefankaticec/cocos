package to.etc.cocos.hub;

import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope.Builder;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 1-7-19.
 */
final public class ResponseBuilder {
	private final CentralSocketHandler m_handler;

	private final Builder m_envelope = Envelope.newBuilder();

	public ResponseBuilder(CentralSocketHandler handler) {
		m_handler = handler;
	}

	/**
	 * Initialize the response envelope from the source envelope.
	 */
	public void fromEnvelope(Envelope envelope) {
		m_envelope
			.setVersion(envelope.getVersion())
			.setCommand(envelope.getCommand())
			.setCommandId(envelope.getCommandId())
			.setSourceId(envelope.getTargetId())			// Swap src and dest
			.setTargetId(envelope.getSourceId())
			;
	}

	public Builder getEnvelope() {
		return m_envelope;
	}

	public void send() {
		m_handler.sendResponse(this);
	}
}
