package to.etc.cocos.connectors.ifaces;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 24-01-20.
 */
public class RemoteClientException extends RuntimeException {
	public RemoteClientException() {
		super();
	}

	public RemoteClientException(String message) {
		super(message);
	}

	public RemoteClientException(Throwable cause, String message) {
		super(message, cause);
	}
}
