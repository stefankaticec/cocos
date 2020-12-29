package to.etc.cocos.hub.telnetcommands;

import org.eclipse.jdt.annotation.NonNullByDefault;
import to.etc.telnet.ITelnetCommandHandler;
import to.etc.telnet.TelnetPrintWriter;
import to.etc.util.CmdStringDecoder;

public class HelpTelnetCommandHandler implements ITelnetCommandHandler {
	@Override
	public boolean executeTelnetCommand(TelnetPrintWriter tpw, CmdStringDecoder csd) throws Exception {
		if(csd.currIs("?")){
			tpw.println("Usage: clients");
			tpw.println("Usage: servers");
			return true;
		}
		return false;
	}
}
