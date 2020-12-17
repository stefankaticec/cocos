package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.ifaces.RemoteClientNotPresentException;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.AckableMessage;
import to.etc.cocos.messages.Hubcore.CommandError;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.function.IExecute;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.util.DateUtil;
import to.etc.util.StringTool;
import to.etc.util.TimerUtil;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
	/**
	 * How much time do we "remember" that we've seen a packet (minimal).
	 */
	static private final long SEENSET_KEEPTIME = 60 * 1000;


	static private final long SEND_RETRY_TIME = 5 * 1000L;

	static private final long SEQUENCE_OFFSET = DateUtil.dateFor(2020, 3, 1).getTime();

	private final HubConnectorBase<?> m_connector;

	private final String m_peerId;

	private int m_txSequence;

	private final List<PendingTxPacket> m_txQueue = new ArrayList<>();

	private long m_peerLastPresent;

	@Nullable
	private ScheduledFuture<?> m_timerTask;

	private int m_seenSetIndex;

	private long m_lastSeenWrap;

	private int m_seenSetCount;

	private Set<Integer>[] m_seenSets = new Set[] {
		new HashSet<Integer>(),
		new HashSet<Integer>(),
		new HashSet<Integer>()
	};

	private Set<Integer> m_unseenSet = new HashSet<>();

	private int m_lastSequenceSeen;

	private boolean m_connected;

	public Peer(HubConnectorBase<?> connector, String peerId) {
		if(peerId.isBlank())
			throw new IllegalStateException("Invalid peer ID");
		m_connector = connector;
		m_peerId = peerId;
		long seq = (System.currentTimeMillis() - SEQUENCE_OFFSET) / 1000;	// #of seconds since SEQUENCE_OFFSET initializes sequence ID to make packets unique
		m_txSequence = (int) seq;
	}

	public void send(AckableMessage.Builder packetBuilder, @Nullable IBodyTransmitter bodyTransmitter, Duration expiryDuration, IExecute onSendFailure) {
		send(packetBuilder, bodyTransmitter, expiryDuration, onSendFailure, null);
	}

	public void send(AckableMessage.Builder packetBuilder, @Nullable IBodyTransmitter bodyTransmitter, Duration expiryDuration, IExecute onSendFailure, @Nullable IExecute onAck) {
		long dur = expiryDuration.toMillis();
		long cts = System.currentTimeMillis();
		long peerDisconnectedDuration = m_connector.getPeerDisconnectedDuration();

		PendingTxPacket p;
		synchronized(this) {
			if(! m_connected) {
				if(cts - m_peerLastPresent >= peerDisconnectedDuration)			// Not seen for too long a time -> refuse packet
					throw new RemoteClientNotPresentException("Client " + m_peerId + " is not currently connected");
			}
			while(m_txQueue.size() > m_connector.getMaxQueuedPackets()) {
				if(m_connector.isTransmitBlocking()) {
					try {
						wait(10_000);								// Sleep until the #packets decreases
					} catch(InterruptedException x) {
						throw new RuntimeException(x);
					}
				} else {
					throw new PeerPacketBufferOverflowException(m_peerId + ": too many queued and unacknowledged packets");
				}
			}

			packetBuilder.setSequence(m_txSequence++);							// Assign sequence # to ack
			Envelope env = Hubcore.Envelope.newBuilder()
				.setAckable(packetBuilder)
				.setSourceId(m_connector.getMyId())
				.setTargetId(m_peerId)
				.setVersion(1)
				.build();

			p = new PendingTxPacket(env, bodyTransmitter, cts, cts + dur, cts + SEND_RETRY_TIME, onSendFailure, onAck);
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
					m_connector.log("Ack received for " + p);
					m_txQueue.remove(i);
					try {
						p.callAcked();
					} catch(Exception x) {
						m_connector.log("Packet " + p + " completed, success handler threw exception");
						x.printStackTrace();
					}
					if(m_txQueue.size() == 0)							// Nothing else to do -> cancel timer
						cancelTimer();
					notify();											// More space free, guys'n girls.
					return;
				}
			}
		}
		m_connector.log("Unhandled ack received, sequence# " + sequenceNr);
	}

	/**
	 * Walk all packets and see which ones require work.
	 */
	private void checkTimeouts() {
		long cts = System.currentTimeMillis();

		List<PendingTxPacket> expireList = new ArrayList<>();
		boolean connected = m_connector.getState() == ConnectorState.AUTHENTICATED;
		synchronized(this) {
			for(PendingTxPacket p : new ArrayList<>(m_txQueue)) {
				if(p.getExpiresAt() <= cts) {
					expireList.add(p);
					m_txQueue.remove(p);
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
					try {
						txPacket.callExpired();
					} catch(Exception x) {
						m_connector.log("Packet " + txPacket + " expired, the handler threw " + x);
						x.printStackTrace();
					}
				}
			});
		}
	}

	private void startTimer() {
		synchronized(this) {
			if(m_timerTask != null)										// Already timer task present?
				return;

			m_timerTask = TimerUtil.scheduleAtFixedRate(SEND_RETRY_TIME, SEND_RETRY_TIME, TimeUnit.MILLISECONDS, this::checkTimeouts);
		}
	}

	private void cancelTimer() {
		synchronized(this) {
			ScheduledFuture<?> tt = m_timerTask;
			if(null != tt) {
				m_timerTask = null;
				tt.cancel(true);
			}
		}
	}

	/**
	 * Returns T if the packet with the sequence number specified has already been
	 * seen from this peer. In that case the effect of the packet has already been
	 * done, so we should ignore it.
	 *
	 * Our sequences are initially time based (so they are large) but after that
	 * initial large number they are usually sequential. But if the peer restarts
	 * it will reinit the sequence number to a large number again, and this will leave
	 * a large gap. This makes it hard to use a simple method to detect which
	 * sequences have been seen. We can make a reasonably effective mechanism if
	 * we knew which acks the receiver has seen- but we do not (yet) have that info.
	 *
	 * So, as our sequences are gappy we need to somehow keep the things we've seen
	 * in memory during the time which we can expect retransmits. To do this we need
	 * kind of a thingy that cleans up the sequence#s we've seen once they are older
	 * than this RETRANSMIT_TIME.
	 *
	 * For now we use the following mechanism:
	 * We have three Set&lt;int&gt; in a round-robin list. One of the sets is current,
	 * defined by the m_seenIndex variable. New packets that come in are put into that
	 * current set always if they have not yet been seen.
	 *
	 * To check whether a set is seen we test the current set AND the set before it.
	 * If it is in either the packet is seen. And if not we put it in current to mark
	 * it as seen.
	 *
	 * Clearing "too old" sequences is done by incrementing the current seenSet index
	 * every RETRANSMIT_TIME. The current set then becomes the old set, and the set
	 * that was before the old set is the set of ids to remove: they are just cleared.
	 */
	public boolean seen(int sequence) {
		synchronized(this) {
			Integer seq = Integer.valueOf(sequence);
			int ix = m_seenSetIndex;
			if(m_seenSets[ix].contains(seq))
				return true;
			ix--;									// Move to "older" set
			if(ix < 0)								// Handle wraparound for round-robin
				ix = 2;
			if(m_seenSets[ix].contains(seq))
				return true;

			m_seenSets[m_seenSetIndex].add(seq);	// Has now been seen

			//-- Time for a wraparound?
			if(m_seenSetCount++ > 100) {
				m_seenSetIndex = 0;
				long cts = System.currentTimeMillis();
				if(cts >= m_lastSeenWrap) {
					m_seenSetIndex++;
					if(m_seenSetIndex > 2)
						m_seenSetIndex = 0;
					m_seenSets[ix].clear();			 // ix pointed to the old set, but it is now the expired set
					m_lastSeenWrap = cts + SEENSET_KEEPTIME;
				}
			}
			return false;
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
		send(b, null, getErrorDuration(), () -> {
			m_connector.log("Command error packet send failed: " + src.getTargetId() + " " + code);
		});
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
		send(b, null, getErrorDuration(), () -> {
			m_connector.log("Command error packet send failed: " + src.getTargetId() + " error to send was: " + t);
		});
	}

	private Duration getErrorDuration() {
		return Duration.ofHours(1);
	}

	public void setConnected() {
		synchronized(this) {
			m_connected = true;
			m_peerLastPresent = System.currentTimeMillis();
		}
	}

	public synchronized boolean isConnected() {
		return m_connected;
	}

	public synchronized LocalDateTime getLastPresent() {
		return DateUtil.toLocalDateTime(m_peerLastPresent);
	}

	public void setDisconnected() {
		synchronized(this) {
			m_connected = false;
			m_peerLastPresent = System.currentTimeMillis();
		}
	}
}
