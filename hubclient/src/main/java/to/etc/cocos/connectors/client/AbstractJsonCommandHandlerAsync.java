package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.HubConnectorBase;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.common.ProtocolViolationException;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.messages.Hubcore.Command;
import to.etc.hubserver.protocol.HubException;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 20-09-19.
 */
@NonNullByDefault
abstract public class AbstractJsonCommandHandlerAsync implements IClientCommandHandler {
	abstract protected JsonPacket execute(CommandContext ctx, @NonNull JsonPacket packet) throws Exception;

	@Override final public void execute(CommandContext ctx, List<byte[]> data, Consumer<Throwable> onFinished) throws Exception {
		Throwable error = null;
		try {
			JsonPacket packet = decodeBody(ctx, data);
			if(null == packet)
				throw new ProtocolViolationException("Missing JSON packet");
			Command cmd = ctx.getSourceEnvelope().getCmd();
			ctx.getConnector().getExecutor().execute(() -> {
				Throwable asyError = null;
				try {
					ctx.setStatus(RemoteCommandStatus.RUNNING);
					JsonPacket result = execute(ctx, packet);
					ctx.respondJson(result);
				} catch(HubException hx) {
					asyError = hx;
					ctx.log("Command " + cmd.getName() + " failed: " + hx);
					ctx.respondWithHubErrorPacket(hx);
				} catch(Exception x) {
					asyError = x;
					ctx.log("Command " + cmd.getName() + " failed: " + x);
					try {
						HubConnectorBase.unwrapAndRethrowException(ctx, x);
					} catch(Exception xx) {
						ctx.log("Could not return protocol error: " + xx);
					}
				} catch(Error x) {
					asyError = x;
					throw x;
				} finally {
					onFinished.accept(asyError);
				}
			});
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
