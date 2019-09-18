package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.server.RemoteClient;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 15-5-19.
 */
@NonNullByDefault
public interface IRemoteClientListener {
	void clientConnected(IRemoteClient handle) throws Exception;

	void clientDisconnected(IRemoteClient handle) throws Exception;

	void clientInventoryPacketReceived(RemoteClient client, JsonPacket packet) throws Exception;
	//void inventoryReceived(IRemoteClient handle, JsonPacket inv);
}
