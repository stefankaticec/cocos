package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.HubConnectorBase;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.common.ProtocolViolationException;
import to.etc.cocos.messages.Hubcore.Command;
import to.etc.hubserver.protocol.HubException;

import java.util.List;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-09-19.
 */
@NonNullByDefault
abstract public class AbstractJsonCommandHandler implements IClientCommandHandler {
	private final boolean m_asynchronous;

	public AbstractJsonCommandHandler(boolean asynchronous) {
		m_asynchronous = asynchronous;
	}

	abstract protected void execute(CommandContext ctx, @Nullable JsonPacket packet) throws Exception;

	@Override final public void execute(CommandContext ctx, List<byte[]> data) throws Exception {
		JsonPacket packet = decodeBody(ctx, data);
		Command cmd = ctx.getSourceEnvelope().getCmd();
		if(m_asynchronous) {
			ctx.getConnector().getExecutor().execute(() -> {
				try {
					execute(ctx, packet);
				} catch(HubException hx) {
					ctx.log("Command " + cmd.getName() + " failed: " + hx);
					ctx.respondWithHubErrorPacket(hx);
				} catch(Exception x) {
					ctx.log("Command " + cmd.getName() + " failed: " + x);
					try {
						HubConnectorBase.unwrapAndRethrowException(ctx, x);
					} catch(Exception xx) {
						ctx.log("Could not return protocol error: " + xx);
					}
				}
			});
		} else {
			execute(ctx, packet);
		}
	}

	@Nullable
	protected JsonPacket decodeBody(CommandContext ctx, List<byte[]> data) throws Exception {
		Object o = ctx.getConnector().decodeBody(ctx.getSourceEnvelope().getCmd().getDataFormat(), data);
		if(null == o)
			return null;
		if(! (o instanceof JsonPacket))
			throw new ProtocolViolationException(ctx.getSourceEnvelope().getCmd().getName() + " expects a JSON packet");
		return (JsonPacket) o;
	}
}
