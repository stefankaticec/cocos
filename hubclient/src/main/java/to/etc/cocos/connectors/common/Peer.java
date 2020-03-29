package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.AckableMessage;
import to.etc.cocos.messages.Hubcore.CommandError;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.util.StringTool;
import to.etc.util.TimerUtil;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

	private Set<Integer> m_unseenSet = new HashSet<>();

	private int m_lastSequenceSeen;

	public Peer(HubConnectorBase connector, String peerId) {
		m_connector = connector;
		m_peerId = peerId;
	}

	public void send(AckableMessage.Builder packetBuilder, IBodyTransmitter bodyTransmitter, Duration expiryDuration) {
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
					try {
						wait(10_000);                                // Sleep until the #packets decreases
					} catch(InterruptedException x) {
						throw new RuntimeException(x);
					}
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

	/**
	 * Returns T if the packet with the sequence number specified has already been
	 * seen from this peer. In that case the effect of the packet has already been
	 * done, so we should ignore it.
	 *
	 * Algorithm: we keep a highest sequence number plus a set of values BELOW
	 * that highest number that is not yet seen. When a new sequence comes in, if
	 * it is lower than the sequence it is unseen only if it is in that set. In that
	 * case remove it from that set.
	 * If the number is > max then we add the missing numbers to the unseen set.
	 * As we expect all ids to be seen this mechanism will empty the set as seq#s
	 * arrive.
	 */
	public boolean seen(int sequence) {
		synchronized(this) {
			if(sequence > m_lastSequenceSeen) {
				if(sequence - m_lastSequenceSeen > 100)
					throw new IllegalStateException("Too many missing packet sequences");
				//-- Add "Unseen" records for all numbers from lastSequence up to sequence
				for(int i = m_lastSequenceSeen + 1; i < sequence; i++) {
					m_unseenSet.add(i);
				}
				m_lastSequenceSeen = sequence;
				return false;
			}

			if(m_unseenSet.remove(sequence)) {
				return true;
			}
			return true;
		}
	}

	public void sendCommandErrorPacket(Envelope src, ErrorCode code, Object... params) {
		String message = MessageFormat.format(code.getText(), params);
		CommandError cmdE = CommandError.newBuilder()
			.setId(src.getAckable().getCmd().getId())
			.setName(src.getAckable().getCmd().getName())
			.setCode(code.name())
			.setMessage(message)
			.build();
		var b = AckableMessage.newBuilder()
			.setCommandError(cmdE)
			;
		send(b, null, getErrorDuration());
	}

	public void sendCommandErrorPacket(Envelope src, Throwable t) {
		String message = "Exception in command: " + t.toString();
		CommandError cmdE = CommandError.newBuilder()
			.setId(src.getAckable().getCmd().getId())
			.setName(src.getAckable().getCmd().getName())
			.setCode(ErrorCode.commandException.name())
			.setMessage(message)
			.setDetails(StringTool.strStacktrace(t))
			.build();
		var b = AckableMessage.newBuilder()
			.setCommandError(cmdE)
			;
		send(b, null, getErrorDuration());
	}

	private Duration getErrorDuration() {
		return Duration.ofHours(1);
	}


}
