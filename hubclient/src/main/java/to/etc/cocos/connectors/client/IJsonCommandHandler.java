package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.packets.CancelReasonCode;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-09-19.
 */
@NonNullByDefault
public interface IJsonCommandHandler<T extends JsonPacket> {
	JsonPacket execute(CommandContext ctx, T packet) throws Exception;

	default void cancel(CommandContext ctx, CancelReasonCode code, @Nullable String cancelReason) throws Exception {}
}
