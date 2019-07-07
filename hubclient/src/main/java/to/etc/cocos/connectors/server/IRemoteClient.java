package to.etc.cocos.connectors.server;

import org.eclipse.jdt.annotation.NonNull;
import to.etc.cocos.connectors.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 06-07-19.
 */
public interface IRemoteClient {
	@NonNull
	String getClientKey();

	@NonNull
	<I extends JsonPacket> I getInventory(Class<I> packetClass);


}
