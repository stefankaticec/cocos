package to.etc.hubserver;

/**
 * @author <a href="mailto:jal@etc.to">Frits Jalvingh</a>
 * Created on 9-1-19.
 */
public class ProtocolViolationException extends RuntimeException {
	public ProtocolViolationException(String message) {
		super(message);
	}
}
