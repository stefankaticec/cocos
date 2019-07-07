package to.etc.cocos.connectors.server;

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
}
