package to.etc.cocos.connectors.server;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Listen for clients arriving and leaving.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 06-07-19.
 */
@NonNullByDefault
public interface IClientListener {
	void clientConnected(IRemoteClient client) throws Exception;

	void clientDisconnected(IRemoteClient client) throws Exception;
}
