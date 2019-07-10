package to.etc.cocos.hub.parties;

import to.etc.cocos.hub.AbstractConnection;
import to.etc.cocos.hub.CentralSocketHandler;
import to.etc.cocos.hub.Hub;
import to.etc.hubserver.protocol.CommandNames;
import to.etc.hubserver.protocol.ErrorCode;
import to.etc.hubserver.protocol.FatalHubException;
import to.etc.hubserver.protocol.HubException;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.util.ByteBufferOutputStream;
import to.etc.util.ConsoleUtil;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
public class Server extends AbstractConnection {
	public Server(Cluster cluster, Hub systemContext, String id) {
		super(cluster, systemContext, id);
	}

	public void packetReceived(Envelope envelope) {
		if(!isUsable())
			throw new FatalHubException(ErrorCode.serverDisconnected);
		log("Packet received: " + envelope.getCommand());

		String targetId = envelope.getTargetId();
		if(targetId.length() == 0) {
			handleHubCommand(envelope);
		} else {
			Client client = getCluster().findClient(targetId);
			if(null != client) {
				client.packetFromServer(this, envelope);
			} else {
				CentralSocketHandler tmpClient = getDirectory().findTempClient(targetId);
				if(null == tmpClient) {
					throw new HubException(ErrorCode.clientNotFound, targetId);
				}
				tmpClient.tmpGotResponseFrom(this, envelope);
			}
		}
	}

	@Override
	public void log (String s){
		ConsoleUtil.consoleLog("Hub:Server", getFullId(), s);
	}


	private void handleHubCommand (Envelope envelope){
		log("HUB command packet received");

	}

	/**
	 * Send a "client unregistered" packet to the remote.
	 */
	public void sendEventClientUnregistered(String fullId) {
		getHandler().packetBuilder(CommandNames.CLIENT_DISCONNECTED)
			.sourceId(fullId)
			.send();
	}

	public void sendEventClientRegistered(String clientId) {
		getHandler().packetBuilder(CommandNames.CLIENT_CONNECTED)
			.sourceId(clientId)
			.send();
	}

	public void sendEventClientInventory(String clientId, ByteBufferOutputStream payload) {
		getHandler().packetBuilder(CommandNames.CLIENT_CONNECTED)
			.sourceId(clientId)
			.send();



	}
}
