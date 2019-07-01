package to.etc.cocos.hub;

import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import to.etc.hubserver.protocol.CommandNames;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-1-19.
 */
final public class PacketBuilder {
	final private ByteBufAllocator m_allocator;

	final private CompositeByteBuf m_composite;

	final private ByteBuf m_headerBuf;

	private ByteBuf m_current;

	public PacketBuilder(ByteBufAllocator allocator) {
		m_allocator = allocator;
		m_composite = allocator.compositeBuffer();
		m_headerBuf = m_current = allocator.buffer(1024);
		m_composite.addComponent(m_headerBuf);
		m_headerBuf.writeBytes(CommandNames.HEADER);
	}

	public PacketBuilder bytes(byte[] bs) {
		if(null == bs) {
			m_current.writeInt(0);
			return this;
		}
		int len = bs.length;
		m_current.writeInt(len);
		m_current.writeBytes(bs);
		return this;
	}

	public PacketBuilder emptyBody() {
		m_current.writeInt(0);
		return this;
	}

	public ByteBuf getCompleted() {
		return m_composite;
	}

	/**
	 * Append a protobuf message.
	 */
	public PacketBuilder appendMessage(Message message) {
		byte[] bytes = message.toByteArray();
		bytes(bytes);
		return this;
	}
}
