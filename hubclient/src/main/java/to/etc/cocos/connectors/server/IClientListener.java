package to.etc.cocos.connectors.server;

import to.etc.hubserver.protocol.HubException;

/**
 * Listen for clients arriving and leaving.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 06-07-19.
 */
public interface IClientListener {
	void clientConnected(IRemoteClient client) throws Exception;

	void clientDisconnected(HubException why) throws Exception;
}
