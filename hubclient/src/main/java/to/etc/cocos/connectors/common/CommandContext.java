package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.client.IClientCommandHandler;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.Command;
import to.etc.cocos.messages.Hubcore.CommandOutput;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.Envelope.Builder;
import to.etc.cocos.messages.Hubcore.HubErrorResponse;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.HubException;
import to.etc.util.StringTool;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
@NonNullByDefault
final public class CommandContext {
	private final HubConnectorBase m_connector;

	private final Hubcore.Envelope m_envelope;

	private final Builder m_responseEnvelope;

	private RemoteCommandStatus m_status = RemoteCommandStatus.SCHEDULED;

	private int m_stdoutPacketNumber;

	@Nullable
	private IClientCommandHandler m_handler;

	@Nullable
	private String m_cancelReason;

	public CommandContext(HubConnectorBase connector, Envelope envelope) {
		m_connector = connector;
		m_envelope = envelope;

		// Create a response envelope with defaults.
		m_responseEnvelope = Hubcore.Envelope.newBuilder()
			.setVersion(envelope.getVersion())
			.setSourceId(envelope.getTargetId())			// Swap src and dest
			.setTargetId(envelope.getSourceId())
			;
	}

	public String getId() {
		if(! m_envelope.hasCmd())
			throw new IllegalStateException("This is not a command");
		return m_envelope.getCmd().getId();
	}

	public void respondJson(@NonNull Object jsonPacket) {
		final Envelope envelope = m_responseEnvelope.build();
		m_connector.sendPacket(envelope, jsonPacket);
	}

	public void respond() {
		final Envelope envelope = m_responseEnvelope.build();
		m_connector.sendPacket(envelope, null);
	}

	public Envelope getSourceEnvelope() {
		return m_envelope;
	}

	public Builder getResponseEnvelope() {
		return m_responseEnvelope;
	}

	public HubConnectorBase getConnector() {
		return m_connector;
	}

	public void log(String s) {
		m_connector.log(s);
	}

	public void error(String s) {
		m_connector.error(s);
	}

	public void respondWithHubErrorPacket(ErrorCode code, String details) {
		getResponseEnvelope().setHubError(HubErrorResponse.newBuilder()
			.setCode(code.name())
			.setText(code.getText())
			.setDetails(details)
			.build()
		);
		respond();
	}

	public void respondWithHubErrorPacket(HubException t) {
		getResponseEnvelope().setHubError(HubErrorResponse.newBuilder()
			.setCode(t.getCode().name())
			.setText(t.getMessage())
			.setDetails(StringTool.strStacktrace(t))
			.build()
		);
		respond();
	}

	public RemoteCommandStatus getStatus() {
		synchronized(m_connector) {
			return m_status;
		}
	}

	public void setStatus(RemoteCommandStatus status) {
		synchronized(m_connector) {
			m_status = status;
		}
	}

	private synchronized int nextSequenceNumber() {
		return m_stdoutPacketNumber++;
	}

	public void respondCommandErrorPacket(Exception x) {
		getConnector().sendCommandErrorPacket(this, x);
	}

	public void sendStdoutPacket(String s) {
		System.out.println("stdout> " + s);
		Command cmd = m_envelope.getCmd();
		Envelope envelope = Envelope.newBuilder()
			.setVersion(m_envelope.getVersion())
			.setSourceId(m_envelope.getTargetId())            // Swap src and dest
			.setTargetId(m_envelope.getSourceId())
			.setOutput(CommandOutput.newBuilder()
				.setCode("stdout")
				.setId(cmd.getId())
				.setName(cmd.getName())
				.setEncoding("utf-8")
				.setSequence(nextSequenceNumber())
			).build();

		//-- Create the JSON packet body
		m_connector.sendPacket(os -> os.sendString(envelope, s));
	}

	public synchronized void setHandler(@Nullable IClientCommandHandler handler) {
		if(m_cancelReason != null) {
			throw new CommandFailedException("The command was cancelled: " + m_cancelReason);
		}
		m_handler = handler;
	}

	@Nullable
	public synchronized IClientCommandHandler prepareCancellation(String cancelReason) {
		m_cancelReason = cancelReason;
		return m_handler;
	}
}
