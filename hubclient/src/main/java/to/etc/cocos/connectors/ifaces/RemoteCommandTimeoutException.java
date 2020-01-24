package to.etc.cocos.connectors.ifaces;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 24-01-20.
 */
final public class RemoteCommandTimeoutException extends RemoteClientException {
	public RemoteCommandTimeoutException() {
		super("Remote Command timed out");
	}
}
