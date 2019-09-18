package to.etc.cocos.connectors.ifaces;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 25-6-19.
 */
@NonNullByDefault
public interface IDaemon {
	DaemonKey getDaemonKey();

	//@NonNull
	//InventoryPacket getInventory();

	String sendJsonCommand(JsonPacket packet, long commandTimeout, @Nullable String commandKey, String description, IDaemonCommandListener l) throws Exception;
}
