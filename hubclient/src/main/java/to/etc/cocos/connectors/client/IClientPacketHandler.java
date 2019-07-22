package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.CommandContext;
import to.etc.cocos.connectors.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 07-07-19.
 */
@NonNullByDefault
public interface IClientPacketHandler {
	JsonPacket getInventory();

	void executeCommand(CommandContext cc, JsonPacket packet) throws Exception;
}
