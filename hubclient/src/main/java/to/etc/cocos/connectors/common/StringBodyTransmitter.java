package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 29-03-20.
 */
final public class StringBodyTransmitter implements IBodyTransmitter {
	@Nullable
	private final String m_value;

	public StringBodyTransmitter(@Nullable String value) {
		m_value = value;
	}

	@Override
	public void sendBody(@NonNull PacketWriter os) throws Exception {
		os.writeStringObject(m_value);
	}
}
