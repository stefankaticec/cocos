package to.etc.cocos.hub;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.Envelope.PayloadCase;
import to.etc.util.WrappedException;

import java.util.concurrent.CompletableFuture;

/**
 * A packet to be transmitted to a remote as soon as its channel is
 * free.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-07-19.
 */
@NonNullByDefault
final public class TxPacket {
	private final int m_id;

	private final Envelope m_envelope;

	/** The party to tell when sending failed. */
	@Nullable
	private final AbstractConnection m_onBehalfOf;

	private final IPacketBodySender m_bodySender;

	private final CompletableFuture<TxPacket> m_sendFuture = new CompletableFuture<>();

	@Nullable
	private Runnable m_packetRemoveFromQueue;

	private static int m_nextId;

	public TxPacket(Envelope envelope, @Nullable AbstractConnection onBehalfOf, @Nullable IPacketBodySender bodySender, @Nullable IExecute onAfter) {
		if(envelope.getPayloadCase() == PayloadCase.PAYLOAD_NOT_SET)
			throw new IllegalStateException("PAYLOAD NOT SET");
		m_id = nextId();
		m_envelope = envelope;
		m_onBehalfOf = onBehalfOf;
		m_bodySender = bodySender != null ? bodySender : a -> {
			//-- Send a null body
			a.getHeaderBuf().writeInt(0);
		};

		if(onAfter != null) {
			m_sendFuture.thenAccept(txPacket -> {
				try {
					onAfter.execute();
				} catch(Exception x) {
					throw WrappedException.wrap(x);
				}
			});
		}
	}

	static private synchronized int nextId() {
		return ++m_nextId;
	}

	public Envelope getEnvelope() {
		return m_envelope;
	}

	@Nullable
	public AbstractConnection getOnBehalfOf() {
		return m_onBehalfOf;
	}

	public IPacketBodySender getBodySender() {
		return m_bodySender;
	}

	public CompletableFuture<TxPacket> getSendFuture() {
		return m_sendFuture;
	}

	@Nullable
	public synchronized Runnable getPacketRemoveFromQueue() {
		return m_packetRemoveFromQueue;
	}

	public synchronized void setPacketRemoveFromQueue(@Nullable Runnable packetRemoveFromQueue) {
		m_packetRemoveFromQueue = packetRemoveFromQueue;
	}

	@Override
	public String toString() {
		return m_id + ": " + m_envelope.getSourceId() + " -> " + m_envelope.getTargetId() + " " + Hub.getPacketType(m_envelope);
	}
}
