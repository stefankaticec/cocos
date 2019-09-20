package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.common.ProtocolViolationException;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.messages.Hubcore.Command;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-09-19.
 */
@NonNullByDefault
abstract public class AbstractJsonCommandHandler implements IClientCommandHandler {
	abstract protected JsonPacket execute(CommandContext ctx, @Nullable JsonPacket packet) throws Exception;

	@Override final public void execute(CommandContext ctx, List<byte[]> data, Consumer<Throwable> onFinished) throws Exception {
		Throwable error = null;
		try {
			ctx.setStatus(RemoteCommandStatus.RUNNING);
			JsonPacket packet = decodeBody(ctx, data);
			Command cmd = ctx.getSourceEnvelope().getCmd();
			JsonPacket result = execute(ctx, packet);
			ctx.respondJson(result);
		} catch(Exception | Error x) {
			error = x;
			throw x;
		} finally {
			onFinished.accept(error);
		}
	}

	@Nullable
	private JsonPacket decodeBody(CommandContext ctx, List<byte[]> data) throws Exception {
		Object o = ctx.getConnector().decodeBody(ctx.getSourceEnvelope().getCmd().getDataFormat(), data);
		if(null == o)
			return null;
		if(! (o instanceof JsonPacket))
			throw new ProtocolViolationException(ctx.getSourceEnvelope().getCmd().getName() + " expects a JSON packet");
		return (JsonPacket) o;
	}
}
