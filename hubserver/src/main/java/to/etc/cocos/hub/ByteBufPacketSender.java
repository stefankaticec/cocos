package to.etc.cocos.hub;

import io.netty.buffer.ByteBuf;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 15-07-19.
 */
@NonNullByDefault
public class ByteBufPacketSender implements IPacketBodySender {
	private final ByteBuf m_bb;

	public ByteBufPacketSender(ByteBuf bb) {
		m_bb = bb;
	}

	@Override public void sendBody(BufferWriter handler) throws Exception {
		int len = m_bb.readableBytes();
		handler.getHeaderBuf().writeInt(len);
		handler.addBuffer(m_bb);
	}
}
