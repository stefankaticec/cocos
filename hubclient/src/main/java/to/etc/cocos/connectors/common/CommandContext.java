package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.client.IClientCommandHandler;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.packets.CancelReasonCode;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.AckableMessage;
import to.etc.cocos.messages.Hubcore.Command;
import to.etc.cocos.messages.Hubcore.CommandOutput;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.Envelope.Builder;

import java.time.Duration;
import java.time.Instant;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-6-19.
 */
@NonNullByDefault
final public class CommandContext {
	private final HubConnectorBase<?> m_connector;

	private final Hubcore.Envelope m_envelope;

	private final Builder m_responseEnvelope;

	private Peer m_peer;

	private RemoteCommandStatus m_status = RemoteCommandStatus.SCHEDULED;

	private int m_stdoutPacketNumber;

	@Nullable
	private IClientCommandHandler m_handler;

	@Nullable
	private String m_cancelReason;

	@Nullable
	private Instant m_startedAt;

	@Nullable
	private Instant m_finishedAt;

	@Nullable
	private CancelReasonCode m_cancelCode;

	public CommandContext(HubConnectorBase<?> connector, Envelope envelope, Peer peer) {
		m_connector = connector;
		m_envelope = envelope;

		// Create a response envelope with defaults.
		m_responseEnvelope = Hubcore.Envelope.newBuilder()
			.setVersion(envelope.getVersion())
			.setSourceId(envelope.getTargetId())			// Swap src and dest
			.setTargetId(envelope.getSourceId())
			;
		m_peer = peer;
	}

	public Peer peer() {
		return m_peer;
	}

	public String getId() {
		if(! m_envelope.hasAckable() && ! m_envelope.getAckable().hasCmd())
			throw new IllegalStateException("This is not a command");
		return m_envelope.getAckable().getCmd().getId();
	}

	//public void respondJson(PacketPrio prio, @NonNull Object jsonPacket) {
	//	final Envelope envelope = m_responseEnvelope.build();
	//	m_connector.sendPacketPrimitive(prio, envelope, jsonPacket);
	//}
	//
	//public void respond(PacketPrio prio) {
	//	final Envelope envelope = m_responseEnvelope.build();
	//	m_connector.sendPacketPrimitive(prio, envelope, null);
	//}
	//
	public Envelope getSourceEnvelope() {
		return m_envelope;
	}

	public Builder getResponseEnvelope() {
		return m_responseEnvelope;
	}

	public HubConnectorBase<?> getConnector() {
		return m_connector;
	}

	public void log(String s) {
		m_connector.log(s);
	}

	public void error(String s) {
		m_connector.error(s);
	}

	public void markAsStarted() {
		synchronized(m_connector) {
			m_startedAt = Instant.now();
		}
	}

	public void markAsFinished() {
		synchronized(m_connector) {
			m_finishedAt = Instant.now();
		}
	}

	@Nullable
	public Instant getStartedAt() {
		synchronized(m_connector) {
			return m_startedAt;
		}
	}

	@Nullable
	public Instant getFinishedAt() {
		synchronized(m_connector) {
			return m_finishedAt;
		}
	}

	//public void respondWithHubErrorPacket(PacketPrio prio, ErrorCode code, String details) {
	//	getResponseEnvelope().setHubError(HubErrorResponse.newBuilder()
	//		.setCode(code.name())
	//		.setText(code.getText())
	//		.setDetails(details)
	//		.build()
	//	);
	//	respond(prio);
	//}
	//
	//public void respondWithHubErrorPacket(PacketPrio prio, HubException t) {
	//	getResponseEnvelope().setHubError(HubErrorResponse.newBuilder()
	//		.setCode(t.getCode().name())
	//		.setText(t.getMessage())
	//		.setDetails(StringTool.strStacktrace(t))
	//		.build()
	//	);
	//	respond(prio);
	//}

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

	//public void respondCommandErrorPacket(Exception x) {
	//	getConnector().sendCommandErrorPacket(this, x);
	//}

	public void sendStdoutPacket(String s) {
		System.out.println("stdout> " + s);
		Command cmd = m_envelope.getAckable().getCmd();

		//-- Create the JSON packet body
		AckableMessage.Builder ackable = AckableMessage.newBuilder()
			.setOutput(
				CommandOutput.newBuilder()
					.setCode("stdout")
					.setId(cmd.getId())
					.setName(cmd.getName())
					.setEncoding("utf-8")
					.setSequence(nextSequenceNumber())

			);

		peer().send(ackable, new StringBodyTransmitter(s), Duration.ofHours(1), (erc) -> {
			m_connector.log("STDOUT packet send failed with error code=" + erc);
		});
	}

	public void setHandler(@Nullable IClientCommandHandler handler) {
		synchronized(m_connector) {
			if(m_cancelReason != null) {
				throw new CommandFailedException("The command was cancelled: " + m_cancelReason + " (" + m_cancelCode +")");
			}
			m_handler = handler;
		}
	}

	@Nullable
	public IClientCommandHandler prepareCancellation(CancelReasonCode code, String cancelReason) {
		synchronized(m_connector) {
			m_cancelReason = cancelReason;
			m_cancelCode = code;
			return m_handler;
		}
	}

	@Override
	public String toString() {
		return getId();
	}
}
