package to.etc.cocos.hub;

import com.google.protobuf.Message;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-1-19.
 */
public class PacketBuilder {
	final private ByteBufAllocator m_allocator;

	final private CompositeByteBuf m_composite;

	final private ByteBuf m_headerBuf;

	private ByteBuf m_current;

	public PacketBuilder(ByteBufAllocator allocator, byte packetType, String target, String source, String command) {
		m_allocator = allocator;
		m_composite = allocator.compositeBuffer();
		m_headerBuf = m_current = allocator.buffer(1024);
		m_composite.addComponent(m_headerBuf);
		m_headerBuf.writeInt(0);						// Start with an empty size
		m_headerBuf.writeByte(packetType);
		string(target);
		string(source);
		string(command);
	}

	public PacketBuilder string(byte[] bs) {
		if(null == bs) {
			m_current.writeByte(0);
			return this;
		}
		int len = bs.length;
		if(len > 255)
			throw new ProtocolViolationException("Byte buffer length is too large: " + len);
		m_current.writeByte(len);
		m_current.writeBytes(bs);
		return this;
	}

	public PacketBuilder string(String s) {
		string(s.getBytes(StandardCharsets.UTF_8));
		return this;
	}

	public ByteBuf getCompleted() {
		int len = m_headerBuf.readableBytes();
		m_headerBuf.setInt(0, len - 4);
		return m_headerBuf;
	}

	/**
	 * Append a protobuf message.
	 */
	public void appendMessage(Message message) {
		byte[] bytes = message.toByteArray();
		m_current.writeBytes(bytes);
	}
}
