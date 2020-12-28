package to.etc.cocos.hub.telnetcommands;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.cocos.hub.CentralSocketHandler;
import to.etc.cocos.hub.Hub;
import to.etc.telnet.ITelnetCommandHandler;
import to.etc.telnet.TelnetPrintWriter;
import to.etc.util.CmdStringDecoder;

public class ListClientsTelnetCommandHandler implements ITelnetCommandHandler {

	private Hub m_hub;

	public ListClientsTelnetCommandHandler(Hub hub) {
		m_hub = hub;
	}

	@Override
	public boolean executeTelnetCommand(TelnetPrintWriter tpw, CmdStringDecoder csd) throws Exception {
		if(csd.currIs("client*")) {
			var connectedClients = m_hub.getDirectory().getConnectedClients();
			tpw.println("All connected clients: "+ connectedClients.size());
			for(CentralSocketHandler connectedClient : connectedClients) {
				tpw.println(connectedClient.getMyID());
			}
			return true;
		}
		return false;
	}
}
