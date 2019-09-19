package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNull;
import to.etc.cocos.messages.Hubcore;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 19-09-19.
 */
public class EventCommandError extends EventCommandBase {
	private final String m_code;

	private final String m_details;

	private final String m_message;

	public EventCommandError(@NonNull IRemoteCommand command, Hubcore.CommandError err) {
		super(command);
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
