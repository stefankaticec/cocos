package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 15-5-19.
 */
@NonNullByDefault
public interface IRemoteClientListener {
	void clientRegistered(IRemoteClient handle);

	void clientDisconnected(IRemoteClient handle);

	//void inventoryReceived(IRemoteClient handle, InventoryPacket inv);
}
