package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNull;
import to.etc.cocos.connectors.server.ServerEventType;
import to.etc.cocos.messages.Hubcore;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 19-09-19.
 */
public class EvCommandError extends ServerCommandEventBase {
	private final String m_code;

	private final String m_details;

	private final String m_message;

	public EvCommandError(@NonNull IRemoteCommand command, Hubcore.CommandError err) {
		super(ServerEventType.commandError, command);
		m_code = err.getCode();
		m_details = err.getDetails();
		m_message = err.getMessage();
	}

	public String getCode() {
		return m_code;
	}

	public String getDetails() {
		return m_details;
	}

	public String getMessage() {
		return m_message;
	}
}
