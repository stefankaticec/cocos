package to.etc.cocos.connectors.server;

/**
 * Listen for clients arriving and leaving.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 06-07-19.
 */
public interface IClientListener {
	void clientConnected(IRemoteClient client) throws Exception;

	void clientDisconnected(IRemoteClient client) throws Exception;
}
