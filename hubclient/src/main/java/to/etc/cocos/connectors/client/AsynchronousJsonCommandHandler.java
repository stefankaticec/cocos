package to.etc.cocos.connectors.client;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.CommandFailedException;
import to.etc.cocos.connectors.common.JsonBodyTransmitter;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.common.ProtocolViolationException;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.packets.CancelReasonCode;
import to.etc.cocos.messages.Hubcore.AckableMessage;
import to.etc.cocos.messages.Hubcore.AckableMessage.Builder;
import to.etc.cocos.messages.Hubcore.Command;
import to.etc.cocos.messages.Hubcore.CommandResponse;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.util.WrappedException;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 20-09-19.
 */
@NonNullByDefault
final public class AsynchronousJsonCommandHandler<T extends JsonPacket> implements IClientCommandHandler {
	private final IJsonCommandHandler<T> m_jsonHandler;

	@Nullable
	private String m_cancelReason;

	public AsynchronousJsonCommandHandler(IJsonCommandHandler<T> jsonHandler) {
		m_jsonHandler = jsonHandler;
	}

	@Override final public void execute(CommandContext ctx, List<byte[]> data, Consumer<Throwable> onFinished) throws Exception {
		Throwable error = null;
		try {
			T packet = (T) decodeBody(ctx, data);
			if(null == packet)
				throw new ProtocolViolationException("Missing JSON packet");
			Command cmd = ctx.getSourceEnvelope().getAckable().getCmd();
			ctx.getConnector().getExecutor().execute(() -> {
				Throwable asyError = null;
				try {
					String cancelReason = getCancelReason();
					if(null != cancelReason)
						throw new CommandFailedException("Command was cancelled: " + cancelReason);

					ctx.setStatus(RemoteCommandStatus.RUNNING);
					JsonPacket result = m_jsonHandler.execute(ctx, packet);
					Builder ack = AckableMessage.newBuilder()
						.setResponse(CommandResponse.newBuilder()
							.setName(cmd.getName())
							.setId(cmd.getId())
							.setDataFormat(CommandNames.getJsonDataFormat(result))
						);
					ctx.peer().send(ack, new JsonBodyTransmitter(result), Duration.ofHours(2), (erc) -> {
						ctx.log("ERROR Command response packet send failed with code=" + erc);
					});
				} catch(Exception x) {
					asyError = x;
					ctx.log("Command " + cmd.getName() + " failed: " + x);
					Exception unwrapped = WrappedException.unwrap(x);
					ctx.peer().sendCommandErrorPacket(ctx.getSourceEnvelope(), unwrapped);
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
			if(error != null)
				onFinished.accept(error);
		}
	}

	@Nullable
	private synchronized String getCancelReason() {
		return m_cancelReason;
	}

	/**
	 * As the command only scheduled the command for execution we need to see if it already started...
	 */
	@Override
	public void cancel(CommandContext ctx, CancelReasonCode code, @Nullable String cancelReason) throws Exception {
		System.err.println("AsyncJsonCommand: cancel " + ctx.getId() + ": " + cancelReason);
		synchronized(this) {
			m_cancelReason = cancelReason;					// Make sure that IF it starts to run it will die again
		}

		if(ctx.getStatus() == RemoteCommandStatus.RUNNING) {
			m_jsonHandler.cancel(ctx, code, cancelReason);		// Ask the handler to die
		} else {
			System.err.println("AsyncJsonCommand: cancel " + ctx.getId() + " failed, command is not running");
		}
	}

	@Nullable
	private JsonPacket decodeBody(CommandContext ctx, List<byte[]> data) throws Exception {
		Object o = ctx.getConnector().decodeBody(ctx.getSourceEnvelope().getAckable().getCmd().getDataFormat(), data);
		if(null == o)
			return null;
		if(! (o instanceof JsonPacket))
			throw new ProtocolViolationException(ctx.getSourceEnvelope().getAckable().getCmd().getName() + " expects a JSON packet");
		return (JsonPacket) o;
	}
}
