package to.etc.cocos.hub;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.messages.Hubcore.Envelope;

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

	@Nullable
	private final IExecute m_onAfter;

	private final CompletableFuture<TxPacket> m_sendFuture = new CompletableFuture<>();

	public TxPacket(Envelope envelope, AbstractConnection onBehalfOf, @Nullable IPacketBodySender bodySender, @Nullable IExecute onAfter) {
		m_envelope = envelope;
		m_onBehalfOf = onBehalfOf;
		m_bodySender = bodySender != null ? bodySender : a -> {
			//-- Send a null body
			a.getHeaderBuf().writeInt(0);
		};
		m_onAfter = onAfter;
	}

	//public TxPacket(Envelope envelope, AbstractConnection onBehalfOf) {
	//	this(envelope, onBehalfOf, null);
	//}

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
