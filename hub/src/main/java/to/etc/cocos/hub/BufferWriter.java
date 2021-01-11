package to.etc.cocos.hub;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import java.io.OutputStream;
import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-07-19.
 */
@NonNullByDefault
final public class BufferWriter implements AutoCloseable {
	private final List<ByteBuf> m_list;

	final private ByteBuf m_headerBuf;

	@Nullable
	private ByteBufOutputStream m_os;

	public BufferWriter(List<ByteBuf> list, ByteBuf headerBuf) {
		m_list = list;
		m_headerBuf = headerBuf;
	}

	public BufferWriter addBuffer(ByteBuf buffer) {
		m_list.add(buffer);
		return this;
	}

	public ByteBuf getHeaderBuf() {
		return m_headerBuf;
	}

	public OutputStream getOutputStream() {
		ByteBufOutputStream os = m_os;
		if(null == os) {
			os = m_os = new ByteBufOutputStream(m_headerBuf);
		}
		return os;
	}

	@Override
	public void close() {
		ByteBufOutputStream os = m_os;
		if(null != os) {
			try {
				os.close();
			} catch(Exception x) {
				System.err.println("BufferWriter: " + x);
				x.printStackTrace();
			}
			m_os = os;
		}
	}
}
