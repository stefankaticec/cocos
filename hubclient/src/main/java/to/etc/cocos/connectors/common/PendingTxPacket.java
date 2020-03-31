package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.Envelope.PayloadCase;

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

	private long m_retryAt;

	public PendingTxPacket(Envelope envelope, @Nullable IBodyTransmitter bodyTransmitter, long submittedAt, long expiresAt, long retryAt) {
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
	}

	public PendingTxPacket(Envelope envelope, @Nullable IBodyTransmitter bodyTransmitter) {
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

	public void callExpired() {
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(m_envelope.getSourceId()).append("->").append(m_envelope.getTargetId()).append(" ");
		if(m_envelope.getPayloadCase() == PayloadCase.ACKABLE) {
			sb.append(m_envelope.getAckable().getPayloadCase().name());
		} else {
			sb.append(m_envelope.getPayloadCase().name());
		}
		return sb.toString();
	}
}
