package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.messages.Hubcore.Envelope;

/**
 * A packet that needs to be transmitted and acknowledged.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 29-03-20.
 */
@NonNullByDefault
final class PendingTxPacket {
	private final Envelope m_envelope;

	private final IBodyTransmitter m_bodyTransmitter;

	private final long m_submittedAt;

	private final long m_expiresAt;

	private long m_retryAt;

	public PendingTxPacket(Envelope envelope, IBodyTransmitter bodyTransmitter, long submittedAt, long expiresAt, long retryAt) {
		m_envelope = envelope;
		m_bodyTransmitter = bodyTransmitter;
		m_submittedAt = submittedAt;
		m_expiresAt = expiresAt;
		m_retryAt = retryAt;
	}

	public PendingTxPacket(Envelope envelope, IBodyTransmitter bodyTransmitter) {
		m_envelope = envelope;
		m_bodyTransmitter = bodyTransmitter;
		m_submittedAt = 0;
		m_expiresAt = 0;
	}

	public Envelope getEnvelope() {
		return m_envelope;
	}

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
}
