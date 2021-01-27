package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.packets.CancelReasonCode;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-09-19.
 */
@NonNullByDefault
public interface IClientCommandHandler {
	void execute(CommandContext ctx, List<byte[]> data, Consumer<Throwable> onComplete) throws Exception;

	void cancel(CommandContext ctx, CancelReasonCode code, @Nullable String cancelReason) throws Exception;
}
