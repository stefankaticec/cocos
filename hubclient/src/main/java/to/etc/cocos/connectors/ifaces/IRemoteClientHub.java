package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import java.util.List;

/**
 * This is a hub which can be used to get information about and send commands
 * to remote machines connected through the Comet system or its repeater.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
@NonNullByDefault
public interface IRemoteClientHub extends AutoCloseable {
	/**
	 * List all remotely reachable targets.
	 */
	List<String> getClientIdList();

	List<IRemoteClient> getClientList();

	void addListener(IRemoteClientListener l);

	void removeListener(IRemoteClientListener l);

	@Nullable
	IRemoteClient findClient(String id);

	default IRemoteClient getClient(String id) throws RemoteClientNotPresentException {
		IRemoteClient client = findClient(id);
		if(null == client)
			throw new RemoteClientNotPresentException("Client " + id + " is not available");
		return client;
	}

	@Nullable
	IDaemonCommand findCommand(String code);

	@Nullable
	IDaemonCommand findCommand(String clientId, String commandKey);
}
