package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.function.ConsumerEx;
import to.etc.function.IExecute;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;

/**
 * A packet that needs to be transmitted and acknowledged.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 29-03-20.
 */
@NonNullByDefault
final class PendingTxPacket {
	private final Envelope m_envelope;

	@Nullable
	private final IBodyTransmitter m_bodyTransmitter;

	private final long m_submittedAt;

	private final long m_expiresAt;

	@Nullable
	private final IExecute m_onAcknowledged;

	private long m_retryAt;

	final private ConsumerEx<ErrorCode> m_onSendFailure;

	public PendingTxPacket(Envelope envelope, @Nullable IBodyTransmitter bodyTransmitter, long submittedAt, long expiresAt, long retryAt, ConsumerEx<ErrorCode> onSendFailure, @Nullable IExecute onAcknowledged) {
		m_onSendFailure = onSendFailure;
		if(envelope.getSourceId().length() == 0)
			throw new IllegalStateException("Missing source ID");
		if(envelope.getSourceId().equals(envelope.getTargetId()))
			throw new IllegalStateException("Source and target IDs are the same: " + envelope.getSourceId());
		//if(envelope.getTargetId().length() == 0)
		//	throw new IllegalStateException("Missing target ID");
		m_envelope = envelope;
		m_bodyTransmitter = bodyTransmitter;
		m_submittedAt = submittedAt;
		m_expiresAt = expiresAt;
		m_retryAt = retryAt;
		m_onAcknowledged = onAcknowledged;
	}

	public PendingTxPacket(Envelope envelope, @Nullable IBodyTransmitter bodyTransmitter, ConsumerEx<ErrorCode> onSendFailure, @Nullable IExecute onAcknowledged) {
		if(envelope.getSourceId().length() == 0)
			throw new IllegalStateException("Missing source ID");
		if(envelope.getSourceId().equals(envelope.getTargetId()))
			throw new IllegalStateException("Source and target IDs are the same: " + envelope.getSourceId());

		//if(envelope.getTargetId().length() == 0)
		//	throw new IllegalStateException("Missing target ID");
		m_envelope = envelope;
		m_bodyTransmitter = bodyTransmitter;
		m_submittedAt = 0;
		m_expiresAt = 0;
		m_onSendFailure = onSendFailure;
		m_onAcknowledged = onAcknowledged;
	}

	public Envelope getEnvelope() {
		return m_envelope;
	}

	@Nullable
	public IBodyTransmitter getBodyTransmitter() {
		return m_bodyTransmitter;
	}

	public long getSubmittedAt() {
		return m_submittedAt;
	}

	public long getExpiresAt() {
		return m_expiresAt;
	}

	long getRetryAt() {
		return m_retryAt;
	}

	void setRetryAt(long retryAt) {
		m_retryAt = retryAt;
	}

	public void callFailed(ErrorCode code) throws Exception {
		m_onSendFailure.accept(code);
	}

	@Override
	public String toString() {
		return CommandNames.getPacketName(m_envelope);
	}

	public void callAcked() throws Exception {
		var a = m_onAcknowledged;
		if(a != null) {
			a.execute();
		}
	}
}
