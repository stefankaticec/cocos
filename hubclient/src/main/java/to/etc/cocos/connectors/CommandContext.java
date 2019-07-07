package to.etc.cocos.connectors;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.HubException;
import to.etc.puzzler.daemon.rpc.messages.Hubcore;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope.Builder;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.ErrorResponse;
import to.etc.util.StringTool;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
@NonNullByDefault
final public class CommandContext {
	private final HubConnector m_connector;

	private final Hubcore.Envelope m_envelope;

	private final Builder m_responseEnvelope;

	public CommandContext(HubConnector connector, Envelope envelope) {
		m_connector = connector;
		m_envelope = envelope;

		// Create a response envelope with defaults.
		m_responseEnvelope = Hubcore.Envelope.newBuilder()
			.setVersion(envelope.getVersion())
			.setCommand(envelope.getCommand())
			.setCommandId(envelope.getCommandId())
			.setSourceId(envelope.getTargetId())			// Swap src and dest
			.setTargetId(envelope.getSourceId())
			;
	}

	public void respondJson(@NonNull Object jsonPacket) {
		if(jsonPacket == null)
			m_responseEnvelope.setDataFormat("");
		else
			m_responseEnvelope.setDataFormat(CommandNames.BODY_JSON + ":" + jsonPacket.getClass().getName());
		final Envelope envelope = m_responseEnvelope.build();
		m_connector.sendPacket(os -> os.send(envelope, jsonPacket));
	}

	public void respond() {
		m_responseEnvelope.setDataFormat("");
		final Envelope envelope = m_responseEnvelope.build();
		m_connector.sendPacket(os -> os.send(envelope, null));
	}

	public Envelope getSourceEnvelope() {
		return m_envelope;
	}

	public Builder getResponseEnvelope() {
		return m_responseEnvelope;
	}

	public HubConnector getConnector() {
		return m_connector;
	}

	public void log(String s) {
		m_connector.log(s);
	}

	public void error(String s) {
		m_connector.error(s);
	}

	public void respondErrorPacket(ErrorCode code, String details) {
		getResponseEnvelope().setError(ErrorResponse.newBuilder()
			.setCode(code.name())
			.setText(code.getText())
			.setDetails(details)
			.build()
		);
		respond();
	}

	public void respondHubException(HubException t) {
		getResponseEnvelope().setError(ErrorResponse.newBuilder()
			.setCode(t.getCode().name())
			.setText(t.getMessage())
			.setDetails(StringTool.strStacktrace(t))
			.build()
		);
		respond();
	}
}
