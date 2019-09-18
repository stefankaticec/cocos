package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 15-5-19.
 */
@NonNullByDefault
public interface IDaemonListener {
	void clientRegistered(IDaemon handle);

	void clientDisconnected(IDaemon handle);

	//void inventoryReceived(IRemoteClient handle, InventoryPacket inv);
}
