package to.etc.cocos.connectors.ifaces;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 15-5-19.
 */
final public class DaemonNotPresentException extends RuntimeException {
	public DaemonNotPresentException(String message) {
		super(message);
	}
}
