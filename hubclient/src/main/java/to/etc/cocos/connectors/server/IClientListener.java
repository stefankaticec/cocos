package to.etc.cocos.connectors.server;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.JsonPacket;
import to.etc.cocos.connectors.RemoteClient;

/**
 * Listen for clients arriving and leaving.
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 06-07-19.
 */
@NonNullByDefault
public interface IClientListener {
	void clientConnected(RemoteClient client) throws Exception;

	void clientDisconnected(RemoteClient client) throws Exception;

	void clientInventoryPacketReceived(RemoteClient client, JsonPacket packet);
}
