package to.etc.cocos.connectors.common;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 29-03-20.
 */
@NonNullByDefault
final public class JsonBodyTransmitter implements IBodyTransmitter {
	private final JsonPacket m_packet;

	public JsonBodyTransmitter(JsonPacket packet) {
		m_packet = packet;
	}

	@Override
	public void sendBody(@NonNull PacketWriter os) throws Exception {
		os.writeJsonObject(m_packet);
	}
}
