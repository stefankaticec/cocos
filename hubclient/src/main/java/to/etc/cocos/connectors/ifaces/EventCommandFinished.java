package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNull;
import to.etc.cocos.connectors.common.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 19-09-19.
 */
final public class EventCommandFinished extends EventCommandBase {
	private final String m_dataFormat;

	private final JsonPacket m_packet;

	public EventCommandFinished(@NonNull IRemoteCommand command, String dataFormat, JsonPacket packet) {
		super(command);
		m_dataFormat = dataFormat;
		m_packet = packet;
	}

	public String getDataFormat() {
		return m_dataFormat;
	}

	public JsonPacket getPacket() {
		return m_packet;
	}
}
