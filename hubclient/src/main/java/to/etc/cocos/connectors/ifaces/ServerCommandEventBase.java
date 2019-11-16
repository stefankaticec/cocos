package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNull;
import to.etc.cocos.connectors.server.ServerEventBase;

import java.util.Objects;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 19-09-19.
 */
public class ServerCommandEventBase extends ServerEventBase {
	@NonNull private final IRemoteCommand m_command;

	public ServerCommandEventBase(IServerEventType type, @NonNull IRemoteCommand command) {
		super(type, command.getClient());
		m_command = command;
	}

	@Override
	@NonNull
	public IRemoteClient getClient() {
		return Objects.requireNonNull(super.getClient(), "The client in a command cannot be null");
	}

	@NonNull
	public IRemoteCommand getCommand() {
		return m_command;
	}
}
