package to.etc.cocos.connectors.ifaces;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 15-5-19.
 */
final public class RemoteClientNotPresentException extends RuntimeException {
	public RemoteClientNotPresentException(String message) {
		super(message);
	}
}
