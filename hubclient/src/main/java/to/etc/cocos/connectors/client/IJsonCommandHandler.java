package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.JsonPacket;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-09-19.
 */
@NonNullByDefault
public interface IJsonCommandHandler<T extends JsonPacket> {
	JsonPacket execute(CommandContext ctx, T packet) throws Exception;
}
