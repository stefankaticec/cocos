package to.etc.hubclient;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 12-1-19.
 */
public class ConnectorDisconnectedException extends RuntimeException {
	public ConnectorDisconnectedException() {
		super("Server disconnected: EOF on input socket");
	}
}
