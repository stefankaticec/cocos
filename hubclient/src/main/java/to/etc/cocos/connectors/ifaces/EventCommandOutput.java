package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 30-09-19.
 */
@NonNullByDefault
final public class EventCommandOutput extends EventCommandBase {
	private final String m_streamName;

	private final String m_output;

	public EventCommandOutput(@NonNull IRemoteCommand command, String streamName, String output) {
		super(command);
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
