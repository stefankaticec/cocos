package to.etc.cocos.connectors.client;

import com.google.protobuf.ByteString;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import to.etc.cocos.connectors.common.CommandContext;
import to.etc.cocos.connectors.common.CommandFailedException;
import to.etc.cocos.connectors.common.HubConnectorBase;
import to.etc.cocos.connectors.common.JsonBodyTransmitter;
import to.etc.cocos.connectors.common.JsonPacket;
import to.etc.cocos.connectors.common.Peer;
import to.etc.cocos.connectors.ifaces.RemoteCommandStatus;
import to.etc.cocos.connectors.packets.CancelPacket;
import to.etc.cocos.messages.Hubcore;
import to.etc.cocos.messages.Hubcore.ClientInventory;
import to.etc.cocos.messages.Hubcore.Command;
import to.etc.cocos.messages.Hubcore.Envelope;
import to.etc.cocos.messages.Hubcore.HubErrorResponse;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;

import java.util.ArrayList;
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
final public class HubClient extends HubConnectorBase<Peer> {
	private static final int KEEP_COMMAND_FOR_MILIS = 1_000 * 60 * 60;
	private final String m_clientVersion = "HubClient 1.0";

	private final String m_targetCluster;

	private final IClientAuthenticationHandler m_authHandler;

	private final Map<String, Supplier<IClientCommandHandler>> m_commandHandlerMap = new HashMap<>();

	private final Map<String, CommandContext> m_commandMap = new ConcurrentHashMap<>();

	private final List<CommandContext> m_runningCommandList = new ArrayList<>();

	private HubClient(String hubServer, int hubServerPort, IClientAuthenticationHandler authHandler, String targetClusterAndOrg, String myId) {
		super(hubServer, hubServerPort, targetClusterAndOrg, myId, "Client");
		m_authHandler = authHandler;
		if(targetClusterAndOrg.indexOf('@') != -1)
			throw new IllegalStateException("The target for a client must be in the format 'organisation#cluster' or just a cluster name");
		m_targetCluster = targetClusterAndOrg;
	}

	@Override
	public long getPeerDisconnectedDuration() {
		return 1 * 60 * 60 * 1000L;
	}

	@Override
	public boolean isTransmitBlocking() {
		return true;
	}

	@Override
	public int getMaxQueuedPackets() {
		return 8192;
	}

	static public HubClient create(IClientAuthenticationHandler handler, String hubServer, int hubServerPort, String targetClusterAndOrg, String myId) {
		HubClient responder = new HubClient(hubServer, hubServerPort, handler, targetClusterAndOrg, myId);
		responder.registerJsonCommandAsync(CancelPacket.class, () -> new CancelCommand(responder));
		return responder;
	}

	//@Override
	//protected void handleUnackablePackets(Envelope env, ArrayList<byte[]> body) throws Exception {
	//	throw new IllegalStateException("Unexpected packet type " + getPacketType(env));
	//}


	@Override
	protected void handleCLAUTH(Envelope env) throws Exception {
		throw new IllegalStateException("Unexpected packet type " + getPacketType(env));
	}

	@Override
	protected void handleAckable(CommandContext cc, ArrayList<byte[]> body) throws Exception {
		switch(cc.getSourceEnvelope().getAckable().getPayloadCase()) {
			default:
				throw new CommandFailedException("Unexpected ackable payload " + cc.getSourceEnvelope().getAckable().getPayloadCase());

			case CMD:
				handleCommand(cc, body);
				break;
		}
	}

	//@Override protected void handlePacketReceived(CommandContext ctx, List<byte[]> data) throws Exception {
	//	switch(ctx.getSourceEnvelope().getPayloadCase()) {
	//		default:
	//			throw new ProtocolViolationException("Unexpected packet type=" + ctx.getSourceEnvelope().getPayloadCase());
	//
	//		case CHALLENGE:
	//			handleHELO(ctx);
	//			break;
	//
	//		case AUTH:
	//			handleAUTH(ctx);
	//			break;
	//
	//		case CMD:
	//			handleCommand(ctx, data);
	//			break;
	//
	//		case HUBERROR:
	//			onErrorPacket(ctx.getSourceEnvelope());
	//			forceDisconnect("Hub error: " + ctx.getSourceEnvelope().getHubError().getText());
	//			break;
	//	}
	//}

	public synchronized void registerCommand(String commandName, Supplier<IClientCommandHandler> handler) {
		if(null != m_commandHandlerMap.put(commandName, handler))
			throw new IllegalStateException("Duplicate command name registered: " + commandName);
	}

	public synchronized <T extends JsonPacket> void registerJsonCommand(Class<T> packet, Supplier<IJsonCommandHandler<T>> handlerSupplier) {
		registerCommand(packet.getName(), () -> new SynchronousJsonCommandHandler<T>(handlerSupplier.get()));
	}

