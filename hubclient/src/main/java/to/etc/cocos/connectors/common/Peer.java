package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.AckableMessage;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.util.TimerUtil;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

/**
 * One of the communication peers. A client has only one peer (the server it talks with), but a server
 * has one peer per client (the client actually _is_ a peer). The peer class handles ackable data by
 * keeping track of ackable packets to send.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 29-03-20.
 */
@NonNullByDefault
public class Peer {
	static private final long SEND_RETRY_TIME = 5 * 1000L;

	private final HubConnectorBase m_connector;

	private final String m_peerId;

	private int m_txSequence = 12;

	private final List<PendingTxPacket> m_txQueue = new ArrayList<>();

	private long m_peerLastPresent;

	@Nullable
	private TimerTask m_timerTask;

	public Peer(HubConnectorBase connector, String peerId) {
		m_connector = connector;
		m_peerId = peerId;
	}

	public void send(AckableMessage.Builder packetBuilder, IBodyTransmitter bodyTransmitter, Duration expiryDuration) throws Exception {
		long dur = expiryDuration.get(ChronoUnit.MILLIS);
		long cts = System.currentTimeMillis();
		long peerDisconnectedDuration = m_connector.getPeerDisconnectedDuration();

		Envelope env = Hubcore.Envelope.newBuilder()
			.setAckable(packetBuilder)
			.setSourceId(m_connector.getMyId())
			.setTargetId(m_peerId)
			.setVersion(1)
			.build();

		PendingTxPacket p;
		synchronized(this) {
			if(cts - m_peerLastPresent >= peerDisconnectedDuration)			// Not seen for too long a time -> refuse packet
				throw new PeerAbsentException(m_peerId);
			while(m_txQueue.size() > m_connector.getMaxQueuedPackets()) {
				if(m_connector.isTransmitBlocking()) {
					wait(10_000);								// Sleep until the #packets decreases
				} else {
					throw new PeerPacketBufferOverflowException(m_peerId + ": too many queued and unacknowledged packets");
				}
			}

			packetBuilder.setSequence(m_txSequence++);						// Assign sequence # to ack
			p = new PendingTxPacket(env, bodyTransmitter, cts, cts + dur, cts + SEND_RETRY_TIME);
			m_txQueue.add(p);
			startTimer();
		}

		trySendPacket(cts, p);
	}

	private void trySendPacket(long cts, PendingTxPacket p) {
		if(m_connector.getState() == ConnectorState.AUTHENTICATED) {
			synchronized(this) {
				p.setRetryAt(cts + SEND_RETRY_TIME);
			}
			m_connector.sendPacketPrimitive(p);
		}
	}

	/**
	 * Remove the packet with this sequence# from the packet send queue (it is acknowledged).
	 */
	void ackReceived(int sequenceNr) {
		synchronized(this) {
			for(int i = m_txQueue.size() - 1; i >= 0; i--) {
				PendingTxPacket p = m_txQueue.get(i);
				if(p.getEnvelope().getAckable().getSequence() == sequenceNr) {
					m_txQueue.remove(i);
					if(m_txQueue.size() == 0)							// Nothing else to do -> cancel timer
						cancelTimer();
					notify();											// More space free, guys'n girls.
					return;
				}
			}
		}
	}

	/**
	 * Walk all packets and see which ones require work.
	 */
	private void checkTimeouts() {
		long cts = System.currentTimeMillis();

		List<PendingTxPacket> expireList = new ArrayList<>();
		boolean connected = m_connector.getState() == ConnectorState.AUTHENTICATED;
		synchronized(this) {
			for(int i = m_txQueue.size() - 1; i >= 0; i--) {
				PendingTxPacket p = m_txQueue.get(i);
				if(p.getExpiresAt() >= cts) {
					expireList.add(p);
					m_txQueue.remove(i);
				} else if(cts >= p.getRetryAt()) {
					p.setRetryAt(cts + SEND_RETRY_TIME);
					if(connected) {
						m_connector.sendPacketPrimitive(p);
					}
				}
			}
		}

		if(expireList.size() > 0) {
			m_connector.getEventExecutor().execute(() -> {
				for(PendingTxPacket txPacket : expireList) {
					txPacket.callExpired();
				}
			});
		}
	}

	private void startTimer() {
		synchronized(this) {
			if(m_timerTask != null)										// Already timer task present?
				return;

			TimerTask tt = m_timerTask = new TimerTask() {
				@Override
				public void run() {
					checkTimeouts();
				}
			};
			TimerUtil.getTimer().schedule(tt, SEND_RETRY_TIME, SEND_RETRY_TIME);
		}
	}

	private void cancelTimer() {
		synchronized(this) {
			TimerTask tt = m_timerTask;
			if(null != tt) {
				m_timerTask = null;
				tt.cancel();
			}
		}
	}
}
