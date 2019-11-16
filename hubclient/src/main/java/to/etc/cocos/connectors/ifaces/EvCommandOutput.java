package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.server.ServerEventType;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-09-19.
 */
@NonNullByDefault
final public class EvCommandOutput extends ServerCommandEventBase {
	private final String m_streamName;

	private final String m_output;

	public EvCommandOutput(@NonNull IRemoteCommand command, String streamName, String output) {
		super(ServerEventType.commandOutput, command);
		m_streamName = streamName;
		m_output = output;
	}

	public String getStreamName() {
		return m_streamName;
	}

	public String getOutput() {
		return m_output;
	}
}
