package to.etc.cocos.connectors.server;

import to.etc.cocos.connectors.ifaces.IServerEventType;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 07-07-19.
 */
public enum ServerEventType implements IServerEventType {
	serverConnected,
	serverDisconnected,
	clientConnected,
	clientDisconnected,
	clientInventoryReceived,

	//-- Commands
	commandError,
	commandOutput,
	commandFinished,

}
