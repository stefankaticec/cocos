package to.etc.cocos.hub.parties;

import to.etc.cocos.hub.HubServer;
import to.etc.puzzler.daemon.rpc.messages.Hubcore.Envelope;
import to.etc.util.ConsoleUtil;

/**
 * Represents a client (daemon).
 *
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 13-1-19.
 */
final public class Client extends AbstractConnection {
	public Client(Cluster cluster, HubServer context, String id) {
		super(cluster, context, id);
	}

	@Override
	public void log(String s) {
		ConsoleUtil.consoleLog("Hub:Server", getFullId(), s);
	}

	public void packetFromServer(Server server, Envelope envelope) {
		log("RX from server " + server.getFullId() + ": " + envelope.getCommand());
	}
}
