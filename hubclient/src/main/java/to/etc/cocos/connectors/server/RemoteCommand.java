package to.etc.cocos.connectors.server;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.ifaces.IRemoteCommandListener;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 22-07-19.
 */
@NonNullByDefault
final public class RemoteCommand {
	private final String m_commandId;

	private final String m_clientKey;

	private final long m_commandTimeout;

	@Nullable
	private final String m_commandKey;

	private final String m_description;

	private CopyOnWriteArrayList<IRemoteCommandListener> m_listeners = new CopyOnWriteArrayList<>();

	public RemoteCommand(String commandId, String clientKey, long commandTimeout, @Nullable String commandKey, String description) {
		m_commandId = commandId;
		m_clientKey = clientKey;
		m_commandTimeout = commandTimeout;
		m_commandKey = commandKey;
		m_description = description;
	}

	public void addListener(IRemoteCommandListener l) {
		m_listeners.add(l);
	}

	public void removeListener(IRemoteCommandListener l) {
		m_listeners.remove(l);
	}

	public String getCommandId() {
		return m_commandId;
	}

	public String getClientKey() {
		return m_clientKey;
	}

	public long getCommandTimeout() {
		return m_commandTimeout;
	}

	@Nullable
	public String getCommandKey() {
		return m_commandKey;
	}

	public String getDescription() {
		return m_description;
	}

	public CopyOnWriteArrayList<IRemoteCommandListener> getListeners() {
		return m_listeners;
	}
}
