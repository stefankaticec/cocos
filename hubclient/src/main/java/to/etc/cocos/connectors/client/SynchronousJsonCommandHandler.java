package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.common.ProtocolViolationException;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.messages.Hubcore.Command;
import to.etc.hubserver.protocol.CommandNames;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-09-19.
 */
@NonNullByDefault
final public class SynchronousJsonCommandHandler<T extends JsonPacket> implements IClientCommandHandler {
	private final IJsonCommandHandler<T> m_jsonHandler;

	public SynchronousJsonCommandHandler(IJsonCommandHandler<T> jsonHandler) {
		m_jsonHandler = jsonHandler;
	}

	@Override final public void execute(CommandContext ctx, List<byte[]> data, Consumer<Throwable> onFinished) throws Exception {
		Throwable error = null;
		try {
			ctx.setStatus(RemoteCommandStatus.RUNNING);
			T packet = (T) decodeBody(ctx, data);
			if(null == packet)
				throw new ProtocolViolationException("Null packet in command");
			Command cmd = ctx.getSourceEnvelope().getCmd();
			JsonPacket result = m_jsonHandler.execute(ctx, packet);
			ctx.getResponseEnvelope()
				.getResponseBuilder()
				.setName(cmd.getName())
				.setId(cmd.getId())
				.setDataFormat(CommandNames.getJsonDataFormat(result));
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
