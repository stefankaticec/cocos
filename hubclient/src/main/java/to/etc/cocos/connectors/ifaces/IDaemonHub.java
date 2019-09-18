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
public interface IDaemonHub extends AutoCloseable {
	/**
	 * List all remotely reachable targets.
	 */
	List<DaemonKey> getClientKeyList();

	List<IDaemon> getClientList();

	void addListener(IDaemonListener l);

	void removeListener(IDaemonListener l);

	@Nullable
	IDaemon findClient(DaemonKey key);

	default IDaemon getClient(DaemonKey key) throws DaemonNotPresentException {
		IDaemon client = findClient(key);
		if(null == client)
			throw new DaemonNotPresentException("Client " + key + " is not available");
		return client;
	}

	@Nullable
	IDaemonCommand findCommand(String code);

	@Nullable
	IDaemonCommand findCommand(DaemonKey server, String commandKey);
}