	public synchronized <T extends JsonPacket> void registerJsonCommandAsync(Class<T> packet, Supplier<IJsonCommandHandler<T>> handlerSupplier) {
		registerCommand(packet.getName(), () -> new AsynchronousJsonCommandHandler<T>(handlerSupplier.get()));
	}

	@Nullable
	private synchronized IClientCommandHandler findCommandHandler(String commandName) {
		Supplier<IClientCommandHandler> factory = m_commandHandlerMap.get(commandName);
		if(null == factory)
			return null;
		return factory.get();
	}

	private void handleCommand(CommandContext ctx, List<byte[]> data) throws Exception {
		Command cmd = ctx.getSourceEnvelope().getAckable().getCmd();
		IClientCommandHandler commandHandler = findCommandHandler(cmd.getName());
		if(null == commandHandler) {
			error("No command handler for " + cmd.getName());
			ctx.peer().sendCommandErrorPacket(ctx.getSourceEnvelope(), ErrorCode.commandNotFound, cmd.getName());
			return;
		}
		ctx.log("Running handler for " + cmd.getName() + " and command " + cmd.getId());

		m_commandMap.put(ctx.getId(), ctx);
		synchronized(this) {
			m_runningCommandList.add(ctx);
		}
		ctx.setHandler(commandHandler);
		ctx.markAsStarted();
		cleanupOldCommands();
		try {
			commandHandler.execute(ctx, data, throwable -> {
				synchronized(this) {
					m_runningCommandList.remove(ctx);
				}
				ctx.markAsFinished();
				ctx.setStatus(throwable == null ? RemoteCommandStatus.FINISHED : RemoteCommandStatus.FAILED);
				log("Command " + ctx.getId() + " completion handler called with exception=" + throwable);
			});
		} catch(Exception x) {
			ctx.log("Command " + cmd.getName() + " failed: " + x);
			x.printStackTrace();
			ctx.peer().sendCommandErrorPacket(ctx.getSourceEnvelope(), x);
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
	@Override
	protected void handleCHALLENGE(Envelope src) throws Exception {
		ByteString ba = src.getChallenge().getChallenge();
		byte[] challenge = ba.toByteArray();
		byte[] response = m_authHandler.createAuthenticationResponse(getMyId(), challenge);

		Envelope reply = responseEnvelope(src, src.getTargetId())
			.setTargetId(m_targetCluster)
			.setHeloClient(Hubcore.ClientHeloResponse.newBuilder()
				.setChallengeResponse(ByteString.copyFrom(response))
				.setClientVersion(m_clientVersion)
				.build()
			).build()
			;

		sendPacketPrimitive(reply, null, ()-> {
			forceDisconnect("Challenge response packet send failed");
		});
	}

	/**
	 * If the authorization was successful we receive this; move to AUTHORIZED status.
	 */
	@Override
	protected void handleAUTH(Envelope src) throws Exception {
		authorized();
		log("Authenticated successfully");

		//-- Immediately send the inventory packet
		JsonPacket inventory = m_authHandler.getInventory();
		Envelope response = responseEnvelope(src, src.getTargetId())
			.setInventory(ClientInventory.newBuilder()
				.setDataFormat(CommandNames.getJsonDataFormat(inventory))
			)
			.build();
		sendPacketPrimitive(response, new JsonBodyTransmitter(inventory), () -> {
			forceDisconnect("AUTH response inventory packet send failed");
		});
	}

	public void updateInventory() {
		JsonPacket inventory = m_authHandler.getInventory();
		var response = Envelope.newBuilder()
			.setVersion(1)
			.setTargetId("")
			.setSourceId(getMyId())
			.setInventory(ClientInventory.newBuilder()
				.setDataFormat(CommandNames.getJsonDataFormat(inventory))
			)
			.build();
		sendPacketPrimitive(response, new JsonBodyTransmitter(inventory), () -> {
			forceDisconnect("Inventory update packet send failed");
		});
	}

	@Override protected void onErrorPacket(Envelope env) {
		HubErrorResponse hubError = env.getHubError();
		log("HUB error: " + hubError.getCode() + " " + hubError.getText());
		forceDisconnect("HUB error received");
	}

	@Override
	protected Peer createPeer(String peerId) {
		return new Peer(this, peerId);
	}

	public int getRunningCommands() {
		return m_runningCommandList.size();
	}

	public CommandContext getCommand(String commandId) {
		return m_commandMap.get(commandId);
	}

	private void cleanupOldCommands() {
		new ArrayList<>(m_commandMap.values()).forEach(x->{
			var finished = x.getFinishedAt();
			if(finished != null) {
				if(finished.toEpochMilli() + KEEP_COMMAND_FOR_MILIS < System.currentTimeMillis()) {
					m_commandMap.remove(x.getId());
				}
			}
		});
	}
}
