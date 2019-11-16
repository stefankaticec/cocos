package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNull;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.server.ServerEventType;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 19-09-19.
 */
final public class EvCommandFinished extends ServerCommandEventBase {
	private final String m_dataFormat;

	private final JsonPacket m_packet;

	public EvCommandFinished(@NonNull IRemoteCommand command, String dataFormat, JsonPacket packet) {
		super(ServerEventType.commandFinished, command);
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
