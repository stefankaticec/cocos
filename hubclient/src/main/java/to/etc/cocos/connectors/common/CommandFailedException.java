package to.etc.cocos.connectors.common;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 11-09-19.
 */
public class CommandFailedException extends RuntimeException {
	public CommandFailedException(String message) {
		super(message);
	}

	public CommandFailedException(Throwable cause, String message) {
		super(message, cause);
	}
}
