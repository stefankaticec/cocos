package to.etc.cocos.connectors.common;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 10-1-19.
 */
public class ConnectorException extends RuntimeException {
	public ConnectorException(String message) {
		super(message);
	}

	public ConnectorException(Throwable cause, String message) {
		super(message, cause);
	}
}
