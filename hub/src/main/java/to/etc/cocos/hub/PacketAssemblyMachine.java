package to.etc.cocos.hub;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.hub.CentralSocketHandler.IReadHandler;
import to.etc.cocos.hub.problems.ProtocolViolationException;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.hubserver.protocol.CommandNames;

import java.util.Objects;

/**
 * This state machine assembles packets, consisting of an envelope and an optional
 * payload from data arriving over the socket. As data arrives it gets collected
 * until we have all the necessary parts, after that it will call the packetReceived
 * handler passed with the fully assembled packet.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 31-03-20.
 */
@NonNullByDefault
final class PacketAssemblyMachine {
	final private IHandlePacket m_packetReceived;

	/**
	 * PacketReaderState: the state for the engine that reads bytes and converts them into packet data.
	 */
	private IReadHandler m_prState = this::prReadHeaderLong;

	@NonNull
	private ByteBuf m_intBuf;

	@NonNull
	final private byte[] m_lenBuf = new byte[4];

	private int pshLength;

	@Nullable
	private byte[] m_envelopeBuffer;

	private int m_envelopeOffset;

	@Nullable
	private Envelope m_envelope;

	private int m_payloadLength;

	@Nullable
	private ByteBuf m_payloadBuffer;

	@FunctionalInterface // Lambda's my ass.
	interface IHandlePacket {
		void handlePacket(@NonNull Envelope envelope, @Nullable ByteBuf payload, int payloadLength) throws Exception;
	}

	public PacketAssemblyMachine(@NonNull IHandlePacket packetReceived, ByteBufAllocator allocator) {
		m_packetReceived = packetReceived;
		m_intBuf = allocator.buffer(4);
	}

	public void handleRead(ChannelHandlerContext context, ByteBuf data) throws Exception {
		m_prState.handleRead(context, data);
	}

	/**
	 * PacketReader: read the header and check it once read.
	 */
	private void prReadHeaderLong(ChannelHandlerContext context, ByteBuf source) {
		m_intBuf.writeBytes(source);
		if(m_intBuf.readableBytes() >= 4) {
			//-- Compare against header
			for(byte b : CommandNames.HEADER) {
				if(b != m_intBuf.readByte()) {
					throw new ProtocolViolationException("Packet header incorrect");
				}
			}

			//-- It worked. Next thing is the envelope length.
			m_prState = this::prReadEnvelopeLength;
		}
	}

	/**
	 * Read the length bytes for the envelope.
	 */
	private void prReadEnvelopeLength(ChannelHandlerContext context, ByteBuf source) {
		m_intBuf.writeBytes(source);
		if(m_intBuf.readableBytes() >= 4) {
			int length = m_intBuf.readInt();
			if(length < 0 || length >= CommandNames.MAX_ENVELOPE_LENGTH) {
				throw new ProtocolViolationException("Envelope length " + length + " is out of limits");
			}
			pshLength = length;
			m_envelopeBuffer = new byte[length];
			m_envelopeOffset = 0;
			m_prState = this::prReadEnvelope;
		}
	}

	/**
	 * With the length from the previous step, collect the envelope data into a byte array
	 * and when finished convert it into the Envelope class.
	 */
	private void prReadEnvelope(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
		int available = byteBuf.readableBytes();
		if(available == 0)
			return;
		int todo = pshLength - m_envelopeOffset;
		if(todo > available) {
			todo = available;
		}

		byteBuf.readBytes(m_envelopeBuffer, m_envelopeOffset, todo);
		m_envelopeOffset += todo;

		//-- All data read?
		if(m_envelopeOffset < pshLength)
			return;

		//-- Create the Envelope
		try {
			m_envelope = Hubcore.Envelope.parseFrom(m_envelopeBuffer);
		} finally {
			m_envelopeBuffer = null;
		}

		m_prState = this::prReadPayloadLength;
	}

	/**
	 * Envelope has been fully obtained and decoded as an Envelope. What follows is the payload length.
	 */
	private void prReadPayloadLength(ChannelHandlerContext channelHandlerContext, ByteBuf source) throws Exception {
		m_intBuf.writeBytes(source);
		if(m_intBuf.readableBytes() >= 4) {
			int length = m_intBuf.readInt();
			if(length < 0 || length >= CommandNames.MAX_DATA_LENGTH) {
				throw new ProtocolViolationException("Packet payload length " + length + " is out of limits");
			}
			pshLength = m_payloadLength = length;
			if(length == 0) {
				//-- Nothing to do: we're just set for another packet.
				m_prState = this::prReadHeaderLong;
				m_payloadBuffer = null;
				m_packetReceived.handlePacket(Objects.requireNonNull(m_envelope), null, 0);
			} else {
				m_payloadBuffer = channelHandlerContext.alloc().buffer(length, CommandNames.MAX_DATA_LENGTH);
				m_prState = this::prReadPayload;
			}
		}
	}

	/**
	 * Copy payload bytes to the payload buffer for this channel until all bytes have been transferred.
	 */
	private void prReadPayload(ChannelHandlerContext channelHandlerContext, ByteBuf source) throws Exception {
		int available = source.readableBytes();
		if(available == 0)
			return;
		int todo = pshLength;
		if(todo > available) {
			todo = available;
		}
		ByteBuf outb = m_payloadBuffer;
		if(null == outb)
			throw new IllegalStateException("No payload buffer in readPayload phase");
		outb.writeBytes(source, todo);
		pshLength -= todo;
		if(pshLength == 0) {
			m_prState = this::prReadHeaderLong;
			m_payloadBuffer = null;
			m_packetReceived.handlePacket(Objects.requireNonNull(m_envelope), outb, m_payloadLength);
		}
	}

	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
	}

	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		m_intBuf.release();
	}
	public void destroy() {
		m_intBuf.release();
	}

}
