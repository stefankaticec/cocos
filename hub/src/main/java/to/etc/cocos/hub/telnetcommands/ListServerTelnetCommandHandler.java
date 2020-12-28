package to.etc.cocos.hub.telnetcommands;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.hub.Hub;
import to.etc.cocos.hub.parties.Client;
import to.etc.cocos.hub.parties.Cluster;
import to.etc.cocos.hub.parties.Server;
import to.etc.telnet.ITelnetCommandHandler;
import to.etc.telnet.TelnetPrintWriter;
import to.etc.util.CmdStringDecoder;

import java.util.Collection;
import java.util.List;

public class ListServerTelnetCommandHandler implements ITelnetCommandHandler {

	private final Hub m_hub;

	public ListServerTelnetCommandHandler(Hub hub) {
		m_hub = hub;
	}

	@Override
	public boolean executeTelnetCommand(TelnetPrintWriter tpw, CmdStringDecoder csd) throws Exception {
		if(csd.currIs("serve*")) {
			Collection<Cluster> connectedClusters = m_hub.getDirectory().getConnectedClusters();
			tpw.println("All connected clusters: "+ connectedClusters.size());
			for(Cluster cluster : connectedClusters) {
				tpw.println("Cluster: "+ cluster.getClusterId());
				List<Server> servers = cluster.getAllServers();
				tpw.println("Servers: " + servers.size());
				for(Server server : servers) {
					tpw.println("Connected: " + server.getFullId());
				}
				tpw.println("-----------------------------");
				var clients = cluster.getAllClients();
				tpw.println("Authenticated clients: " + clients.size());
				for(Client client : clients) {
					System.out.println("Connected client: " + client.getFullId());
				}
			}
			return true;
		}
		return false;
	}
}
