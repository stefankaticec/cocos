package to.etc.cocos.hub;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;

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
	private final Envelope m_envelope;

	/** The party to tell when sending failed. */
	private final AbstractConnection m_onBehalfOf;

	private final IPacketBodySender m_bodySender;

	private final CompletableFuture<TxPacket> m_sendFuture = new CompletableFuture<>();

	public TxPacket(Envelope envelope, AbstractConnection onBehalfOf, IPacketBodySender bodySender) {
		m_envelope = envelope;
		m_onBehalfOf = onBehalfOf;
		m_bodySender = bodySender;
	}

	public TxPacket(Envelope envelope, AbstractConnection onBehalfOf) {
		m_envelope = envelope;
		m_onBehalfOf = onBehalfOf;
		m_bodySender = a -> {
			//-- Send a null body
			a.getHeaderBuf().writeInt(0);
		};
	}

	public Envelope getEnvelope() {
		return m_envelope;
	}

	public AbstractConnection getOnBehalfOf() {
		return m_onBehalfOf;
	}

	public IPacketBodySender getBodySender() {
		return m_bodySender;
	}

	public CompletableFuture<TxPacket> getSendFuture() {
		return m_sendFuture;
	}
}
