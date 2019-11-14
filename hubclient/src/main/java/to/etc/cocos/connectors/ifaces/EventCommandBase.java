package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNull;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 19-09-19.
 */
public class EventCommandBase {
	@NonNull private final IRemoteCommand m_command;

	public EventCommandBase(@NonNull IRemoteCommand command) {
		m_command = command;
	}

	@NonNull
	public IRemoteCommand getCommand() {
		return m_command;
	}
}
