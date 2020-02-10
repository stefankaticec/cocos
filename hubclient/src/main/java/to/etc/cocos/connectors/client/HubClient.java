package to.etc.cocos.connectors.client;

import com.google.protobuf.ByteString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.HubConnectorBase;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.common.ProtocolViolationException;
import to.etc.cocos.connectors.common.Synchronous;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.packets.CancelPacket;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.Command;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.HubErrorResponse;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 23-1-19.
 */
@NonNullByDefault
final public class HubClient extends HubConnectorBase {
	private final String m_clientVersion = "HubClient 1.0";

	private final String m_targetCluster;

	private final IClientAuthenticationHandler m_authHandler;

	private final Map<String, Supplier<IClientCommandHandler>> m_commandHandlerMap = new HashMap<>();

	private final Map<String, CommandContext> m_commandMap = new ConcurrentHashMap<>();

	private HubClient(String hubServer, int hubServerPort, IClientAuthenticationHandler authHandler, String targetClusterAndOrg, String myId) {
		super(hubServer, hubServerPort, targetClusterAndOrg, myId, "Client");
		m_authHandler = authHandler;
		if(targetClusterAndOrg.indexOf('@') != -1)
			throw new IllegalStateException("The target for a client must be in the format 'organisation#cluster' or just a cluster name");
		m_targetCluster = targetClusterAndOrg;
	}

	static public HubClient create(IClientAuthenticationHandler handler, String hubServer, int hubServerPort, String targetClusterAndOrg, String myId) {
		HubClient responder = new HubClient(hubServer, hubServerPort, handler, targetClusterAndOrg, myId);
		responder.registerJsonCommandAsync(CancelPacket.class, new CancelCommand(responder));
		return responder;
	}

	@Override protected void handlePacketReceived(CommandContext ctx, List<byte[]> data) throws Exception {
		switch(ctx.getSourceEnvelope().getPayloadCase()) {
			default:
				throw new ProtocolViolationException("Unexpected packet type=" + ctx.getSourceEnvelope().getPayloadCase());

			case CHALLENGE:
				handleHELO(ctx);
				break;

			case AUTH:
				handleAUTH(ctx);
				break;

			case CMD:
				handleCommand(ctx, data);
				break;

			case HUBERROR:
				onErrorPacket(ctx.getSourceEnvelope());
				forceDisconnect("Hub error: " + ctx.getSourceEnvelope().getHubError().getText());
				break;
		}
	}

	public synchronized void registerCommand(String commandName, Supplier<IClientCommandHandler> handler) {
		if(null != m_commandHandlerMap.put(commandName, handler))
			throw new IllegalStateException("Duplicate command name registered: " + commandName);
	}

	public synchronized <T extends JsonPacket> void registerJsonCommand(Class<T> packet, IJsonCommandHandler<T> handler) {
		registerCommand(packet.getName(), () -> new SynchronousJsonCommandHandler<T>(handler));
	}

	public synchronized <T extends JsonPacket> void registerJsonCommandAsync(Class<T> packet, IJsonCommandHandler<T> handler) {
		registerCommand(packet.getName(), () -> new AsynchronousJsonCommandHandler<T>(handler));
	}

	@Nullable
	private synchronized IClientCommandHandler findCommandHandler(String commandName) {
		Supplier<IClientCommandHandler> factory = m_commandHandlerMap.get(commandName);
		if(null == factory)
			return null;
		return factory.get();
	}

	private void handleCommand(CommandContext ctx, List<byte[]> data) throws Exception {
		Command cmd = ctx.getSourceEnvelope().getCmd();
		IClientCommandHandler commandHandler = findCommandHandler(cmd.getName());
		if(null == commandHandler) {
			ctx.error("No command handler for " + cmd.getName());
			sendCommandErrorPacket(ctx, ErrorCode.commandNotFound, cmd.getName());
			return;
		}
		ctx.log("Running handler for " + cmd.getName());

		m_commandMap.put(ctx.getId(), ctx);
		ctx.setHandler(commandHandler);
		try {
			commandHandler.execute(ctx, data, throwable -> {
				synchronized(this) {
					ctx.setStatus(throwable == null ? RemoteCommandStatus.FINISHED : RemoteCommandStatus.FAILED);
					m_commandMap.remove(ctx.getId());
				}
			});
		} catch(Exception x) {
			ctx.log("Command " + cmd.getName() + " failed: " + x);
			x.printStackTrace();
			sendCommandErrorPacket(ctx, x);
		}
	}

	public void cancelCommand(String commandId, String cancelReason) throws Exception {
		CommandContext commandContext = m_commandMap.get(commandId);
		if(null == commandContext) {							// Not there: command is cancelled or has finished before.
			error("Cancel command failed: id " + commandId + " not found");
			return;
		}
		IClientCommandHandler handler = commandContext.prepareCancellation(cancelReason);
		if(null == handler) {
			error("Cancel command failed: no handler returned for id=" + commandId);
			return;												// No handler: not running yet, but marked for cancellation as soon as it tries to run.
		}
		handler.cancel(commandContext, cancelReason);			// Ask the thing to cancel
	}

	/**
	 * Respond with a Client HELO response. This encodes the challenge with the password, or something.
	 */
	@Synchronous
	public void handleHELO(CommandContext cc) throws Exception {
		System.out.println("Got HELO request");
		ByteString ba = cc.getSourceEnvelope().getChallenge().getChallenge();
		byte[] challenge = ba.toByteArray();

		byte[] response = m_authHandler.createAuthenticationResponse(cc.getConnector().getMyId(), challenge);

		cc.getResponseEnvelope()
			.setSourceId(cc.getConnector().getMyId())
			.setVersion(1)
			.setTargetId(m_targetCluster)
			.setHeloClient(Hubcore.ClientHeloResponse.newBuilder()
				.setChallengeResponse(ByteString.copyFrom(response))
				.setClientVersion(m_clientVersion)
				.build()
			);
		cc.respond();
	}

	/**
	 * If the authorization was successful we receive this; move to AUTHORIZED status.
	 */
	@Synchronous
	public void handleAUTH(CommandContext cc) throws Exception {
		cc.getConnector().authorized();
		cc.log("Authenticated successfully");

		//-- Immediately send the inventory packet
		JsonPacket inventory = m_authHandler.getInventory();
		cc.getResponseEnvelope()
			.setInventory(Hubcore.ClientInventory.newBuilder()
				.setDataFormat(CommandNames.getJsonDataFormat(inventory))
			)
			;
		cc.respondJson(inventory);
	}

	@Override protected void onErrorPacket(Envelope env) {
		HubErrorResponse hubError = env.getHubError();
		log("HUB error: " + hubError.getCode() + " " + hubError.getText());
		forceDisconnect("HUB error received");
	}



}
